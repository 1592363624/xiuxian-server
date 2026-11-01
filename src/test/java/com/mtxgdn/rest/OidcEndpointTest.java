package com.mtxgdn.rest;

import com.mtxgdn.TestBase;
import com.mtxgdn.oauth.OidcKeyManager;
import com.mtxgdn.oauth.OidcSubject;
import com.mtxgdn.util.AppConfig;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.net.URI;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * OIDC 端点行为。
 *
 * <p>覆盖第三方客户端（New API 等）实际会打到的那几个地址：
 * Discovery、JWKS、userinfo。</p>
 */
class OidcEndpointTest extends TestBase {

    private OidcResource resource;
    private HttpHeaders headers;

    @BeforeEach
    void setUp() throws Exception {
        resource = new OidcResource();
        headers = mock(HttpHeaders.class);

        Field f = OidcResource.class.getDeclaredField("httpHeaders");
        f.setAccessible(true);
        f.set(resource, headers);

        // 按请求推导地址的路径需要 UriInfo，这里给一个固定值
        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getAbsolutePath()).thenReturn(URI.create("https://game.example.com/api/auth/oidc/x"));
        Field u = OidcResource.class.getDeclaredField("uriInfo");
        u.setAccessible(true);
        u.set(resource, uriInfo);

        if (!OidcKeyManager.isReady()) {
            java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("oidc-test");
            OidcKeyManager.resetForTest();
            OidcKeyManager.init(dir);
        }
    }

    @AfterEach
    void tearDown() {
        AppConfig.setRuntimeOverride("oidc.enabled", null);
        AppConfig.setRuntimeOverride("oidc.issuer", null);
        AppConfig.setRuntimeOverride("oidc.public-base-url", null);
        com.mtxgdn.oauth.OAuthGrantStore.clear();
    }

    private void enable() {
        AppConfig.setRuntimeOverride("oidc.enabled", "true");
        AppConfig.setRuntimeOverride("oidc.issuer", "https://game.example.com");
    }

    private void withToken(String token) {
        when(headers.getHeaderString("Authorization")).thenReturn("Bearer " + token);
    }

    // ==================== Discovery ====================

    @Test
    @DisplayName("Discovery 返回客户端自动发现所需的全部端点")
    void discoveryHasRequiredFields() {
        enable();
        Response resp = resource.discovery();
        assertEquals(200, resp.getStatus());
        String body = String.valueOf(resp.getEntity());

        // 这几个是 OIDC Discovery 的必填项，少一个客户端就配不起来
        assertTrue(body.contains("\"issuer\""));
        assertTrue(body.contains("\"authorization_endpoint\""));
        assertTrue(body.contains("\"token_endpoint\""));
        assertTrue(body.contains("\"jwks_uri\""));
        assertTrue(body.contains("\"userinfo_endpoint\""));
        assertTrue(body.contains("\"RS256\""), "必须声明使用 RS256");
        assertTrue(body.contains("\"openid\""), "必须声明支持 openid scope");
    }

    @Test
    @DisplayName("Discovery 的端点都用配置的 issuer 拼绝对地址")
    void discoveryUsesConfiguredIssuer() {
        enable();
        String body = String.valueOf(resource.discovery().getEntity());

        assertTrue(body.contains("https://game.example.com/api/auth/oauth/authorize"),
                "必须用配置的 issuer —— 服务在反代后面时请求地址不是对外地址");
        assertTrue(body.contains("https://game.example.com/api/auth/oidc/jwks.json"));
    }

    @Test
    @DisplayName("未启用时 Discovery 返回 404，不泄露端点结构")
    void discoveryHiddenWhenDisabled() {
        AppConfig.setRuntimeOverride("oidc.enabled", "false");
        assertEquals(404, resource.discovery().getStatus());
    }

    // ==================== JWKS ====================

    @Test
    @DisplayName("JWKS 返回公钥，且不含私钥分量")
    void jwksExposesOnlyPublic() {
        enable();
        Response resp = resource.jwks();
        assertEquals(200, resp.getStatus());
        String body = String.valueOf(resp.getEntity());

        assertTrue(body.contains("\"keys\""));
        assertTrue(body.contains("\"kty\":\"RSA\""));
        assertFalse(body.contains("\"d\""), "JWKS 公开可访问，绝不能含私钥");
        assertFalse(body.contains("\"p\""));
        assertFalse(body.contains("\"q\""));
    }

    // ==================== userinfo ====================

    @Test
    @DisplayName("userinfo 用 access_token 换出 sub，客户端据此识别账号")
    void userinfoReturnsSub() {
        enable();
        long userId = 8810L;
        String username = "站内玩家";
        var grant = com.mtxgdn.oauth.OAuthGrantStore.authorize(
                userId, username, "app", Set.of("openid", "profile"));
        String jti = "jti-userinfo-1";
        com.mtxgdn.oauth.OAuthGrantStore.registerToken(jti, grant.grantId());
        String token = com.mtxgdn.service.OAuthService.issueUserAccessToken(
                userId, username, "app", Set.of("openid", "profile"), grant.grantId(), jti);
        withToken(token);

        Response resp = resource.userinfo();
        assertEquals(200, resp.getStatus(), String.valueOf(resp.getEntity()));
        String body = String.valueOf(resp.getEntity());

        assertTrue(body.contains("\"sub\""), "必须有 sub —— 这是 OIDC 登录的核心字段");
        assertTrue(body.contains(OidcSubject.of(username)), "sub 应与该用户的计算值一致");
        assertTrue(body.contains("preferred_username"));
    }

    @Test
    @DisplayName("没有 token 时返回 401，并按 OIDC 规范带 WWW-Authenticate")
    void userinfoRequiresToken() {
        enable();
        Response resp = resource.userinfo();

        assertEquals(401, resp.getStatus());
        Object header = resp.getHeaders().getFirst("WWW-Authenticate");
        assertNotNull(header, "OIDC 客户端依赖这个头判断该用哪种认证");
        assertTrue(String.valueOf(header).contains("invalid_token"));
    }

    @Test
    @DisplayName("无效 token 返回 401")
    void invalidTokenRejected() {
        enable();
        withToken("not-a-real-token");
        assertEquals(401, resource.userinfo().getStatus());
    }

    @Test
    @DisplayName("客户端凭据型 token 不能用于登录 —— 它不代表任何玩家")
    void clientTokenCannotLogin() {
        enable();
        // 机器对机器的 token，没有 uid
        String token = com.mtxgdn.service.OAuthService.buildTokenResponse("app", "read")
                .get("access_token").toString();
        withToken(token);

        assertEquals(401, resource.userinfo().getStatus(),
                "应用身份必须不能当人用 —— 这是整套授权模型的红线");
    }

    @Test
    @DisplayName("撤销授权后 userinfo 立即失效")
    void revokedGrantCannotLogin() {
        enable();
        long userId = 8811L;
        var grant = com.mtxgdn.oauth.OAuthGrantStore.authorize(
                userId, "被撤销者", "app", Set.of("openid"));
        String jti = "jti-userinfo-2";
        com.mtxgdn.oauth.OAuthGrantStore.registerToken(jti, grant.grantId());
        String token = com.mtxgdn.service.OAuthService.issueUserAccessToken(
                userId, "被撤销者", "app", Set.of("openid"), grant.grantId(), jti);
        withToken(token);

        assertEquals(200, resource.userinfo().getStatus(), "撤销前应可用");

        com.mtxgdn.oauth.OAuthGrantStore.revoke(grant.grantId());

        assertEquals(401, resource.userinfo().getStatus(),
                "玩家撤销后必须立刻无法用该身份登录");
    }

    @Test
    @DisplayName("userinfo 响应禁止缓存")
    void userinfoIsNotCacheable() {
        enable();
        long userId = 8812L;
        var grant = com.mtxgdn.oauth.OAuthGrantStore.authorize(
                userId, "缓存测试", "app", Set.of("openid"));
        String jti = "jti-userinfo-3";
        com.mtxgdn.oauth.OAuthGrantStore.registerToken(jti, grant.grantId());
        withToken(com.mtxgdn.service.OAuthService.issueUserAccessToken(
                userId, "缓存测试", "app", Set.of("openid"), grant.grantId(), jti));

        Response resp = resource.userinfo();
        assertEquals("no-store", resp.getHeaders().getFirst("Cache-Control"),
                "含个人信息，不能被任何中间层缓存");
    }
}
