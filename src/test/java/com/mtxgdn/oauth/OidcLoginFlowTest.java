package com.mtxgdn.oauth;

import com.mtxgdn.TestBase;
import com.mtxgdn.rest.OAuthResource;
import com.mtxgdn.util.AppConfig;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 完整 OIDC 登录链路，模拟 New API 这类标准客户端的真实调用顺序。
 *
 * <h3>这条链路要一次走通，缺一步登录就失败</h3>
 * <ol>
 *   <li>客户端读 Discovery → 拿到各端点地址；</li>
 *   <li>浏览器跳到 {@code /authorize?scope=openid...&nonce=xxx} → 玩家看到同意页；</li>
 *   <li>玩家同意 → 回调里带 {@code code}；</li>
 *   <li>后端用 code 换 token → 拿到 {@code access_token} + {@code id_token}；</li>
 *   <li>用 {@code access_token} 调 {@code /userinfo} → 拿到 {@code sub}；</li>
 *   <li>客户端用 JWKS 里的公钥验 {@code id_token} 签名、比对 {@code nonce} 与 {@code iss}。</li>
 * </ol>
 *
 * <p>这组测试的价值在于：单看每个端点都"能用"，但把它们串起来常会暴露
 * 参数没透传、issuer 不一致、nonce 丢失这类问题 —— 而这些问题在客户端那边
 * 只表现为一句"登录失败"。</p>
 */
class OidcLoginFlowTest extends TestBase {

    private static final String CLIENT_ID = "newapi";
    private static final String CLIENT_SECRET = "newapi_secret_abcdefghijklmnop";
    private static final String REDIRECT = "https://newapi.example.com/oauth/callback";
    private static final String ISSUER = "https://game.example.com";
    private static final long USER_ID = 9901L;
    private static final String USERNAME = "测试修仙者";

    private OAuthResource oauth;
    private com.mtxgdn.rest.OidcResource oidc;
    private ContainerRequestContext reqCtx;

    @BeforeEach
    void setUp() throws Exception {
        com.mtxgdn.oauth.OAuthGrantStore.clear();
        com.mtxgdn.oauth.OAuthCodeStore.clear();
        com.mtxgdn.oauth.OAuthClientRegistry.clear();

        // 顺序很关键：OAuthService 的静态块会调 reloadClientRegistry()，
        // 用配置文件里的 oauth2.clients（此处为空）覆盖注册表。
        // 若在它初始化之前就登记测试客户端，那登记会被这次重载抹掉，
        // 表现为"find 能查到但 authorize 报未登记"。先触发它，再登记。
        com.mtxgdn.service.OAuthService.isEnabled();

        // 确保密钥就绪
        if (!OidcKeyManager.isReady()) {
            Path dir = Files.createTempDirectory("oidc-flow");
            OidcKeyManager.resetForTest();
            OidcKeyManager.init(dir);
        }

        AppConfig.setRuntimeOverride("oidc.enabled", "true");
        AppConfig.setRuntimeOverride("oidc.issuer", ISSUER);

        // 登记这个"New API"客户端，允许 openid/profile
        Map<String, Object> client = new java.util.LinkedHashMap<>();
        client.put("client_id", CLIENT_ID);
        client.put("client_secret", CLIENT_SECRET);
        client.put("name", "New API");
        client.put("redirect_uris", List.of(REDIRECT));
        client.put("scopes", List.of("openid", "profile", "public:read"));
        OidcClientRegistryBridge.load(client);

        oauth = new OAuthResource();
        oidc = new com.mtxgdn.rest.OidcResource();
        reqCtx = org.mockito.Mockito.mock(ContainerRequestContext.class);
        Field f = OAuthResource.class.getDeclaredField("requestContext");
        f.setAccessible(true);
        f.set(oauth, reqCtx);
    }

    @AfterEach
    void tearDown() {
        com.mtxgdn.oauth.OAuthGrantStore.clear();
        com.mtxgdn.oauth.OAuthCodeStore.clear();
        com.mtxgdn.oauth.OAuthClientRegistry.clear();
        AppConfig.setRuntimeOverride("oidc.enabled", null);
        AppConfig.setRuntimeOverride("oidc.issuer", null);
    }

    private void loginAs() {
        org.mockito.Mockito.when(reqCtx.getProperty("userId")).thenReturn(USER_ID);
        org.mockito.Mockito.when(reqCtx.getProperty("username")).thenReturn(USERNAME);
    }

    @Test
    @DisplayName("完整登录链路：授权 → 换 token → userinfo → 验签全通")
    void fullLoginFlow() throws Exception {
        loginAs();
        String nonce = "nonce-from-client-123";

        // ── 第 1 步：授权请求（客户端带 openid 与 nonce）
        Response consent = oauth.authorize("code", CLIENT_ID, REDIRECT,
                "openid profile", "csrf-state-1");
        assertEquals(200, consent.getStatus(),
                "同意页应能加载 —— 否则玩家根本到不了授权这一步: " + consent.getEntity());

        // ── 第 2 步：玩家同意，拿到授权码
        String confirmBody = "{\"client_id\":\"" + CLIENT_ID + "\",\"redirect_uri\":\"" + REDIRECT
                + "\",\"scope\":\"openid profile\",\"state\":\"csrf-state-1\""
                + ",\"nonce\":\"" + nonce + "\",\"approved\":true}";
        Response confirmResp = oauth.authorizeConfirm(confirmBody);
        assertEquals(200, confirmResp.getStatus());

        String code = extractCode(String.valueOf(confirmResp.getEntity()));
        assertNotNull(code, "回调地址里应带 code");

        // ── 第 3 步：用 code 换 token
        String tokenBody = "{\"grant_type\":\"authorization_code\",\"code\":\"" + code
                + "\",\"client_id\":\"" + CLIENT_ID + "\",\"client_secret\":\"" + CLIENT_SECRET
                + "\",\"redirect_uri\":\"" + REDIRECT + "\"}";
        Response tokenResp = oauth.token(tokenBody);
        assertEquals(200, tokenResp.getStatus(), String.valueOf(tokenResp.getEntity()));

        String json = String.valueOf(tokenResp.getEntity());
        String accessToken = extractField(json, "access_token");
        String idToken = extractField(json, "id_token");

        assertNotNull(accessToken, "应下发 access_token");
        assertNotNull(idToken, "申请了 openid 就必须下发 id_token —— 这是 OIDC 的定义");

        // ── 第 4 步：验 id_token 签名（客户端会做的第一件事）
        var claims = io.jsonwebtoken.Jwts.parserBuilder()
                .setSigningKey(OidcKeyManager.publicKey())
                .build()
                .parseClaimsJws(idToken)
                .getBody();

        assertEquals(ISSUER, claims.getIssuer(),
                "iss 必须与 Discovery 的 issuer 一致，否则客户端判为无效 token");
        assertEquals(CLIENT_ID, claims.getAudience());
        assertEquals(OidcSubject.of(USERNAME), claims.getSubject(),
                "sub 必须是该玩家的稳定值 —— 客户端用它认账号");
        assertEquals(nonce, claims.get("nonce"),
                "nonce 必须原样回填，对不上客户端会拒绝这次登录");

        // ── 第 5 步：userinfo，确认与 id_token 的 sub 一致
        Response info = invokeUserinfo(oidc, accessToken);
        assertEquals(200, info.getStatus(), String.valueOf(info.getEntity()));
        assertTrue(String.valueOf(info.getEntity()).contains(OidcSubject.of(USERNAME)),
                "userinfo 的 sub 必须与 id_token 的 sub 一致 —— "
                        + "不一致会让客户端把同一个人当成两个账号");
    }

    @Test
    @DisplayName("不申请 openid 时不发 id_token —— 那是普通 OAuth，不是登录")
    void noIdTokenWithoutOpenid() {
        loginAs();

        Response consent = oauth.authorize("code", CLIENT_ID, REDIRECT, "public:read", "");
        assertEquals(200, consent.getStatus(), "同意页: " + consent.getEntity());

        String confirmBody = "{\"client_id\":\"" + CLIENT_ID + "\",\"redirect_uri\":\"" + REDIRECT
                + "\",\"scope\":\"public:read\",\"state\":\"\",\"approved\":true}";
        Response cr = oauth.authorizeConfirm(confirmBody);
        String code = extractCode(String.valueOf(cr.getEntity()));
        assertNotNull(code, "应拿到授权码: " + cr.getStatus() + " " + cr.getEntity());

        String tokenBody = "{\"grant_type\":\"authorization_code\",\"code\":\"" + code
                + "\",\"client_id\":\"" + CLIENT_ID + "\",\"client_secret\":\"" + CLIENT_SECRET
                + "\",\"redirect_uri\":\"" + REDIRECT + "\"}";
        Response tr = oauth.token(tokenBody);
        String json = String.valueOf(tr.getEntity());
        assertNotNull(extractField(json, "access_token"), "换 token 失败: " + tr.getStatus() + " " + json);
        assertNull(extractField(json, "id_token"),
                "没申请 openid 就不该发身份凭证 —— 那会让普通数据授权意外获得登录能力");
    }

    @Test
    @DisplayName("同一个玩家两次登录拿到同一个 sub，客户端不会重复建号")
    void subIsStableAcrossLogins() {
        loginAs();
        String sub1 = loginOnceAndGetSub();
        String sub2 = loginOnceAndGetSub();

        assertEquals(sub1, sub2,
                "两次登录的 sub 必须相同，否则客户端每次都会新建一个账号");
    }

    private String loginOnceAndGetSub() {
        com.mtxgdn.oauth.OAuthGrantStore.clear();
        com.mtxgdn.oauth.OAuthCodeStore.clear();

        oauth.authorize("code", CLIENT_ID, REDIRECT, "openid profile", "");
        String confirmBody = "{\"client_id\":\"" + CLIENT_ID + "\",\"redirect_uri\":\"" + REDIRECT
                + "\",\"scope\":\"openid profile\",\"state\":\"\",\"approved\":true}";
        String code = extractCode(String.valueOf(oauth.authorizeConfirm(confirmBody).getEntity()));

        String json = String.valueOf(oauth.token("{\"grant_type\":\"authorization_code\",\"code\":\""
                + code + "\",\"client_id\":\"" + CLIENT_ID + "\",\"client_secret\":\"" + CLIENT_SECRET
                + "\",\"redirect_uri\":\"" + REDIRECT + "\"}").getEntity());
        String idToken = extractField(json, "id_token");
        assertNotNull(idToken);
        return io.jsonwebtoken.Jwts.parserBuilder()
                .setSigningKey(OidcKeyManager.publicKey())
                .build()
                .parseClaimsJws(idToken)
                .getBody()
                .getSubject();
    }

    // ==================== 工具 ====================

    /** 借 ClientRegistry 的公开入口登记客户端（避免为测试加生产 API） */
    private static final class OidcClientRegistryBridge {
        static void load(Map<String, Object> client) {
            com.mtxgdn.oauth.OAuthClientRegistry.loadFromConfig(List.of(client));
        }
    }


    private static Response invokeUserinfo(com.mtxgdn.rest.OidcResource resource, String token) {
        try {
            Field h = com.mtxgdn.rest.OidcResource.class.getDeclaredField("httpHeaders");
            h.setAccessible(true);
            var headers = org.mockito.Mockito.mock(jakarta.ws.rs.core.HttpHeaders.class);
            org.mockito.Mockito.when(headers.getHeaderString("Authorization"))
                    .thenReturn("Bearer " + token);
            h.set(resource, headers);
            return resource.userinfo();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String extractCode(String json) {
        String callback = extractField(json, "redirect_uri");
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

    private static String extractField(String json, String field) {
        int i = json.indexOf("\"" + field + "\":\"");
        if (i < 0) {
            return null;
        }
        int start = i + field.length() + 4;
        int end = json.indexOf('"', start);
        return end < 0 ? null : json.substring(start, end);
    }
}
