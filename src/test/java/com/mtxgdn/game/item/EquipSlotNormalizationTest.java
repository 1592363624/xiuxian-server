package com.mtxgdn.game.item;

import com.mtxgdn.TestBase;
import com.mtxgdn.common.service.ServiceRegistry;
import com.mtxgdn.db.DatabaseManager;
import com.mtxgdn.game.entity.PlayerInfo;
import com.mtxgdn.game.service.ItemService;
import com.mtxgdn.game.service.PlayerService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 装备槽位归一化的回归测试。
 *
 * <p><b>缺陷形态（用户实测）</b>：{@code /装备 灵剑 武器} 能成功，物品从背包扣掉，
 * 但 {@code players_equipment.slot} 存的是「武器」；之后 {@code /已装备}、
 * {@code /卸下 weapon}、{@code /强化 weapon} 全按 {@code weapon} 查 ——
 * 表现为"装备凭空消失、还卸不下来，东西也拿不回来"。</p>
 *
 * <p>本测试钉住：写入只写规范名、读取兼容历史行、非法槽位拒绝且不吞物品、
 * 同一物理槽位的旧行会被替换而不是叠加。</p>
 */
class EquipSlotNormalizationTest extends TestBase {

    private static final String USER_PREFIX = "equip_slot_user_";
    private static final String SWORD = "mtxgdn:spirit_sword";
    private static final String ARMOR_ITEM = "mtxgdn:jade_armor";
    /** 另一件护甲：用来验证"替换旧装备"而不是"重复装备同一件" */
    private static final String NEW_ARMOR_ITEM = "mtxgdn:phoenix_armor";

    private final ItemService itemService = ServiceRegistry.container().getItemService();
    private final PlayerService playerService = ServiceRegistry.container().getPlayerService();

    private PlayerInfo player;
    private long playerId;

    @BeforeAll
    static void registerItems() {
        ItemScanner.scanAndRegister();
    }

    @BeforeEach
    void setUp() throws Exception {
        cleanup();
        long userId = insertUser();
        player = playerService.createPlayer(userId, "槽位测试者" + System.nanoTime());
        assertNotNull(player, "测试角色创建失败");
        playerId = player.getId();
        assertTrue(itemService.addItem(playerId, SWORD, 2), "放入灵剑应成功");
    }

    @AfterEach
    void tearDown() throws Exception {
        cleanup();
    }

    @Test
    @DisplayName("用中文「武器」装备 → 库里写规范名 weapon，/卸下 能按 weapon 找到")
    void chineseSlotIsNormalizedOnWrite() {
        assertTrue(itemService.equipItem(playerId, SWORD, "武器").success());

        Map<String, String> equipment = itemService.getEquipment(playerId);
        assertEquals(SWORD, equipment.get("weapon"), "必须用规范槽位 weapon 存，实际: " + equipment);
        assertFalse(equipment.containsKey("武器"), "不应留下中文槽位行");

        assertTrue(itemService.unequipItem(playerId, "weapon").success(),
                "按规范名 /卸下 weapon 必须能卸下");
        assertEquals(2, itemService.getItemCount(playerId, SWORD), "卸下后物品应回到背包（初始 2 件）");
    }

    @Test
    @DisplayName("大写/带空格的槽位也归一化")
    void uppercaseSlotIsNormalized() {
        assertTrue(itemService.equipItem(playerId, SWORD, " WEAPON ").success());
        assertEquals(SWORD, itemService.getEquipment(playerId).get("weapon"));
    }

    @Test
    @DisplayName("非法槽位直接拒绝：不扣物品、不写装备行")
    void unknownSlotIsRejectedWithoutLosingItem() {
        var result = itemService.equipItem(playerId, SWORD, "头盔");
        assertFalse(result.success(), "未知槽位必须拒绝");
        assertTrue(result.message().contains("部位"), "提示应说明可用部位: " + result.message());

        assertEquals(2, itemService.getItemCount(playerId, SWORD), "被拒绝的装备不得扣物品");
        assertTrue(itemService.getEquipment(playerId).isEmpty(), "被拒绝的装备不得写库");
    }

    @Test
    @DisplayName("历史脏行（slot=「武器」）仍能被读取与卸下")
    void legacyChineseSlotRowIsStillVisibleAndUnequippable() throws Exception {
        // 模拟旧版本写下的坏行：物品从背包扣掉，装备行存中文槽位
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO players_equipment (player_id, slot, item_key) VALUES (?, ?, ?)")) {
            ps.setLong(1, playerId);
            ps.setString(2, "武器");
            ps.setString(3, SWORD);
            ps.executeUpdate();
        }
        itemService.removeItem(playerId, SWORD, 1);

        assertEquals(SWORD, itemService.getEquipment(playerId).get("weapon"),
                "/已装备 等读路径必须能看见历史中文槽位");

        assertTrue(itemService.unequipItem(playerId, "weapon").success(),
                "历史脏行也必须能卸下（按真实存的槽位删除）");
        assertEquals(2, itemService.getItemCount(playerId, SWORD), "卸下后物品应回背包（初始 2 件）");
        assertTrue(itemService.getEquipment(playerId).isEmpty(), "脏行应已清理");
    }

    @Test
    @DisplayName("同一槽位已有历史行时再装备：替换旧行而不是叠两行")
    void equippingReplacesLegacyRowInsteadOfDuplicating() throws Exception {
        assertTrue(itemService.addItem(playerId, ARMOR_ITEM, 1), "放入护甲应成功");
        // 历史行：护甲占着「防具」，物品已不在背包
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO players_equipment (player_id, slot, item_key) VALUES (?, ?, ?)")) {
            ps.setLong(1, playerId);
            ps.setString(2, "防具");
            ps.setString(3, ARMOR_ITEM);
            ps.executeUpdate();
        }
        itemService.removeItem(playerId, ARMOR_ITEM, 1);

        // 再装备另一件护甲（规范槽位）：应替换旧行，旧护甲回背包
        assertTrue(itemService.addItem(playerId, NEW_ARMOR_ITEM, 1), "放入新护甲应成功");
        var r = itemService.equipItem(playerId, NEW_ARMOR_ITEM, "armor");
        assertTrue(r.success(), r.message());

        Map<String, String> equipment = itemService.getEquipment(playerId);
        assertEquals(1, equipment.size(), "同一槽位不能出现两行: " + equipment);
        assertEquals(NEW_ARMOR_ITEM, equipment.get("armor"), "槽位应是新装备");
        assertEquals(1, itemService.getItemCount(playerId, ARMOR_ITEM),
                "被替换下来的旧护甲应退回背包（背包里 1 件）");

        long rows = equipmentRowCount();
        assertEquals(1, rows, "players_equipment 里同一玩家同一槽位只应有一行");
    }

    // ==================== 夹具 ====================

    private long equipmentRowCount() throws Exception {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM players_equipment WHERE player_id = ?")) {
            ps.setLong(1, playerId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
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

    private void cleanup() throws Exception {
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
