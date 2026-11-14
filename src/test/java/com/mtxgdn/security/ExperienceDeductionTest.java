package com.mtxgdn.security;

import com.mtxgdn.TestBase;
import com.mtxgdn.common.service.ServiceRegistry;
import com.mtxgdn.entity.Player;
import com.mtxgdn.game.service.PlayerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 经验扣除的下限保护测试。
 *
 * <p><b>背景</b>：管理端「发放」指令用负数表示扣除（{@code /发放 1 经验=-100}）。
 * 而 {@code PlayerService.addExperience} 底层是
 * {@code UPDATE players SET experience = experience + ?}，<b>没有下限保护</b> ——
 * 传 {@code -99999999} 会把经验扣成负数，之后所有
 * {@code 当前经验 < 所需经验} 的比较全部失真，玩家会卡在无法突破的状态，
 * 而且这种坏数据一旦落库比崩溃更难收拾。</p>
 *
 * <p><b>为什么不能"先查余额再扣"</b>：{@code addExperience} 会先过
 * {@code experienceGain} 的倍率加成（称号 + 功法 + 经验翻倍丹），
 * 负值乘倍率后<b>实际扣减量大于传入值</b>。按原始值判断余额会低估扣除量，
 * 仍可能扣穿。所以 {@code removeExperience} 内部用同一个倍率函数算出
 * 真实扣减量再判断。</p>
 */
class ExperienceDeductionTest extends TestBase {

    private PlayerService playerService;
    private long userId;
    private long playerId;

    @BeforeEach
    void setUp() throws Exception {
        playerService = ServiceRegistry.container().getPlayerService();
        assertNotNull(playerService, "PlayerService 应可用");
        // players.user_id 有外键约束，必须先建 users 行（用独立连接建，
        // 避免并入 PlayerService 的事务语义）。
        long[] ids = createUserAndPlayer("惊验扣除" + System.nanoTime());
        userId = ids[0];
        playerId = ids[1];
    }

    private static long[] createUserAndPlayer(String username) throws Exception {
        try (java.sql.Connection conn = com.mtxgdn.db.DatabaseManager.getConnection()) {
            try (java.sql.PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO users (username, password) VALUES (?, ?)")) {
                ps.setString(1, username);
                ps.setString(2, "x");
                ps.executeUpdate();
            }
            long uid;
            try (java.sql.PreparedStatement ps = conn.prepareStatement(
                    "SELECT id FROM users WHERE username = ?")) {
                ps.setString(1, username);
                try (java.sql.ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next(), "users 行应已插入");
                    uid = rs.getLong(1);
                }
            }
            try (java.sql.PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO players (user_id, name, spiritual_root, realm, sub_realm, "
                            + "hp, max_hp, mp, max_mp, attack, defense, speed, spirit, experience, "
                            + "gold, last_breakthrough_time) "
                            + "VALUES (?, ?, 'FIRE_WOOD_EARTH', 0, 0, 100, 100, 50, 50, 10, 10, 10, 10, 0, 0, 0)")) {
                ps.setLong(1, uid);
                ps.setString(2, username);
                ps.executeUpdate();
            }
            try (java.sql.PreparedStatement ps = conn.prepareStatement(
                    "SELECT id FROM players WHERE user_id = ?")) {
                ps.setLong(1, uid);
                try (java.sql.ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next(), "players 行应已插入");
                    return new long[]{uid, rs.getLong(1)};
                }
            }
        }
    }

    @Test
    @DisplayName("扣除不超过余额时成功，且余额正确减少")
    void deductWithinBalance() {
        playerService.addExperience(playerId, 1000);

        // addExperience 会经过倍率加成，到账值可能大于 1000，
        // 所以以实际到账值为基准断言，而不是假定正好 1000。
        long before = currentExp();
        assertTrue(before >= 1000, "先确认已到账，实际 " + before);

        // 扣除按传入值原样扣（不经过获得经验的倍率），这里扣一个余额内的固定值
        long deduct = Math.min(500, before);
        assertTrue(playerService.removeExperience(playerId, deduct), "余额足够时应扣除成功");
        assertEquals(before - deduct, currentExp(), "应正好扣掉 " + deduct);
    }

    @Test
    @DisplayName("扣除超过余额时必须被拒绝，且余额保持不变（不得变成负数）")
    void deductBeyondBalanceRejected() {
        playerService.addExperience(playerId, 100);
        long before = currentExp();

        boolean ok = playerService.removeExperience(playerId, before + 1);

        assertFalse(ok, "余额不足时必须返回 false");
        assertEquals(before, currentExp(), "被拒绝时不得改动余额");
        assertTrue(currentExp() >= 0, "经验绝不能变成负数");
    }

    @Test
    @DisplayName("余额刚好相等时可以扣到 0（边界）")
    void deductExactBalance() {
        playerService.addExperience(playerId, 777);
        long before = currentExp();

        boolean ok = playerService.removeExperience(playerId, before);

        assertTrue(ok, "刚好扣完应当允许");
        assertEquals(0, currentExp(), "余额应为 0，不能是负数");
    }

    @Test
    @DisplayName("扣除量为 0 或负数时视为无事可做，不报错也不改余额")
    void nonPositiveAmountIsNoop() {
        playerService.addExperience(playerId, 50);
        long before = currentExp();

        assertTrue(playerService.removeExperience(playerId, 0), "0 视为成功");
        assertTrue(playerService.removeExperience(playerId, -5), "负数视为成功（无事可做）");

        assertEquals(before, currentExp(), "不应改动余额");
    }

    @Test
    @DisplayName("不存在的玩家扣除应返回 false 而不是抛异常")
    void unknownPlayerReturnsFalse() {
        assertFalse(playerService.removeExperience(999_999_999L, 10));
    }

    @Test
    @DisplayName("反复扣除不会把余额扣穿（模拟管理端连续误操作）")
    void repeatedDeductionCannotGoNegative() {
        playerService.addExperience(playerId, 100);
        long start = currentExp();

        // 尝试扣除 10 次开始余额，其中只有前几次应该成功
        int succeeded = 0;
        for (int i = 0; i < 10; i++) {
            if (playerService.removeExperience(playerId, start)) {
                succeeded++;
            }
        }

        assertEquals(1, succeeded, "只应按当前余额成功一次");
        assertTrue(currentExp() >= 0, "经验不能为负，实际 " + currentExp());
        assertEquals(0, currentExp(), "扣完一次后应剩 0");
    }

    private long currentExp() {
        Player p = playerService.getPlayerById(playerId);
        assertNotNull(p, "玩家应存在");
        return p.getExperience();
    }
}
