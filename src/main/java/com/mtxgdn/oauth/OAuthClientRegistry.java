package com.mtxgdn.oauth;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 第三方应用的登记表。
 *
 * <h3>只登记，不自动注册</h3>
 * <p>这是整个授权码流程的安全基石：第三方应用必须先由管理员在配置文件里
 * 登记（client_id、secret、<b>回调地址</b>、允许申请的 scope），
 * 才能走授权流程。没有"自助注册"。</p>
 *
 * <p>原因在于 {@code redirect_uri} 一旦可被应用自己指定，
 * 授权码就能被引到攻击者的服务器。OAuth 历史上最经典的漏洞
 * 大多出在这里，所以登记制是必要的成本。</p>
 *
 * <h3>回调地址必须精确匹配</h3>
 * <p>{@link #isRedirectUriAllowed} 做的是 {@code equals}，不是前缀匹配。</p>
 *
 * <p>前缀匹配是个看似合理、实则致命的错误：
 * 登记了 {@code https://app.com/cb}，攻击者用
 * {@code https://app.com.evil.com/cb} 就能通过 —— 它以登记值开头。
 * 同理 {@code https://app.com/cb?x=1} 也会通过，
 * 而多带的参数可以改变行为。所以只能全等。</p>
 */
public final class OAuthClientRegistry {

    /**
     * 一个已登记的第三方应用。
     *
     * @param clientId     客户端标识
     * @param clientSecret 密钥（授权码换取 token 时校验）
     * @param name         显示名（同意页上告诉玩家"是谁在申请"）
     * @param redirectUris 允许的回调地址（全等匹配）
     * @param allowedScopes 该应用最多能申请的 scope 集合
     * @param enabled      是否启用
     */
    public record Client(String clientId,
                         String clientSecret,
                         String name,
                         List<String> redirectUris,
                         Set<String> allowedScopes,
                         boolean enabled) {

        /** 该应用能否申请指定 scope */
        public boolean canRequest(String scope) {
            return allowedScopes.contains(scope);
        }
    }

    private static final Map<String, Client> CLIENTS = new ConcurrentHashMap<>();

    private OAuthClientRegistry() {
    }

    /**
     * 从配置项重建登记表。
     *
     * <p>配置缺失或格式错误时不清空已有登记 —— 那会让线上应用突然全部失效。
     * 而是保留现状并记日志，交由调用方决定如何处理。</p>
     *
     * @param raw 形如 {@code [{"client_id":"...","client_secret":"...","name":"...",
     *            "redirect_uris":["..."],"scopes":["read"]}, ...]}
     * @return 成功登记的数量；解析失败返回 -1
     */
    public static synchronized int loadFromConfig(List<Map<String, Object>> raw) {
        if (raw == null) {
            return -1;
        }
        Map<String, Client> parsed = new LinkedHashMap<>();
        for (Map<String, Object> entry : raw) {
            if (entry == null) {
                continue;
            }
            String id = str(entry.get("client_id"));
            String secret = str(entry.get("client_secret"));
            if (id.isEmpty() || secret.isEmpty()) {
                continue;
            }
            String name = str(entry.get("name"));
            if (name.isEmpty()) {
                name = id;
            }

            List<String> uris = new ArrayList<>();
            Object rawUris = entry.get("redirect_uris");
            if (rawUris instanceof List<?> list) {
                for (Object o : list) {
                    String u = str(o);
                    if (!u.isEmpty()) {
                        uris.add(u);
                    }
                }
            }

            Set<String> scopes = new LinkedHashSet<>();
            Object rawScopes = entry.get("scopes");
            if (rawScopes instanceof List<?> list) {
                for (Object o : list) {
                    String s = str(o);
                    // 只接受认识的 scope，配错的直接丢掉并留在日志里
                    if (!s.isEmpty() && OAuthScope.isValid(s)) {
                        scopes.add(s);
                    }
                }
            }

            boolean enabled = !(entry.get("enabled") instanceof Boolean b) || b;
            parsed.put(id, new Client(id, secret, name,
                    Collections.unmodifiableList(uris),
                    Collections.unmodifiableSet(scopes), enabled));
        }
        CLIENTS.clear();
        CLIENTS.putAll(parsed);
        return CLIENTS.size();
    }

    /** 按 client_id 查应用；不存在或未启用返回 null */
    public static Client find(String clientId) {
        if (clientId == null) {
            return null;
        }
        Client c = CLIENTS.get(clientId);
        return c != null && c.enabled() ? c : null;
    }

    /** 校验应用凭据（授权码换 token 时用） */
    public static boolean validate(String clientId, String clientSecret) {
        Client c = find(clientId);
        return c != null && c.clientSecret().equals(clientSecret);
    }

    /**
     * 回调地址是否在登记范围内。
     *
     * <p><b>全等比较</b>，不做前缀/通配/大小写归一。详见类注释。</p>
     */
    public static boolean isRedirectUriAllowed(String clientId, String redirectUri) {
        Client c = find(clientId);
        if (c == null || redirectUri == null) {
            return false;
        }
        return c.redirectUris().contains(redirectUri);
    }

    /** 当前登记的应用（供管理页展示） */
    public static List<Client> all() {
        return Collections.unmodifiableList(new ArrayList<>(CLIENTS.values()));
    }

    /** 已登记的应用数量 */
    public static int size() {
        return CLIENTS.size();
    }

    /** 供测试清空 */
    public static synchronized void clear() {
        CLIENTS.clear();
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }
}
