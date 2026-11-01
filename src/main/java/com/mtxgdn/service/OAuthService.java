package com.mtxgdn.service;

import com.mtxgdn.util.AppConfig;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import com.mtxgdn.i18n.I18n;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * OAuth 2.0 支持（client_credentials）。
 *
 * <p>提供标准令牌签发/校验/内省，并新增了<b>受保护资源端点</b>（见 {@code OAuthResource}），
 * 使签发的 token 可以真正"用起来"：访问 /api/auth/oauth/me、/api/auth/oauth/protected/* 等。</p>
 *
 * <p>配置支持运行时修改并持久化到 config/application.yml（通过管理后台 /api/admin/oauth/config 或 WebUI）。</p>
 */
public class OAuthService {

    private static volatile boolean enabled = AppConfig.getBoolean("oauth2.enabled", true);
    private static volatile String clientId = AppConfig.get("oauth2.client_id", "xiuxian_oauth_client");
    private static volatile String clientSecret = AppConfig.get("oauth2.client_secret", "xiuxian_oauth_secret_please_change");
    private static volatile String scopes = AppConfig.get("oauth2.scopes", "read write");
    private static volatile long tokenTtlMs = Math.max(60, AppConfig.getInt("oauth2.token_ttl_seconds", 3600)) * 1000L;

    private static volatile SecretKey secretKey = buildKey(clientSecret);

    private OAuthService() {
    }

    private static SecretKey buildKey(String secret) {
        byte[] raw = secret.getBytes(StandardCharsets.UTF_8);
        if (raw.length < 32) {
            // 补齐到至少 256 位，避免 HS256 密钥过短
            byte[] padded = new byte[32];
            System.arraycopy(raw, 0, padded, 0, raw.length);
            for (int i = raw.length; i < 32; i++) {
                padded[i] = (byte) ('x' + i);
            }
            return Keys.hmacShaKeyFor(padded);
        }
        return Keys.hmacShaKeyFor(raw);
    }

    // ==================== 配置 ====================

    public static boolean isEnabled() {
        return enabled;
    }

    public static String getClientId() {
        return clientId;
    }

    public static String getScopes() {
        return scopes;
    }

    public static long getTokenTtlSeconds() {
        return tokenTtlMs / 1000;
    }

    /** 获取当前配置（secret 默认打码，revealSecret=true 时返回明文） */
    public static Map<String, Object> getConfig(boolean revealSecret) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("enabled", enabled);
        config.put("client_id", clientId);
        config.put("client_secret", revealSecret ? clientSecret : maskSecret(clientSecret));
        config.put("scopes", scopes);
        config.put("token_ttl_seconds", getTokenTtlSeconds());
        config.put("persistPath", persistPath());
        return config;
    }

    private static String maskSecret(String secret) {
        if (secret == null || secret.isEmpty()) {
            return "";
        }
        if (secret.length() <= 6) {
            return "******";
        }
        return secret.substring(0, 3) + "******" + secret.substring(secret.length() - 3);
    }

    /**
     * 运行时更新 OAuth 配置并持久化到 config/application.yml。
     * 未提供的字段保持不变；client_secret 为空串则视为"不修改"。
     */
    public static synchronized Map<String, Object> updateConfig(Map<String, Object> update) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (update.containsKey("enabled")) {
            enabled = Boolean.TRUE.equals(update.get("enabled"));
        }
        if (update.get("client_id") != null && !String.valueOf(update.get("client_id")).isBlank()) {
            clientId = String.valueOf(update.get("client_id")).trim();
        }
        String newSecret = update.get("client_secret") == null ? "" : String.valueOf(update.get("client_secret")).trim();
        if (!newSecret.isEmpty()) {
            clientSecret = newSecret;
            secretKey = buildKey(newSecret);
        }
        if (update.get("scopes") != null && !String.valueOf(update.get("scopes")).isBlank()) {
            scopes = String.valueOf(update.get("scopes")).trim();
        }
        if (update.get("token_ttl_seconds") instanceof Number n) {
            tokenTtlMs = Math.max(60, n.longValue()) * 1000L;
        }

        String persistMsg = persistConfig();
        result.put("success", true);
        result.put("message", I18n.t("oauth.config_updated",
                persistMsg != null ? "（" + persistMsg + "）" : ""));
        result.putAll(getConfig(false));
        return result;
    }

    /** 将 oauth2 配置写回 config/application.yml（无该文件则跳过）。仅更新 oauth2 段的值，保留注释与排版。 */
    private static String persistConfig() {
        try {
            Path path = persistPath();
            if (!Files.exists(path)) {
                return "配置文件不存在，仅内存生效";
            }
            Map<String, Object> updates = new LinkedHashMap<>();
            updates.put("oauth2.enabled", enabled);
            updates.put("oauth2.client_id", clientId);
            updates.put("oauth2.client_secret", clientSecret);
            updates.put("oauth2.scopes", scopes);
            updates.put("oauth2.token_ttl_seconds", getTokenTtlSeconds());
            AppConfig.update(updates);
            return "已写入 " + path.toAbsolutePath();
        } catch (Exception e) {
            return "持久化失败: " + e.getMessage();
        }
    }

    private static Path persistPath() {
        return AppConfig.getJarDir().resolve("config").resolve("application.yml");
    }

    // ==================== 令牌 ====================

    /** 校验客户端凭据（client_credentials 流程） */
    public static boolean validateClient(String id, String secret) {
        if (!enabled) {
            return false;
        }
        return clientId.equals(id) && clientSecret.equals(secret);
    }

    /**
     * 签发 access_token。
     *
     * <p><b>{@code scope} 为 null 与空串要区别对待</b>：</p>
     * <ul>
     *   <li>{@code null} —— 调用方没指定，给配置的全部 scopes（"不指定即全部"）；</li>
     *   <li>{@code ""} —— 调用方指定的 scope 全都不在配置白名单里，
     *       收窄后就是空。这时<b>必须保持为空</b>：如果回落成配置的全部，
     *       恰好把"请求了非法 scope"变成"拿到全部权限"，方向正好相反。</li>
     * </ul>
     */
    public static String issueAccessToken(String clientId, String scope) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + tokenTtlMs);
        String effectiveScope = scope == null ? scopes : scope;

        String token = Jwts.builder()
                .setSubject("oauth2:" + clientId)
                .claim("token_type", "oauth2")
                .claim("client_id", clientId)
                .claim("scope", effectiveScope)
                .setIssuedAt(now)
                .setExpiration(expiration)
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
        // issuedTokens 只写不读，且从不清理 —— 每签发一个 token 就永久占一条，
        // 长跑的服务会缓慢吃内存。这里不再往里塞。
        return token;
    }

    /** 解析 token；非法/过期/非 OAuth token 返回 null */
    public static Claims parseToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(secretKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            if (!"oauth2".equals(claims.get("token_type", String.class))) {
                return null;
            }
            return claims;
        } catch (Exception e) {
            return null;
        }
    }

    /** 校验 token 是否有效（仅对本服务签发的 OAuth token 有效） */
    public static boolean isValidToken(String token) {
        return parseToken(token) != null;
    }

    /** 从 Bearer 头中提取 token；无有效 Bearer 返回 null */
    public static String extractBearer(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return null;
        }
        String token = authorizationHeader.substring("Bearer ".length()).trim();
        return token.isEmpty() ? null : token;
    }

    /** RFC 7662 风格的 token 内省 */
    public static Map<String, Object> introspect(String token) {
        Map<String, Object> info = new LinkedHashMap<>();
        Claims claims = parseToken(token);
        if (claims == null) {
            info.put("active", false);
            return info;
        }
        info.put("active", true);
        info.put("client_id", claims.get("client_id", String.class));
        info.put("scope", claims.get("scope", String.class));
        info.put("token_type", "Bearer");
        info.put("sub", claims.getSubject());
        info.put("iat", claims.getIssuedAt().getTime() / 1000);
        info.put("exp", claims.getExpiration().getTime() / 1000);
        return info;
    }

    /**
     * 组装标准 token 响应体。
     *
     * <p><b>为什么要把请求的 scope 与配置求交集</b>：{@code scope} 是客户端自己填的，
     * 不校验就等于让调用方自选权限 —— 请求
     * {@code {"scope":"read write admin delete"}} 时，签出的 token 里就真的
     * 写着 {@code read write admin delete}，{@code /introspect} 还会原样回报。</p>
     *
     * <p>当前受保护端点只认 {@code read} / {@code write} 两个具体值，
     * 多出来的名字暂时没有端点响应，所以还无法直接提权。但这留下两处隐患：
     * 将来任何按 scope 名授权的新端点都会默认信任这个自填字段；下游系统读到
     * 内省结果也可能据此放行。RFC 6749 §3.3 要求的就是"签发方按自身策略收窄"。</p>
     *
     * <p>因此这里做白名单收窄：只保留配置里出现过的 scope。请求留空则给配置的全部；
     * 请求全是未授权项则签出一个不含任何权限的 token（调用方后续会拿到 401，
     * 这比悄悄给他一个默认 full 权限要安全）。</p>
     */
    public static Map<String, Object> buildTokenResponse(String clientId, String requestedScope) {
        String effectiveScope = narrowToConfiguredScopes(requestedScope);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("access_token", issueAccessToken(clientId, effectiveScope));
        resp.put("token_type", "Bearer");
        resp.put("expires_in", tokenTtlMs / 1000);
        resp.put("scope", effectiveScope);
        return resp;
    }

    /**
     * 把请求的 scope 收窄到配置允许的范围。
     *
     * <p>请求为空（null / 空白）时返回配置的全部 scopes —— 与"不指定即全部"的
     * 既有语义保持一致。否则逐项比对配置，只保留命中项。</p>
     */
    static String narrowToConfiguredScopes(String requested) {
        if (requested == null || requested.isBlank()) {
            return scopes;
        }
        java.util.Set<String> allowed = new java.util.LinkedHashSet<>();
        for (String s : scopes.split("\\s+")) {
            if (!s.isBlank()) {
                allowed.add(s);
            }
        }
        StringBuilder narrowed = new StringBuilder();
        for (String s : requested.split("\\s+")) {
            if (allowed.contains(s)) {
                if (narrowed.length() > 0) {
                    narrowed.append(' ');
                }
                narrowed.append(s);
            }
        }
        return narrowed.toString();
    }

    /** 是否包含指定 scope（"read write" 包含 "read"） */
    public static boolean hasScope(String tokenScopes, String required) {
        if (tokenScopes == null) {
            return false;
        }
        for (String s : tokenScopes.split("\\s+")) {
            if (s.equals(required)) {
                return true;
            }
        }
        return false;
    }
}
