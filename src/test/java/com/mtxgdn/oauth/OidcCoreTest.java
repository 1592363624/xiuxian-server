package com.mtxgdn.oauth;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OIDC 核心行为：sub 稳定性、id_token 签发与验证。
 *
 * <p>这些是"第三方把本服当登录方式"依赖的地基。任何一条错了，
 * 客户端那边表现都是"登录失败"，但原因各不相同，所以逐条锁住。</p>
 */
class OidcCoreTest {

    @TempDir
    static Path keyDir;

    @BeforeAll
    static void setUp() {
        OidcKeyManager.resetForTest();
        OidcKeyManager.init(keyDir);
    }

    @AfterAll
    static void tearDown() {
        OidcKeyManager.resetForTest();
    }

    // ==================== sub ====================

    @Test
    @DisplayName("sub 对同一用户名稳定，不同用户名不同")
    void subjectIsStableAndDistinct() {
        String a1 = OidcSubject.of("张三");
        String a2 = OidcSubject.of("张三");
        String b = OidcSubject.of("李四");

        assertEquals(a1, a2, "同一用户必须得到同一 sub —— 这是登录态能持续的前提");
        assertNotEquals(a1, b);
    }

    @Test
    @DisplayName("sub 不可从用户名推导 —— 防止抢注别人的身份")
    void subjectIsNotGuessable() {
        String sub = OidcSubject.of("张三");

        // 如果 sub 就是用户名，攻击者能抢先用自己的客户端完成一次登录，
        // 把对方的身份抢注走。所以必须含一段算不出来的摘要
        assertNotEquals("张三", sub);
        assertTrue(sub.startsWith("uid_"), "应带可读前缀便于排查");
        assertTrue(sub.contains("张三"), "应含用户名便于人工核对");
        assertTrue(sub.length() > "uid_张三_".length() + 8, "必须含足够长的摘要");
    }

    @Test
    @DisplayName("verify 能认出伪造的 sub")
    void verifyRejectsForged() {
        String real = OidcSubject.of("张三");

        assertTrue(OidcSubject.verify(real, "张三"));
        assertFalse(OidcSubject.verify("uid_张三_0000000000000000", "张三"),
                "摘要不对必须识别出来");
        assertFalse(OidcSubject.verify(real, "李四"), "换个人必须不匹配");
        assertFalse(OidcSubject.verify(null, "张三"));
        assertFalse(OidcSubject.verify(real, null));
    }

    @Test
    @DisplayName("空用户名被拒绝，而不是产生一个奇怪的 sub")
    void blankUsernameRejected() {
        assertThrows(IllegalArgumentException.class, () -> OidcSubject.of(""));
        assertThrows(IllegalArgumentException.class, () -> OidcSubject.of("   "));
        assertThrows(IllegalArgumentException.class, () -> OidcSubject.of(null));
    }

    // ==================== id_token ====================

    @Test
    @DisplayName("id_token 含全部必填 claim，且能验证签名")
    void idTokenHasRequiredClaimsAndValidSignature() {
        String token = OidcIdToken.sign(
                "https://game.example.com", "newapi_client", "张三",
                "nonce-abc", Set.of("openid", "profile"), null);

        var claims = io.jsonwebtoken.Jwts.parserBuilder()
                .setSigningKey(OidcKeyManager.publicKey())
                .build()
                .parseClaimsJws(token)
                .getBody();

        assertEquals("https://game.example.com", claims.getIssuer(),
                "iss 必须与 Discovery 的 issuer 完全一致，否则客户端判为无效");
        assertEquals("newapi_client", claims.getAudience());
        assertEquals(OidcSubject.of("张三"), claims.getSubject());
        assertEquals("nonce-abc", claims.get("nonce"),
                "nonce 必须原样回填 —— 对不上客户端会拒绝登录");
        assertNotNull(claims.getIssuedAt());
        assertNotNull(claims.getExpiration());
        assertTrue(claims.getExpiration().after(claims.getIssuedAt()));
    }

    @Test
    @DisplayName("签名是 RS256，不是 HS256")
    void signedWithRs256() {
        String token = OidcIdToken.sign("https://x", "c", "张三", null, Set.of("openid"), null);
        String header = new String(java.util.Base64.getUrlDecoder()
                .decode(token.split("\\.")[0]), java.nio.charset.StandardCharsets.UTF_8);

        assertTrue(header.contains("RS256"),
                "必须是 RS256：客户端要用公钥验签，HS256 的密钥不能给出去");
        assertTrue(header.contains("kid"), "必须带 kid，客户端据此挑公钥");
        assertTrue(header.contains(OidcKeyManager.keyId()), "kid 应等于当前密钥 id");
    }

    @Test
    @DisplayName("篡改过的 id_token 验签失败")
    void tamperedTokenFailsVerification() {
        String token = OidcIdToken.sign("https://x", "c", "张三", null, Set.of("openid"), null);
        String[] parts = token.split("\\.");

        // 改 payload 里的 sub，但不动签名
        String forgedPayload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"iss\":\"https://x\",\"sub\":\"someone_else\",\"aud\":\"c\"}"
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String forged = parts[0] + "." + forgedPayload + "." + parts[2];

        assertThrows(io.jsonwebtoken.JwtException.class, () ->
                io.jsonwebtoken.Jwts.parserBuilder()
                        .setSigningKey(OidcKeyManager.publicKey())
                        .build()
                        .parseClaimsJws(forged),
                "改了内容但签名不变，必须验签失败");
    }

    @Test
    @DisplayName("未申请 profile/email 时不返回多余信息")
    void noExtraClaimsWithoutScope() {
        String token = OidcIdToken.sign("https://x", "c", "张三", null,
                Set.of("openid"), "zhangsan@example.com");

        var claims = io.jsonwebtoken.Jwts.parserBuilder()
                .setSigningKey(OidcKeyManager.publicKey())
                .build()
                .parseClaimsJws(token).getBody();

        assertNull(claims.get("name"), "没申请 profile 不该给 name");
        assertNull(claims.get("email"), "没申请 email 不该给邮箱 —— 客户端不该拿到超出范围的信息");
    }

    @Test
    @DisplayName("申请了 email 才带邮箱，且如实标 email_verified=false")
    void emailClaimHonesty() {
        String token = OidcIdToken.sign("https://x", "c", "张三", null,
                Set.of("openid", "email"), "zhangsan@example.com");

        var claims = io.jsonwebtoken.Jwts.parserBuilder()
                .setSigningKey(OidcKeyManager.publicKey())
                .build()
                .parseClaimsJws(token).getBody();

        assertEquals("zhangsan@example.com", claims.get("email"));
        // 本服的邮箱是玩家自己填的，没有验证流程。
        // 谎报 true 会让下游把未验证的邮箱当成可信身份
        assertEquals(Boolean.FALSE, claims.get("email_verified"),
                "没有验证流程就必须如实标 false");
    }
}
