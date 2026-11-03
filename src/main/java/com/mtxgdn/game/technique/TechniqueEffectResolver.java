package com.mtxgdn.game.technique;

import com.mtxgdn.db.PlayerTechniqueDAO;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 把玩家已装备功法的词条聚合成一组战斗修正值。
 *
 * <h3>为什么需要聚合 + 缓存</h3>
 * <p>词条分散在多门功法上（每人最多 3 门自创 + 若干官方功法），而伤害计算
 * <b>每回合都要问一次</b>。直接每次查库会让一场几十回合的战斗产生上百次查询。</p>
 *
 * <p>所以这里做两层：先把所有已装备功法聚成一个不可变对象，再对它做短 TTL 缓存。
 * 战斗通常几秒内结束，30 秒缓存意味着<b>一场战斗只读一次库</b>。</p>
 *
 * <h3>安全性的关键性质：默认全零</h3>
 * <p>官方功法的 {@code effect_spec} 是空串，聚合结果就是 {@link Aggregate#EMPTY}，
 * 所有修正值为 0。战斗公式乘上 {@code 1 + 0} 与原来<b>逐位相同</b> ——
 * 这就是「加这个功能不会改变现有战斗结果」的保证。</p>
 *
 * <h3>为什么要对聚合结果再设上限</h3>
 * <p>单门功法的词条有上限，但玩家可以装备多门。不设总上限的话，
 * 3 门各 30% 破防叠起来就是 90%，防御形同虚设。<b>单件有上限不等于总量有上限。</b></p>
 */
public final class TechniqueEffectResolver {

    /** 缓存存活时间：够覆盖一场战斗，又不至于让换功法后太久不生效 */
    private static final long CACHE_TTL_MS = 30_000;

    /** 聚合后的总上限 —— 单门的上限不能直接当总量的上限 */
    private static final double CAP_ARMOR_PEN = 0.45;
    private static final double CAP_CRIT_BONUS = 0.30;
    private static final double CAP_DAMAGE_AMP = 0.30;
    private static final double CAP_GUARD = 0.30;
    private static final double CAP_LAST_STAND = 0.40;

    /**
     * 聚合结果。
     *
     * @param armorPen  无视防御的比例
     * @param critBonus 暴击率加成
     * @param damageAmp 造成伤害的加成
     * @param guard     受到伤害的减免
     * @param lastStand 气血越低伤害越高的最大加成
     */
    public record Aggregate(double armorPen, double critBonus, double damageAmp,
                            double guard, double lastStand) {

        public static final Aggregate EMPTY = new Aggregate(0, 0, 0, 0, 0);

        public boolean isZero() {
            return armorPen == 0 && critBonus == 0 && damageAmp == 0 && guard == 0 && lastStand == 0;
        }

        /**
         * 背水带来的实际增伤系数。
         *
         * <p>满血时为 1（无加成），气血越低越接近 {@code 1 + lastStand}。
         * 血量比例未知时按满血处理 —— 宁可少给加成，也不能凭空多给。</p>
         */
        public double lastStandMultiplier(double hpRatio) {
            if (lastStand <= 0) {
                return 1.0;
            }
            // 越界或 NaN 一律按满血处理：数据异常不该变成战斗优势。
            // 注意 NaN 的任何比较都是 false，所以这里必须用「在区间内」的正向判断。
            if (!(hpRatio >= 0 && hpRatio <= 1)) {
                return 1.0;
            }
            return 1.0 + lastStand * (1 - hpRatio);
        }

        /** 综合增伤系数：战意 + 背水 */
        public double attackMultiplier(double hpRatio) {
            return (1 + damageAmp) * lastStandMultiplier(hpRatio);
        }

        /** 受伤系数：护体 */
        public double incomingMultiplier() {
            return 1 - guard;
        }
    }

    private static final class Entry {
        final long expiresAt;
        final Aggregate value;

        Entry(long expiresAt, Aggregate value) {
            this.expiresAt = expiresAt;
            this.value = value;
        }
    }

    private static final Map<Long, Entry> CACHE = new ConcurrentHashMap<>();
    private static final PlayerTechniqueDAO DAO = new PlayerTechniqueDAO();

    private TechniqueEffectResolver() {
    }

    /** 取某玩家的聚合词条；任何异常都退化为「无词条」，绝不因词条影响战斗可用性 */
    public static Aggregate forPlayer(Long playerId) {
        if (playerId == null) {
            return Aggregate.EMPTY;
        }
        long now = System.currentTimeMillis();
        Entry cached = CACHE.get(playerId);
        if (cached != null && cached.expiresAt > now) {
            return cached.value;
        }

        Aggregate agg = load(playerId);
        CACHE.put(playerId, new Entry(now + CACHE_TTL_MS, agg));
        return agg;
    }

    /**
     * 清掉某玩家的缓存。
     *
     * <p>装备/卸下/删除功法后应当调用 —— 否则最多 30 秒内词条还是旧的。
     * 对战斗来说这个延迟可以接受，但让调用方能主动清是对的。</p>
     */
    public static void invalidate(Long playerId) {
        if (playerId != null) {
            CACHE.remove(playerId);
        }
    }

    /** 测试用：清空全部缓存 */
    public static void clearCache() {
        CACHE.clear();
    }

    private static Aggregate load(long playerId) {
        List<String> specs;
        try {
            specs = DAO.listEquippedEffectSpecs(playerId);
        } catch (SQLException | RuntimeException e) {
            // 词条是锦上添花，读失败不该让玩家打不了架
            return Aggregate.EMPTY;
        }
        if (specs.isEmpty()) {
            return Aggregate.EMPTY;
        }

        double armorPen = 0;
        double critBonus = 0;
        double damageAmp = 0;
        double guard = 0;
        double lastStand = 0;

        for (String raw : specs) {
            TechniqueEffectSpec.ParseResult parsed = TechniqueEffectSpec.parse(raw);
            if (!parsed.ok()) {
                // 库里的数据理应合法；不合法就跳过这一门，而不是让整场战斗失败
                continue;
            }
            TechniqueEffectSpec spec = parsed.spec();
            armorPen += spec.valueOf(TechniqueEffect.ARMOR_PEN);
            critBonus += spec.valueOf(TechniqueEffect.CRIT_BONUS);
            damageAmp += spec.valueOf(TechniqueEffect.DAMAGE_AMP);
            guard += spec.valueOf(TechniqueEffect.GUARD);
            lastStand += spec.valueOf(TechniqueEffect.LAST_STAND);
        }

        return new Aggregate(
                Math.min(armorPen, CAP_ARMOR_PEN),
                Math.min(critBonus, CAP_CRIT_BONUS),
                Math.min(damageAmp, CAP_DAMAGE_AMP),
                Math.min(guard, CAP_GUARD),
                Math.min(lastStand, CAP_LAST_STAND));
    }
}
