package com.mtxgdn.rest;
import com.mtxgdn.util.JwtUtil;
import com.mtxgdn.util.TokenBlacklist;
import jakarta.ws.rs.core.Response;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;

/**
 * 玩家 JWT 鉴权过滤器。
 *
 * <p><b>优先级很关键</b>：本过滤器负责把 {@code userId} 写入请求上下文，而
 * {@link PermissionFilter}（{@code Priorities.AUTHORIZATION} = 2000）会读取该属性。
 * 未标注 {@code @Priority} 时 JAX-RS 默认按 {@code Priorities.USER} = 5000 处理，
 * 即本过滤器会排在权限校验<b>之后</b>执行 —— 届时 {@code userId} 恒为 {@code null}，
 * 所有带 {@code @RequirePermission} 的玩家端点都会返回 401。
 * 因此这里显式钉在 {@code AUTHENTICATION + 10}（= 1010）：
 * 晚于 {@link LanguageRequestFilter}(900) 与 {@link AdminAuthFilter}(1000)，
 * 早于 {@link ModuleGateFilter}(1950) 与 {@link PermissionFilter}(2000)。
 * 该顺序由 {@code AuthFilterOrderTest} 守护，改动请同步更新测试。
 */
@Provider
@Priority(Priorities.AUTHENTICATION + 10)
public class JwtAuthFilter implements ContainerRequestFilter {

    @Override
    public void filter(ContainerRequestContext requestContext) {
        String path = requestContext.getUriInfo().getPath();
        if (path == null) {
            path = "";
        }

        // V9 修复：原实现把 "test/" 列入免鉴权白名单，调试端点 /api/test/test 可匿名访问。
        // 这里移除该白名单（端点本身保留，但现在需要一个合法的玩家 token）。
        //
        // 同时把前缀匹配收紧为"路径段"匹配：原实现用 path.startsWith("auth/")，
        // 会让 "authXxx/..." 这类路径被误判进白名单。
        //
        // mc-command 是机器对机器端点（MC 桥接插件调用，没有玩家 JWT 可用），
        // 因此仍在此放行，但 McCommandResource 内部强制校验 minecraft.api_token，
        // 未配置令牌时直接拒绝——不再是可以匿名冒充玩家的入口。
        if (isBypassPath(path, "auth") || isBypassPath(path, "install") || isBypassPath(path, "mc-command")) {
            return;
        }

        // admin/energy/* 路径支持双鉴权：不强制要求用户 JWT，但如有则设置 userId
        boolean isDualAuthPath = isBypassPath(path, "admin/energy");

        if (isBypassPath(path, "admin") && !isDualAuthPath) {
            System.out.println("[JwtFilter] bypass: " + path);
            return;
        }

        String authorizationHeader = requestContext.getHeaderString("Authorization");

        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            if (isDualAuthPath) {
                return; // 无 token，放行（AdminAuthFilter 可能已通过 admin JWT 鉴权）
            }
            requestContext.abortWith(Response.status(Response.Status.UNAUTHORIZED)
                    .entity("{\"code\":401,\"message\":\"未登录或登录已过期\"}")
                    .build());
            return;
        }

        String token = authorizationHeader.substring("Bearer ".length()).trim();

        // ---------- 先判断是不是 OAuth 用户型 token ----------
        // 第三方应用拿着玩家授权过的 token 访问游戏端点时走这条分支。
        // 必须是"用户授权型"（带 uid claim）—— 客户端凭证型的 token
        // 永远拿不到用户身份，否则任何拿到 client_secret 的应用
        // 都能冒充任意玩家（见 OAuthService.extractUserId 的注释）。
        com.mtxgdn.oauth.OAuthAccess access = com.mtxgdn.oauth.OAuthAccess.resolve(token, path,
                requestContext.getMethod());
        if (access.handled()) {
            if (!access.allowed()) {
                requestContext.abortWith(Response.status(Response.Status.FORBIDDEN)
                        .entity("{\"code\":403,\"message\":\"" + escape(access.reason()) + "\"}")
                        .build());
                return;
            }
            requestContext.setProperty("token", token);
            requestContext.setProperty("userId", access.userId());
            // 标记本次请求来自第三方授权 —— 下游可据此收紧行为
            requestContext.setProperty("oauthGrantId", access.grantId());
            requestContext.setProperty("oauthScopes", access.scopes());
            if (access.username() != null) {
                requestContext.setProperty("username", access.username());
            }
            return;
        }

        if (TokenBlacklist.isBlacklisted(token)) {
            requestContext.abortWith(Response.status(Response.Status.UNAUTHORIZED)
                    .entity("{\"code\":401,\"message\":\"Token已失效，请重新登录\"}")
                    .build());
            return;
        }

        if (!JwtUtil.validateToken(token)) {
            if (isDualAuthPath) {
                return; // 用户 token 无效，放行（可能是 admin JWT，AdminAuthFilter 已处理）
            }
            requestContext.abortWith(Response.status(Response.Status.UNAUTHORIZED)
                    .entity("{\"code\":401,\"message\":\"Token无效或已过期，请重新登录\"}")
                    .build());
            return;
        }

        requestContext.setProperty("token", token);
        requestContext.setProperty("userId", JwtUtil.extractUserId(token));
        // 授权同意页要显示"以谁的身份授权"，所以用户名也放进上下文
        requestContext.setProperty("username", JwtUtil.extractUsername(token));
    }

    /**
     * V9：按"路径段"判断是否命中白名单前缀。
     *
     * <p>要求前缀后紧跟 {@code /}（或路径与该段完全相等），
     * 避免 {@code adminXxx}、{@code authEvil} 这类路径因 {@code startsWith} 被误放行。</p>
     */
    private static boolean isBypassPath(String path, String segment) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        return path.equals(segment) || path.startsWith(segment + "/");
    }

    /**
     * 转义要拼进 JSON 的文本。
     *
     * <p>拒绝原因是固定文案，理论上不含特殊字符；但这行代码会拼字符串造 JSON，
     * 万一将来有人把用户输入拼进 reason，没转义就是 JSON 注入。
     * 这里挡住双引号、反斜杠与控制字符，成本极低。</p>
     */
    private static String escape(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(text.length() + 8);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}
