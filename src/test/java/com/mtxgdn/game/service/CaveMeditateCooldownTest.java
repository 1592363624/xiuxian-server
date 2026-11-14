package com.mtxgdn.game.service;

import com.mtxgdn.TestBase;
import com.mtxgdn.common.service.ServiceRegistry;
import com.mtxgdn.db.CaveDAO;
import com.mtxgdn.db.DatabaseManager;
import com.mtxgdn.game.entity.Cave;
import com.mtxgdn.game.cave.CaveMeditateResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 洞府冥想的冷却回归。
 *
 * <p><b>缺陷形态</b>：{@code /洞府 meditate} 原来没有任何冷却，连点就能无限拿经验；
 * 洞府等级/阵法加成越高，刷得越快。经验获取没有节奏，等于一条无上限的挂机产线。</p>
 *
 * <p>本测试钉住：第一次成功、紧接着第二次被拒、冷却过去后恢复可用；
 * 并顺手验证"被拒的那次没有真的加经验"。</p>
 */
class CaveMeditateCooldownTest extends TestBase {

    private static final String USER_PREFIX = "meditate_cd_user_";

    private final CaveService caveService = ServiceRegistry.container().getCaveService();
    private final CaveDAO caveDAO = new CaveDAO();

    private long userId;
    private long playerId;

    @BeforeEach
    void setUp() throws Exception {
        cleanup();
        userId = insertUser();
        playerId = insertPlayer("冥想冷却测试者");
        insertCave();
    }

    @AfterEach
    void tearDown() throws Exception {
        cleanup();
    }

    @Test
    @DisplayName("第一次冥想成功，紧接着第二次被冷却拒绝")
    void secondMeditateIsRejectedByCooldown() {
        CaveMeditateResult first = caveService.meditate(playerId);
        assertTrue(first.success(), "第一次冥想应成功: " + first.message());

        CaveMeditateResult second = caveService.meditate(playerId);
        assertFalse(second.success(), "冷却期内第二次冥想必须被拒绝");
        assertTrue(second.message().contains("等待") || second.message().contains("wait"),
                "拒绝理由应说明还要等多久，实际: " + second.message());
    }

    @Test
    @DisplayName("被冷却拒绝时不产生经验")
    void rejectedMeditateGrantsNoExp() {
        assertTrue(caveService.meditate(playerId).success());

        long expAfterFirst = playerService().getPlayerById(playerId).getExperience();
        CaveMeditateResult second = caveService.meditate(playerId);
        assertFalse(second.success());
        assertEquals(expAfterFirst, playerService().getPlayerById(playerId).getExperience(),
                "被拒绝的冥想不得加经验");
    }

    @Test
    @DisplayName("冷却过期后可以再次冥想（默认 5 分钟）")
    void meditateWorksAgainAfterCooldown() throws Exception {
        assertTrue(caveService.meditate(playerId).success());
        assertFalse(caveService.meditate(playerId).success());

        // 把上次冥想时间往前挪过冷却窗口，模拟等待结束
        long past = System.currentTimeMillis() - CaveService.MEDITATE_COOLDOWN_MS - 1_000L;
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE caves SET last_meditate_time = ? WHERE player_id = ?")) {
            ps.setLong(1, past);
            ps.setLong(2, playerId);
            ps.executeUpdate();
        }

        assertTrue(caveService.meditate(playerId).success(), "冷却结束后应能再次冥想");
    }

    @Test
    @DisplayName("没有洞府时冥想被拒（原有行为不回归）")
    void meditateWithoutCaveIsRejected() throws Exception {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM caves WHERE player_id = ?")) {
            ps.setLong(1, playerId);
            ps.executeUpdate();
        }
        CaveMeditateResult result = caveService.meditate(playerId);
        assertFalse(result.success());
    }

    // ==================== 夹具 ====================

    private PlayerService playerService() {
        return ServiceRegistry.container().getPlayerService();
    }

    private long insertUser() throws Exception {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO users (username, password) VALUES (?, ?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, USER_PREFIX + System.nanoTime());
            ps.setString(2, "x");
            ps.executeUpdate();
            try (var keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : 0L;
            }
        }
    }

    private long insertPlayer(String name) throws Exception {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO players (user_id, name) VALUES (?, ?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, userId);
            ps.setString(2, name + "_" + System.nanoTime());
            ps.executeUpdate();
            try (var keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : 0L;
            }
        }
    }

    private void insertCave() throws Exception {
        try (Connection conn = DatabaseManager.getConnection()) {
            long caveId = caveDAO.insertCave(conn, playerId, "测试洞府",
                    Cave.getMaxSpiritEnergyForLevel(1),
                    Cave.getCultivationBonusForLevel(1),
                    Cave.getStorageBonusForLevel(1), 0L);
            assertTrue(caveId > 0, "洞府创建失败");
        }
    }

    private void cleanup() throws Exception {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM caves WHERE player_id IN "
                             + "(SELECT id FROM players WHERE user_id IN "
                             + "(SELECT id FROM users WHERE username LIKE ?))")) {
            ps.setString(1, USER_PREFIX + "%");
            ps.executeUpdate();
        }
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM players WHERE user_id IN (SELECT id FROM users WHERE username LIKE ?)")) {
            ps.setString(1, USER_PREFIX + "%");
            ps.executeUpdate();
        }
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM users WHERE username LIKE ?")) {
            ps.setString(1, USER_PREFIX + "%");
            ps.executeUpdate();
        }
    }
}
