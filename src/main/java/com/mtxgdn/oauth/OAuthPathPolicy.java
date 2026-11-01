package com.mtxgdn.oauth;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 把游戏 API 路径映射到它所属的资源域。
 *
 * <h3>为什么需要这张表</h3>
 * <p>scope 是 {@code inventory:read} 这种形式的，而端点路径是
 * {@code /api/game/inventory}。要让「第三方只能读背包」这条规则真正落地，
 * 必须在请求进来时判断：<b>这个路径属于哪个域？</b></p>
 *
 * <p>没有这张表就只能按路径前缀瞎猜，而游戏路径并不总是与域同名
 * （{@code /game/equipment} 属于 inventory 域、{@code /game/market} 属于
 * economy 域、{@code /game/players/search} 属于 public 域）。</p>
 *
 * <h3>默认拒绝</h3>
 * <p>表里查不到的路径一律视为<b>不可通过 OAuth 访问</b>，而不是"放行"。
 * 这条规则很重要：将来新增端点时，如果忘了在这里登记，结果是第三方访问不到，
 * 而不是意外获得了未登记的新能力。宁可功能缺失，不可权限外溢。</p>
 */
public final class OAuthPathPolicy {

    /**
     * 路径前缀 → 资源域。
     *
     * <p>按最长前缀匹配，所以 {@code game/players/search} 会先命中
     * {@code game/players}，不会落到 {@code game/player}。</p>
     */
    private static final Map<String, String> PREFIX_TO_DOMAIN = new LinkedHashMap<>();

    /** 路径前缀 → 该端点的读写性质覆盖（默认由 HTTP 方法决定） */
    private static final Map<String, Boolean> FORCE_WRITE = new LinkedHashMap<>();

    private OAuthPathPolicy() {
    }

    static {
        // ---------- 公共数据：不含任何玩家私有信息 ----------
        domain("game/public", "public");
        domain("game/players", "public");        // 玩家列表/搜索（公开名录）
        domain("game/spiritual_roots", "public");
        domain("game/item/registry", "public");  // 物品图鉴
        domain("game/map", "public");
        domain("game/rank", "public");
        domain("game/system", "public");

        // ---------- 角色 ----------
        domain("game/player", "player");
        domain("game/buff", "player");

        // ---------- 修炼 ----------
        domain("game/cultivation", "cultivation");
        domain("game/realm", "cultivation");
        domain("game/technique", "cultivation");
        domain("game/daily", "cultivation");

        // ---------- 背包 / 装备 / 技能 ----------
        domain("game/inventory", "inventory");
        domain("game/equipment", "inventory");
        domain("game/skill", "inventory");
        domain("game/item", "inventory");

        // ---------- 经济 ----------
        domain("game/market", "economy");
        domain("game/economy", "economy");

        // ---------- 宗门 ----------
        domain("game/sect", "sect");

        // ---------- 社交 ----------
        domain("game/friend", "social");
        domain("game/team", "social");
        domain("game/message", "social");
        domain("game/chat", "social");

        // ---------- 战斗：读战报属角色数据，开打属写 ----------
        domain("game/battle", "player");
        forceWrite("game/battle");

        // ---------- 农场 ----------
        domain("game/farm", "inventory");
        forceWrite("game/farm");
    }

    /**
     * 登记一个路径前缀所属的资源域。
     *
     * @param prefix         API 路径前缀（相对 {@code /api}）
     * @param resourceDomain 资源域名，<b>不带</b> {@code :read}/{@code :write} 后缀，
     *                       如 {@code inventory}。完整 scope 由
     *                       {@link #requiredScope} 按 HTTP 方法拼出来。
     */
    private static void domain(String prefix, String resourceDomain) {
        PREFIX_TO_DOMAIN.put(prefix, resourceDomain);
    }

    private static void forceWrite(String prefix) {
        FORCE_WRITE.put(prefix, Boolean.TRUE);
    }

    /**
     * 找出路径所属的资源域。
     *
     * @param apiPath 相对 {@code /api} 的路径，如 {@code game/inventory}
     * @return 资源域（如 {@code inventory}），未知返回 {@code null}
     */
    public static String domainOfPath(String apiPath) {
        String p = normalize(apiPath);
        if (p.isEmpty()) {
            return null;
        }
        String bestPrefix = null;
        for (String prefix : PREFIX_TO_DOMAIN.keySet()) {
            if (matches(p, prefix)) {
                // 最长前缀优先
                if (bestPrefix == null || prefix.length() > bestPrefix.length()) {
                    bestPrefix = prefix;
                }
            }
        }
        return bestPrefix == null ? null : PREFIX_TO_DOMAIN.get(bestPrefix);
    }

    /**
     * 该路径需要的是读权限还是写权限。
     *
     * <p>默认按 HTTP 方法判断（GET → 读，其余 → 写），
     * 但有些端点用 POST 做"查询"（如带筛选条件的搜索），
     * 这类在 {@link #forceWrite} 里显式标注。</p>
     */
    public static boolean requiresWrite(String apiPath, String httpMethod) {
        String p = normalize(apiPath);
        for (String prefix : FORCE_WRITE.keySet()) {
            if (matches(p, prefix)) {
                return true;
            }
        }
        return !"GET".equalsIgnoreCase(httpMethod) && !"HEAD".equalsIgnoreCase(httpMethod);
    }

    /**
     * 计算访问该请求所需的 scope。
     *
     * @return scope 名；路径不在白名单内返回 {@code null}（应拒绝）
     */
    public static String requiredScope(String apiPath, String httpMethod) {
        String domain = domainOfPath(apiPath);
        if (domain == null) {
            return null;
        }
        boolean write = requiresWrite(apiPath, httpMethod);
        return domain + (write ? ":write" : ":read");
    }

    private static String normalize(String path) {
        if (path == null) {
            return "";
        }
        String p = path.trim();
        // 去掉查询串（调用方可能传完整 URI）
        int q = p.indexOf('?');
        if (q >= 0) {
            p = p.substring(0, q);
        }
        // 去掉 /api 前缀，方便调用方两种写法都行
        if (p.startsWith("/api/")) {
            p = p.substring(5);
        } else if (p.startsWith("api/")) {
            p = p.substring(4);
        }
        while (p.startsWith("/")) {
            p = p.substring(1);
        }
        return p;
    }

    /** 前缀匹配：要求边界是 {@code /} 或结尾，避免 {@code game/player} 匹配到 {@code game/playersxxx} */
    private static boolean matches(String path, String prefix) {
        return path.equals(prefix) || path.startsWith(prefix + "/");
    }

    /** 已登记的资源域前缀数量，供测试断言表没被清空 */
    public static int registeredPrefixCount() {
        return PREFIX_TO_DOMAIN.size();
    }
}
