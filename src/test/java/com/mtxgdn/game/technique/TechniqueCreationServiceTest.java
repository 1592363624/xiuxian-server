package com.mtxgdn.game.technique;

import com.mtxgdn.TestBase;
import com.mtxgdn.db.DatabaseManager;
import com.mtxgdn.entity.Player;
import com.mtxgdn.game.service.TechniqueService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 自创功法的服务层测试（含真实 SQLite）。
 *
 * <p>重点不在「能不能造出来」，而在<b>边界与越权</b>：
 * 重名、冒充、超预算、数量上限、删别人的。这些是玩家一定会试的地方。</p>
 */
class TechniqueCreationServiceTest extends TestBase {

    private static final long USER_A = 90001L;
    private static final long USER_B = 90002L;

    private TechniqueCreationService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new TechniqueCreationService();
        cleanUp();
    }

    @AfterEach
    void tearDown() throws Exception {
        cleanUp();
    }

    /** 删掉测试造的功法，避免污染其它测试对 techniques 表的计数 */
    private void cleanUp() throws Exception {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM techniques WHERE creator_user_id IN (?, ?)")) {
            ps.setLong(1, USER_A);
            ps.setLong(2, USER_B);
            ps.executeUpdate();
        }
    }

    /**
     * 造一个只带 id 与境界的 Player。
     *
     * <p>不用 Mockito mock —— 本机是 Java 25，Byte Buddy 只支持到 22，
     * 代理具体类会直接抛 UnsupportedClassVersion。Player 本身有 setter 且非 final，
     * 直接 new 一个更省事也更真实。</p>
     */
    private Player player(long id, int realm) {
        Player p = new Player();
        p.setId(id);
        p.setRealm(realm);
        return p;
    }

    private TechniqueCreationService.Request req(String name) {
        return new TechniqueCreationService.Request(name, "自创", "ATTACK",
                TechniqueBudget.plan()
                        .set(TechniqueBudget.Stat.ATTACK, 3)
                        .set(TechniqueBudget.Stat.HP, 10));
    }

    @Test
    @DisplayName("正常创造：落库成功并返回 id")
    void createsSuccessfully() {
        var r = service.create(player(USER_A, 8), req("test_自创剑法"));
        assertTrue(r.success(), r.message());
        assertTrue(r.techniqueId() > 0);
        assertTrue(r.cost() > 0);
    }

    @Test
    @DisplayName("造出来的功法能被现有功法系统读到 —— 复用通路的关键验证")
    void createdTechniqueIsVisibleToExistingPipeline() {
        var r = service.create(player(USER_A, 8), req("test_可见性验证"));
        assertTrue(r.success(), r.message());

        var all = new TechniqueService().getAllTechniques();
        boolean found = all.stream().anyMatch(t -> t.getId() == r.techniqueId());
        assertTrue(found,
                "自创功法必须出现在 getAllTechniques 里，否则玩家学不到 —— "
                        + "它和官方功法是同一张表、同一套通路");
    }

    @Test
    @DisplayName("重名会被拒绝 —— 包括别人已占用的名字")
    void rejectsNameCollision() {
        // 不依赖"测试库里有官方功法"这个前提：先自己造一门，再撞名。
        // 这同时覆盖了「玩家之间抢注」这个更常见的场景。
        assertTrue(service.create(player(USER_A, 8), req("test_撞名目标")).success());

        var sameNameOtherPlayer = service.create(player(USER_B, 8), req("test_撞名目标"));
        assertFalse(sameNameOtherPlayer.success(), "别人占用的名字不能再造");
        assertTrue(sameNameOtherPlayer.message().contains("同名"), sameNameOtherPlayer.message());

        var sameNameSelf = service.create(player(USER_A, 8), req("test_撞名目标"));
        assertFalse(sameNameSelf.success(), "自己也不能重复用同一个名字");
    }

    @Test
    @DisplayName("名字里带「官方」这类字眼会被拒绝")
    void rejectsImpersonationWords() {
        var r = service.create(player(USER_A, 8), req("官方特供剑法"));
        assertFalse(r.success(), "允许这种名字等于给骗号开门");
        assertTrue(r.message().contains("官方"), r.message());
    }

    @Test
    @DisplayName("超预算被拒绝，且不产生任何库记录")
    void rejectsOverBudget() throws Exception {
        var plan = TechniqueBudget.plan().set(TechniqueBudget.Stat.ATTACK, 40);
        var r = service.create(player(USER_A, 0),
                new TechniqueCreationService.Request("test_超预算", "", "ATTACK", plan));

        assertFalse(r.success());
        assertTrue(r.cost() > r.budget(), "应回报超了多少，供前端提示");
        assertEquals(0, countOwned(), "被拒绝的方案不能留下半条记录");
    }

    @Test
    @DisplayName("每人最多三门，第四门被拒绝")
    void enforcesPerPlayerLimit() {
        assertTrue(service.create(player(USER_A, 8), req("test_第一门")).success());
        assertTrue(service.create(player(USER_A, 8), req("test_第二门")).success());
        assertTrue(service.create(player(USER_A, 8), req("test_第三门")).success());

        var fourth = service.create(player(USER_A, 8), req("test_第四门"));
        assertFalse(fourth.success());
        assertTrue(fourth.message().contains("最多"), fourth.message());
    }

    @Test
    @DisplayName("只能删自己的功法")
    void cannotDeleteOthers() {
        var mine = service.create(player(USER_A, 8), req("test_归属验证"));
        assertTrue(mine.success());

        var byOther = service.delete(player(USER_B, 8), mine.techniqueId());
        assertFalse(byOther.success(), "别的玩家不能删我的功法");

        var byOwner = service.delete(player(USER_A, 8), mine.techniqueId());
        assertTrue(byOwner.success());
    }

    @Test
    @DisplayName("名字长度与空方案被拒绝")
    void rejectsBadInput() {
        assertFalse(service.create(player(USER_A, 8), req("单")).success(), "一个字的法名");
        assertFalse(service.create(player(USER_A, 8), req("x".repeat(20))).success(), "过长");

        var empty = new TechniqueCreationService.Request("test_空方案", "", "ATTACK",
                TechniqueBudget.plan());
        assertFalse(service.create(player(USER_A, 8), empty).success());
    }

    @Test
    @DisplayName("非法类型被拒绝")
    void rejectsUnknownType() {
        var r = service.create(player(USER_A, 8),
                new TechniqueCreationService.Request("test_类型", "", "GODLIKE",
                        TechniqueBudget.plan().set(TechniqueBudget.Stat.ATTACK, 2)));
        assertFalse(r.success());
    }

    @Test
    @DisplayName("列表只返回自己造的")
    void listsOnlyOwn() {
        assertTrue(service.create(player(USER_A, 8), req("test_我的")).success());
        assertTrue(service.create(player(USER_B, 8), req("test_别人的")).success());

        assertEquals(1, service.listMine(player(USER_A, 8)).size());
        assertEquals(1, service.listMine(player(USER_B, 8)).size());
    }

    private int countOwned() throws Exception {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM techniques WHERE creator_user_id = ?")) {
            ps.setLong(1, USER_A);
            try (var rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }
}
