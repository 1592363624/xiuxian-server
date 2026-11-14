package com.mtxgdn.oauth;

import com.mtxgdn.TestBase;
import com.mtxgdn.service.OAuthService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 细粒度 scope 是否真的限制住第三方能做的事。
 *
 * <p>这是整个改造的核心诉求：<b>管理员能指定第三方读什么、写什么</b>。
 * scope 声明得再漂亮，如果 {@link OAuthAccess} 没在请求时真正比对，
 * 那就是一张空头支票。</p>
 *
 * <p>所以这组测试不看 scope 定义，只验证「某个 scope 的 token
 * 访问某个路径时，到底放不放行」。</p>
 */
class OAuthScopeEnforcementTest extends TestBase {

    private static final String CLIENT = "scoped_app";
    private static final long USER = 42L;
    private String grantId;

    @BeforeEach
    void setUp() {
        OAuthGrantStore.clear();
        OAuthClientRegistry.clear();

        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("enabled", true);
        cfg.put("client_id", "machine_client");
        cfg.put("client_secret", "machine_secret_0123456789");
        cfg.put("scopes", "read write");
        cfg.put("token_ttl_seconds", 3600);
        OAuthService.updateConfig(cfg);

        grantId = OAuthGrantStore.authorize(USER, "张三", CLIENT, Set.of("player:read")).grantId();
    }

    @AfterEach
    void tearDown() {
        OAuthGrantStore.clear();
        OAuthClientRegistry.clear();
    }

    /** 用指定 scope 签一个用户 token，登记好 jti，返回可用于鉴权判定的结果 */
    private OAuthAccess access(String scopes, String path, String method) {
        Set<String> scopeSet = OAuthScope.parse(scopes);
        var grant = OAuthGrantStore.authorize(USER, "张三", CLIENT, scopeSet);
        String jti = "jti-" + scopes + "-" + path;
        OAuthGrantStore.registerToken(jti, grant.grantId());
        String token = OAuthService.issueUserAccessToken(
                USER, "张三", CLIENT, scopeSet, grant.grantId(), jti);
        return OAuthAccess.resolve(token, path, method);
    }

    // ==================== 只读 scope 不能写 ====================

    @Test
    @DisplayName("inventory:read 的 token 能读背包，不能使用物品")
    void readScopeCannotWrite() {
        OAuthAccess read = access("inventory:read", "game/inventory", "GET");
        assertTrue(read.handled());
        assertTrue(read.allowed(), "读背包应放行: " + read.reason());

        OAuthAccess write = access("inventory:read", "game/item/use", "POST");
        assertTrue(write.handled());
        assertFalse(write.allowed(),
                "只有 read 权限的 token 不能执行写操作 —— 否则 scope 形同虚设");
    }

    @Test
    @DisplayName("读不到没授权的资源域")
    void cannotReadOtherDomains() {
        OAuthAccess allowed = access("player:read", "game/player", "GET");
        assertTrue(allowed.allowed(), "有 player:read 应能读角色: " + allowed.reason());

        OAuthAccess denied = access("player:read", "game/inventory", "GET");
        assertFalse(denied.allowed(),
                "只有 player:read 不该能读背包 —— 资源域之间必须隔离");
    }

    @Test
    @DisplayName("经济域与宗门域互相隔离")
    void domainsAreIsolated() {
        assertTrue(access("economy:read", "game/market", "GET").allowed());
        assertFalse(access("economy:read", "game/sect/members", "GET").allowed());
        assertTrue(access("sect:read", "game/sect/members", "GET").allowed());
        assertFalse(access("sect:read", "game/market", "GET").allowed());
    }

    // ==================== 写 scope 才能写 ====================

    @Test
    @DisplayName("inventory:write 可以执行背包写操作")
    void writeScopeAllowsWrite() {
        OAuthAccess result = access("inventory:write", "game/equipment/equip", "POST");
        assertTrue(result.allowed(), "有 write 权限应能装备: " + result.reason());
    }

    @Test
    @DisplayName("write 权限也包含读")
    void writeImpliesRead() {
        // 注意：这里断言的是"写权限能读写"还是"写权限只能写"？
        // 设计选择：write 不自动包含 read，需要在同意页同时勾选两个。
        // 理由：让玩家清楚看到"这个应用能读我的背包"，而不是被 write 顺带授权。
        // 若将来改成 write 蕴含 read，这条测试会失败，提醒同步更新同意页文案。
        OAuthAccess result = access("inventory:write", "game/inventory", "GET");
        assertFalse(result.allowed(),
                "只勾 write 不含 read —— 权限要显式授予，不能被隐含");
    }

    // ==================== 永不委托的端点 ====================

    @ParameterizedTest
    @ValueSource(strings = {
            "game/player/data/restore",
            "game/item/add",
            "game/sect/kick/12",
            "game/sect/appoint",
            "game/sect/transfer",
            "game/sect/dissolve"
    })
    @DisplayName("即使 scope 覆盖，这些端点也永远不能通过 OAuth 调用")
    void neverDelegatedEndpointsAreBlocked(String path) {
        // 给一个"权限拉满"的 token
        String everything = OAuthScope.join(OAuthScope.all());
        OAuthAccess result = access(everything, path, "POST");

        assertTrue(result.handled());
        assertFalse(result.allowed(),
                "该端点必须被硬性拦截，即使拥有全部 scope：" + path);
        assertTrue(result.reason().contains("不允许通过第三方授权"),
                "拒绝原因应明确指出这是不可委托操作，实际: " + result.reason());
    }

    // ==================== 未登记路径默认拒绝 ====================

    @Test
    @DisplayName("未登记资源域的路径默认拒绝（宁可功能缺失，不可权限外溢）")
    void unregisteredPathDeniedByDefault() {
        String everything = OAuthScope.join(OAuthScope.all());
        OAuthAccess result = access(everything, "game/some/future/endpoint", "GET");

        assertTrue(result.handled());
        assertFalse(result.allowed(),
                "新端点忘了登记时应访问不到，而不是意外开放");
    }

    // ==================== 客户端凭证型 token 无用户身份 ====================

    @Test
    @DisplayName("客户端凭证型 token 无法拿到用户身份")
    void clientTokenGivesNoUserId() {
        String token = OAuthService.issueAccessToken("machine_client", "public:read");
        OAuthAccess result = OAuthAccess.resolve(token, "game/players", "GET");

        assertTrue(result.handled(), "应被识别为 OAuth token");
        assertTrue(result.allowed(), "登记为公共可读的端点应放行");
        assertNull(result.userId(),
                "客户端凭证型 token 绝不能映射出用户 id");
    }

    @Test
    @DisplayName("客户端凭证型 token 不能访问任何需要用户身份的端点")
    void clientTokenCannotAccessUserData() {
        String token = OAuthService.issueAccessToken("machine_client", "read");
        // 即使强行给它 inventory 的路径
        OAuthAccess result = OAuthAccess.resolve(token, "game/inventory", "GET");

        assertTrue(result.handled());
        assertFalse(result.allowed(),
                "没有用户授权就不该能读任何人的背包");
    }

    // ==================== 路径与方法的映射 ====================

    @ParameterizedTest
    @CsvSource({
            "game/player,            GET,  player:read",
            "game/inventory,         GET,  inventory:read",
            "game/inventory,         POST, inventory:write",
            "game/market,            GET,  economy:read",
            "game/market/buy,        POST, economy:write",
            "game/sect/members,      GET,  sect:read",
            "game/players,           GET,  public:read",
            "game/item/registry,     GET,  public:read",
    })
    @DisplayName("路径 + 方法 → 所需 scope 的映射")
    void pathToScopeMapping(String path, String method, String expected) {
        assertEquals(expected, OAuthPathPolicy.requiredScope(path, method),
                "映射错误会导致放行不该放行的请求，或拒绝该放行的请求");
    }

    @Test
    @DisplayName("路径前缀匹配要有边界：game/player 不能匹配 game/players")
    void prefixMatchRespectsBoundaries() {
        // game/players 是公开名录，game/player 是私人角色信息。
        // 用 startsWith("game/player") 判断会把前者误当后者，
        // 导致公开名录被要求 player:read 权限。
        assertEquals("public:read", OAuthPathPolicy.requiredScope("game/players", "GET"));
        assertEquals("player:read", OAuthPathPolicy.requiredScope("game/player", "GET"));
    }
}
