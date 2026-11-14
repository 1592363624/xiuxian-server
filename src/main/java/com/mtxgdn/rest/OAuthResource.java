package com.mtxgdn.rest;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mtxgdn.Main;
import com.mtxgdn.service.OAuthService;
import com.mtxgdn.util.AppConfig;
import io.jsonwebtoken.Claims;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import com.mtxgdn.i18n.I18n;

/**
 * OAuth 2.0 REST 接口。
 *
 * <h3>机器对机器（client_credentials）</h3>
 * <ul>
 *   <li>POST /api/auth/oauth/token — 客户端凭据换 token（只能访问公共只读数据）</li>
 *   <li>POST /api/auth/oauth/introspect — token 内省（需客户端凭据）</li>
 *   <li>GET  /api/auth/oauth/me — 查看当前 token 信息</li>
 *   <li>GET  /api/auth/oauth/protected/status — 服务器状态</li>
 *   <li>POST /api/auth/oauth/protected/echo — 回显</li>
 * </ul>
 *
 * <h3>第三方代表玩家访问（authorization_code）</h3>
 * <ul>
 *   <li>GET  /api/auth/oauth/authorize — 校验授权请求，返回同意页数据（不发码）</li>
 *   <li>POST /api/auth/oauth/authorize/confirm — 玩家同意后才签发授权码</li>
 *   <li>POST /api/auth/oauth/token — 用授权码换 access_token + refresh_token</li>
 *   <li>POST /api/auth/oauth/revoke — 撤销授权（token 立即失效）</li>
 *   <li>GET  /api/auth/oauth/grants — 我授权过哪些应用</li>
 * </ul>
 *
 * <p>两种 token 靠 {@code uid} claim 区分：有 uid 才是「代表某玩家」。
 * 详见 {@link com.mtxgdn.oauth.OAuthAccess}。</p>
 */
@Path("/auth/oauth")
public class OAuthResource extends BaseResource {

    private static final Gson gson = new Gson();

    @Context
    private HttpHeaders httpHeaders;

    /** 请求上下文：{@code JwtAuthFilter} 会把 userId / username 写进属性里 */
    @Context
    private jakarta.ws.rs.container.ContainerRequestContext requestContext;

    // ==================== 令牌端点 ====================

    @POST
    @Path("/token")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response token(String body) {
        if (!OAuthService.isEnabled()) {
            return error(503, "oauth2_not_enabled", "OAuth 2.0 支持未启用（oauth2.enabled=false）");
        }

        JsonObject json;
        try {
            json = com.google.gson.JsonParser.parseString(body).getAsJsonObject();
        } catch (Exception e) {
            return error(400, "invalid_request", "请求体不是合法的 JSON");
        }

        String grantType = json.has("grant_type") ? json.get("grant_type").getAsString() : "";
        String clientId = json.has("client_id") ? json.get("client_id").getAsString() : "";
        String clientSecret = json.has("client_secret") ? json.get("client_secret").getAsString() : "";
        String scope = json.has("scope") ? json.get("scope").getAsString() : null;

        if ("authorization_code".equals(grantType)) {
            return tokenFromAuthorizationCode(json);
        }
        if ("refresh_token".equals(grantType)) {
            return tokenFromRefreshToken(json);
        }
        if (!"client_credentials".equals(grantType)) {
            return error(400, "unsupported_grant_type",
                    "仅支持 client_credentials / authorization_code / refresh_token");
        }
        if (clientId.isBlank() || clientSecret.isBlank()) {
            return error(400, "invalid_client", "缺少 client_id / client_secret");
        }
        if (!OAuthService.validateClient(clientId, clientSecret)) {
            return error(401, "invalid_client", "客户端凭据无效");
        }

        return Response.ok(gson.toJson(OAuthService.buildTokenResponse(clientId, scope))).build();
    }

    /**
     * 用授权码换 token。
     *
     * <p>这是第三方应用的后端调用的端点。<b>必须校验 redirect_uri</b>：
     * RFC 6749 §4.1.3 要求，若授权请求里带了 redirect_uri，换取时也要带上且一致。
     * 不校验的话，攻击者截获 code 后可以用自己的 redirect_uri 换 token。</p>
     */
    private Response tokenFromAuthorizationCode(JsonObject json) {
        String code = json.has("code") ? json.get("code").getAsString() : "";
        String clientId = json.has("client_id") ? json.get("client_id").getAsString() : "";
        String clientSecret = json.has("client_secret") ? json.get("client_secret").getAsString() : "";
        String redirectUri = json.has("redirect_uri") ? json.get("redirect_uri").getAsString() : null;

        if (!com.mtxgdn.oauth.OAuthClientRegistry.validate(clientId, clientSecret)) {
            return error(401, "invalid_client", "客户端凭据无效");
        }
        if (code.isBlank()) {
            return error(400, "invalid_request", "缺少 code");
        }

        // consume 内部会校验 一次性 + 过期 + 绑定的 client_id / redirect_uri
        com.mtxgdn.oauth.OAuthCodeStore.Entry entry =
                com.mtxgdn.oauth.OAuthCodeStore.consume(code, clientId, redirectUri);
        if (entry == null) {
            return error(400, "invalid_grant", "授权码无效、已过期、已被使用，或与客户端不匹配");
        }

        return issueUserTokenResponse(entry.clientId(), entry.userId(), entry.username(),
                entry.scopes(), entry.nonce());
    }

    /** 用 refresh_token 换新的一对 token（轮换：旧码立即作废） */
    private Response tokenFromRefreshToken(JsonObject json) {
        String clientId = json.has("client_id") ? json.get("client_id").getAsString() : "";
        String clientSecret = json.has("client_secret") ? json.get("client_secret").getAsString() : "";
        String refreshToken = json.has("refresh_token") ? json.get("refresh_token").getAsString() : "";

        if (!com.mtxgdn.oauth.OAuthClientRegistry.validate(clientId, clientSecret)) {
            return error(401, "invalid_client", "客户端凭据无效");
        }
        if (refreshToken.isBlank()) {
            return error(400, "invalid_request", "缺少 refresh_token");
        }

        com.mtxgdn.oauth.OAuthGrantStore.RotationResult rotation =
                com.mtxgdn.oauth.OAuthGrantStore.rotate(refreshToken);
        if (rotation == null) {
            // 注意：重放检测命中时整条授权已被吊销，这里统一回 invalid_grant
            return error(400, "invalid_grant", "refresh_token 无效、已过期或已被使用");
        }

        com.mtxgdn.oauth.OAuthGrantStore.Grant grant = rotation.grant();
        if (!grant.clientId().equals(clientId)) {
            return error(400, "invalid_grant", "refresh_token 不属于该客户端");
        }

        return issueUserTokenResponseWithRefresh(grant.clientId(), grant.userId(),
                grant.username(), grant.scopes(), rotation.refreshToken());
    }

    /**
     * 签发用户型 token 的响应体（首次授权）。
     *
     * <p>{@code grantId} 由 {@link com.mtxgdn.oauth.OAuthGrantStore#authorize} 生成 ——
     * 玩家在同意页点"同意"时就已经建立了授权记录，这里只是签发凭证。</p>
     */
    private Response issueUserTokenResponse(String clientId, long userId, String username,
                                            java.util.Set<String> scopes) {
        return issueUserTokenResponse(clientId, userId, username, scopes, null);
    }

    /**
     * 签发用户型 token 的响应体（首次授权）。
     *
     * <p>{@code grantId} 由 {@link com.mtxgdn.oauth.OAuthGrantStore#authorize} 生成 ——
     * 玩家在同意页点"同意"时就已经建立了授权记录，这里只是签发凭证。</p>
     *
     * @param nonce OIDC 的 nonce，非 OIDC 请求时为 null
     */
    private Response issueUserTokenResponse(String clientId, long userId, String username,
                                            java.util.Set<String> scopes, String nonce) {
        com.mtxgdn.oauth.OAuthGrantStore.Grant grant =
                com.mtxgdn.oauth.OAuthGrantStore.authorize(userId, username, clientId, scopes);
        String refreshToken = com.mtxgdn.oauth.OAuthGrantStore.issueRefreshToken(grant.grantId());
        return buildUserTokenResponse(grant, refreshToken, nonce);
    }

    private Response issueUserTokenResponseWithRefresh(String clientId, long userId, String username,
                                                       java.util.Set<String> scopes, String refreshToken) {
        com.mtxgdn.oauth.OAuthGrantStore.Grant grant =
                com.mtxgdn.oauth.OAuthGrantStore.authorize(userId, username, clientId, scopes);
        // 刷新流程不带 nonce —— OIDC 规范只要求首次 id_token 回填它，
        // 客户端在刷新时也不会再拿它做比对
        return buildUserTokenResponse(grant, refreshToken, null);
    }

    /** 组装用户型 token 的响应体 */
    private Response buildUserTokenResponse(com.mtxgdn.oauth.OAuthGrantStore.Grant grant,
                                            String refreshToken, String nonce) {
        String jti = java.util.UUID.randomUUID().toString();
        com.mtxgdn.oauth.OAuthGrantStore.registerToken(jti, grant.grantId());

        String accessToken = OAuthService.issueUserAccessToken(
                grant.userId(), grant.username(), grant.clientId(),
                grant.scopes(), grant.grantId(), jti);

        java.util.Map<String, Object> resp = new java.util.LinkedHashMap<>();
        resp.put("access_token", accessToken);
        resp.put("token_type", "Bearer");
        resp.put("expires_in", OAuthService.getTokenTtlSeconds());
        resp.put("scope", com.mtxgdn.oauth.OAuthScope.join(grant.scopes()));
        resp.put("refresh_token", refreshToken);

        // 只有申请了 openid 才签发 id_token —— 这是 OIDC 规范的分界线：
        // 没有 openid 的请求是普通 OAuth，不应拿到身份凭证
        if (grant.scopes().contains(com.mtxgdn.oauth.OAuthScope.OPENID)) {
            String issuer = new OidcResource().issuerForToken();
            String email = grant.scopes().contains(com.mtxgdn.oauth.OAuthScope.EMAIL)
                    ? lookupEmailQuietly(grant.userId()) : null;
            resp.put("id_token", com.mtxgdn.oauth.OidcIdToken.sign(
                    issuer, grant.clientId(), grant.username(), nonce, grant.scopes(), email));
        }
        return Response.ok(gson.toJson(resp)).build();
    }

    /** 查邮箱，失败返回 null（邮箱是可选字段，不该让发 token 失败） */
    private static String lookupEmailQuietly(long userId) {
        try {
            return new com.mtxgdn.db.UserServiceDAO().findEmailById(userId);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * RFC 7662 风格的 token 内省。
     *
     * <p><b>必须带客户端凭据</b>：内省返回的是"这个 token 是否有效、属于谁、
     * 有什么 scope"。如果不鉴权，任何人都能拿一个 token 来问它是否有效 ——
     * 这等于给了一个免费的 token 有效性探针，也让内省结果可以被随意解析。
     * RFC 7662 §2.1 明确要求内省端点受保护（通常用 client 凭据认证）。</p>
     *
     * <p>这里支持三处取值：请求体里的 {@code client_id}/{@code client_secret}，
     * 或 HTTP Basic 认证（标准做法）。校验不通过一律 401，不区分
     * "凭据错"与"token 不存在"，避免成为枚举工具。</p>
     */
    @POST
    @Path("/introspect")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response introspect(String body) {
        if (!OAuthService.isEnabled()) {
            return error(503, "oauth2_not_enabled", "OAuth 2.0 支持未启用（oauth2.enabled=false）");
        }

        JsonObject json;
        try {
            json = com.google.gson.JsonParser.parseString(body).getAsJsonObject();
        } catch (Exception e) {
            return error(400, "invalid_request", "请求体不是合法的 JSON");
        }

        // 凭据来源：请求体字段优先，其次 HTTP Basic
        String clientId = json.has("client_id") ? json.get("client_id").getAsString() : "";
        String clientSecret = json.has("client_secret") ? json.get("client_secret").getAsString() : "";
        if (clientId.isBlank() || clientSecret.isBlank()) {
            String[] basic = parseBasicAuth(httpHeaders.getHeaderString("Authorization"));
            if (basic != null) {
                clientId = basic[0];
                clientSecret = basic[1];
            }
        }

        if (!OAuthService.validateClient(clientId, clientSecret)) {
            // 不区分"凭据错"和"token 不存在"：否则这个端点就成了 token 有效性探针
            return error(401, "invalid_client", "需要有效的客户端凭据才能内省 token");
        }

        String token = json.has("token") ? json.get("token").getAsString() : "";
        return Response.ok(gson.toJson(OAuthService.introspect(token))).build();
    }

    /** 解析 {@code Authorization: Basic base64(id:secret)}；不适应返回 {@code null}。 */
    private static String[] parseBasicAuth(String header) {
        if (header == null || !header.regionMatches(true, 0, "Basic ", 0, 6)) {
            return null;
        }
        try {
            String decoded = new String(java.util.Base64.getDecoder().decode(header.substring(6).trim()),
                    java.nio.charset.StandardCharsets.UTF_8);
            int colon = decoded.indexOf(':');
            if (colon < 0) {
                return null;
            }
            return new String[]{decoded.substring(0, colon), decoded.substring(colon + 1)};
        } catch (RuntimeException e) {
            return null;
        }
    }

    // ==================== 受保护资源端点（OAuth token 真正可用之处）====================

    /** 查看当前 OAuth token 信息与服务器概况（需有效 Bearer token） */
    @GET
    @Path("/me")
    @Produces(MediaType.APPLICATION_JSON)
    public Response me() {
        Claims claims = requireOAuthToken("read");
        if (claims == null) {
            return unauthorized();
        }
        JsonObject data = new JsonObject();
        data.addProperty("client_id", claims.get("client_id", String.class));
        data.addProperty("scope", claims.get("scope", String.class));
        data.addProperty("token_type", "Bearer");
        data.addProperty("issuedAt", claims.getIssuedAt().getTime());
        data.addProperty("expiresAt", claims.getExpiration().getTime());
        data.addProperty("subject", claims.getSubject());
        data.add("server", serverInfo());
        return ok("OAuth token 验证通过", data);
    }

    /** 受保护资源示例：服务器状态（需 read scope） */
    @GET
    @Path("/protected/status")
    @Produces(MediaType.APPLICATION_JSON)
    public Response protectedStatus() {
        Claims claims = requireOAuthToken("read");
        if (claims == null) {
            return unauthorized();
        }
        JsonObject data = serverInfo();
        data.addProperty("client_id", claims.get("client_id", String.class));
        data.addProperty("message", I18n.t("oauth.accessing_protected_resource",
                claims.get("scope", String.class)));
        return ok(I18n.t("oauth.server_status_protected"), data);
    }

    /** 受保护资源示例：回显（需 write scope） */
    @POST
    @Path("/protected/echo")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response protectedEcho(String body) {
        Claims claims = requireOAuthToken("write");
        if (claims == null) {
            return unauthorized();
        }
        JsonObject data = new JsonObject();
        data.addProperty("client_id", claims.get("client_id", String.class));
        data.addProperty("scope", claims.get("scope", String.class));
        data.addProperty("echo", body == null || body.isBlank() ? "(空请求体)" : body);
        data.addProperty("receivedAt", System.currentTimeMillis());
        return ok("OAuth write 资源调用成功", data);
    }

    private Claims requireOAuthToken(String requiredScope) {
        String authorization = httpHeaders.getHeaderString("Authorization");
        String token = OAuthService.extractBearer(authorization);
        if (token == null) {
            return null;
        }
        Claims claims = OAuthService.parseToken(token);
        if (claims == null) {
            return null;
        }
        String scope = claims.get("scope", String.class);
        if (!OAuthService.hasScope(scope, requiredScope)) {
            return null;
        }
        return claims;
    }

    private JsonObject serverInfo() {
        JsonObject server = new JsonObject();
        server.addProperty("appName", AppConfig.get("app.name", "修仙世界"));
        server.addProperty("onlineCount", Main.gameWebSocketApp != null ? Main.gameWebSocketApp.getOnlineCount() : 0);
        server.addProperty("uptimeSeconds", (System.currentTimeMillis() - Main.serverStartTime) / 1000);
        server.addProperty("serverTime", System.currentTimeMillis());
        return server;
    }

    private Response unauthorized() {
        return error(401, "invalid_token", "缺少有效的 OAuth access_token（Authorization: Bearer <token>），或 scope 不足");
    }

    private Response ok(String message, JsonObject data) {
        JsonObject json = new JsonObject();
        json.addProperty("code", 0);
        json.addProperty("message", message);
        if (data != null) {
            json.add("data", data);
        }
        return Response.ok(json.toString()).build();
    }

    // ==================== 授权码流程 ====================

    /**
     * 授权请求的第一步：校验参数并返回「同意页」需要的信息。
     *
     * <p>标准 OAuth 在这里应该 302 到登录页或同意页；本项目的前端是 SPA，
     * 所以这里返回 JSON，由前端渲染同意页。<b>但不会在这一步发授权码</b> ——
     * 必须等玩家点"同意"（{@link #authorizeConfirm}）才发。</p>
     *
     * <h3>校验顺序很重要</h3>
     * <p>按 RFC 6749 §4.1.2.1，{@code client_id} 与 {@code redirect_uri} 必须
     * <b>先</b>校验：如果这两个不合法，绝不能往那个地址重定向，
     * 否则这个端点就变成了开放重定向。</p>
     */
    @GET
    @Path("/authorize")
    @Produces(MediaType.APPLICATION_JSON)
    public Response authorize(@QueryParam("response_type") String responseType,
                              @QueryParam("client_id") String clientId,
                              @QueryParam("redirect_uri") String redirectUri,
                              @QueryParam("scope") String scope,
                              @QueryParam("state") String state) {
        if (!OAuthService.isEnabled()) {
            return error(503, "oauth2_not_enabled", "OAuth 2.0 支持未启用");
        }
        if (!"code".equals(responseType)) {
            return error(400, "unsupported_response_type", "仅支持 response_type=code");
        }

        // 应用必须已登记，否则直接拒绝（不做任何重定向）
        com.mtxgdn.oauth.OAuthClientRegistry.Client client =
                com.mtxgdn.oauth.OAuthClientRegistry.find(clientId);
        if (client == null) {
            return error(400, "invalid_client", "应用未登记或已停用");
        }

        // 回调地址必须与登记值全等 —— 前缀匹配会让 app.com.evil.com 通过
        if (!com.mtxgdn.oauth.OAuthClientRegistry.isRedirectUriAllowed(clientId, redirectUri)) {
            return error(400, "invalid_request",
                    "redirect_uri 未登记。出于安全考虑必须与登记的地址完全一致");
        }

        // scope 必须是该应用被允许申请的，且全部合法
        java.util.Set<String> requested = com.mtxgdn.oauth.OAuthScope.parse(scope);
        if (requested.isEmpty()) {
            return error(400, "invalid_scope", "必须指定至少一个 scope");
        }
        for (String s : requested) {
            if (!com.mtxgdn.oauth.OAuthScope.isValid(s)) {
                return error(400, "invalid_scope", "未知的 scope: " + s);
            }
            if (!client.canRequest(s)) {
                return error(400, "invalid_scope", "应用未被允许申请: " + s);
            }
        }

        // 需要玩家已登录才能展示同意页
        Long userId = currentUserId();
        if (userId == null) {
            return error(401, "login_required", "请先登录再授权第三方应用");
        }

        // 返回同意页所需数据（含 scope 的人类可读描述）
        JsonObject data = new JsonObject();
        data.addProperty("client_id", client.clientId());
        data.addProperty("client_name", client.name());
        data.addProperty("redirect_uri", redirectUri);
        data.addProperty("state", state == null ? "" : state);

        com.google.gson.JsonArray scopeArray = new com.google.gson.JsonArray();
        for (com.mtxgdn.oauth.OAuthScope.Definition def : com.mtxgdn.oauth.OAuthScope.definitions()) {
            if (requested.contains(def.name())) {
                JsonObject item = new JsonObject();
                item.addProperty("name", def.name());
                item.addProperty("title", def.title());
                item.addProperty("description", def.description());
                item.addProperty("risk", def.risk().name());
                item.addProperty("icon", def.icon());
                scopeArray.add(item);
            }
        }
        data.add("scopes", scopeArray);
        data.addProperty("userId", userId);

        JsonObject result = new JsonObject();
        result.addProperty("code", 0);
        result.addProperty("message", client.name() + " 申请访问你的游戏数据");
        result.add("data", data);
        return Response.ok(result.toString()).build();
    }

    /**
     * 授权请求的第二步：玩家点了"同意"，签发授权码。
     *
     * <p>授权码必须绑上 {@code redirect_uri}，换取 token 时再校验一次 ——
     * 否则截获 code 的攻击者可以用自己的回调地址换 token。</p>
     */
    @POST
    @Path("/authorize/confirm")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response authorizeConfirm(String body) {
        if (!OAuthService.isEnabled()) {
            return error(503, "oauth2_not_enabled", "OAuth 2.0 支持未启用");
        }

        Long userId = currentUserId();
        if (userId == null) {
            return error(401, "login_required", "请先登录");
        }

        JsonObject json;
        try {
            json = com.google.gson.JsonParser.parseString(body).getAsJsonObject();
        } catch (Exception e) {
            return error(400, "invalid_request", "请求体不是合法的 JSON");
        }

        String clientId = json.has("client_id") ? json.get("client_id").getAsString() : "";
        String redirectUri = json.has("redirect_uri") ? json.get("redirect_uri").getAsString() : "";
        String scopeStr = json.has("scope") ? json.get("scope").getAsString() : "";
        String state = json.has("state") ? json.get("state").getAsString() : "";
        boolean approved = !json.has("approved") || json.get("approved").getAsBoolean();

        com.mtxgdn.oauth.OAuthClientRegistry.Client client =
                com.mtxgdn.oauth.OAuthClientRegistry.find(clientId);
        if (client == null) {
            return error(400, "invalid_client", "应用未登记或已停用");
        }
        if (!com.mtxgdn.oauth.OAuthClientRegistry.isRedirectUriAllowed(clientId, redirectUri)) {
            return error(400, "invalid_request", "redirect_uri 未登记");
        }

        // 玩家点了"拒绝"：按 RFC 6749 §4.1.2.1 回 access_denied，不发码
        if (!approved) {
            JsonObject denied = new JsonObject();
            denied.addProperty("code", 0);
            denied.addProperty("message", "已拒绝授权");
            denied.addProperty("redirect_uri", appendQuery(redirectUri,
                    "error=access_denied" + (state.isEmpty() ? "" : "&state=" + urlEncode(state))));
            return Response.ok(denied.toString()).build();
        }

        java.util.Set<String> scopes = com.mtxgdn.oauth.OAuthScope.parse(scopeStr);
        if (scopes.isEmpty()) {
            return error(400, "invalid_scope", "必须指定至少一个 scope");
        }
        for (String s : scopes) {
            if (!com.mtxgdn.oauth.OAuthScope.isValid(s)) {
                return error(400, "invalid_scope", "未知的 scope: " + s);
            }
            if (!client.canRequest(s)) {
                return error(400, "invalid_scope", "应用未被允许申请: " + s);
            }
        }

        String username = currentUsername();
        // nonce 由 OIDC 客户端在授权请求里给出，必须一路带到 id_token 里。
        // 它不是机密，但客户端会拿原值比对 —— 丢了会导致登录被拒。
        String nonce = json.has("nonce") && !json.get("nonce").isJsonNull()
                ? json.get("nonce").getAsString() : null;
        String code = com.mtxgdn.oauth.OAuthCodeStore.issue(
                clientId, redirectUri, userId, username, scopes, nonce);

        // state 原样带回 —— 服务端不解释它的内容，但绝不能丢，
        // 第三方靠它防 CSRF
        String callback = appendQuery(redirectUri,
                "code=" + urlEncode(code) + (state.isEmpty() ? "" : "&state=" + urlEncode(state)));

        JsonObject data = new JsonObject();
        data.addProperty("code", 0);
        data.addProperty("message", "授权成功");
        data.addProperty("redirect_uri", callback);
        return Response.ok(data.toString()).build();
    }

    /**
     * 撤销一条授权，或在没有 grant_id 时撤销当前用户对某应用的全部授权。
     *
     * <p>撤销后该授权下签发的 access_token 与 refresh_token 立即失效
     * （{@link com.mtxgdn.oauth.OAuthGrantStore#revoke} 会同时清除 jti 登记）。</p>
     */
    @POST
    @Path("/revoke")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response revoke(String body) {
        Long userId = currentUserId();
        if (userId == null) {
            return error(401, "login_required", "请先登录");
        }

        JsonObject json;
        try {
            json = com.google.gson.JsonParser.parseString(body == null || body.isBlank() ? "{}" : body)
                    .getAsJsonObject();
        } catch (Exception e) {
            return error(400, "invalid_request", "请求体不是合法的 JSON");
        }

        String grantId = json.has("grant_id") ? json.get("grant_id").getAsString() : "";
        String clientId = json.has("client_id") ? json.get("client_id").getAsString() : "";

        boolean revoked;
        if (!grantId.isBlank()) {
            // 只能撤销自己的授权 —— 用 grantId 反查所属用户做归属校验，
            // 否则任何登录用户都能用别人的 grantId 踢掉别人的授权
            com.mtxgdn.oauth.OAuthGrantStore.Grant grant =
                    com.mtxgdn.oauth.OAuthGrantStore.findGrant(grantId);
            if (grant == null || grant.userId() != userId) {
                return error(404, "not_found", "授权不存在");
            }
            revoked = com.mtxgdn.oauth.OAuthGrantStore.revoke(grantId);
        } else if (!clientId.isBlank()) {
            revoked = com.mtxgdn.oauth.OAuthGrantStore.revoke(userId, clientId);
        } else {
            return error(400, "invalid_request", "需要 grant_id 或 client_id");
        }

        JsonObject data = new JsonObject();
        data.addProperty("code", 0);
        data.addProperty("message", revoked ? "已撤销授权" : "没有找到对应的授权");
        data.addProperty("revoked", revoked);
        return Response.ok(data.toString()).build();
    }

    /**
     * 当前玩家已授权的应用列表。
     *
     * <p>数据来源是 {@link com.mtxgdn.oauth.OAuthGrantStore}，
     * 每个条目带上应用名、权限、授权时间与最近使用时间，
     * 让玩家能判断"这到底是不是我授权的"。</p>
     */
    @GET
    @Path("/grants")
    @Produces(MediaType.APPLICATION_JSON)
    public Response listGrants() {
        Long userId = currentUserId();
        if (userId == null) {
            return error(401, "login_required", "请先登录");
        }

        com.google.gson.JsonArray array = new com.google.gson.JsonArray();
        for (com.mtxgdn.oauth.OAuthGrantStore.Grant grant
                : com.mtxgdn.oauth.OAuthGrantStore.grantsOf(userId)) {
            JsonObject item = new JsonObject();
            item.addProperty("grant_id", grant.grantId());
            item.addProperty("client_id", grant.clientId());

            com.mtxgdn.oauth.OAuthClientRegistry.Client client =
                    com.mtxgdn.oauth.OAuthClientRegistry.find(grant.clientId());
            item.addProperty("client_name", client != null ? client.name() : grant.clientId());

            com.google.gson.JsonArray scopeArray = new com.google.gson.JsonArray();
            for (com.mtxgdn.oauth.OAuthScope.Definition def : com.mtxgdn.oauth.OAuthScope.definitions()) {
                if (grant.scopes().contains(def.name())) {
                    JsonObject s = new JsonObject();
                    s.addProperty("name", def.name());
                    s.addProperty("title", def.title());
                    s.addProperty("risk", def.risk().name());
                    scopeArray.add(s);
                }
            }
            item.add("scopes", scopeArray);
            item.addProperty("created_at", grant.createdAt());
            item.addProperty("last_used_at", grant.lastUsedAt());
            array.add(item);
        }

        JsonObject data = new JsonObject();
        data.add("grants", array);
        JsonObject result = new JsonObject();
        result.addProperty("code", 0);
        result.addProperty("message", "共 " + array.size() + " 个应用已获得授权");
        result.add("data", data);
        return Response.ok(result.toString()).build();
    }

    /** 从请求上下文取当前用户 id（由 JwtAuthFilter 写入） */
    private Long currentUserId() {
        Object uid = requestContext == null ? null : requestContext.getProperty("userId");
        return uid instanceof Long l ? l : null;
    }

    /** 从请求上下文取当前用户名 */
    private String currentUsername() {
        Object name = requestContext == null ? null : requestContext.getProperty("username");
        return name instanceof String s ? s : "";
    }

    /** 往 URL 追加查询参数，正确保留原有参数 */
    private static String appendQuery(String url, String query) {
        if (url == null || url.isEmpty()) {
            return url;
        }
        return url + (url.contains("?") ? "&" : "?") + query;
    }

    private static String urlEncode(String s) {
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
    }

    private Response error(int status, String error, String description) {
        JsonObject err = new JsonObject();
        err.addProperty("error", error);
        err.addProperty("error_description", description);
        return Response.status(status).entity(err.toString()).build();
    }
}
