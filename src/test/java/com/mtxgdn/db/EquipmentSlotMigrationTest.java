package com.mtxgdn.db;

import com.mtxgdn.TestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 迁移 027：历史装备行里的中文槽位必须被改写为规范名。
 *
 * <p>只改代码不够：旧版本已经把「武器」写进了库，必须把存量行迁过来，
 * 否则玩家那件装备永远看不到、也卸不下来。重复行要保留一行、其余退回背包。</p>
 */
class EquipmentSlotMigrationTest extends TestBase {

    
    private long playerA;
    private long playerB;
    private long playerC;

    @Test
    @DisplayName("迁移：中文槽位改写为规范名；同槽重复行只留一行、多余物品退回背包")
    void normalizesLegacyRows() throws SQLException {
        cleanup();
        try (Connection conn = DatabaseManager.getConnection()) {
            playerA = createPlayer(conn, "equip_mig_a");
            playerB = createPlayer(conn, "equip_mig_b");
            playerC = createPlayer(conn, "equip_mig_c");
            // A：同一物理槽位两条历史行（canonical 行 + 中文行）
            insertEquipment(conn, playerA, "weapon", "mtxgdn:spirit_sword");
            insertEquipment(conn, playerA, "武器", "mtxgdn:iron_sword");
            // B：只有中文行，应被改写为规范名（物品不从背包动）
            insertEquipment(conn, playerB, "护甲", "mtxgdn:iron_armor");
            // C：不认识的脏槽位，必须原样保留
            insertEquipment(conn, playerC, "头盔", "mtxgdn:helmet");

            DatabaseMigration.normalizeEquipmentSlots(conn);

            // A：只剩规范行 weapon，且保留的是原本规范那行
            assertEquals("mtxgdn:spirit_sword", equipmentKey(conn, playerA, "weapon"));
            assertEquals(1, equipmentCount(conn, playerA), "同一槽位重复行必须收敛为一行");
            // 被删掉那行的物品退回背包（否则玩家净亏一件）
            assertEquals(1, itemCount(conn, playerA, "mtxgdn:iron_sword"),
                    "重复行被删时，对应物品必须退回背包");

            // B：中文槽位被改写成规范名，物品没有被重复退回
            assertEquals("mtxgdn:iron_armor", equipmentKey(conn, playerB, "armor"));
            assertEquals(0, itemCount(conn, playerB, "mtxgdn:iron_armor"),
                    "仅改名（未被删除）的行不应把物品退回背包");

            // C：无法识别的槽位原样保留
            assertEquals("mtxgdn:helmet", equipmentKey(conn, playerC, "头盔"));
        } finally {
            cleanup();
        }
    }

    // ==================== 夹具 ====================

    private static void insertEquipment(Connection conn, long playerId, String slot, String itemKey)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO players_equipment (player_id, slot, item_key) VALUES (?, ?, ?)")) {
            ps.setLong(1, playerId);
            ps.setString(2, slot);
            ps.setString(3, itemKey);
            ps.executeUpdate();
        }
    }

    private static String equipmentKey(Connection conn, long playerId, String slot) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT item_key FROM players_equipment WHERE player_id = ? AND slot = ?")) {
            ps.setLong(1, playerId);
            ps.setString(2, slot);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private static int equipmentCount(Connection conn, long playerId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM players_equipment WHERE player_id = ?")) {
            ps.setLong(1, playerId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private static long itemCount(Connection conn, long playerId, String itemKey) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT quantity FROM players_items WHERE player_id = ? AND item_key = ?")) {
            ps.setLong(1, playerId);
            ps.setString(2, itemKey);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }

    /** 建一个真实玩家（players_equipment 有外键，不能用假 id）。 */
    private static long createPlayer(Connection conn, String namePrefix) throws SQLException {
        long userId;
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO users (username, password) VALUES (?, ?)",
                java.sql.Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, namePrefix + "_" + System.nanoTime());
            ps.setString(2, "x");
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                userId = rs.next() ? rs.getLong(1) : 0L;
            }
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO players (user_id, name) VALUES (?, ?)",
                java.sql.Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, userId);
            ps.setString(2, namePrefix + "_角色_" + System.nanoTime());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }

    private static void cleanup() throws SQLException {
        try (Connection conn = DatabaseManager.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM players WHERE user_id IN "
                            + "(SELECT id FROM users WHERE username LIKE 'equip_mig_%')")) {
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM users WHERE username LIKE 'equip_mig_%'")) {
                ps.executeUpdate();
            }
        }
    }
}
