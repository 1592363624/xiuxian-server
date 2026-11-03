package com.mtxgdn.game.technique;

import com.mtxgdn.TestBase;
import com.mtxgdn.db.DatabaseManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 词条系统的两条链路：<b>解析校验</b>（创造时）与<b>聚合生效</b>（战斗时）。
 *
 * <p>放在一个文件里，是因为它们共用同一套词条定义，且"解析出来的规格"
 * 正是"聚合要吃进去的规格"—— 拆开反而看不出这个衔接。</p>
 */
class TechniqueEffectTest extends TestBase {

    /** 玩家表的 user_id，用来定位测试玩家 */
    private static final long USER = 91001L;

    /** players_techniques 有外键指向 players，所以必须先有一个真实玩家 */
    private long playerId;

    @BeforeEach
    void setUp() throws Exception {
        TechniqueEffectResolver.clearCache();
        cleanUp();
        // players.user_id 外键指向 users，所以要先有账号再有角色
        long userId;
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO users (username, password) VALUES (?, ?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, "effect_test_user_" + USER);
            ps.setString(2, "x");
            ps.executeUpdate();
            try (var keys = ps.getGeneratedKeys()) {
                userId = keys.next() ? keys.getLong(1) : 0L;
            }
        }
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO players (user_id, name) VALUES (?, ?)")) {
            ps.setLong(1, userId);
            ps.setString(2, "词条测试者");
            ps.executeUpdate();
        }
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT id FROM players WHERE user_id = ?")) {
            ps.setLong(1, userId);
            try (var rs = ps.executeQuery()) {
                playerId = rs.next() ? rs.getLong(1) : 0L;
            }
        }
        assertTrue(playerId > 0, "测试玩家未能创建");
    }

    @AfterEach
    void tearDown() throws Exception {
        TechniqueEffectResolver.clearCache();
        cleanUp();
    }

    private void cleanUp() throws Exception {
        try (Connection conn = DatabaseManager.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM players_techniques WHERE player_id = ?")) {
                ps.setLong(1, playerId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM techniques WHERE creator_user_id = ?")) {
                ps.setLong(1, playerId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM players WHERE name = ?")) {
                ps.setString(1, "词条测试者");
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM users WHERE username = ?")) {
                ps.setString(1, "effect_test_user_" + USER);
                ps.executeUpdate();
            }
        }
    }

    // ==================== 解析与校验 ====================

    @Nested
    @DisplayName("解析与校验")
    class Parsing {

        @Test
        @DisplayName("正常配置能解析出来，并能存回去")
        void parsesAndRoundTrips() {
            var r = TechniqueEffectSpec.parse("armor_pen:15;crit_bonus:10");
            assertTrue(r.ok(), String.valueOf(r.problems()));
            assertEquals(0.15, r.spec().valueOf(TechniqueEffect.ARMOR_PEN), 1e-9);
            assertEquals(0.10, r.spec().valueOf(TechniqueEffect.CRIT_BONUS), 1e-9);

            // 存回去再解析，结果必须一致 —— 否则重启后词条会漂移
            var again = TechniqueEffectSpec.parse(r.spec().toStorageString());
            assertTrue(again.ok());
            assertEquals(r.spec().toStorageString(), again.spec().toStorageString());
        }

        @Test
        @DisplayName("空配置是合法的 —— 表示不带词条")
        void emptyIsLegal() {
            assertTrue(TechniqueEffectSpec.parse("").ok());
            assertTrue(TechniqueEffectSpec.parse(null).ok());
            assertTrue(TechniqueEffectSpec.parse("").spec().isEmpty());
            assertEquals(0, TechniqueEffectSpec.parse("").spec().cost(), 1e-9);
        }

        @Test
        @DisplayName("不认识的词条被拒绝，而不是忽略")
        void unknownKeyRejected() {
            // 忽略会让玩家以为选上了，实际没生效，日志里还什么都没有
            var r = TechniqueEffectSpec.parse("god_mode:99");
            assertFalse(r.ok());
            assertTrue(r.problems().get(0).contains("god_mode"), String.valueOf(r.problems()));
        }

        @Test
        @DisplayName("超过单项上限被拒绝")
        void overCapRejected() {
            int over = TechniqueEffect.ARMOR_PEN.maxUnits() + 1;
            var r = TechniqueEffectSpec.parse("armor_pen:" + over);
            assertFalse(r.ok());
            assertTrue(r.problems().get(0).contains("最多"), String.valueOf(r.problems()));
        }

        @Test
        @DisplayName("重复词条被拒绝")
        void duplicateRejected() {
            var r = TechniqueEffectSpec.parse("armor_pen:10;armor_pen:5");
            assertFalse(r.ok());
        }

        @Test
        @DisplayName("词条数量有上限，不能全带上")
        void tooManyRejected() {
            String tooMany = "armor_pen:5;crit_bonus:5;last_stand:5;guard:5";
            assertFalse(TechniqueEffectSpec.parse(tooMany).ok(),
                    "不限制数量的话最优解就是全带，权衡就没了");
        }

        @Test
        @DisplayName("互斥词条不能同时选 —— 攻与守不能兼得")
        void exclusiveRejected() {
            var r = TechniqueEffectSpec.parse("damage_amp:10;guard:10");
            assertFalse(r.ok(), "既能大幅增伤又能大幅减伤就成了没有弱点的通用解");
            assertTrue(r.problems().get(0).contains("不能同时"), String.valueOf(r.problems()));
        }

        @Test
        @DisplayName("零或负数值被拒绝")
        void nonPositiveRejected() {
            assertFalse(TechniqueEffectSpec.parse("armor_pen:0").ok());
            assertFalse(TechniqueEffectSpec.parse("armor_pen:-5").ok());
        }

        @Test
        @DisplayName("格式错误被拒绝而不是崩溃")
        void malformedRejected() {
            assertFalse(TechniqueEffectSpec.parse("armor_pen").ok());
            assertFalse(TechniqueEffectSpec.parse(":10").ok());
            assertFalse(TechniqueEffectSpec.parse("armor_pen:abc").ok());
        }
    }

    // ==================== 聚合生效 ====================

    @Nested
    @DisplayName("聚合生效")
    class Aggregation {

        @Test
        @DisplayName("没有功法时全为零 —— 现有战斗结果不受影响")
        void noTechniqueMeansZero() {
            var fx = TechniqueEffectResolver.forPlayer(playerId);
            assertTrue(fx.isZero(), "官方功法没有词条，聚合必须是全零，否则等于偷偷改了平衡");
            assertEquals(1.0, fx.attackMultiplier(1.0), 1e-9);
            assertEquals(1.0, fx.incomingMultiplier(), 1e-9);
            assertEquals(1.0, fx.lastStandMultiplier(0.0), 1e-9);
        }

        @Test
        @DisplayName("装备带词条的功法后生效；卸下后失效")
        void equippedOnly() throws Exception {
            long techId = createTechniqueWithEffects("armor_pen:20;crit_bonus:10");
            equip(techId, true);

            var fx = TechniqueEffectResolver.forPlayer(playerId);
            assertEquals(0.20, fx.armorPen(), 1e-9);
            assertEquals(0.10, fx.critBonus(), 1e-9);

            // 卸下后必须失效 —— 否则玩家可以"学完就卸下"，白拿机制
            equip(techId, false);
            TechniqueEffectResolver.clearCache();
            assertTrue(TechniqueEffectResolver.forPlayer(playerId).isZero(),
                    "词条只在装备时生效，否则等于绕过装备位限制");
        }

        @Test
        @DisplayName("多门功法的词条会叠加")
        void stacks() throws Exception {
            long a = createTechniqueWithEffects("armor_pen:15");
            long b = createTechniqueWithEffects("armor_pen:10");
            equip(a, true);
            equip(b, true);

            assertEquals(0.25, TechniqueEffectResolver.forPlayer(playerId).armorPen(), 1e-9);
        }

        @Test
        @DisplayName("叠加有总上限 —— 单件有上限不等于总量有上限")
        void stackedIsCapped() throws Exception {
            // 用三个不同属性凑满 3 门，每门都点满破防
            long a = createTechniqueWithEffects("armor_pen:30");
            long b = createTechniqueWithEffects("armor_pen:30");
            long c = createTechniqueWithEffects("armor_pen:30");
            equip(a, true);
            equip(b, true);
            equip(c, true);

            double pen = TechniqueEffectResolver.forPlayer(playerId).armorPen();
            assertTrue(pen <= 0.45 + 1e-9, "总上限必须兜住，否则防御形同虚设，实际=" + pen);
            assertEquals(0.45, pen, 1e-9);
        }

        @Test
        @DisplayName("背水：满血无加成，血越少加成越高")
        void lastStandScales() throws Exception {
            long t = createTechniqueWithEffects("last_stand:20");
            equip(t, true);

            var fx = TechniqueEffectResolver.forPlayer(playerId);
            assertEquals(1.0, fx.lastStandMultiplier(1.0), 1e-9, "满血不该白拿加成");
            assertEquals(1.10, fx.lastStandMultiplier(0.5), 1e-9, "半血拿一半");
            assertEquals(1.20, fx.lastStandMultiplier(0.0), 1e-9, "空血拿满");
        }

        @Test
        @DisplayName("血量比例异常时按满血处理，不白给加成")
        void lastStandHandlesBadRatio() throws Exception {
            long t = createTechniqueWithEffects("last_stand:20");
            equip(t, true);
            var fx = TechniqueEffectResolver.forPlayer(playerId);
            assertEquals(1.0, fx.lastStandMultiplier(-5), 1e-9);
            assertEquals(1.0, fx.lastStandMultiplier(99), 1e-9);
        }

        @Test
        @DisplayName("缓存可主动失效，换功法后立刻生效")
        void cacheInvalidation() throws Exception {
            long t = createTechniqueWithEffects("guard:15");
            equip(t, true);
            assertEquals(0.15, TechniqueEffectResolver.forPlayer(playerId).guard(), 1e-9);

            TechniqueEffectResolver.invalidate(playerId);
            equip(t, false);
            TechniqueEffectResolver.invalidate(playerId);
            assertTrue(TechniqueEffectResolver.forPlayer(playerId).isZero());
        }

        @Test
        @DisplayName("玩家 id 为空时返回全零，不抛异常")
        void nullPlayerSafe() {
            assertTrue(TechniqueEffectResolver.forPlayer(null).isZero());
        }

        @Test
        @DisplayName("库里的词条数据损坏时跳过该门，不影响战斗")
        void corruptSpecSkipped() throws Exception {
            long t = createTechniqueWithEffects("");
            // 直接写一条坏数据，模拟手工改库或旧版本遗留
            try (Connection conn = DatabaseManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "UPDATE techniques SET effect_spec = ? WHERE id = ?")) {
                ps.setString(1, "armor_pen:abc;;;");
                ps.setLong(2, t);
                ps.executeUpdate();
            }
            equip(t, true);

            assertTrue(TechniqueEffectResolver.forPlayer(playerId).isZero(),
                    "坏数据应被跳过，而不是让玩家打不了架");
        }
    }

    // ==================== 工具 ====================

    private long createTechniqueWithEffects(String effects) throws Exception {
        for (int i = 0; i < 100; i++) {
            String name = "词条测试" + System.nanoTime() % 100000 + "_" + i;
            try (Connection conn = DatabaseManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT INTO techniques (name, description, required_realm, type, max_level, "
                                 + "creator_user_id, player_made, effect_spec) "
                                 + "VALUES (?, '', 0, 'ATTACK', 10, ?, 1, ?)",
                         Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, name);
                ps.setLong(2, playerId);
                ps.setString(3, effects);
                ps.executeUpdate();
                try (var keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        return keys.getLong(1);
                    }
                }
            }
        }
        throw new IllegalStateException("造不出测试功法");
    }

    private void equip(long techniqueId, boolean equipped) throws Exception {
        try (Connection conn = DatabaseManager.getConnection()) {
            try (PreparedStatement del = conn.prepareStatement(
                    "DELETE FROM players_techniques WHERE player_id = ? AND technique_id = ?")) {
                del.setLong(1, playerId);
                del.setLong(2, techniqueId);
                del.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO players_techniques (player_id, technique_id, level, proficiency, is_equipped) "
                            + "VALUES (?, ?, 1, 0, ?)")) {
                ps.setLong(1, playerId);
                ps.setLong(2, techniqueId);
                ps.setBoolean(3, equipped);
                ps.executeUpdate();
            }
        }
    }


}
