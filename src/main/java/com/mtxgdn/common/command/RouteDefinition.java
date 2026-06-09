package com.mtxgdn.common.command;

import com.google.gson.JsonObject;

import java.util.Map;

/**
 * REST API 路由定义。放在 Command 类中，由 UnifiedRestResource 自动注册。
 */
public class RouteDefinition {

    private final String method;      // GET / POST
    private final String path;        // e.g. "sect/list" 或 "sect/info/{sectId}"
    private final String permission;  // 权限码，null 表示无需权限
    private final boolean httpOnly;   // true = 仅 HTTP，不作为 OneBot 子命令
    private final RouteHandler handler;

    private RouteDefinition(String method, String path, String permission,
                            boolean httpOnly, RouteHandler handler) {
        this.method = method;
        this.path = path;
        this.permission = permission;
        this.httpOnly = httpOnly;
        this.handler = handler;
    }

    /** 创建一个 GET 路由 */
    public static RouteDefinition get(String path, RouteHandler handler) {
        return new RouteDefinition("GET", path, null, false, handler);
    }
    public static RouteDefinition get(String path, String permission, RouteHandler handler) {
        return new RouteDefinition("GET", path, permission, false, handler);
    }

    /** 创建一个 POST 路由 */
    public static RouteDefinition post(String path, RouteHandler handler) {
        return new RouteDefinition("POST", path, null, false, handler);
    }
    public static RouteDefinition post(String path, String permission, RouteHandler handler) {
        return new RouteDefinition("POST", path, permission, false, handler);
    }

    /** HTTP 独有路由（不出现在 OneBot 帮助中，仅通过 HTTP 访问） */
    public static RouteDefinition httpOnlyGet(String path, String permission, RouteHandler handler) {
        return new RouteDefinition("GET", path, permission, true, handler);
    }
    public static RouteDefinition httpOnlyPost(String path, String permission, RouteHandler handler) {
        return new RouteDefinition("POST", path, permission, true, handler);
    }

    public String getMethod() { return method; }
    public String getPath() { return path; }
    public String getPermission() { return permission; }
    public boolean isHttpOnly() { return httpOnly; }
    public RouteHandler getHandler() { return handler; }

    /**
     * REST 路由处理器。接收上下文，返回 JsonObject 或抛出异常。
     */
    @FunctionalInterface
    public interface RouteHandler {
        JsonObject handle(RestContext ctx) throws Exception;
    }

    /**
     * REST 请求上下文，由 UnifiedRestResource 在调用 handler 前填充。
     */
    public static class RestContext {
        private final String body;              // JSON 请求体
        private final Map<String, String> pathParams;  // e.g. {"sectId": "5"}
        private final Map<String, String> queryParams; // e.g. {"type": "realm"}
        private final long userId;              // JWT 认证的 user ID
        private final int playerId;             // 对应的玩家 ID

        public RestContext(String body, Map<String, String> pathParams,
                           Map<String, String> queryParams, long userId, int playerId) {
            this.body = body;
            this.pathParams = pathParams;
            this.queryParams = queryParams;
            this.userId = userId;
            this.playerId = playerId;
        }

        public String body() { return body; }
        public long userId() { return userId; }
        public int playerId() { return playerId; }
        public String pathParam(String name) { return pathParams.get(name); }
        public String queryParam(String name) { return queryParams.get(name); }
        public long pathParamLong(String name) { return Long.parseLong(pathParams.get(name)); }

        /**
         * 把 body 解析为 JsonObject
         */
        public com.google.gson.JsonObject bodyJson() {
            return com.google.gson.JsonParser.parseString(body).getAsJsonObject();
        }
    }

    /**
     * 把已注册的 URL 模式与传入路径匹配，提取路径参数。
     * e.g. pattern="sect/info/{sectId}" vs actual="sect/info/5" → {sectId: "5"}
     * 返回 null 表示不匹配。
     */
    public static Map<String, String> matchPath(String pattern, String actualPath) {
        String[] patParts = pattern.split("/");
        String[] actParts = actualPath.split("/");

        if (patParts.length != actParts.length) return null;

        java.util.LinkedHashMap<String, String> params = new java.util.LinkedHashMap<>();
        for (int i = 0; i < patParts.length; i++) {
            if (patParts[i].startsWith("{") && patParts[i].endsWith("}")) {
                params.put(patParts[i].substring(1, patParts[i].length() - 1), actParts[i]);
            } else if (!patParts[i].equals(actParts[i])) {
                return null;
            }
        }
        return params;
    }
}
