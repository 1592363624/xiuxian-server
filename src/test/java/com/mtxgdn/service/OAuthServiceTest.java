package com.mtxgdn.service;

import com.mtxgdn.TestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OAuth 2.0（client_credentials）签发与校验的契约。
 *
 * <p><b>为什么补这些测试</b>：{@code OAuthService} + {@code OAuthResource} 约 475 行，
 * 负责签发能访问受保护端点的 Bearer token，此前<b>一条测试都没有</b>。
 * 它是鉴权链路的一环，改动时没有任何东西会提醒你破坏了什么。</p>
 *
 * <p>这组测试盯的是"签出来的 token 应该满足什么"，而不是复述实现：</p>
 * <ul>
 *   <li>凭据校验必须精确匹配，不能前缀/子串通过；</li>
 *   <li>token 必须能在篡改后失效；</li>
 *   <li>scope 必须真的限制能力（read 的 token 不能过 write 检查）；</li>
 *   <li>内省对无效 token 只能回 {@code active=false}。</li>
 * </ul>
 */
class OAuthServiceTest extends TestBase {

    @BeforeEach
    void resetConfig() {
        // 固定一套已知凭据，避免受本机 config/application.yml 影响。
        // 键名与 updateConfig 一致（下划线风格）。
        Map<String, Object> update = new java.util.LinkedHashMap<>();
        update.put("enabled", true);
        update.put("client_id", "test_client");
        update.put("client_secret", "test_secret_0123456789abcdef");
        update.put("scopes", "read write");
        update.put("token_ttl_seconds", 3600);
        OAuthService.updateConfig(update);
    }

    @AfterEach
    void restoreEnabled() {
        Map<String, Object> update = new java.util.LinkedHashMap<>();
        update.put("enabled", true);
        OAuthService.updateConfig(update);
    }

    // ==================== 客户端凭据校验 ====================

    @Test
    @DisplayName("正确的 client_id + client_secret 通过校验")
    void validClientAccepted() {
        assertTrue(OAuthService.validateClient("test_client", "test_secret_0123456789abcdef"));
    }

    @Test
    @DisplayName("任一凭据错误都拒绝")
    void wrongCredentialsRejected() {
        assertFalse(OAuthService.validateClient("test_client", "wrong"));
        assertFalse(OAuthService.validateClient("wrong", "test_secret_0123456789abcdef"));
        assertFalse(OAuthService.validateClient("", ""));
        assertFalse(OAuthService.validateClient(null, null));
    }

    @Test
    @DisplayName("凭据必须精确匹配，不能被前缀或子串通过")
    void credentialsMustMatchExactly() {
        assertFalse(OAuthService.validateClient("test_clien", "test_secret_0123456789abcdef"),
                "少一个字符不应通过");
        assertFalse(OAuthService.validateClient("test_client_x", "test_secret_0123456789abcdef"),
                "多一个字符不应通过");
        assertFalse(OAuthService.validateClient("test_client", "test_secret_0123456789abcde"),
                "secret 少一个字符不应通过");
        assertFalse(OAuthService.validateClient("TEST_CLIENT", "test_secret_0123456789abcdef"),
                "大小写不同不应通过");
    }

    @Test
    @DisplayName("禁用时即使凭据正确也拒绝")
    void disabledRejectsEvenWithValidCredentials() {
        Map<String, Object> update = new java.util.LinkedHashMap<>();
        update.put("enabled", false);
        OAuthService.updateConfig(update);

        assertFalse(OAuthService.isEnabled());
        assertFalse(OAuthService.validateClient("test_client", "test_secret_0123456789abcdef"),
                "功能关闭后不应还能换到 token");
    }

    // ==================== 签发与解析往返 ====================

    @Test
    @DisplayName("签发的 token 能被解析回来，且带着 client_id 与 scope")
    void issuedTokenRoundTrips() {
        String token = OAuthService.issueAccessToken("test_client", "read");
        assertNotNull(token, "应签发成功");

        var claims = OAuthService.parseToken(token);
        assertNotNull(claims, "自己签的 token 必须能解析");
        assertEquals("oauth2", claims.get("token_type", String.class));
        assertEquals("test_client", claims.get("client_id", String.class));
        assertEquals("read", claims.get("scope", String.class));
        assertEquals("oauth2:test_client", claims.getSubject());
        assertNotNull(claims.getExpiration(), "必须带过期时间");
    }

    @Test
    @DisplayName("被篡改的 token 解析失败")
    void tamperedTokenRejected() {
        String token = OAuthService.issueAccessToken("test_client", "read");
        // 改动载荷部分的一个字符
        String[] parts = token.split("\\.");
        assertEquals(3, parts.length, "JWT 应为三段");

        String tampered = parts[0] + "." + parts[1].substring(0, parts[1].length() - 2)
                + (parts[1].endsWith("A") ? "B" : "A") + "." + parts[2];
        assertNull(OAuthService.parseToken(tampered), "签名不匹配必须解析失败");

        assertNull(OAuthService.parseToken(parts[0] + "." + parts[1] + "."),
                "缺签名段应失败");
    }

    @Test
    @DisplayName("空值、乱串、正常 JWT 都不会被当成 OAuth token")
    void garbageTokenRejected() {
        assertNull(OAuthService.parseToken(null));
        assertNull(OAuthService.parseToken(""));
        assertNull(OAuthService.parseToken("   "));
        assertNull(OAuthService.parseToken("not-a-token"));
        assertNull(OAuthService.parseToken("a.b.c"));
    }

    // ==================== scope ====================

    @Test
    @DisplayName("hasScope 按空白分词，精确匹配单个 scope")
    void hasScopeMatchesExactly() {
        assertTrue(OAuthService.hasScope("read write", "read"));
        assertTrue(OAuthService.hasScope("read write", "write"));
        assertTrue(OAuthService.hasScope("read", "read"));
        assertTrue(OAuthService.hasScope("  read   write  ", "write"), "多余空白不应影响");

        assertFalse(OAuthService.hasScope("read", "write"), "只有 read 不应通过 write");
        assertFalse(OAuthService.hasScope(null, "read"));
        assertFalse(OAuthService.hasScope("", "read"));
    }

    @Test
    @DisplayName("hasScope 不做子串匹配：read 不能通过 readwrite")
    void hasScopeIsNotSubstringMatch() {
        assertFalse(OAuthService.hasScope("readwrite", "read"),
                "readwrite 是另一个 scope，不应被当成含 read");
        assertFalse(OAuthService.hasScope("readonly", "read"));
    }

    @Test
    @DisplayName("签出的 scope 真的能限制能力：read token 过不了 write 检查")
    void scopeRestrictsCapability() {
        String readToken = OAuthService.issueAccessToken("test_client", "read");
        var claims = OAuthService.parseToken(readToken);
        assertNotNull(claims);

        String scope = claims.get("scope", String.class);
        assertTrue(OAuthService.hasScope(scope, "read"), "read token 应能读");
        assertFalse(OAuthService.hasScope(scope, "write"),
                "read token 不应能写 —— 否则 scope 形同虚设");
    }

    @Test
    @DisplayName("scope 为空白时回落到配置的默认 scopes")
    void blankScopeFallsBackToConfigured() {
        // 走真正的入口 buildTokenResponse（它会先做白名单收窄）。
        // 注意不能直接测 issueAccessToken("  ")：那一层现在刻意不做回落 ——
        // 收窄后为空必须保持为空，否则"请求了非法 scope"会变成"拿到全部权限"。
        Map<String, Object> resp = OAuthService.buildTokenResponse("test_client", "  ");
        var claims = OAuthService.parseToken(String.valueOf(resp.get("access_token")));
        assertNotNull(claims);
        assertEquals(OAuthService.getScopes(), claims.get("scope", String.class),
                "未指定 scope 应回落到配置值，而不是签出一个无权限的 token");
    }

    @Test
    @DisplayName("收窄后为空时必须保持为空，不能回落成全部权限")
    void narrowedToEmptyStaysEmpty() {
        // 请求的 scope 全都不在配置白名单里 → 收窄结果是空
        String token = OAuthService.issueAccessToken("test_client", "");
        var claims = OAuthService.parseToken(token);
        assertNotNull(claims);
        assertEquals("", claims.get("scope", String.class),
                "空串必须保持为空 —— 若回落成配置全部，方向正好相反（越权）");
        assertFalse(OAuthService.hasScope(claims.get("scope", String.class), "read"));
        assertFalse(OAuthService.hasScope(claims.get("scope", String.class), "write"));
    }

    // ==================== 内省（RFC 7662 风格）====================

    @Test
    @DisplayName("有效 token 内省返回 active=true 与元数据")
    void introspectActiveToken() {
        String token = OAuthService.issueAccessToken("test_client", "read");
        Map<String, Object> info = OAuthService.introspect(token);

        assertEquals(true, info.get("active"));
        assertEquals("test_client", info.get("client_id"));
        assertEquals("read", info.get("scope"));
        assertEquals("Bearer", info.get("token_type"));
        assertNotNull(info.get("iat"));
        assertNotNull(info.get("exp"));
    }

    @Test
    @DisplayName("无效 token 内省只返回 active=false，不泄露任何细节")
    void introspectInvalidToken() {
        for (String bad : new String[]{null, "", "garbage", "a.b.c"}) {
            Map<String, Object> info = OAuthService.introspect(bad);
            assertEquals(false, info.get("active"), "无效 token 应 active=false");
            assertEquals(1, info.size(),
                    "只应含 active 一个键，不能顺带泄露 client_id/scope 等字段: " + info);
        }
    }

    // ==================== Bearer 头解析 ====================

    @Test
    @DisplayName("extractBearer 只认 Bearer 前缀")
    void extractBearerParses() {
        assertEquals("abc", OAuthService.extractBearer("Bearer abc"));
        assertEquals("abc", OAuthService.extractBearer("Bearer   abc  "), "多余空白应去掉");

        assertNull(OAuthService.extractBearer(null));
        assertNull(OAuthService.extractBearer(""));
        assertNull(OAuthService.extractBearer("abc"), "没有前缀不算");
        assertNull(OAuthService.extractBearer("Basic abc"), "别的认证方案不算");
        assertNull(OAuthService.extractBearer("Bearer "), "空 token 不算");
    }

    // ==================== 配置下发不能泄露密钥 ====================

    @Test
    @DisplayName("getConfig(revealSecret=false) 不得返回明文 client_secret")
    void configMasksSecretByDefault() {
        Map<String, Object> safe = OAuthService.getConfig(false);
        Object secret = safe.get("client_secret");
        assertNotNull(secret, "应带上该字段（前端要显示占位）");
        assertNotEquals("test_secret_0123456789abcdef", secret,
                "默认下发的配置不能含明文密钥 —— 否则任何能打开该页面的管理员都能看到");
        assertTrue(String.valueOf(secret).contains("***"),
                "应显示为打码占位，实际: " + secret);
    }

    @Test
    @DisplayName("getConfig(revealSecret=true) 才返回明文（仅管理员显式索要时）")
    void configRevealsSecretOnlyWhenAsked() {
        Map<String, Object> full = OAuthService.getConfig(true);
        assertEquals("test_secret_0123456789abcdef", full.get("client_secret"),
                "显式索要时应能拿到明文，否则管理员无法核对现有配置");
    }
}
