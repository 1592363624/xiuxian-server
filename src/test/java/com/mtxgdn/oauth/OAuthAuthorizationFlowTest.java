package com.mtxgdn.oauth;

import com.mtxgdn.TestBase;
import com.mtxgdn.service.OAuthService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 授权码流程的越权与攻击防护。
 *
 * <p>这一组测试的优先级高于任何功能测试 —— 授权码流程的每个环节都有对应的
 * 历史攻击手法，漏一个就是真实的账号数据泄露。</p>
 *
 * <p>每条测试都对应一种具体攻击，而不是泛泛地"测一下能跑"。</p>
 */
class OAuthAuthorizationFlowTest extends TestBase {

    private static final String CLIENT = "third_party_app";
    private static final String SECRET = "app_secret_0123456789abcdef";
    private static final String REDIRECT = "https://app.example.com/oauth/callback";

    @BeforeEach
    void setUp() {
        OAuthCodeStore.clear();
        OAuthGrantStore.clear();
        OAuthClientRegistry.clear();

        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("enabled", true);
        cfg.put("client_id", "machine_client");
        cfg.put("client_secret", "machine_secret_0123456789");
        cfg.put("scopes", "read write");
        cfg.put("token_ttl_seconds", 3600);
        OAuthService.updateConfig(cfg);

        Map<String, Object> app = new LinkedHashMap<>();
        app.put("client_id", CLIENT);
        app.put("client_secret", SECRET);
        app.put("name", "修仙助手");
        app.put("redirect_uris", List.of(REDIRECT));
        app.put("scopes", List.of("player:read", "inventory:read"));
        OAuthClientRegistry.loadFromConfig(List.of(app));
    }

    @AfterEach
    void tearDown() {
        OAuthCodeStore.clear();
        OAuthGrantStore.clear();
        OAuthClientRegistry.clear();
    }

    // ==================== 回调地址 ====================

    @Test
    @DisplayName("攻击：换成外域回调地址 → 拒绝")
    void foreignRedirectUriRejected() {
        assertTrue(OAuthClientRegistry.isRedirectUriAllowed(CLIENT, REDIRECT),
                "登记过的地址应通过");
        assertFalse(OAuthClientRegistry.isRedirectUriAllowed(CLIENT, "https://evil.com/callback"),
                "未登记的域名必须拒绝，否则授权码会被引到攻击者服务器");
    }

    @Test
    @DisplayName("攻击：用 app.com.evil.com 绕前缀匹配 → 拒绝")
    void redirectUriPrefixTrickRejected() {
        // 这是最容易写错的一条：用 startsWith 判断就中招了
        assertFalse(OAuthClientRegistry.isRedirectUriAllowed(
                        CLIENT, "https://app.example.com.evil.com/oauth/callback"),
                "以登记值开头的恶意域名必须拒绝 —— 说明比较必须是全等而非前缀");
    }

    @Test
    @DisplayName("攻击：给登记的地址附加参数 → 拒绝")
    void redirectUriWithExtraParamsRejected() {
        assertFalse(OAuthClientRegistry.isRedirectUriAllowed(
                        CLIENT, REDIRECT + "?redirect=https://evil.com"),
                "多带参数可以改变行为，必须全等匹配");
        assertFalse(OAuthClientRegistry.isRedirectUriAllowed(CLIENT, REDIRECT + "/"),
                "末尾多个斜杠也不同");
    }

    // ==================== 授权码一次性 ====================

    @Test
    @DisplayName("攻击：授权码重放 → 第二次失败")
    void codeIsSingleUse() {
        String code = OAuthCodeStore.issue(CLIENT, REDIRECT, 42L, "张三", Set.of("player:read"));

        assertNotNull(OAuthCodeStore.consume(code, CLIENT, REDIRECT), "第一次应成功");
        assertNull(OAuthCodeStore.consume(code, CLIENT, REDIRECT),
                "同一个码不能换第二次 —— 重放是授权码最经典的攻击");
    }

    @Test
    @DisplayName("攻击：拿别的应用的 code 来换 → 拒绝")
    void codeBoundToClient() {
        String code = OAuthCodeStore.issue(CLIENT, REDIRECT, 42L, "张三", Set.of("player:read"));

        assertNull(OAuthCodeStore.consume(code, "another_app", REDIRECT),
                "授权码绑定签发它的应用，换给别的应用必须失败");
        // 且这次失败也消耗掉了这个码
        assertNull(OAuthCodeStore.consume(code, CLIENT, REDIRECT),
                "校验失败后码也应作废，否则可以反复试不同的 client_id");
    }

    @Test
    @DisplayName("攻击：用不同的 redirect_uri 换 code → 拒绝")
    void codeBoundToRedirectUri() {
        String code = OAuthCodeStore.issue(CLIENT, REDIRECT, 42L, "张三", Set.of("player:read"));

        assertNull(OAuthCodeStore.consume(code, CLIENT, "https://evil.com/cb"),
                "换 code 时的 redirect_uri 必须与签发时一致");
    }

    @Test
    @DisplayName("空码、乱码、null 都不能换到 token")
    void garbageCodeRejected() {
        assertNull(OAuthCodeStore.consume(null, CLIENT, REDIRECT));
        assertNull(OAuthCodeStore.consume("", CLIENT, REDIRECT));
        assertNull(OAuthCodeStore.consume("random-garbage", CLIENT, REDIRECT));
    }

    // ==================== token 身份区分 ====================

    @Test
    @DisplayName("红线：客户端凭证型 token 不带用户身份")
    void clientCredentialsTokenHasNoUserIdentity() {
        String token = OAuthService.issueAccessToken("machine_client", "read");
        var claims = OAuthService.parseToken(token);

        assertNotNull(claims, "token 应可解析");
        assertNull(OAuthService.extractUserId(claims),
                "客户端凭证型 token 绝不能带 uid —— 否则任何拿到 secret 的应用都能冒充玩家");
        assertFalse(OAuthService.isUserToken(claims));
    }

    @Test
    @DisplayName("用户授权型 token 才带 uid")
    void userTokenCarriesUserId() {
        var grant = OAuthGrantStore.authorize(42L, "张三", CLIENT, Set.of("player:read"));
        String jti = "jti-test-1";
        OAuthGrantStore.registerToken(jti, grant.grantId());

        String token = OAuthService.issueUserAccessToken(
                42L, "张三", CLIENT, Set.of("player:read"), grant.grantId(), jti);
        var claims = OAuthService.parseToken(token);

        assertNotNull(claims);
        assertEquals(42L, OAuthService.extractUserId(claims).longValue());
        assertEquals("张三", claims.get("username", String.class));
        assertTrue(OAuthService.isUserToken(claims));
    }

    @Test
    @DisplayName("未登记的 jti 不被当作有效的用户 token")
    void unregisteredJtiIsNotUserToken() {
        // 手工签一个带 uid 但没在 grant store 登记的 token
        String token = OAuthService.issueUserAccessToken(
                42L, "张三", CLIENT, Set.of("player:read"), "fake-grant", "never-registered");

        assertNull(OAuthService.parseUserToken(token),
                "token 必须对应一条真实授权记录，否则等于可以自己造用户 token");
    }

    // ==================== 撤销 ====================

    @Test
    @DisplayName("撤销授权后 token 立即失效")
    void revokeInvalidatesTokenImmediately() {
        var grant = OAuthGrantStore.authorize(42L, "张三", CLIENT, Set.of("player:read"));
        String jti = "jti-revoke-test";
        OAuthGrantStore.registerToken(jti, grant.grantId());

        String token = OAuthService.issueUserAccessToken(
                42L, "张三", CLIENT, Set.of("player:read"), grant.grantId(), jti);

        assertNotNull(OAuthService.parseUserToken(token), "撤销前应有效");

        assertTrue(OAuthGrantStore.revoke(grant.grantId()));

        assertNull(OAuthService.parseUserToken(token),
                "撤销后必须立刻失效 —— 只靠过期时间的话攻击者还能继续用一个小时");
    }

    @Test
    @DisplayName("撤销一个不存在的授权返回 false，不做静默成功")
    void revokeUnknownGrantReturnsFalse() {
        assertFalse(OAuthGrantStore.revoke("no-such-grant"));
        assertFalse(OAuthGrantStore.revoke(null));
    }

    @Test
    @DisplayName("玩家的授权列表只含自己的授权")
    void grantsAreScopedToOwner() {
        OAuthGrantStore.authorize(42L, "张三", CLIENT, Set.of("player:read"));
        OAuthGrantStore.authorize(99L, "李四", CLIENT, Set.of("player:read"));

        assertEquals(1, OAuthGrantStore.grantsOf(42L).size(), "只能看到自己的授权");
        assertEquals(1, OAuthGrantStore.grantsOf(99L).size());
        assertEquals(0, OAuthGrantStore.grantsOf(12345L).size());
    }

    /** 这两个类不能未使用，测试里直接引用以覆盖导入 */
    @Test
    @DisplayName("路径策略表已登记资源域（防止被误清空）")
    void pathPolicyIsPopulated() {
        assertTrue(OAuthPathPolicy.registeredPrefixCount() > 10,
                "资源域前缀表不能为空，否则所有第三方访问都会被拒绝");
    }
}
