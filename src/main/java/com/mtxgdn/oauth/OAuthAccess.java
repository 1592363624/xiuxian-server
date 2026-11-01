package com.mtxgdn.oauth;

import com.mtxgdn.service.OAuthService;
import io.jsonwebtoken.Claims;

import java.util.Set;

/**
 * 一次请求的 OAuth 判定结果。
 *
 * <p>把"这个 Bearer 头是不是 OAuth token、算不算通过、映射到哪个用户"
 * 三件事收在一个地方，让 {@code JwtAuthFilter} 保持简单。
 * 判定逻辑集中在这里也便于逐条写越权测试。</p>
 *
 * <h3>三种结果</h3>
 * <ul>
 *   <li><b>不是 OAuth token</b> —— {@code handled=false}，交回给原来的玩家 JWT 逻辑；</li>
 *   <li><b>是 OAuth token 且允许</b> —— {@code handled=true, allowed=true}，带 userId；</li>
 *   <li><b>是 OAuth token 但不允许</b> —— {@code handled=true, allowed=false}，带原因。</li>
 * </ul>
 *
 * <p>注意「是 OAuth token 但不允许」这一支<b>不能</b>退回去当普通 JWT 处理。
 * 那样一个权限不足的 OAuth token 会因为 JWT 校验失败而变成 401，
 * 或者更糟 —— 如果签名恰好能过就变成了完整玩家权限。
 * 必须直接拒绝。</p>
 */
public final class OAuthAccess {

    private static final OAuthAccess NOT_OAUTH = new OAuthAccess(false, false, null, null, null, Set.of(), "");

    private final boolean handled;
    private final boolean allowed;
    private final Long userId;
    private final String username;
    private final String grantId;
    private final Set<String> scopes;
    private final String reason;

    private OAuthAccess(boolean handled, boolean allowed, Long userId, String username, String grantId,
                        Set<String> scopes, String reason) {
        this.handled = handled;
        this.allowed = allowed;
        this.userId = userId;
        this.username = username;
        this.grantId = grantId;
        this.scopes = scopes;
        this.reason = reason;
    }

    public boolean handled() {
        return handled;
    }

    public boolean allowed() {
        return allowed;
    }

    public Long userId() {
        return userId;
    }

    public String username() {
        return username;
    }

    public String grantId() {
        return grantId;
    }

    public Set<String> scopes() {
        return scopes;
    }

    public String reason() {
        return reason;
    }

    /**
     * 判定一次请求。
     *
     * @param token      Bearer 头里的 token
     * @param apiPath    相对 {@code /api} 的路径
     * @param httpMethod HTTP 方法
     */
    public static OAuthAccess resolve(String token, String apiPath, String httpMethod) {
        // 先看它是不是本系统签发的 OAuth token（带 token_type=oauth2）
        Claims claims = OAuthService.parseToken(token);
        if (claims == null) {
            return NOT_OAUTH;
        }

        Long userId = OAuthService.extractUserId(claims);
        if (userId == null) {
            // 客户端凭证型 token（无 uid）。
            // 它只能访问明确登记为"公共只读"的端点，且不带任何用户身份。
            String required = OAuthPathPolicy.requiredScope(apiPath, httpMethod);
            if (required == null) {
                return deny("该端点不允许第三方应用访问");
            }
            Set<String> scopes = OAuthScope.parse(claims.get("scope", String.class));
            if (!scopes.contains(required)) {
                return deny("缺少所需权限: " + required);
            }
            return new OAuthAccess(true, true, null, null, null, scopes, "");
        }

        // ---------- 用户授权型 token ----------
        // 1) 授权是否已被撤销
        String jti = claims.getId();
        if (!OAuthGrantStore.isTokenActive(jti)) {
            return deny("授权已撤销或 token 已失效，请重新授权");
        }

        // 2) 永不委托的端点一律拒绝（存档覆盖、凭空发物品、影响他人的管理操作）
        if (OAuthScope.isNeverDelegated(apiPath)) {
            return deny("该操作不允许通过第三方授权执行");
        }

        // 3) 路径必须已登记资源域
        String required = OAuthPathPolicy.requiredScope(apiPath, httpMethod);
        if (required == null) {
            return deny("该端点不允许第三方应用访问");
        }

        // 4) scope 必须覆盖所需权限
        Set<String> scopes = OAuthScope.parse(claims.get("scope", String.class));
        if (!scopes.contains(required)) {
            return deny("缺少所需权限: " + required);
        }

        return new OAuthAccess(true, true, userId, claims.get("username", String.class),
                claims.get("grant_id", String.class), scopes, "");
    }

    private static OAuthAccess deny(String reason) {
        return new OAuthAccess(true, false, null, null, null, Set.of(), reason);
    }

    /** 不是 OAuth token 的结果（单例） */
    public static OAuthAccess notOauth() {
        return NOT_OAUTH;
    }
}
