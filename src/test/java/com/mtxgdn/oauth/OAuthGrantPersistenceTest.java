package com.mtxgdn.oauth;

import com.mtxgdn.TestBase;
import com.mtxgdn.db.DatabaseManager;
import com.mtxgdn.db.OAuthGrantsDAO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OAuth 授权记录的持久化。
 *
 * <p>原来授权只存在内存里，重启服务器后玩家需要给每个第三方应用重新授权一遍，
 * 「已授权应用」页也会变空。这组测试验证落库后这些信息能恢复。</p>
 *
 * <h3>需要明确的取舍</h3>
 * <p>持久化的是<b>"玩家授权过谁"</b>，不是会话本身。</p>
 * <ul>
 *   <li>授权记录 → 落库，重启后仍在（玩家能继续看到并撤销）；</li>
 *   <li>access_token 的 jti、refresh_token → 不落库，重启后失效。</li>
 * </ul>
 *
 * <p>所以重启后的实际效果是：第三方需要重新走一次授权码流程，
 * 但玩家在「已授权应用」页看到的列表是对的。这个取舍是刻意的 ——
 * 持久化 refresh_token 意味着数据库泄露就能长期冒用，代价大于收益。</p>
 */
class OAuthGrantPersistenceTest extends TestBase {

    private static final long USER = 7001L;
    private static final String CLIENT = "persist_app";
    private static final String GRANT_ID = USER + ":" + CLIENT;

    private final OAuthGrantsDAO dao = new OAuthGrantsDAO();

    @BeforeEach
    void setUp() throws SQLException {
        cleanTable();
        OAuthGrantStore.clear();
    }

    @AfterEach
    void tearDown() throws SQLException {
        OAuthGrantStore.clear();
        cleanTable();
    }

    /** 直接清表：测试要保证互不影响，而 store.clear() 只清内存 */
    private void cleanTable() throws SQLException {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM oauth_grants")) {
            ps.executeUpdate();
        }
    }

    // ==================== 落库与恢复 ====================

    @Test
    @DisplayName("授权记录落库，模拟重启后能恢复")
    void grantSurvivesRestart() throws SQLException {
        OAuthGrantStore.authorize(USER, "张三", CLIENT, Set.of("player:read", "inventory:read"));

        // 确认真的写进了库
        OAuthGrantsDAO.Row row = dao.find(GRANT_ID);
        assertNotNull(row, "授权必须落库，否则重启就丢了");
        assertEquals(USER, row.userId());
        assertEquals("张三", row.username());
        assertEquals(CLIENT, row.clientId());
        assertFalse(row.revoked());

        // 模拟重启：清空内存后重新载入
        OAuthGrantStore.clear();
        assertEquals(0, OAuthGrantStore.grantCount(), "内存应已清空");

        int loaded = OAuthGrantStore.loadFromDatabase();
        assertEquals(1, loaded, "应载入 1 条授权");

        List<OAuthGrantStore.Grant> grants = OAuthGrantStore.grantsOf(USER);
        assertEquals(1, grants.size());
        assertEquals(CLIENT, grants.get(0).clientId());
        assertTrue(grants.get(0).scopes().contains("player:read"),
                "scope 必须完整恢复 —— 丢了 scope 等于权限判定失效");
        assertTrue(grants.get(0).scopes().contains("inventory:read"));
    }

    @Test
    @DisplayName("恢复时保留原始授权时间，不能重置成'刚刚'")
    void createdAtPreservedOnReload() throws SQLException {
        OAuthGrantStore.authorize(USER, "张三", CLIENT, Set.of("player:read"));
        long originalCreated = dao.find(GRANT_ID).createdAt();

        OAuthGrantStore.clear();
        OAuthGrantStore.loadFromDatabase();

        long afterReload = OAuthGrantStore.grantsOf(USER).get(0).createdAt();
        assertEquals(originalCreated, afterReload,
                "重启后若显示'刚刚授权'，玩家会以为有陌生应用刚拿到权限");
    }

    @Test
    @DisplayName("重新授权覆盖旧 scope，而不是产生两条记录")
    void reauthorizeOverwritesScopes() throws SQLException {
        OAuthGrantStore.authorize(USER, "张三", CLIENT, Set.of("player:read"));
        OAuthGrantStore.authorize(USER, "张三", CLIENT, Set.of("inventory:read", "economy:read"));

        assertEquals(1, OAuthGrantStore.grantsOf(USER).size(), "内存里应只有一条");

        OAuthGrantStore.clear();
        OAuthGrantStore.loadFromDatabase();

        List<OAuthGrantStore.Grant> grants = OAuthGrantStore.grantsOf(USER);
        assertEquals(1, grants.size(), "库里也应只有一条 —— 同一玩家对同一应用不重复");
        assertTrue(grants.get(0).scopes().contains("inventory:read"));
        assertFalse(grants.get(0).scopes().contains("player:read"),
                "旧 scope 应被新的一次授权覆盖，否则等于权限只增不减");
    }

    // ==================== 撤销 ====================

    @Test
    @DisplayName("撤销后重启不会复活")
    void revokedGrantDoesNotComeBack() throws SQLException {
        OAuthGrantStore.authorize(USER, "张三", CLIENT, Set.of("player:read"));
        assertTrue(OAuthGrantStore.revoke(GRANT_ID));

        // 库里应标记为 revoked 而不是删行（保留追溯能力）
        OAuthGrantsDAO.Row row = dao.find(GRANT_ID);
        assertNotNull(row, "撤销不删行 —— 要保留'曾经授权过'的事实");
        assertTrue(row.revoked(), "应标记为已撤销");

        OAuthGrantStore.clear();
        int loaded = OAuthGrantStore.loadFromDatabase();

        assertEquals(0, loaded, "已撤销的授权不该被载入");
        assertEquals(0, OAuthGrantStore.grantsOf(USER).size(),
                "撤销后重启绝不能复活 —— 否则玩家撤销形同虚设");
    }

    @Test
    @DisplayName("撤销后重新授权可以恢复")
    void reauthorizeAfterRevokeWorks() throws SQLException {
        OAuthGrantStore.authorize(USER, "张三", CLIENT, Set.of("player:read"));
        OAuthGrantStore.revoke(GRANT_ID);

        // 玩家后来又同意了
        OAuthGrantStore.authorize(USER, "张三", CLIENT, Set.of("player:read"));

        OAuthGrantsDAO.Row row = dao.find(GRANT_ID);
        assertFalse(row.revoked(), "重新授权应清掉撤销标记");

        OAuthGrantStore.clear();
        assertEquals(1, OAuthGrantStore.loadFromDatabase());
    }

    // ==================== 清理 ====================

    @Test
    @DisplayName("清理只删早该过期的撤销记录，不动有效授权")
    void purgeOnlyRemovesOldRevoked() throws SQLException {
        OAuthGrantStore.authorize(USER, "张三", CLIENT, Set.of("player:read"));
        OAuthGrantStore.authorize(USER + 1, "李四", CLIENT, Set.of("player:read"));
        OAuthGrantStore.revoke(USER + 1 + ":" + CLIENT);

        // 把撤销那条的 expires_at 改成很久以前
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE oauth_grants SET expires_at = ? WHERE grant_id = ?")) {
            ps.setLong(1, System.currentTimeMillis() - 86_400_000L);
            ps.setString(2, (USER + 1) + ":" + CLIENT);
            ps.executeUpdate();
        }

        dao.purgeExpired(System.currentTimeMillis());

        assertNotNull(dao.find(GRANT_ID), "有效授权不能被清理");
        assertNull(dao.find((USER + 1) + ":" + CLIENT),
                "早已过期的撤销记录应被删除，否则表会无限增长");
    }

    @Test
    @DisplayName("重启后：授权还在（玩家能撤销），但旧 token 失效（需重新获取）")
    void restartKeepsGrantButInvalidatesToken() {
        // 这条把「持久化什么、不持久化什么」钉死，避免以后有人"顺手"
        // 把 jti 也持久化了，或反过来把授权记录也扔进内存。
        var grant = OAuthGrantStore.authorize(USER, "张三", CLIENT, Set.of("player:read"));
        String jti = "persist-jti-1";
        OAuthGrantStore.registerToken(jti, grant.grantId());
        String token = com.mtxgdn.service.OAuthService.issueUserAccessToken(
                USER, "张三", CLIENT, Set.of("player:read"), grant.grantId(), jti);

        assertNotNull(com.mtxgdn.service.OAuthService.parseUserToken(token), "重启前有效");

        // 模拟重启
        OAuthGrantStore.clear();
        OAuthGrantStore.loadFromDatabase();

        assertEquals(1, OAuthGrantStore.grantsOf(USER).size(),
                "授权记录要保住 —— 玩家得能看到并撤销它");
        assertNull(com.mtxgdn.service.OAuthService.parseUserToken(token),
                "旧 access_token 应失效：jti 不持久化，重启后无从确认它属于哪条授权");
    }

    // ==================== 容错 ====================

    @Test
    @DisplayName("查不到的 grant 返回 null，不抛异常")
    void findMissingReturnsNull() {
        assertNull(dao.find("9999:no_such_app"));
        assertNull(dao.find(null));
    }

    @Test
    @DisplayName("载入空表不会出错，只是得到 0 条")
    void loadEmptyIsSafe() {
        OAuthGrantStore.clear();
        assertEquals(0, OAuthGrantStore.loadFromDatabase());
        assertTrue(OAuthGrantStore.grantsOf(USER).isEmpty());
    }

    // ==================== 表与白名单 ====================

    @Test
    @DisplayName("新表已登记进备份白名单（否则备份还原会把它当非法表名）")
    void tablesAreInBackupWhitelist() {
        // 这个方法在表名非法/不在白名单时会抛 IllegalArgumentException。
        // 历史上有 5 张表加建表语句时忘了同步白名单，表现为"某几张表备份不出来"，
        // 报错却像安全拦截。这里守住同一个坑。
        assertDoesNotThrow(() -> DatabaseManager.assertSafeTableName("oauth_grants"));
    }
}
