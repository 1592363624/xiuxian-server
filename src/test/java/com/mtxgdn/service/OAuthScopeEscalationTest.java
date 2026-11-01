package com.mtxgdn.service;

import com.mtxgdn.TestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OAuth 客户端不能凭空要求超出配置的权限。
 *
 * <p><b>问题</b>：{@code /token} 的 {@code scope} 请求参数由客户端任意填写，
 * {@code buildTokenResponse} 原样签进 token 并原样回显。</p>
 *
 * <pre>
 *   POST /api/auth/oauth/token
 *   {"grant_type":"client_credentials","client_id":"...","client_secret":"...",
 *    "scope":"read write admin delete"}
 * </pre>
 *
 * <p>服务端配置的 scopes 只有 {@code "read write"}，但签出的 token 里
 * {@code scope} 就是客户端自己写的 {@code "read write admin delete"}。</p>
 *
 * <p><b>影响评估</b>：当前受保护端点只检查 {@code read} / {@code write}
 * 两个具体值（{@code OAuthResource.me} 查 read，{@code protected/echo} 查 write），
 * 所以多出的 {@code admin} / {@code delete} <b>暂时不能被利用</b> —— 没有端点认它们。
 * 但这留下两个隐患：</p>
 * <ul>
 *   <li>将来任何按 scope 名授权的新端点，都会默认信任这个自填字段；</li>
 *   <li>{@code /introspect} 会把伪造的 scope 原样回报给调用方，
 *       下游系统可能据此放行。</li>
 * </ul>
 *
 * <p>所以正确做法是：签发时把请求的 scope 与配置的白名单求交集，
 * 只签发配置允许的部分。这也是 RFC 6749 §3.3 的要求。
 * 这组测试记录的就是这个契约。</p>
 */
class OAuthScopeEscalationTest extends TestBase {

    @BeforeEach
    void setUp() {
        Map<String, Object> update = new LinkedHashMap<>();
        update.put("enabled", true);
        update.put("client_id", "scope_client");
        update.put("client_secret", "scope_secret_0123456789");
        update.put("scopes", "read write");
        update.put("token_ttl_seconds", 3600);
        OAuthService.updateConfig(update);
    }

    @AfterEach
    void tearDown() {
        Map<String, Object> update = new LinkedHashMap<>();
        update.put("enabled", true);
        OAuthService.updateConfig(update);
    }

    @Test
    @DisplayName("请求超出配置的 scope 时，token 里不得含未授权项")
    void cannotEscalateBeyondConfiguredScopes() {
        Map<String, Object> resp = OAuthService.buildTokenResponse("scope_client",
                "read write admin delete");

        String issued = String.valueOf(resp.get("scope"));
        assertFalse(issued.contains("admin"),
                "admin 不在配置的 scopes 里，不应签进 token，实际: " + issued);
        assertFalse(issued.contains("delete"),
                "delete 不在配置的 scopes 里，不应签进 token，实际: " + issued);
        assertTrue(issued.contains("read"), "read 是配置允许的，应保留，实际: " + issued);
    }

    @Test
    @DisplayName("token 载荷里的 scope 同样不能含未授权项")
    void tokenClaimsDoNotCarryEscalation() {
        Map<String, Object> resp = OAuthService.buildTokenResponse("scope_client",
                "read write admin");
        String token = String.valueOf(resp.get("access_token"));

        var claims = OAuthService.parseToken(token);
        assertNotNull(claims, "token 应可解析");
        String inToken = claims.get("scope", String.class);
        assertFalse(inToken.contains("admin"),
                "payload 里的 scope 也不能有 admin，实际: " + inToken);
    }

    @Test
    @DisplayName("内省不得回报未授权的 scope（下游会据此放行）")
    void introspectDoesNotEchoEscalatedScope() {
        Map<String, Object> resp = OAuthService.buildTokenResponse("scope_client",
                "read write admin");
        String token = String.valueOf(resp.get("access_token"));

        Map<String, Object> info = OAuthService.introspect(token);
        assertEquals(true, info.get("active"));
        String scope = String.valueOf(info.get("scope"));
        assertFalse(scope.contains("admin"),
                "内省回报的 scope 不能含未授权项，否则下游系统会误放行，实际: " + scope);
    }

    @Test
    @DisplayName("只请求配置内的 scope 时原样签发")
    void subsetRequestPassesThrough() {
        Map<String, Object> resp = OAuthService.buildTokenResponse("scope_client", "read");
        assertEquals("read", String.valueOf(resp.get("scope")),
                "请求的 scope 本来就是配置的子集，应原样签发");
    }

    @Test
    @DisplayName("完全不请求 scope 时回落到配置的全部 scopes")
    void noScopeFallsBackToConfigured() {
        Map<String, Object> resp = OAuthService.buildTokenResponse("scope_client", null);
        assertEquals("read write", String.valueOf(resp.get("scope")));
    }

    @Test
    @DisplayName("全是未授权 scope 时不签出任何权限")
    void allUnauthorizedScopeYieldsNothingUsable() {
        Map<String, Object> resp = OAuthService.buildTokenResponse("scope_client", "admin delete");
        String issued = String.valueOf(resp.get("scope"));

        var claims = OAuthService.parseToken(String.valueOf(resp.get("access_token")));
        assertNotNull(claims);
        String inToken = claims.get("scope", String.class);
        assertFalse(OAuthService.hasScope(inToken, "read"),
                "没请求 read 就不该有 read，实际: " + inToken);
        assertFalse(OAuthService.hasScope(inToken, "write"),
                "没请求 write 就不该有 write，实际: " + inToken);
        assertFalse(issued.contains("admin"), "更不该有 admin，实际: " + issued);
    }
}
