package com.mtxgdn.security;

import com.mtxgdn.TestBase;
import com.mtxgdn.db.DatabaseManager;
import com.mtxgdn.util.PageRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 分页参数夹取的回归测试。
 *
 * <p><b>攻击形态</b>：{@code /api/game/players}、{@code /players/search}、
 * {@code /game/rank} 三个接口的 {@code limit} / {@code offset} 原本直接透传到
 * {@code LIMIT ? OFFSET ?}，没有任何夹取。攻击者可以：</p>
 *
 * <ul>
 *   <li>{@code limit=2147483647} —— 一次拉走全表，每行都要走 ResultSet
 *       读取 → 对象构造 → JSON 序列化，1 核机器上足以拖垮进程；</li>
 *   <li>{@code limit=-1} —— <b>不报错</b>，因为 SQLite 把负 LIMIT 解释为
 *       「不限制条数」，效果同样是全表；</li>
 *   <li>{@code offset=2000000000} —— 返回空集，但数据库仍要扫描/跳过
 *       这么多行，纯 CPU 消耗。</li>
 * </ul>
 *
 * <p>{@link PageRequest} 把规则集中到一处，本测试既验证夹取逻辑本身，
 * 也用真实 SQLite 验证「负数 LIMIT 确实等于全表」这个前提成立 ——
 * 否则修复的理由就站不住。</p>
 */
class PageRequestTest extends TestBase {

    // ==================== 夹取语义 ====================

    @Test
    @DisplayName("limit 为负数时回落到默认值（负数在 SQLite 里等于不限条数）")
    void negativeLimitFallsBackToDefault() {
        assertEquals(PageRequest.DEFAULT_LIMIT, PageRequest.clampLimit(-1));
        assertEquals(PageRequest.DEFAULT_LIMIT, PageRequest.clampLimit(Integer.MIN_VALUE));
    }

    @Test
    @DisplayName("limit 为 0 时回落到默认值")
    void zeroLimitFallsBackToDefault() {
        assertEquals(PageRequest.DEFAULT_LIMIT, PageRequest.clampLimit(0));
    }

    @Test
    @DisplayName("超大 limit 必须被夹到上限（不能一次拉全表）")
    void hugeLimitClampedToMax() {
        assertEquals(PageRequest.MAX_LIMIT, PageRequest.clampLimit(Integer.MAX_VALUE));
        assertEquals(PageRequest.MAX_LIMIT, PageRequest.clampLimit(1_000_000));
        assertEquals(PageRequest.MAX_LIMIT, PageRequest.clampLimit(PageRequest.MAX_LIMIT + 1));
    }

    @Test
    @DisplayName("合法 limit 原样保留")
    void validLimitPreserved() {
        assertEquals(20, PageRequest.clampLimit(20));
        assertEquals(1, PageRequest.clampLimit(1));
        assertEquals(PageRequest.MAX_LIMIT, PageRequest.clampLimit(PageRequest.MAX_LIMIT));
    }

    @Test
    @DisplayName("负 offset 必须夹到 0，正 offset 原样保留")
    void offsetClamped() {
        assertEquals(0, PageRequest.clampOffset(-1));
        assertEquals(0, PageRequest.clampOffset(Integer.MIN_VALUE));
        assertEquals(0, PageRequest.clampOffset(0));
        assertEquals(40, PageRequest.clampOffset(40));
    }

    @Test
    @DisplayName("夹取结果永远落在安全区间内（对任意输入都不越界）")
    void alwaysWithinSafeRange() {
        int[] probes = {Integer.MIN_VALUE, -1000, -1, 0, 1, 20, 100, 101,
                999_999, Integer.MAX_VALUE};
        for (int p : probes) {
            int limit = PageRequest.clampLimit(p);
            assertTrue(limit > 0, "limit 必须为正，输入 " + p + " 得到 " + limit);
            assertTrue(limit <= PageRequest.MAX_LIMIT,
                    "limit 不得超过上限，输入 " + p + " 得到 " + limit);
            assertTrue(PageRequest.clampOffset(p) >= 0,
                    "offset 必须非负，输入 " + p);
        }
    }

    @Test
    @DisplayName("自定义上限同样被尊重")
    void customMaxRespected() {
        assertEquals(10, PageRequest.clampLimit(9999, 5, 10));
        assertEquals(5, PageRequest.clampLimit(-1, 5, 10));
    }

    // ==================== 前提验证：负数 LIMIT 真的等于全表 ====================

    @Test
    @DisplayName("前提验证：SQLite 中 LIMIT -1 会返回全部行（这正是要夹取的原因）")
    void sqliteNegativeLimitMeansUnlimited() throws Exception {
        try (Connection conn = DatabaseManager.getConnection()) {
            int total = countAll(conn);
            assertTrue(total >= 0, "至少要能数出行数");

            assertEquals(total, countWithLimit(conn, -1),
                    "SQLite 把负 LIMIT 当作「不限制条数」—— 这就是必须夹取的前提");
        }
    }

    @Test
    @DisplayName("前提验证：夹取后的 limit 不会超过上限，且不会被当成不限条数")
    void clampedLimitNeverUnbounded() throws Exception {
        try (Connection conn = DatabaseManager.getConnection()) {
            int total = countAll(conn);
            int safe = PageRequest.clampLimit(Integer.MAX_VALUE);
            int got = countWithLimit(conn, safe);

            assertTrue(safe > 0, "夹取后必须为正（负数会被当成不限条数）");
            assertTrue(got <= safe, "返回行数不得超过夹取后的 limit，实际 " + got);
            if (total > safe) {
                assertEquals(safe, got, "表行数大于上限时，应恰好返回上限条数");
            }
        }
    }

    private static int countAll(Connection conn) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM players");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private static int countWithLimit(Connection conn, int limit) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM players LIMIT ?")) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                int n = 0;
                while (rs.next()) n++;
                return n;
            }
        }
    }
}
