package com.mtxgdn.oauth;

import com.mtxgdn.util.AppConfig;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

import java.util.Date;
import java.util.Set;

/**
 * OIDC {@code id_token} 的签发。
 *
 * <h3>它与 access_token 的区别</h3>
 * <table border="1">
 *   <tr><th></th><th>access_token</th><th>id_token</th></tr>
 *   <tr><td>用途</td><td>调 API</td><td>告诉客户端「这人是谁」</td></tr>
 *   <tr><td>谁验证</td><td>本服</td><td><b>第三方客户端</b></td></tr>
 *   <tr><td>签名</td><td>HS256（对称，本服自验）</td><td>RS256（非对称，公钥公开）</td></tr>
 * </table>
 *
 * <p>这就是 {@link OidcKeyManager} 必须存在的原因：客户端要验签，
 * 所以只能用非对称密钥，而公钥必须能公开获取（JWKS）。</p>
 *
 * <h3>必填 claim</h3>
 * <ul>
 *   <li>{@code iss} —— 必须与 Discovery 里的 issuer <b>逐字符相等</b>，客户端据此校验；</li>
 *   <li>{@code aud} —— client_id，防止把发给 A 的 token 拿到 B 用；</li>
 *   <li>{@code sub} —— 用户标识，见 {@link OidcSubject}；</li>
 *   <li>{@code exp} / {@code iat} —— 有效期与签发时间；</li>
 *   <li>{@code nonce} —— 客户端原样传来的值，防重放。有就必须回填，
 *       客户端会拿它比对，对不上会直接拒绝登录。</li>
 * </ul>
 */
public final class OidcIdToken {

    /** 默认有效期 1 小时 */
    private static final long DEFAULT_TTL_SECONDS = 3600L;

    private OidcIdToken() {
    }

    /**
     * 签发一个 id_token。
     *
     * @param issuer   对外根地址（与 Discovery 的 issuer 一致）
     * @param clientId 目标客户端
     * @param username 玩家用户名（sub 由它推导）
     * @param nonce    客户端传的 nonce，可为 null
     * @param scopes   本次授权的 scope（决定是否带 profile/email 相关 claim）
     * @param email    邮箱，可为 null
     * @return 紧凑序列化的 JWT
     */
    public static String sign(String issuer, String clientId, String username,
                              String nonce, Set<String> scopes, String email) {
        long ttl = ttlSeconds();
        Date now = new Date();
        Date exp = new Date(now.getTime() + ttl * 1000L);

        var builder = Jwts.builder()
                .setIssuer(issuer)
                .setAudience(clientId)
                .setSubject(OidcSubject.of(username))
                .setIssuedAt(now)
                .setExpiration(exp)
                .claim("auth_time", now.getTime() / 1000)
                .claim("preferred_username", username)
                .claim("azp", clientId);

        if (nonce != null && !nonce.isBlank()) {
            builder.claim("nonce", nonce);
        }
        if (scopes != null && scopes.contains("profile")) {
            builder.claim("name", username);
        }
        if (scopes != null && scopes.contains("email") && email != null && !email.isBlank()) {
            builder.claim("email", email);
            // 本服的邮箱是玩家自己填的，没有验证流程，所以如实标 false
            builder.claim("email_verified", false);
        }

        return builder
                // 带上 kid：客户端拿它去 JWKS 里挑对应的公钥。
                // 没有 kid 时部分客户端会退化成"试所有公钥"，换密钥期间容易验签失败
                .setHeaderParam("kid", OidcKeyManager.keyId())
                .setHeaderParam("typ", "JWT")
                .signWith(OidcKeyManager.privateKey(), SignatureAlgorithm.RS256)
                .compact();
    }

    /** id_token 有效期（秒），非法值回退默认 */
    private static long ttlSeconds() {
        String v = AppConfig.get("oidc.id_token_ttl_seconds");
        if (v == null || v.isBlank()) {
            return DEFAULT_TTL_SECONDS;
        }
        try {
            long n = Long.parseLong(v.trim());
            // 下限 60 秒：再短客户端可能刚拿到就过期
            return n < 60 ? 60 : n;
        } catch (NumberFormatException e) {
            return DEFAULT_TTL_SECONDS;
        }
    }
}
