package com.mtxgdn.rest;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mtxgdn.oauth.OidcKeyManager;
import com.mtxgdn.oauth.OidcSubject;
import com.mtxgdn.util.AppConfig;
import io.jsonwebtoken.Claims;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * OIDC 身份提供者端点 —— 让第三方把本服当成登录方式。
 *
 * <h3>与 {@link OAuthResource} 的方向区别</h3>
 * <p>那个类解决「本服去访问别人的数据」，本类解决「别人拿本服账号登录」。
 * 两者共用授权码流程，但 OIDC 多了这几样标准件：</p>
 * <ul>
 *   <li>{@code /.well-known/openid-configuration} —— 客户端填这一个地址就能自动发现其余端点；</li>
 *   <li>{@code /jwks.json} —— 公钥，客户端用它验证 id_token 的 RS256 签名；</li>
 *   <li>{@code /userinfo} —— 用 access_token 换取用户身份（{@code sub} 等）；</li>
 *   <li>授权响应里的 {@code id_token} —— 带 {@code sub} 的签名 JWT。</li>
 * </ul>
 *
 * <h3>为什么 userinfo 不能直接用 /me</h3>
 * <p>{@code /me} 返回的是 token 自身的元信息（scope、过期时间），而 OIDC 客户端要的是
 * <b>这个人是谁</b>——必须有 {@code sub}。二者用途不同，不能合并。</p>
 *
 * <h3>issuer 必须精确</h3>
 * <p>OIDC 规范要求客户端校验 id_token 里的 {@code iss} 与 Discovery 里的
 * {@code issuer} 完全一致。所以这里所有对外 URL 都由同一个 issuer 拼出来，
 * 不允许一个接口用相对路径、另一个用绝对路径 —— 那会让部分客户端验签失败。</p>
 */
@Path("/auth/oidc")
public class OidcResource {

    private static final Gson gson = new Gson();

    @jakarta.ws.rs.core.Context
    private HttpHeaders httpHeaders;

    @jakarta.ws.rs.core.Context
    private jakarta.ws.rs.core.UriInfo uriInfo;

    // ==================== Discovery ====================

    /**
     * OIDC Discovery 元数据。
     *
     * <p>客户端（New API 等）填的就是这个地址，点「自动发现」后会用返回的
     * 各端点地址填满配置表单。</p>
     *
     * <p>故意<b>不</b>声明 {@code response_types_supported} 里没有的能力
     * （如 implicit、hybrid）—— 声明了就要支持，否则客户端会选一个走不通的流程。</p>
     */
    @GET
    @Path("/.well-known/openid-configuration")
    @Produces(MediaType.APPLICATION_JSON)
    public Response discovery() {
        if (!isEnabled()) {
            return unavailable();
        }
        String issuer = issuer();

        JsonObject o = new JsonObject();
        o.addProperty("issuer", issuer);
        o.addProperty("authorization_endpoint", issuer + "/api/auth/oauth/authorize");
        o.addProperty("token_endpoint", issuer + "/api/auth/oauth/token");
        o.addProperty("userinfo_endpoint", issuer + "/api/auth/oidc/userinfo");
        o.addProperty("jwks_uri", issuer + "/api/auth/oidc/jwks.json");

        // 只支持授权码流程。声明 implicit 等于鼓励客户端用更弱的流程
        o.add("response_types_supported", arr("code"));
        o.add("grant_types_supported", arr("authorization_code", "refresh_token"));
        o.add("subject_types_supported", arr("public"));
        o.add("id_token_signing_alg_values_supported", arr("RS256"));
        o.add("scopes_supported", arr("openid", "profile", "email"));
        o.add("token_endpoint_auth_methods_supported",
                arr("client_secret_post", "client_secret_basic"));
        o.add("claims_supported", arr("sub", "preferred_username", "name", "email", "updated_at"));
        // 本服不支持动态注册，客户端需要在服务端配置里登记
        o.addProperty("registration_endpoint", (String) null);
        o.remove("registration_endpoint");

        return Response.ok(gson.toJson(o))
                .header("Cache-Control", "public, max-age=3600")
                .build();
    }

    // ==================== JWKS ====================

    /**
     * 公钥集（JSON Web Key Set）。
     *
     * <p>只含公钥分量。它在匿名可访问的端点上，所以绝不能把私钥参数
     * （{@code d}/{@code p}/{@code q}）混进来 —— {@link OidcKeyManager#publicJwkJson()}
     * 从构造上只取 {@code n} 与 {@code e}。</p>
     *
     * <p>允许缓存：客户端频繁调用它，而密钥极少变（换了 kid 才会变）。</p>
     */
    @GET
    @Path("/jwks.json")
    @Produces(MediaType.APPLICATION_JSON)
    public Response jwks() {
        if (!isEnabled()) {
            return unavailable();
        }
        if (!OidcKeyManager.isReady()) {
            return error(503, "oidc_key_not_ready", "签名密钥尚未就绪");
        }
        String json = "{\"keys\":[" + OidcKeyManager.publicJwkJson() + "]}";
        return Response.ok(json)
                .header("Cache-Control", "public, max-age=3600")
                .build();
    }

    // ==================== userinfo ====================

    /**
     * 用 access_token 换取用户身份。
     *
     * <p><b>核心是 {@code sub}</b>：客户端靠它判断"这是哪个账号"。
     * 其余字段（名字、邮箱）都是展示用的，缺失不会影响登录。</p>
     *
     * <p>必须只接受<b>用户型</b> token（带 uid 的）。客户端凭据型 token 代表一个应用，
     * 不代表某个玩家 —— 用它调这里会得到一个空身份，那没有意义，
     * 所以直接拒绝而不是返回空 sub。</p>
     */
    @GET
    @Path("/userinfo")
    @Produces(MediaType.APPLICATION_JSON)
    public Response userinfo() {
        if (!isEnabled()) {
            return unavailable();
        }

        String token = bearerToken();
        if (token == null) {
            return bearerError("缺少 Authorization: Bearer <token>");
        }

        // 解析并确认是用户型 token（必须带 uid，且授权未被撤销）
        Claims claims = com.mtxgdn.service.OAuthService.parseUserToken(token);
        if (claims == null) {
            return bearerError("access_token 无效、已过期，或对应授权已被撤销");
        }
        Long userId = com.mtxgdn.service.OAuthService.extractUserId(claims);
        String username = claims.get("username", String.class);
        if (userId == null || username == null || username.isBlank()) {
            // 理论上不会发生：parseUserToken 已确保带 uid。
            // 但这里宁可显式拒绝，也不要返回一个 half-formed 身份
            return bearerError("该 token 不代表具体用户，无法用于 OIDC 登录");
        }

        JsonObject o = new JsonObject();
        o.addProperty("sub", OidcSubject.of(username));
        o.addProperty("preferred_username", username);

        // name 与 email 属于 profile / email scope，没申请就不返回 ——
        // 客户端不该拿到超出申请范围的信息
        String scope = claims.get("scope", String.class);
        if (scope != null && scope.contains("profile")) {
            o.addProperty("name", username);
        }
        if (scope != null && scope.contains("email")) {
            String email = lookupEmail(userId);
            if (email != null && !email.isBlank()) {
                o.addProperty("email", email);
                o.addProperty("email_verified", true);
            }
        }

        return Response.ok(gson.toJson(o))
                // 含个人信息，不能被任何中间层缓存
                .header("Cache-Control", "no-store")
                .build();
    }

    // ==================== 内部 ====================

    /** OIDC 是否启用（默认关闭，避免无意中对外暴露登录入口） */
    static boolean isEnabled() {
        String v = AppConfig.get("oidc.enabled");
        return v != null && "true".equalsIgnoreCase(v.trim());
    }

    /**
     * 对外根地址。
     *
     * <p>优先 {@code oidc.issuer}，其次 {@code oidc.public-base-url}。
     * 两者都空时退到"按请求推导"——这只在客户端与服务器同源时才对，
     * 所以会打一次警告，避免在反代环境下悄悄生成错误地址。</p>
     */
    String issuer() {
        String configured = AppConfig.get("oidc.issuer");
        if (configured != null && !configured.isBlank()) {
            return trimSlash(configured.trim());
        }
        String base = AppConfig.get("oidc.public-base-url");
        if (base != null && !base.isBlank()) {
            return trimSlash(base.trim());
        }
        System.err.println("[OIDC] 警告：oidc.issuer 与 oidc.public-base-url 都未配置，"
                + "已按请求地址推导 —— 若服务在反向代理后面，生成的端点地址可能不可达。");
        if (uriInfo != null) {
            String scheme = uriInfo.getAbsolutePath().getScheme();
            String host = uriInfo.getAbsolutePath().getHost();
            int port = uriInfo.getAbsolutePath().getPort();
            boolean defaultPort = ("https".equals(scheme) && port == 443)
                    || ("http".equals(scheme) && port == 80);
            return scheme + "://" + host + (defaultPort ? "" : ":" + port);
        }
        return "http://localhost:8080";
    }

    /**
     * 供 {@link OAuthResource} 签发 id_token 时取 issuer。
     *
     * <p>与 {@link #issuer()} 的区别：这里<b>不</b>按请求推导。签 id_token 发生在
     * {@code /token} 端点，那时请求的各种前缀都可能与授权端点不同，
     * 用请求推导会产生与 Discovery 不一致的 issuer，客户端会直接判为无效 token。
     * 所以这里只用显式配置；两者都没配时退回本地地址并告警。</p>
     */
    public String issuerForToken() {
        String configured = AppConfig.get("oidc.issuer");
        if (configured != null && !configured.isBlank()) {
            return trimSlash(configured.trim());
        }
        String base = AppConfig.get("oidc.public-base-url");
        if (base != null && !base.isBlank()) {
            return trimSlash(base.trim());
        }
        System.err.println("[OIDC] 警告：签发 id_token 时 oidc.issuer 与 oidc.public-base-url "
                + "都未配置，issuer 将回退为 http://localhost:8080 —— 客户端会因 issuer 不匹配"
                + "而拒绝该 id_token。请尽快配置。");
        return "http://localhost:8080";
    }

    private static String trimSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    private String bearerToken() {
        if (httpHeaders == null) {
            return null;
        }
        String auth = httpHeaders.getHeaderString("Authorization");
        if (auth == null || !auth.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return null;
        }
        String token = auth.substring(7).trim();
        return token.isEmpty() ? null : token;
    }

    /**
     * 查玩家邮箱。
     *
     * <p>失败一律返回 null —— 邮箱只是 OIDC 的可选字段，
     * 数据库抖一下不该让整个登录流程失败。</p>
     */
    private String lookupEmail(long userId) {
        try {
            return new com.mtxgdn.db.UserServiceDAO().findEmailById(userId);
        } catch (Throwable t) {
            return null;
        }
    }

    private static com.google.gson.JsonArray arr(String... items) {
        com.google.gson.JsonArray a = new com.google.gson.JsonArray();
        for (String s : items) {
            a.add(s);
        }
        return a;
    }

    /** OIDC 规范要求错误也带 WWW-Authenticate 头，客户端据此判断要用哪种认证 */
    private static Response bearerError(String desc) {
        return Response.status(401)
                .type(MediaType.APPLICATION_JSON)
                .header("WWW-Authenticate", "Bearer error=\"invalid_token\", error_description=\"" + desc + "\"")
                .entity(gson.toJson(errorBody("invalid_token", desc)))
                .build();
    }

    private static Response unavailable() {
        return error(404, "oidc_not_enabled", "OIDC 身份提供者未启用（oidc.enabled=false）");
    }

    private static JsonObject errorBody(String code, String desc) {
        JsonObject o = new JsonObject();
        o.addProperty("error", code);
        o.addProperty("error_description", desc);
        return o;
    }

    private static Response error(int status, String code, String desc) {
        return Response.status(status)
                .type(MediaType.APPLICATION_JSON)
                .entity(gson.toJson(errorBody(code, desc)))
                .build();
    }
}
