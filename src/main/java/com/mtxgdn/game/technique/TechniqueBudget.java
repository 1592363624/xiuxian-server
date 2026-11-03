package com.mtxgdn.game.technique;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 自创功法的「悟道预算」模型。
 *
 * <h3>为什么必须有预算</h3>
 * <p>如果让玩家自由填加成数值，结果只有两种：全是废纸，或者一个
 * 「攻击 +9999」的东西把 PvP 和坊市一起击穿。<b>自由不等于没有约束 ——
 * 是约束让不同选择各有代价，多样性才会涌现。</b></p>
 *
 * <p>所以这里给每个境界一个预算池，玩家在池子里分配。分配得当的功法
 * 能与同境界官方功法一较高下，但不可能全面超越 —— 因为总量被卡死。</p>
 *
 * <h3>三重约束</h3>
 * <ol>
 *   <li><b>总量</b>：消耗不得超过境界预算；</li>
 *   <li><b>边际递减</b>：单项超过软上限后，每点价格翻倍 —— 堆单一属性不划算；</li>
 *   <li><b>硬上限</b>：单项绝对最大值，防止在预算内堆出畸形数值。</li>
 * </ol>
 *
 * <h3>数值标定依据</h3>
 * <p>基础属性是 HP 100 / MP 50 / 攻 10 / 防 5 / 速 5（见 {@code PlayerDAO}）。
 * 以官方功法为参照：</p>
 * <ul>
 *   <li>吐纳术（练气级）修速 +5%；</li>
 *   <li>紫霄天诀（天阶）全项小幅全面加成；</li>
 *   <li>鸿蒙紫气经（渡劫级）攻 +25、气血 +150 一类的大幅数值。</li>
 * </ul>
 * <p>预算按境界递增，使「同境界自创功法」约等于中上品阶官方功法，
 * 但明显弱于天阶 —— 玩家自创是<b>补充</b>，不该直接取代官方内容。</p>
 *
 * <p><b>纯逻辑、不碰数据库</b>，便于单测覆盖全部边界。</p>
 */
public final class TechniqueBudget {

    /**
     * 可分配的属性项。
     *
     * <p>{@code step} 是「一个分配单位」折算成实际数值的大小：
     * 整型属性每单位 +1，百分比属性每单位 +0.01（即 1%）。</p>
     */
    public enum Stat {
        /** 气血上限 */
        HP("hp", 4, 1, 600, false),
        /** 法力上限 */
        MP("mp", 4, 1, 600, false),
        /** 攻击 */
        ATTACK("attack", 20, 1, 40, false),
        /** 防御 */
        DEFENSE("defense", 20, 1, 40, false),
        /** 速度 */
        SPEED("speed", 25, 1, 20, false),
        /** 灵力 */
        SPIRIT("spirit", 15, 1, 50, false),
        /** 修炼速度（百分比） */
        CULTIVATION_SPEED("cultivation_speed", 120, 0.01, 0.30, true),
        /** 经验获取（百分比） */
        EXP("exp", 120, 0.01, 0.20, true),
        /** 战斗伤害加成（百分比） */
        COMBAT_DAMAGE("combat_damage", 200, 0.01, 0.15, true),
        /** 伤害减免（百分比） */
        DAMAGE_REDUCTION("damage_reduction", 200, 0.01, 0.15, true);

        private final String key;
        private final double baseCost;
        private final double step;
        private final double hardCap;
        private final boolean percent;

        Stat(String key, double baseCost, double step, double hardCap, boolean percent) {
            this.key = key;
            this.baseCost = baseCost;
            this.step = step;
            this.hardCap = hardCap;
            this.percent = percent;
        }

        public String key() {
            return key;
        }

        public double baseCost() {
            return baseCost;
        }

        public double step() {
            return step;
        }

        /** 单项硬上限（实际数值，不是单位数） */
        public double hardCap() {
            return hardCap;
        }

        public boolean percent() {
            return percent;
        }

        /** 硬上限折算成单位数 */
        public int maxUnits() {
            return (int) Math.floor(hardCap / step);
        }

        /**
         * 软上限：超过它之后单价翻倍。
         *
         * <p>取硬上限的三分之一 —— 这样「堆满一项」只能到达硬上限，
         * 而且要付出远超线性增长的代价，玩家自然会分散投资。</p>
         */
        public int softUnits() {
            return Math.max(1, maxUnits() / 3);
        }
    }

    /** 每个境界的预算池（索引即 realm，0=练气） */
    private static final double[] REALM_BUDGET = {
            500, 700, 1000, 1400, 2000, 2800, 3800, 5200, 7200
    };

    /** 超过软上限后的单价倍数 */
    private static final double OVER_SOFT_MULTIPLIER = 2.0;

    private TechniqueBudget() {
    }

    /** 某境界可用的预算；超出表范围按最高档算（渡劫之上不再加预算） */
    public static double budgetForRealm(int realm) {
        if (realm < 0) {
            return REALM_BUDGET[0];
        }
        return realm >= REALM_BUDGET.length
                ? REALM_BUDGET[REALM_BUDGET.length - 1]
                : REALM_BUDGET[realm];
    }

    /**
     * 某一项的消耗。
     *
     * <p>分段线性：软上限之内按原价，超出部分翻倍。这让「均衡分配」
     * 比「极限堆叠」划算，但又不至于完全禁止专精。</p>
     */
    public static double costOf(Stat stat, int units) {
        if (units <= 0) {
            return 0;
        }
        int soft = stat.softUnits();
        int normal = Math.min(units, soft);
        int over = Math.max(0, units - soft);
        return stat.baseCost() * normal + stat.baseCost() * OVER_SOFT_MULTIPLIER * over;
    }

    /** 单位数换算成整型属性的实际值 */
    public static int intValue(Stat stat, int units) {
        return (int) Math.round(units * stat.step());
    }

    /** 单位数换算成百分比属性的实际值 */
    public static double doubleValue(Stat stat, int units) {
        return units * stat.step();
    }

    /**
     * 评估结果。
     *
     * @param ok       是否通过
     * @param cost     实际消耗
     * @param budget   该境界预算
     * @param problems 不通过的原因（人类可读，会展示给玩家）
     */
    public record Result(boolean ok, double cost, double budget, List<String> problems) {
    }

    /**
     * 分配方案。
     *
     * <p>只记录「每个属性分配了几个单位」，不记录折算后的数值 ——
     * 避免出现「玩家传了 attack=7 但 step=1 时含义不明」这种歧义。</p>
     */
    public static final class Plan {
        private final Map<Stat, Integer> units = new EnumMap<>(Stat.class);

        public Plan set(Stat stat, int unitCount) {
            if (unitCount <= 0) {
                units.remove(stat);
            } else {
                units.put(stat, unitCount);
            }
            return this;
        }

        public int unitsOf(Stat stat) {
            return units.getOrDefault(stat, 0);
        }

        public Map<Stat, Integer> asMap() {
            return Map.copyOf(units);
        }

        public boolean isEmpty() {
            return units.isEmpty();
        }

        /** 非百分比属性的实际数值；百分比属性返回 0（用 {@link #doubleOf}） */
        public int intOf(Stat stat) {
            return stat.percent() ? 0 : intValue(stat, unitsOf(stat));
        }

        public double doubleOf(Stat stat) {
            return stat.percent() ? doubleValue(stat, unitsOf(stat)) : 0;
        }
    }

    public static Plan plan() {
        return new Plan();
    }

    /**
     * 校验一个分配方案。
     *
     * <p>这是<b>服务端唯一可信的判断</b> —— 前端可以实时估算给玩家看，
     * 但最终必须由这里裁决，绝不能相信客户端算好的结果。</p>
     */
    public static Result evaluate(int realm, Plan plan) {
        List<String> problems = new ArrayList<>();
        double budget = budgetForRealm(realm);
        double total = 0;

        if (plan == null || plan.isEmpty()) {
            problems.add("至少要分配一项属性");
            return new Result(false, 0, budget, problems);
        }

        for (Map.Entry<Stat, Integer> e : plan.asMap().entrySet()) {
            Stat stat = e.getKey();
            int units = e.getValue();

            if (units < 0) {
                problems.add(stat.key() + " 的分配量不能为负");
                continue;
            }
            if (units > stat.maxUnits()) {
                problems.add(String.format("%s 最多 %s，你填了 %s",
                        stat.key(), formatValue(stat, stat.maxUnits()), formatValue(stat, units)));
            }
            total += costOf(stat, units);
        }

        if (total > budget) {
            problems.add(String.format("消耗 %.0f 点，超过本境界预算 %.0f 点", total, budget));
        }

        return new Result(problems.isEmpty(), total, budget, problems);
    }

    /** 把单位数格式化成玩家看得懂的样子（百分比属性显示成 %） */
    public static String formatValue(Stat stat, int units) {
        if (stat.percent()) {
            return String.format("%.0f%%", doubleValue(stat, units) * 100);
        }
        return String.valueOf(intValue(stat, units));
    }
}
