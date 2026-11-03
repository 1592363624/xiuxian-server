package com.mtxgdn.game.technique;

/**
 * 词条词汇表（L2 词条式创造）。
 *
 * <h3>这是"自由"的真正来源</h3>
 * <p>L1 只能调数值 —— 玩家做出来的功法彼此差别只是数字大小。词条不一样：
 * 它给的是<b>机制</b>。同样花一笔预算，"破防 20%"和"背水 25%"导向完全不同的打法，
 * 而这两种打法都可能是对的。</p>
 *
 * <p>每加一个词条，玩家的组合空间是<b>组合级</b>增长的 —— 这是所有玩法里
 * 投入产出比最高的一种。</p>
 *
 * <h3>为什么词条必须是白名单</h3>
 * <p>词条是数据，不是代码。玩家只能在 {@link #values()} 里挑，参数被限制在
 * 上下限之间。这样"创造"的表达力来自组合，而不是来自任意性 ——
 * 前者能配平，后者必然崩坏。</p>
 *
 * <h3>消耗与 L1 共用同一个预算池</h3>
 * <p>词条和数值抢同一笔预算，所以玩家必须在"面板更强"和"多一个机制"之间取舍。
 * 如果分开给两笔预算，最优解永远是两边都点满，权衡就消失了。</p>
 */
public enum TechniqueEffect {

    /** 破防：无视对方一部分防御 */
    ARMOR_PEN("armor_pen", "破防", "攻击时无视对方部分防御", 60, 30),

    /** 锐意：提高暴击率 */
    CRIT_BONUS("crit_bonus", "锐意", "提高暴击率", 80, 20),

    /** 战意：提高造成的伤害 */
    DAMAGE_AMP("damage_amp", "战意", "提高造成的最终伤害", 100, 20),

    /** 护体：减少受到的伤害 */
    GUARD("guard", "护体", "减少受到的伤害", 100, 20),

    /**
     * 背水：血量越低，伤害越高。
     *
     * <p>上限按"满打满算"给，实际收益取决于玩家的血量控制 ——
     * 这类"有条件才生效"的词条单位价格更低，因为它不是白拿的。</p>
     */
    LAST_STAND("last_stand", "背水", "气血越低，造成的伤害越高", 70, 25);

    /** 每个单位 = 1% */
    public static final double UNIT = 0.01;

    private final String key;
    private final String displayName;
    private final String description;
    private final double costPerUnit;
    private final int maxUnits;

    TechniqueEffect(String key, String displayName, String description,
                    double costPerUnit, int maxUnits) {
        this.key = key;
        this.displayName = displayName;
        this.description = description;
        this.costPerUnit = costPerUnit;
        this.maxUnits = maxUnits;
    }

    public String key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    /** 每 1% 消耗的预算点数 */
    public double costPerUnit() {
        return costPerUnit;
    }

    /** 最多能点几单位（1 单位 = 1%） */
    public int maxUnits() {
        return maxUnits;
    }

    /** 单位数换算成实际百分比（0.01 = 1%） */
    public double valueOf(int units) {
        return units * UNIT;
    }

    /** 按 key 查词条；不认识返回 null（调用方负责拒绝） */
    public static TechniqueEffect byKey(String key) {
        if (key == null) {
            return null;
        }
        for (TechniqueEffect e : values()) {
            if (e.key.equals(key)) {
                return e;
            }
        }
        return null;
    }
}
