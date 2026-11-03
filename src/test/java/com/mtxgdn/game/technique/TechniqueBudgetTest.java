package com.mtxgdn.game.technique;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 悟道预算的纯逻辑测试。
 *
 * <p>这一层不碰数据库，所以能把边界情况全测透 —— 而边界恰恰是
 * 「玩家钻空子」最容易发生的地方。</p>
 */
class TechniqueBudgetTest {

    @Test
    @DisplayName("预算随境界递增，越界时收敛到两端")
    void budgetGrowsWithRealm() {
        double prev = 0;
        for (int realm = 0; realm <= 8; realm++) {
            double b = TechniqueBudget.budgetForRealm(realm);
            assertTrue(b > prev, "境界 " + realm + " 的预算应高于前一境界");
            prev = b;
        }
        assertEquals(TechniqueBudget.budgetForRealm(0), TechniqueBudget.budgetForRealm(-5),
                "负境界按最低档算");
        assertEquals(TechniqueBudget.budgetForRealm(8), TechniqueBudget.budgetForRealm(99),
                "超出最高境界按最高档算，不能无限放大");
    }

    @Test
    @DisplayName("软上限内按原价，超出部分单价翻倍")
    void costDoublesBeyondSoftCap() {
        var stat = TechniqueBudget.Stat.ATTACK;
        int soft = stat.softUnits();

        double withinSoft = TechniqueBudget.costOf(stat, soft);
        assertEquals(stat.baseCost() * soft, withinSoft, 0.001,
                "软上限之内应是线性计价");

        double oneOver = TechniqueBudget.costOf(stat, soft + 1);
        assertEquals(withinSoft + stat.baseCost() * 2, oneOver, 0.001,
                "超出一单位应多付两倍单价");

        // 关键性质：把同一笔预算是分散还是堆叠，代价必须不同
        double stacked = TechniqueBudget.costOf(stat, soft * 2);
        double spread = TechniqueBudget.costOf(TechniqueBudget.Stat.HP, stat.softUnits())
                + TechniqueBudget.costOf(TechniqueBudget.Stat.MP, stat.softUnits());
        assertTrue(stacked > spread, "堆单一属性必须比分散更贵，否则玩家只会全点攻击");
    }

    @Test
    @DisplayName("零分配不花钱")
    void zeroCostsNothing() {
        for (var stat : TechniqueBudget.Stat.values()) {
            assertEquals(0, TechniqueBudget.costOf(stat, 0), 0.001);
            assertEquals(0, TechniqueBudget.costOf(stat, -3), 0.001, "负数当零处理");
        }
    }

    @Test
    @DisplayName("合理的分配能通过")
    void smallPlanPasses() {
        var plan = TechniqueBudget.plan()
                .set(TechniqueBudget.Stat.ATTACK, 5)
                .set(TechniqueBudget.Stat.HP, 20);
        var r = TechniqueBudget.evaluate(0, plan);
        assertTrue(r.ok(), String.valueOf(r.problems()));
        assertTrue(r.cost() <= r.budget());
    }

    @Test
    @DisplayName("超预算被拒绝，且提示里带出具体数字")
    void overBudgetRejected() {
        var plan = TechniqueBudget.plan().set(TechniqueBudget.Stat.ATTACK, 40);
        var r = TechniqueBudget.evaluate(0, plan);
        assertFalse(r.ok());
        assertFalse(r.problems().isEmpty());
        assertTrue(r.problems().get(0).contains("超过"), "提示要说清是超预算: " + r.problems());
        assertTrue(r.cost() > r.budget());
    }

    @Test
    @DisplayName("单项硬上限不能靠预算绕过")
    void hardCapEnforced() {
        // 用最高境界的预算去堆一项，数值上够花，但必须被硬上限挡住
        var stat = TechniqueBudget.Stat.ATTACK;
        var plan = TechniqueBudget.plan().set(stat, stat.maxUnits() + 1);
        var r = TechniqueBudget.evaluate(8, plan);
        assertFalse(r.ok(), "超过单项硬上限必须拒绝，哪怕预算够");
        assertTrue(r.problems().get(0).contains("最多"), String.valueOf(r.problems()));
    }

    @Test
    @DisplayName("空方案被拒绝")
    void emptyPlanRejected() {
        assertFalse(TechniqueBudget.evaluate(0, TechniqueBudget.plan()).ok());
        assertFalse(TechniqueBudget.evaluate(0, null).ok(), "null 不能当合法方案");
    }

    @Test
    @DisplayName("分配 0 或负数等于不分配，不会变成「负预算」")
    void zeroOrNegativeMeansUnset() {
        var plan = TechniqueBudget.plan().set(TechniqueBudget.Stat.HP, 10);
        assertFalse(plan.isEmpty());

        plan.set(TechniqueBudget.Stat.HP, 0);
        assertTrue(plan.isEmpty(), "填 0 应当移除该项");

        plan.set(TechniqueBudget.Stat.ATTACK, -5);
        assertTrue(plan.isEmpty(), "填负数也应当移除 —— 负数若能通过会反向减少消耗");
        assertEquals(0, plan.unitsOf(TechniqueBudget.Stat.ATTACK));
    }

    @Test
    @DisplayName("百分比属性折算正确：1 单位 = 1%")
    void percentConversion() {
        assertEquals(0.01, TechniqueBudget.doubleValue(TechniqueBudget.Stat.CULTIVATION_SPEED, 1), 1e-9);
        var plan = TechniqueBudget.plan().set(TechniqueBudget.Stat.CULTIVATION_SPEED, 5);
        assertEquals(0.05, plan.doubleOf(TechniqueBudget.Stat.CULTIVATION_SPEED), 1e-9);
        // 整型属性走 intOf，百分比属性不该被塞进整型字段
        assertEquals(0, plan.intOf(TechniqueBudget.Stat.CULTIVATION_SPEED));
    }

    @Test
    @DisplayName("整型属性折算正确，且不越界成百分比")
    void intConversion() {
        var plan = TechniqueBudget.plan().set(TechniqueBudget.Stat.ATTACK, 7);
        assertEquals(7, plan.intOf(TechniqueBudget.Stat.ATTACK));
        assertEquals(0, plan.doubleOf(TechniqueBudget.Stat.ATTACK), 1e-9,
                "整型属性不该写进百分比字段，否则 +7 会变成 +700%");
    }

    @Test
    @DisplayName("把所有属性都点满必然超预算 —— 不存在完美功法")
    void cannotMaxEverything() {
        var plan = TechniqueBudget.plan();
        for (var stat : TechniqueBudget.Stat.values()) {
            plan.set(stat, stat.maxUnits());
        }
        var r = TechniqueBudget.evaluate(8, plan);
        assertFalse(r.ok(), "如果点满所有属性还能通过，预算就形同虚设");
    }

    @Test
    @DisplayName("高境界的同一方案消耗不变 —— 消耗只由分配决定")
    void costIndependentOfRealm() {
        var plan = TechniqueBudget.plan().set(TechniqueBudget.Stat.ATTACK, 10);
        double low = TechniqueBudget.evaluate(0, plan).cost();
        double high = TechniqueBudget.evaluate(8, plan).cost();
        assertEquals(low, high, 0.001, "消耗只取决于分配，境界只影响可用预算");
    }
}
