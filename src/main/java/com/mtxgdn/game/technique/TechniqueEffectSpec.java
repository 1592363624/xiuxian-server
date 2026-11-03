package com.mtxgdn.game.technique;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 一门功法携带的词条配置。
 *
 * <h3>存储格式</h3>
 * <p>{@code key:单位;key:单位}，例如 {@code armor_pen:15;crit_bonus:10}。
 * 单位是整数，1 单位 = 1%。</p>
 *
 * <p><b>为什么不用 JSON</b>：这个结构只有「若干 [词条, 整数] 对」一种形态，
 * 引一个解析器进来只为了读它，等于把「格式错误」变成「解析器行为」。
 * 紧凑格式校验逻辑短到可以一眼看完 —— 而这里恰恰是最不该出现意外的地方。</p>
 *
 * <p><b>为什么存 key 而不是序号</b>：序号取决于枚举顺序，将来插入一个新词条
 * 就会让所有老功法的词条<b>整体错位</b>（原本的破防突然变成暴击）。
 * key 是稳定标识，加词条不影响存量数据。</p>
 */
public final class TechniqueEffectSpec {

    /** 一门功法最多带几个词条 —— 不限制的话最优解就是全带上 */
    public static final int MAX_EFFECTS_PER_TECHNIQUE = 3;

    /**
     * 互斥组：同一组里只能选一个。
     *
     * <p>「战意」与「护体」互斥是有意的：如果一门功法既能大幅增伤又能大幅减伤，
     * 它就成了没有弱点的通用解，其它搭配全部失去意义。
     * <b>互斥制造选择，选择才有流派。</b></p>
     */
    private static final List<List<TechniqueEffect>> EXCLUSIVE_GROUPS = List.of(
            List.of(TechniqueEffect.DAMAGE_AMP, TechniqueEffect.GUARD)
    );

    private final Map<TechniqueEffect, Integer> units;

    private TechniqueEffectSpec(Map<TechniqueEffect, Integer> units) {
        this.units = units;
    }

    public static TechniqueEffectSpec empty() {
        return new TechniqueEffectSpec(new EnumMap<>(TechniqueEffect.class));
    }

    /** 所有词条的总预算消耗 */
    public double cost() {
        double sum = 0;
        for (Map.Entry<TechniqueEffect, Integer> e : units.entrySet()) {
            sum += e.getKey().costPerUnit() * e.getValue();
        }
        return sum;
    }

    public boolean isEmpty() {
        return units.isEmpty();
    }

    public Map<TechniqueEffect, Integer> asMap() {
        return Collections.unmodifiableMap(units);
    }

    /** 某项词条的百分比数值；没有该词条返回 0 */
    public double valueOf(TechniqueEffect effect) {
        Integer u = units.get(effect);
        return u == null ? 0 : effect.valueOf(u);
    }

    public boolean has(TechniqueEffect effect) {
        return units.containsKey(effect);
    }

    /** 序列化成存储格式；空则返回空串 */
    public String toStorageString() {
        if (units.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<TechniqueEffect, Integer> e : units.entrySet()) {
            if (sb.length() > 0) {
                sb.append(';');
            }
            sb.append(e.getKey().key()).append(':').append(e.getValue());
        }
        return sb.toString();
    }

    /**
     * 解析结果。
     *
     * @param spec     解析成功时的规格；失败为 {@link #empty()}
     * @param problems 失败原因（人类可读，直接展示给玩家）
     */
    public record ParseResult(TechniqueEffectSpec spec, List<String> problems) {
        public boolean ok() {
            return problems.isEmpty();
        }
    }

    /**
     * 解析玩家提交的词条配置。
     *
     * <p>凡是「看不懂的东西」一律拒绝而不是忽略：忽略会让玩家以为自己选了
     * 某个词条，实际没生效，而服务端日志里什么都没有。</p>
     */
    public static ParseResult parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return new ParseResult(empty(), List.of());
        }

        Map<TechniqueEffect, Integer> parsed = new EnumMap<>(TechniqueEffect.class);
        List<String> problems = new ArrayList<>();

        for (String token : raw.split(";")) {
            String t = token.trim();
            if (t.isEmpty()) {
                continue;
            }
            int idx = t.lastIndexOf(':');
            if (idx <= 0 || idx == t.length() - 1) {
                problems.add("词条格式不正确：" + t);
                continue;
            }
            String key = t.substring(0, idx).trim();
            String numText = t.substring(idx + 1).trim();

            TechniqueEffect effect = TechniqueEffect.byKey(key);
            if (effect == null) {
                problems.add("不认识的词条：" + key);
                continue;
            }
            if (parsed.containsKey(effect)) {
                problems.add("词条重复：" + effect.displayName());
                continue;
            }

            int u;
            try {
                u = Integer.parseInt(numText);
            } catch (NumberFormatException e) {
                problems.add("词条数值不是整数：" + t);
                continue;
            }
            if (u <= 0) {
                problems.add(effect.displayName() + " 的数值必须大于 0");
                continue;
            }
            if (u > effect.maxUnits()) {
                problems.add(effect.displayName() + " 最多 " + effect.maxUnits() + "%，你填了 " + u + "%");
                continue;
            }
            parsed.put(effect, u);
        }

        if (parsed.size() > MAX_EFFECTS_PER_TECHNIQUE) {
            problems.add("一门功法最多 " + MAX_EFFECTS_PER_TECHNIQUE + " 个词条");
        }

        for (List<TechniqueEffect> group : EXCLUSIVE_GROUPS) {
            long selected = group.stream().filter(parsed::containsKey).count();
            if (selected > 1) {
                StringBuilder names = new StringBuilder();
                for (TechniqueEffect e : group) {
                    if (parsed.containsKey(e)) {
                        if (names.length() > 0) {
                            names.append("、");
                        }
                        names.append(e.displayName());
                    }
                }
                problems.add(names + " 不能同时选择");
            }
        }

        if (!problems.isEmpty()) {
            return new ParseResult(empty(), problems);
        }
        return new ParseResult(new TechniqueEffectSpec(parsed), List.of());
    }
}
