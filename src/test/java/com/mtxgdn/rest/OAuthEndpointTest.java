package com.mtxgdn.rest;

import com.mtxgdn.TestBase;
import com.mtxgdn.service.OAuthService;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * OAuth 端点的端到端行为：从换 token 到访问受保护资源。
 *
 * <p><b>为什么需要它</b>：{@code OAuthServiceTest} 覆盖了 service 层，
 * 但"这个 OAuth 到底能不能正常用"取决于 {@code OAuthResource} 这一层：
 * 状态码对不对、scope 检查有没有真的拦、Bearer 头解析是不是接上了。
 * 这些只有在端点层面测才看得出来。</p>
 *
 * <p>这里直接实例化 {@code OAuthResource}，用反射注入 {@code @Context HttpHeaders}，
 * 走的是与真实请求同一段代码，只是跳过了 Jersey 的分发。</p>
 */
class OAuthEndpointTest extends TestBase {

    private static final String CLIENT_ID = "ep_client";
    private static final String CLIENT_SECRET = "ep_secret_0123456789abcdef";

    private OAuthResource resource;
    private HttpHeaders headers;

    @BeforeEach
    void setUp() throws Exception {
        Map<String, Object> update = new LinkedHashMap<>();
        update.put("enabled", true);
        update.put("client_id", CLIENT_ID);
        update.put("client_secret", CLIENT_SECRET);
        update.put("scopes", "read write");
        update.put("token_ttl_seconds", 3600);
        OAuthService.updateConfig(update);

        resource = new OAuthResource();
        headers = mock(HttpHeaders.class);
        // @Context 字段在真实容器里由 Jersey 注入，测试里手工塞一个
        Field f = OAuthResource.class.getDeclaredField("httpHeaders");
        f.setAccessible(true);
        f.set(resource, headers);
    }

    @AfterEach
    void tearDown() {
        Map<String, Object> update = new LinkedHashMap<>();
        update.put("enabled", true);
        OAuthService.updateConfig(update);
    }

    // ==================== /token ====================

    @Test
    @DisplayName("正确凭据能换到 token（200，且含 access_token/expires_in/scope）")
    void tokenIssuedWithValidCredentials() {
        Response resp = resource.token(tokenRequest(CLIENT_ID, CLIENT_SECRET, null));

        assertEquals(200, resp.getStatus(), "凭据正确应 200");
        String body = String.valueOf(resp.getEntity());
        assertTrue(body.contains("access_token"), "应返回 access_token，实际: " + body);
        assertTrue(body.contains("token_type"), "应返回 token_type");
        assertTrue(body.contains("expires_in"), "应返回 expires_in");
        assertTrue(body.contains("Bearer"), "token_type 应为 Bearer");
    }

    @Test
    @DisplayName("错误凭据返回 401 invalid_client")
    void tokenRejectedWithWrongCredentials() {
        Response resp = resource.token(tokenRequest(CLIENT_ID, "wrong", null));

        assertEquals(401, resp.getStatus(), "凭据错误应 401");
        assertTrue(String.valueOf(resp.getEntity()).contains("invalid_client"));
    }

    @Test
    @DisplayName("缺少 client_id/client_secret 返回 400")
    void tokenRequiresCredentials() {
        assertEquals(400, resource.token(tokenRequest("", "", null)).getStatus());
    }

    @Test
    @DisplayName("只支持 client_credentials，其它 grant_type 返回 400")
    void unsupportedGrantTypeRejected() {
        String body = "{\"grant_type\":\"authorization_code\",\"client_id\":\"" + CLIENT_ID
                + "\",\"client_secret\":\"" + CLIENT_SECRET + "\"}";
        Response resp = resource.token(body);
        assertEquals(400, resp.getStatus());
        assertTrue(String.valueOf(resp.getEntity()).contains("unsupported_grant_type"));
    }

    @Test
    @DisplayName("非法 JSON 返回 400 而不是 500")
    void malformedJsonRejected() {
        assertEquals(400, resource.token("{").getStatus());
        assertEquals(400, resource.token("").getStatus());
        assertEquals(400, resource.token("not json").getStatus());
    }

    @Test
    @DisplayName("OAuth 关闭时换 token 返回 503")
    void disabledReturns503() {
        Map<String, Object> update = new LinkedHashMap<>();
        update.put("enabled", false);
        OAuthService.updateConfig(update);

        Response resp = resource.token(tokenRequest(CLIENT_ID, CLIENT_SECRET, null));
        assertEquals(503, resp.getStatus(), "未启用应 503 而不是 401");
    }

    // ==================== 受保护端点 ====================

    @Test
    @DisplayName("带上有效 token 能访问 /me")
    void meWithValidToken() {
        String token = issueToken("read");
        when(headers.getHeaderString("Authorization")).thenReturn("Bearer " + token);

        Response resp = resource.me();
        assertEquals(200, resp.getStatus(), "/me 应放行，实际: " + resp.getEntity());
        assertTrue(String.valueOf(resp.getEntity()).contains(CLIENT_ID),
                "应回报 token 所属 client_id");
    }

    @Test
    @DisplayName("没有 Authorization 头访问 /me 返回 401")
    void meWithoutToken() {
        when(headers.getHeaderString("Authorization")).thenReturn(null);
        assertEquals(401, resource.me().getStatus());
    }

    @Test
    @DisplayName("伪造的 token 访问 /me 返回 401")
    void meWithGarbageToken() {
        when(headers.getHeaderString("Authorization")).thenReturn("Bearer garbage.token.here");
        assertEquals(401, resource.me().getStatus());
    }

    @Test
    @DisplayName("read token 能读（/protected/status），不能写（/protected/echo）")
    void readTokenCannotWrite() {
        String token = issueToken("read");
        when(headers.getHeaderString("Authorization")).thenReturn("Bearer " + token);

        assertEquals(200, resource.protectedStatus().getStatus(), "read 应能访问 status");
        assertEquals(401, resource.protectedEcho("{}").getStatus(),
                "read token 不应能访问需要 write 的 echo —— 否则 scope 形同虚设");
    }

    @Test
    @DisplayName("write token 能写")
    void writeTokenCanWrite() {
        String token = issueToken("write");
        when(headers.getHeaderString("Authorization")).thenReturn("Bearer " + token);

        Response resp = resource.protectedEcho("{\"hello\":1}");
        assertEquals(200, resp.getStatus(), "write 应能访问 echo");
    }

    @Test
    @DisplayName("请求的 scope 覆盖配置时被收窄，实际权限以配置为准")
    void escalatedScopeIsNarrowed() {
        // 客户端试图多要 admin
        Response resp = resource.token(tokenRequest(CLIENT_ID, CLIENT_SECRET, "read write admin"));
        assertEquals(200, resp.getStatus());

        String body = String.valueOf(resp.getEntity());
        assertFalse(body.contains("admin"),
                "响应里的 scope 不应含未授权项，实际: " + body);
    }

    // ==================== /introspect ====================

    @Test
    @DisplayName("带客户端凭据可以内省有效 token")
    void introspectWithCredentials() {
        String token = issueToken("read");
        String body = "{\"client_id\":\"" + CLIENT_ID + "\",\"client_secret\":\"" + CLIENT_SECRET
                + "\",\"token\":\"" + token + "\"}";

        Response resp = resource.introspect(body);
        assertEquals(200, resp.getStatus());
        assertTrue(String.valueOf(resp.getEntity()).contains("\"active\":true"),
                "有效 token 应 active=true");
    }

    @Test
    @DisplayName("不带凭据的内省被拒绝（否则等于免费的 token 有效性探针）")
    void introspectRequiresCredentials() {
        String token = issueToken("read");
        Response resp = resource.introspect("{\"token\":\"" + token + "\"}");

        assertEquals(401, resp.getStatus(),
                "内省必须要求客户端凭据（RFC 7662 §2.1）");
    }

    @Test
    @DisplayName("凭据错误的内省同样 401，不泄露 token 是否存在")
    void introspectWithWrongCredentials() {
        String token = issueToken("read");
        String body = "{\"client_id\":\"" + CLIENT_ID + "\",\"client_secret\":\"wrong\",\"token\":\""
                + token + "\"}";
        assertEquals(401, resource.introspect(body).getStatus());
    }

    // ==================== 工具 ====================

    private String issueToken(String scope) {
        return OAuthService.issueAccessToken(CLIENT_ID, scope);
    }

    private static String tokenRequest(String id, String secret, String scope) {
        StringBuilder sb = new StringBuilder("{\"grant_type\":\"client_credentials\"");
        if (id != null && !id.isEmpty()) {
            sb.append(",\"client_id\":\"").append(id).append("\"");
        }
        if (secret != null && !secret.isEmpty()) {
            sb.append(",\"client_secret\":\"").append(secret).append("\"");
        }
        if (scope != null) {
            sb.append(",\"scope\":\"").append(scope).append("\"");
        }
        return sb.append("}").toString();
    }
}
