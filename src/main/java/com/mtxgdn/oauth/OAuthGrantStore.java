package com.mtxgdn.oauth;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 玩家对第三方应用的授权记录。
 *
 * <h3>这个类解决三件事</h3>
 * <ol>
 *   <li><b>"哪些应用能读我的数据"</b> —— 已授权应用列表页的数据来源；</li>
 *   <li><b>撤销</b> —— 玩家点一下，该应用拿到的 token 立即失效；</li>
 *   <li><b>refresh_token 轮换</b> —— 每次刷新都换新，旧码作废。</li>
 * </ol>
 *
 * <h3>为什么撤销要能立刻生效</h3>
 * <p>access_token 是无状态 JWT，签出去后服务端不记录。
 * 如果只靠"过期自然失效"，玩家撤销后攻击者还能继续用一个小时。</p>
 *
 * <p>所以这里保存每次授权产生的 token 的<b>标识</b>（jti），
 * 校验 token 时额外查一次：这个 jti 是否还在有效授权里。
 * 撤销时删掉整条授权记录，jti 查不到，token 立刻失效。</p>
 *
 * <p>代价是每个请求多一次内存查表 —— 可接受，且远小于查库。
 * 这里不追求无状态，安全性优先。</p>
 *
 * <h3>refresh_token 轮换与重放检测</h3>
 * <p>每次刷新都发新 refresh_token，旧的立刻失效。如果某个<b>已失效</b>的
 * refresh_token 又来请求，说明它可能已泄露（合法客户端不会重放），
 * 这时吊销整条授权链，强制用户重新授权。RFC 6749 §10.4 的建议。</p>
 */
public final class OAuthGrantStore {

    /** refresh_token 有效期：30 天 */
    public static final long REFRESH_TTL_MS = 30L * 24 * 60 * 60 * 1000;

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * 一条授权记录：某玩家允许某应用以某组 scope 访问。
     *
     * <p>{@code grantId} 是稳定标识（userId + clientId 的组合），
     * 玩家撤销时按它删除。</p>
     */
    public static final class Grant {
        private final String grantId;
        private final long userId;
        private final String username;
        private final String clientId;
        private final Set<String> scopes;
        private final long createdAt;
        private volatile long lastUsedAt;

        Grant(String grantId, long userId, String username, String clientId, Set<String> scopes) {
            this(grantId, userId, username, clientId, scopes,
                    System.currentTimeMillis(), System.currentTimeMillis());
        }

        /**
         * 从持久化记录重建（保留原始授权时间，不能重置成 now）。
         *
         * <p>重启后如果 createdAt 变成 now，「已授权应用」页会显示
         * "刚刚授权"，玩家会以为有陌生应用刚获取了权限。</p>
         */
        Grant(String grantId, long userId, String username, String clientId, Set<String> scopes,
              long createdAt, long lastUsedAt) {
            this.grantId = grantId;
            this.userId = userId;
            this.username = username;
            this.clientId = clientId;
            this.scopes = new LinkedHashSet<>(scopes);
            this.createdAt = createdAt;
            this.lastUsedAt = lastUsedAt;
        }

        /** 这条授权的最晚有效期（refresh_token 的期限），用于数据库清理 */
        long expiresAt() {
            return createdAt + REFRESH_TTL_MS;
        }

        public String grantId() {
            return grantId;
        }

        public long userId() {
            return userId;
        }

        public String username() {
            return username;
        }

        public String clientId() {
            return clientId;
        }

        public Set<String> scopes() {
            return Set.copyOf(scopes);
        }

        public long createdAt() {
            return createdAt;
        }

        public long lastUsedAt() {
            return lastUsedAt;
        }
    }

    /** grantId → 授权记录 */
    private static final Map<String, Grant> GRANTS = new ConcurrentHashMap<>();

    /** 有效的 access_token jti → grantId（撤销时据此让 token 失效） */
    private static final Map<String, String> ACTIVE_TOKEN_JTI = new ConcurrentHashMap<>();

    /** 有效的 refresh_token 哈希 → RefreshEntry */
    private static final Map<String, RefreshEntry> REFRESH = new ConcurrentHashMap<>();

    /** 已失效的 refresh_token 哈希 → 所属 grantId（用于检测重放） */
    private static final Map<String, String> USED_REFRESH = new ConcurrentHashMap<>();

    /** 持久化入口。DAO 内部已做异常降级，这里不用再包 try/catch。 */
    private static final com.mtxgdn.db.OAuthGrantsDAO DAO = new com.mtxgdn.db.OAuthGrantsDAO();

    private record RefreshEntry(String grantId, long expiresAt) {
        boolean expired(long now) {
            return now > expiresAt;
        }
    }

    private OAuthGrantStore() {
    }

    // ==================== 启动加载 ====================

    /**
     * 从数据库载入全部有效授权。
     *
     * <p>不载入任何 access_token —— 它们是无状态 JWT，重启后仍需重新校验。
     * 但授权<b>记录</b>要保住：否则玩家每次重启服务器都得把每个第三方应用
     * 重新授权一遍。</p>
     *
     * <p>载入后 {@code ACTIVE_TOKEN_JTI} 是空的，意味着重启前签发的 token
     * 会因为 {@link #isTokenActive} 查不到 jti 而失效。这是刻意的：
     * 我们不持久化 token 的 jti（量太大且很快过期），重启后要求客户端
     * 用 refresh_token 换新的即可。refresh_token 同样是内存态、重启失效，
     * 所以实际效果是「重启后需要重新走一次授权码流程」。</p>
     *
     * <p><b>这个取舍需要明确记录</b>：持久化的是"玩家授权过谁"这件事，
     * 不是会话本身。好处是「已授权应用」页在重启后仍然正确，
     * 玩家能撤销；坏处是第三方需要重新拿一次 token。</p>
     *
     * @return 载入的授权条数；数据库不可用时返回 0
     */
    public static int loadFromDatabase() {
        GRANTS.clear();
        ACTIVE_TOKEN_JTI.clear();
        REFRESH.clear();
        USED_REFRESH.clear();

        java.util.List<com.mtxgdn.db.OAuthGrantsDAO.Row> rows = DAO.findAllActive();
        for (com.mtxgdn.db.OAuthGrantsDAO.Row row : rows) {
            Set<String> scopes = OAuthScope.parse(row.scopes());
            GRANTS.put(row.grantId(), new Grant(
                    row.grantId(), row.userId(), row.username(), row.clientId(),
                    scopes, row.createdAt(), row.lastUsedAt()));
        }
        return GRANTS.size();
    }

    // ==================== 授权 ====================

    /**
     * 建立或更新一条授权。同一玩家对同一应用只保留一条（scope 以最新同意的为准）。
     *
     * <p>同时写入内存与数据库。数据库写失败不影响本次授权（内存已生效），
     * 只是重启后需要重新授权 —— 这个降级方向是安全的。</p>
     */
    public static Grant authorize(long userId, String username, String clientId, Set<String> scopes) {
        String grantId = grantId(userId, clientId);
        Grant grant = new Grant(grantId, userId, username, clientId, scopes);
        GRANTS.put(grantId, grant);

        DAO.upsert(grantId, userId, username, clientId,
                OAuthScope.join(scopes), grant.createdAt(), grant.lastUsedAt(), grant.expiresAt());
        return grant;
    }

    public static Grant findGrant(String grantId) {
        return grantId == null ? null : GRANTS.get(grantId);
    }

    /** 某玩家的全部授权（已授权应用页用） */
    public static List<Grant> grantsOf(long userId) {
        List<Grant> result = new ArrayList<>();
        for (Grant g : GRANTS.values()) {
            if (g.userId() == userId) {
                result.add(g);
            }
        }
        result.sort((a, b) -> Long.compare(b.lastUsedAt(), a.lastUsedAt()));
        return result;
    }

    /**
     * 撤销一条授权。
     *
     * <p>做三件事：删授权记录、吊销该授权下所有 access_token、删掉 refresh_token。
     * 三步都做完才算真正撤销 —— 只删记录而留着 jti，
     * 会出现"记录没了但 token 还能用"的窗口。</p>
     *
     * @return 是否确实撤销了一条存在的授权
     */
    public static boolean revoke(String grantId) {
        if (grantId == null) {
            return false;
        }
        Grant removed = GRANTS.remove(grantId);
        if (removed == null) {
            return false;
        }
        ACTIVE_TOKEN_JTI.entrySet().removeIf(e -> e.getValue().equals(grantId));
        REFRESH.entrySet().removeIf(e -> e.getValue().grantId().equals(grantId));

        // 落库用标记而不是删行：保留"曾经授权过"的事实便于追溯。
        // 真删由 purgeExpired 在 refresh_token 都过期之后做。
        DAO.markRevoked(grantId);
        return true;
    }

    /** 撤销某玩家对某应用的授权 */
    public static boolean revoke(long userId, String clientId) {
        return revoke(grantId(userId, clientId));
    }

    /** 玩家撤销自己全部授权（注销/恐慌时用） */
    public static int revokeAllOf(long userId) {
        int count = 0;
        for (Grant g : grantsOf(userId)) {
            if (revoke(g.grantId())) {
                count++;
            }
        }
        return count;
    }

    // ==================== access_token 登记 ====================

    /**
     * 登记一个刚签发的 access_token，使它可被撤销。
     *
     * @param jti     token 的唯一标识（写进 JWT 的 jti claim）
     * @param grantId 所属授权
     */
    public static void registerToken(String jti, String grantId) {
        if (jti != null && grantId != null) {
            ACTIVE_TOKEN_JTI.put(jti, grantId);
        }
    }

    /**
     * 该 token 是否仍属于一条有效授权。
     *
     * <p>返回 false 表示：授权已被撤销，或者 token 不是本系统签发的用户型 token。</p>
     */
    public static boolean isTokenActive(String jti) {
        if (jti == null) {
            return false;
        }
        String grantId = ACTIVE_TOKEN_JTI.get(jti);
        if (grantId == null) {
            return false;
        }
        Grant grant = GRANTS.get(grantId);
        if (grant == null) {
            ACTIVE_TOKEN_JTI.remove(jti);
            return false;
        }
        touchLastUsed(grant);
        return true;
    }

    /** lastUsedAt 落库的最小间隔 —— 这个字段只用于展示，不值得每个请求都写一次库 */
    private static final long TOUCH_INTERVAL_MS = 60_000L;

    /**
     * 更新最近使用时间。
     *
     * <p>内存每次都更新（列表排序用），数据库按 {@link #TOUCH_INTERVAL_MS} 节流 ——
     * 否则一个活跃的第三方应用会把每次 API 调用都变成一次 UPDATE。</p>
     */
    private static void touchLastUsed(Grant grant) {
        long now = System.currentTimeMillis();
        long previous = grant.lastUsedAt;
        grant.lastUsedAt = now;
        if (now - previous >= TOUCH_INTERVAL_MS) {
            DAO.touch(grant.grantId(), now);
        }
    }

    // ==================== refresh_token ====================

    /** 为一条授权签发 refresh_token */
    public static String issueRefreshToken(String grantId) {
        String token = randomToken();
        REFRESH.put(OAuthCodeStore.sha256(token),
                new RefreshEntry(grantId, System.currentTimeMillis() + REFRESH_TTL_MS));
        return token;
    }

    /**
     * 用 refresh_token 换新的一对 token。
     *
     * <p>成功时旧 refresh_token 立即作废并返回新码；失败返回 {@code null}。</p>
     *
     * <p><b>重放检测</b>：如果这个 refresh_token 之前已经用过（在 USED_REFRESH 里），
     * 说明它可能泄露给了第三方。这时吊销整条授权链 ——
     * 合法客户端不会重放旧码，重放只可能来自攻击者或者被窃取的副本。</p>
     */
    public static RotationResult rotate(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return null;
        }
        String hash = OAuthCodeStore.sha256(refreshToken);

        // 重放检测：这个码之前被用过，说明泄露了
        String replayGrant = USED_REFRESH.get(hash);
        if (replayGrant != null) {
            revoke(replayGrant);
            return null;
        }

        RefreshEntry entry = REFRESH.remove(hash);
        if (entry == null) {
            return null;
        }
        if (entry.expired(System.currentTimeMillis())) {
            return null;
        }
        Grant grant = GRANTS.get(entry.grantId());
        if (grant == null) {
            return null;
        }

        // 旧码标记为已用，下次再来就知道是重放
        USED_REFRESH.put(hash, entry.grantId());
        grant.lastUsedAt = System.currentTimeMillis();

        return new RotationResult(grant, issueRefreshToken(entry.grantId()));
    }

    /**
     * 轮换结果。
     *
     * @param grant        所属授权
     * @param refreshToken 新的 refresh_token
     */
    public record RotationResult(Grant grant, String refreshToken) {
    }

    // ==================== 维护 ====================

    /**
     * 清理过期数据。
     *
     * <p>内存里清过期 refresh_token；数据库里删掉「已撤销且早已过期」的记录
     * （撤销记录要留一段时间便于追溯，不能撤销即删）。</p>
     */
    public static void purgeExpired() {
        long now = System.currentTimeMillis();
        REFRESH.entrySet().removeIf(e -> e.getValue().expired(now));
        // USED_REFRESH 只用于重放检测，超过阈值直接清空即可
        if (USED_REFRESH.size() > 10_000) {
            USED_REFRESH.clear();
        }
        DAO.purgeExpired(now);
    }

    public static int grantCount() {
        return GRANTS.size();
    }

    /**
     * 清空内存状态。
     *
     * <p>只清内存、<b>不</b>删数据库 —— 测试用它保证用例之间互不影响，
     * 而生产代码没有"清空所有玩家授权"的场景。真要清库请显式调 DAO。</p>
     */
    public static void clear() {
        GRANTS.clear();
        ACTIVE_TOKEN_JTI.clear();
        REFRESH.clear();
        USED_REFRESH.clear();
    }

    // ==================== 工具 ====================

    private static String grantId(long userId, String clientId) {
        return userId + ":" + clientId;
    }

    private static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
