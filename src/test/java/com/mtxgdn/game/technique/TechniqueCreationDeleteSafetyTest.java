package com.mtxgdn.game.technique;

import com.mtxgdn.TestBase;
import com.mtxgdn.common.service.ServiceRegistry;
import com.mtxgdn.db.DatabaseManager;
import com.mtxgdn.db.PlayerTechniqueDAO;
import com.mtxgdn.db.TechniqueDAO;
import com.mtxgdn.entity.Player;
import com.mtxgdn.game.service.PlayerService;
import com.mtxgdn.game.service.TechniqueService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 自创功法删除的安全性回归。
 *
 * <p>背景（改造前会踩到的两个坑）：</p>
 * <ol>
 *   <li><b>装备时属性已写进 {@code players} 行</b>，而删除只删 {@code techniques} 行，
 *       {@code players_techniques} 又随外键级联消失 —— 属性永久留在身上，
 *       删了再造再装备可以无限叠加；</li>
 *   <li>{@code players_techniques.technique_id} 是 {@code ON DELETE CASCADE}，
 *       创作者一删，<b>其他玩家学过的功法凭空消失</b>，学费不退。</li>
 * </ol>
 *
 * <p>本类按"作者/学习者/装备状态"三种组合把规则钉死：
 * 有人运转 → 拒绝；有他人学过 → 拒绝；只有作者自己学过且未装备 → 允许并清理。</p>
 */
class TechniqueCreationDeleteSafetyTest extends TestBase {

    private static final String NAME_PREFIX = "test_del_safety_";
    private static final String USER_PREFIX = "del_safety_user_";

    private final PlayerTechniqueDAO playerTechDao = new PlayerTechniqueDAO();
    private final TechniqueDAO techniqueDao = new TechniqueDAO();

    private TechniqueCreationService creationService;
    private TechniqueService techniqueService;
    private PlayerService playerService;

    private long creatorUserId;
    private long creatorPlayerId;
    private long learnerUserId;
    private long learnerPlayerId;

    @BeforeEach
    void setUp() throws Exception {
        cleanup();
        creationService = new TechniqueCreationService();
        techniqueService = new TechniqueService(ServiceRegistry.container());
        playerService = new PlayerService(ServiceRegistry.container());
        creatorUserId = createUser("creator");
        creatorPlayerId = createPlayer(creatorUserId, "删除测试作者");
        learnerUserId = createUser("learner");
        learnerPlayerId = createPlayer(learnerUserId, "删除测试学习者");
    }

    @AfterEach
    void tearDown() throws Exception {
        cleanup();
    }

    // ==================== 测试 ====================

    @Test
    @DisplayName("装备中的自创功法不能删除；卸下后可删，属性正常回退")
    void cannotDeleteWhileEquipped() throws Exception {
        long baseAttack = attackOf(creatorPlayerId);

        long techId = createAttackTechnique(creatorPlayerId, 3);
        assertTrue(techniqueService.learnTechnique(creatorPlayerId, techId).success(), "学习应成功");
        assertTrue(techniqueService.equipTechnique(creatorPlayerId, techId).success(), "装备应成功");
        assertEquals(baseAttack + 3, attackOf(creatorPlayerId), "装备后攻击应 +3");

        // ① 装备中删除 → 拒绝，且属性不再叠加
        var denied = creationService.delete(playerOf(creatorPlayerId), techId);
        assertFalse(denied.success(), "装备中的功法不能删 —— 否则属性会永久留在身上");
        assertTrue(denied.message().contains("运转") || denied.message().contains("卸下"),
                "拒绝理由应让玩家知道先卸下，实际: " + denied.message());
        assertEquals(baseAttack + 3, attackOf(creatorPlayerId),
                "被拒绝的删除不得改动任何属性");
        assertTrue(techniqueDao.hasTechnique(creatorPlayerId, techId), "功法应仍在");

        // ② 卸下 → 属性回退
        assertTrue(techniqueService.unequipTechnique(creatorPlayerId, techId).success());
        assertEquals(baseAttack, attackOf(creatorPlayerId), "卸下后攻击应回到基础值");

        // ③ 再删除 → 成功（创建者自己学过但未装备：允许删，并清掉自己的学习记录）
        var deleted = creationService.delete(playerOf(creatorPlayerId), techId);
        assertTrue(deleted.success(), "未装备时可以删除，实际: " + deleted.message());
        assertFalse(techniqueDao.hasTechnique(creatorPlayerId, techId), "学习记录应随删除清理");
        assertNull(techniqueDao.findById(techId), "功法行应已删除");
    }

    @Test
    @DisplayName("已有其他玩家学过时，创建者不能删除（不允许作者一删全服丢功法）")
    void cannotDeleteWhenOthersLearnedIt() throws Exception {
        long techId = createAttackTechnique(creatorPlayerId, 2);
        assertTrue(techniqueService.learnTechnique(learnerPlayerId, techId).success(),
                "其他玩家学习应成功");

        var denied = creationService.delete(playerOf(creatorPlayerId), techId);
        assertFalse(denied.success(), "有人学过时删除必须被拒绝");
        assertTrue(denied.message().contains("习得") || denied.message().contains("无法删除"),
                "拒绝理由应说明有人已习得，实际: " + denied.message());

        // 学习者手里的功法不能凭空消失
        assertTrue(techniqueDao.hasTechnique(learnerPlayerId, techId),
                "拒绝删除后，学习者的记录必须原样保留");
        assertNotEquals(null, techniqueDao.findById(techId), "功法行必须仍在");
    }

    @Test
    @DisplayName("没人学过时可正常删除（原有行为不回归）")
    void canDeleteWhenNobodyLearnedIt() throws Exception {
        long techId = createAttackTechnique(creatorPlayerId, 1);
        var deleted = creationService.delete(playerOf(creatorPlayerId), techId);
        assertTrue(deleted.success(), deleted.message());
        assertNull(techniqueDao.findById(techId));
    }

    @Test
    @DisplayName("别人不能借删除接口删我的功法")
    void cannotDeleteSomeoneElsesTechnique() throws Exception {
        long techId = createAttackTechnique(creatorPlayerId, 1);
        var denied = creationService.delete(playerOf(learnerPlayerId), techId);
        assertFalse(denied.success(), "只能删自己的");
        assertNotEquals(null, techniqueDao.findById(techId));
    }

    // ==================== 夹具 ====================

    private long createAttackTechnique(long creatorPlayerId, int attackBonus) throws Exception {
        return playerTechDao.insert(creatorPlayerId, NAME_PREFIX + System.nanoTime(),
                "删除安全性测试", 0, 0, 0, 0, 0,
                "ATTACK", 10, 0, 0, attackBonus, 0, 0, 0, 0, 0, 0, 0, "");
    }

    private Player playerOf(long playerId) {
        return playerService.getPlayerById(playerId);
    }

    private long attackOf(long playerId) {
        return playerService.getPlayerById(playerId).getAttack();
    }

    /**
     * 造一个 users 行。
     *
     * <p>用 {@code System.nanoTime()} 后缀而不是固定名：测试库跨类复用，
     * 固定名会与历史残留撞唯一键。</p>
     */
    private long createUser(String role) throws Exception {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO users (username, password) VALUES (?, ?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, USER_PREFIX + role + "_" + System.nanoTime());
            ps.setString(2, "x");
            ps.executeUpdate();
            try (var keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : 0L;
            }
        }
    }

    private long createPlayer(long userId, String name) throws Exception {
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

    private void cleanup() throws Exception {
        // 先删功法（外键会级联清掉 players_techniques），再删玩家/账号
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM techniques WHERE name LIKE ?")) {
            ps.setString(1, NAME_PREFIX + "%");
            ps.executeUpdate();
        }
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM players WHERE user_id IN (SELECT id FROM users WHERE username LIKE ?)")) {
            ps.setString(1, USER_PREFIX + "%");
            ps.executeUpdate();
        }
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM users WHERE username LIKE ?")) {
            ps.setString(1, USER_PREFIX + "%");
            ps.executeUpdate();
        }
    }
}
