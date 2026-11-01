package com.mtxgdn.oauth;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 授权码的签发与消费。
 *
 * <p>授权码是"用户同意过一次"的凭证，用以换取 access_token。
 * 它的生命周期极短且必须一次性，因此存在内存里即可（重启失效是可接受的，
 * 毕竟它只活 10 分钟）。</p>
 *
 * <h3>三条硬性约束</h3>
 *
 * <p><b>1. 一次性</b> —— {@link #consume} 先 {@code remove} 再校验。
 * 先校验后删除会留下"两个并发请求都能通过"的窗口，虽然概率低但没必要留。</p>
 *
 * <p><b>2. 绑定 client_id 与 redirect_uri</b> —— 换取时必须与签发时一致。
 * 否则 A 应用截获 B 应用的授权码就能换到 token（OAuth 2.0 的经典攻击）。</p>
 *
 * <p><b>3. 过期即失效</b> —— 默认 10 分钟。授权码只是一次跳转的中转，
 * 正常情况下几秒内就被消费掉，给 10 分钟已经很宽松。</p>
 *
 * <h3>为什么存的是哈希而不是原文</h3>
 * <p>授权码会经过浏览器地址栏、Referer 头、代理服务器日志、浏览器历史。
 * 万一内存被 dump，存原文等于把所有人的授权码一起暴露。
 * 所以这里存 SHA-256，换取时用同样的方式算一遍再比对 —— 与密码存储同理。</p>
 */
public final class OAuthCodeStore {

    /** 授权码有效期：10 分钟 */
    public static final long CODE_TTL_MS = 10 * 60 * 1000L;

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * 一个待消费的授权码。
     *
     * @param codeHash     授权码的 SHA-256（不存原文）
     * @param clientId     签发时绑定的应用
     * @param redirectUri  签发时绑定的回调地址
     * @param userId       授权的玩家
     * @param username     玩家名（签发 token 时写进 token，避免再查库）
     * @param scopes       玩家同意的 scope
     * @param expiresAt    过期时间戳
     */
    public record Entry(String codeHash,
                        String clientId,
                        String redirectUri,
                        long userId,
                        String username,
                        Set<String> scopes,
                        long expiresAt) {

        boolean expired(long now) {
            return now > expiresAt;
        }
    }

    private static final Map<String, Entry> CODES = new ConcurrentHashMap<>();

    private OAuthCodeStore() {
    }

    /**
     * 签发一个授权码。
     *
     * @return 授权码原文（只在这一次返回，之后无法再取回）
     */
    public static String issue(String clientId, String redirectUri, long userId,
                               String username, Set<String> scopes) {
        purgeExpired();

        String code = randomToken();
        String hash = sha256(code);
        CODES.put(hash, new Entry(hash, clientId, redirectUri, userId, username,
                Set.copyOf(scopes), System.currentTimeMillis() + CODE_TTL_MS));
        return code;
    }

    /**
     * 消费一个授权码。
     *
     * <p>无论成功失败，这个码都会被移除 —— 失败也不允许重试，
     * 否则攻击者可以拿同一个码反复试探不同的 client_id。</p>
     *
     * @return 成功返回条目；码不存在/已过期/client 或 redirect 不匹配返回 {@code null}
     */
    public static Entry consume(String code, String clientId, String redirectUri) {
        if (code == null || code.isBlank()) {
            return null;
        }
        // 先移除：保证"一个码只能用一次"，即使后面校验失败也不再可用
        Entry entry = CODES.remove(sha256(code));
        if (entry == null) {
            return null;
        }
        if (entry.expired(System.currentTimeMillis())) {
            return null;
        }
        if (!entry.clientId().equals(clientId)) {
            // 授权码不是发给这个应用的 —— 可能是截获尝试
            return null;
        }
        if (redirectUri != null && !entry.redirectUri().equals(redirectUri)) {
            return null;
        }
        return entry;
    }

    /** 当前待消费的授权码数量（供测试与监控） */
    public static int size() {
        return CODES.size();
    }

    /** 清空（供测试） */
    public static void clear() {
        CODES.clear();
    }

    /** 清理过期条目，避免长跑积累 */
    private static void purgeExpired() {
        long now = System.currentTimeMillis();
        CODES.entrySet().removeIf(e -> e.getValue().expired(now));
    }

    /** 32 字节随机数，URL 安全的 Base64 表示 */
    private static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是所有 JVM 都必须提供的，走到这里说明运行环境被裁剪过
            throw new IllegalStateException("当前 JVM 不支持 SHA-256，无法安全处理授权码", e);
        }
    }
}
