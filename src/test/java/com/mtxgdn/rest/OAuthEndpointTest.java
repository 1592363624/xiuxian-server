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
import java.util.List;
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
    private jakarta.ws.rs.container.ContainerRequestContext requestContext;

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

        // 授权码相关端点要从请求上下文取 userId / username
        requestContext = mock(jakarta.ws.rs.container.ContainerRequestContext.class);
        Field ctxField = OAuthResource.class.getDeclaredField("requestContext");
        ctxField.setAccessible(true);
        ctxField.set(resource, requestContext);
    }

    /** 让后续调用认为是指定玩家在操作 */
    private void loginAs(long userId, String username) {
        when(requestContext.getProperty("userId")).thenReturn(userId);
        when(requestContext.getProperty("username")).thenReturn(username);
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
    @DisplayName("不认识的 grant_type 返回 400")
    void unsupportedGrantTypeRejected() {
        // 注意：authorization_code 与 refresh_token 现在是支持的，
        // 所以这里必须用一个真正不存在的类型 —— 否则测的是别的分支
        String body = "{\"grant_type\":\"password\",\"client_id\":\"" + CLIENT_ID
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

    // ==================== 授权码流程端点 ====================

    private static final String APP_ID = "third_party_app";
    private static final String APP_SECRET = "app_secret_0123456789abcdef";
    private static final String APP_REDIRECT = "https://app.example.com/oauth/callback";

    /** 登记一个第三方应用并重置授权状态 */
    private void registerApp() {
        com.mtxgdn.oauth.OAuthCodeStore.clear();
        com.mtxgdn.oauth.OAuthGrantStore.clear();
        com.mtxgdn.oauth.OAuthClientRegistry.clear();

        Map<String, Object> app = new LinkedHashMap<>();
        app.put("client_id", APP_ID);
        app.put("client_secret", APP_SECRET);
        app.put("name", "修仙助手");
        app.put("redirect_uris", List.of(APP_REDIRECT));
        app.put("scopes", List.of("player:read", "inventory:read"));
        com.mtxgdn.oauth.OAuthClientRegistry.loadFromConfig(List.of(app));
    }

    @Test
    @DisplayName("授权请求：未登录时要求先登录")
    void authorizeRequiresLogin() {
        registerApp();
        Response resp = resource.authorize("code", APP_ID, APP_REDIRECT, "player:read", "xyz");
        assertEquals(401, resp.getStatus());
    }

    @Test
    @DisplayName("授权请求：返回同意页所需的 scope 描述")
    void authorizeReturnsConsentData() {
        registerApp();
        loginAs(42L, "张三");

        Response resp = resource.authorize("code", APP_ID, APP_REDIRECT, "player:read inventory:read", "xyz");
        assertEquals(200, resp.getStatus());

        String body = String.valueOf(resp.getEntity());
        assertTrue(body.contains("修仙助手"), "应带上应用名，玩家才知道是谁在申请");
        assertTrue(body.contains("player:read"), "应列出申请的权限");
        assertTrue(body.contains("xyz"), "state 必须原样带回，第三方靠它防 CSRF");
    }

    @Test
    @DisplayName("攻击：伪造回调地址的授权请求被拒（防开放重定向）")
    void authorizeRejectsForeignRedirect() {
        registerApp();
        loginAs(42L, "张三");

        Response resp = resource.authorize("code", APP_ID, "https://evil.com/cb", "player:read", "");
        assertEquals(400, resp.getStatus(),
                "回调地址不符必须拒绝，且不能往那个地址重定向");
    }

    @Test
    @DisplayName("攻击：申请应用未被允许的 scope 被拒")
    void authorizeRejectsUnallowedScope() {
        registerApp();
        loginAs(42L, "张三");

        // 该应用只被允许 player:read / inventory:read
        Response resp = resource.authorize("code", APP_ID, APP_REDIRECT, "economy:write", "");
        assertEquals(400, resp.getStatus());
        assertTrue(String.valueOf(resp.getEntity()).contains("invalid_scope"));
    }

    @Test
    @DisplayName("攻击：未登记的 client_id 被拒")
    void authorizeRejectsUnknownClient() {
        registerApp();
        loginAs(42L, "张三");

        Response resp = resource.authorize("code", "not_registered", APP_REDIRECT, "player:read", "");
        assertEquals(400, resp.getStatus());
    }

    @Test
    @DisplayName("同意后拿到带 code 的回调地址，code 可换 token")
    void consentThenExchangeCode() {
        registerApp();
        loginAs(42L, "张三");

        Response confirm = resource.authorizeConfirm(confirmRequest(true, "state123"));
        assertEquals(200, confirm.getStatus());
        String callback = String.valueOf(confirm.getEntity());
        assertTrue(callback.contains("code="), "应返回带 code 的回调地址: " + callback);
        assertTrue(callback.contains("state123"), "state 应原样带回");

        String code = extractCode(callback);
        assertNotNull(code);

        // 用 code 换 token
        String tokenBody = "{\"grant_type\":\"authorization_code\",\"code\":\"" + code
                + "\",\"client_id\":\"" + APP_ID + "\",\"client_secret\":\"" + APP_SECRET
                + "\",\"redirect_uri\":\"" + APP_REDIRECT + "\"}";
        Response tokenResp = resource.token(tokenBody);
        assertEquals(200, tokenResp.getStatus(), "换 token 应成功: " + tokenResp.getEntity());

        String body = String.valueOf(tokenResp.getEntity());
        assertTrue(body.contains("access_token"));
        assertTrue(body.contains("refresh_token"), "应同时下发 refresh_token");
    }

    @Test
    @DisplayName("拒绝授权时不发 code，而是回 access_denied")
    void denyReturnsAccessDenied() {
        registerApp();
        loginAs(42L, "张三");

        Response resp = resource.authorizeConfirm(confirmRequest(false, ""));
        assertEquals(200, resp.getStatus());
        String body = String.valueOf(resp.getEntity());
        assertTrue(body.contains("access_denied"), "拒绝应回 access_denied: " + body);
        assertFalse(body.contains("code="), "拒绝时绝不能发授权码");
    }

    @Test
    @DisplayName("授权码只能换一次")
    void codeCannotBeExchangedTwice() {
        registerApp();
        loginAs(42L, "张三");
        String code = extractCode(String.valueOf(resource.authorizeConfirm(confirmRequest(true, "")).getEntity()));

        String tokenBody = "{\"grant_type\":\"authorization_code\",\"code\":\"" + code
                + "\",\"client_id\":\"" + APP_ID + "\",\"client_secret\":\"" + APP_SECRET
                + "\",\"redirect_uri\":\"" + APP_REDIRECT + "\"}";

        assertEquals(200, resource.token(tokenBody).getStatus());
        assertEquals(400, resource.token(tokenBody).getStatus(), "第二次必须失败");
    }

    @Test
    @DisplayName("已授权应用列表只返回自己的授权")
    void grantsListIsOwnerScoped() {
        registerApp();
        loginAs(42L, "张三");
        String code = extractCode(String.valueOf(resource.authorizeConfirm(confirmRequest(true, "")).getEntity()));
        resource.token("{\"grant_type\":\"authorization_code\",\"code\":\"" + code
                + "\",\"client_id\":\"" + APP_ID + "\",\"client_secret\":\"" + APP_SECRET
                + "\",\"redirect_uri\":\"" + APP_REDIRECT + "\"}");

        Response mine = resource.listGrants();
        assertEquals(200, mine.getStatus());
        assertTrue(String.valueOf(mine.getEntity()).contains("修仙助手"), "应能看到授权的应用");

        // 换个人登录，看不到
        loginAs(99L, "李四");
        Response others = resource.listGrants();
        assertFalse(String.valueOf(others.getEntity()).contains("修仙助手"),
                "不能看到别人的授权");
    }

    @Test
    @DisplayName("攻击：用别人的 grant_id 撤销 → 404")
    void cannotRevokeOthersGrant() {
        registerApp();
        loginAs(42L, "张三");
        String code = extractCode(String.valueOf(resource.authorizeConfirm(confirmRequest(true, "")).getEntity()));
        resource.token("{\"grant_type\":\"authorization_code\",\"code\":\"" + code
                + "\",\"client_id\":\"" + APP_ID + "\",\"client_secret\":\"" + APP_SECRET
                + "\",\"redirect_uri\":\"" + APP_REDIRECT + "\"}");

        var grants = com.mtxgdn.oauth.OAuthGrantStore.grantsOf(42L);
        assertFalse(grants.isEmpty());
        String grantId = grants.get(0).grantId();

        // 李四尝试撤销张三的授权
        loginAs(99L, "李四");
        Response resp = resource.revoke("{\"grant_id\":\"" + grantId + "\"}");
        assertEquals(404, resp.getStatus(), "不能用别人的 grant_id 撤销 —— 否则成了越权踢人");
    }

    @Test
    @DisplayName("撤销后 token 立即失效")
    void revokeEndpointInvalidatesToken() {
        registerApp();
        loginAs(42L, "张三");
        String code = extractCode(String.valueOf(resource.authorizeConfirm(confirmRequest(true, "")).getEntity()));
        String tokenBody = String.valueOf(resource.token("{\"grant_type\":\"authorization_code\",\"code\":\""
                + code + "\",\"client_id\":\"" + APP_ID + "\",\"client_secret\":\"" + APP_SECRET
                + "\",\"redirect_uri\":\"" + APP_REDIRECT + "\"}").getEntity());

        String accessToken = extractJsonField(tokenBody, "access_token");
        assertNotNull(OAuthService.parseUserToken(accessToken), "撤销前应有效");

        assertEquals(200, resource.revoke("{\"client_id\":\"" + APP_ID + "\"}").getStatus());

        assertNull(OAuthService.parseUserToken(accessToken),
                "撤销后必须立刻失效");
    }

    private String confirmRequest(boolean approved, String state) {
        return "{\"client_id\":\"" + APP_ID + "\",\"redirect_uri\":\"" + APP_REDIRECT
                + "\",\"scope\":\"player:read\",\"state\":\"" + state
                + "\",\"approved\":" + approved + "}";
    }

    /**
     * 从 confirm 的响应里取出授权码。
     *
     * <p>响应的 {@code code} 字段是状态码（数字 0），授权码在
     * {@code redirect_uri} 里 —— 形如 {@code ...callback?code=xxx&state=yyy}。
     * 别把它们搞混：按 {@code "code":"} 去找会扑空。</p>
     */
    private static String extractCode(String json) {
        String callback = extractJsonField(json, "redirect_uri");
        if (callback == null) {
            return null;
        }
        int i = callback.indexOf("code=");
        if (i < 0) {
            return null;
        }
        int start = i + 5;
        int end = callback.indexOf('&', start);
        return end < 0 ? callback.substring(start) : callback.substring(start, end);
    }

    /** 从 JSON 串里粗取字段值（这些响应都是扁平的，不值得为测试引一个解析器） */
    private static String extractJsonField(String json, String field) {
        int i = json.indexOf("\"" + field + "\":\"");
        if (i < 0) {
            return null;
        }
        int start = i + field.length() + 4;
        int end = json.indexOf('"', start);
        return end < 0 ? null : json.substring(start, end);
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
