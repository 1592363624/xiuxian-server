package com.mtxgdn.game.item;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 装备栏位（{@code weapon} / {@code armor} / {@code accessory}）的唯一规范名。
 *
 * <h3>为什么需要这个类</h3>
 * <p>装备栏位在代码里有三处"命名"来源：{@code players_equipment.slot} 列、
 * 指令参数（玩家可能打 {@code 武器} 或 {@code weapon}）、以及显示名。
 * 之前 {@code equipItem} <b>不校验也不归一化</b> slot —— 玩家打
 * {@code /装备 灵剑 武器} 会原样把 "武器" 写进数据库：物品从背包扣掉了，
 * 但 {@code /已装备}、{@code /卸下 weapon}、{@code /强化 weapon} 全按
 * {@code weapon} 查，于是表现为"装备凭空消失、还卸不下来"。</p>
 *
 * <p>所以栏位必须<b>先过这里</b>：写库只写规范名，读库也按规范名比较。</p>
 */
public final class EquipmentSlot {

    public static final String WEAPON = "weapon";
    public static final String ARMOR = "armor";
    public static final String ACCESSORY = "accessory";

    /** 规范名（固定顺序，等价于旧 {@code DurabilityService.SLOTS}）。 */
    public static final String[] CANONICAL = {WEAPON, ARMOR, ACCESSORY};

    /** 与规范名一一对应的显示名。 */
    private static final String[] DISPLAY_NAMES = {"武器", "防具", "饰品"};

    /** 允许的输入 → 规范名。key 已小写化。 */
    private static final Map<String, String> ALIASES = new LinkedHashMap<>();

    static {
        register(WEAPON, "武器", "主手", "兵器", "weapon");
        register(ARMOR, "防具", "护甲", "衣服", "armor", "armour");
        register(ACCESSORY, "饰品", "首饰", "配件", "accessory");
    }

    private static void register(String canonical, String... aliases) {
        ALIASES.put(canonical, canonical);
        for (String alias : aliases) {
            ALIASES.put(alias.toLowerCase(Locale.ROOT), canonical);
        }
    }

    private EquipmentSlot() {
    }

    /**
     * 把玩家输入/库里的栏位名归一化成规范名。
     *
     * @return {@code weapon} / {@code armor} / {@code accessory}；不认识返回 {@code null}
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String key = raw.trim().toLowerCase(Locale.ROOT);
        if (key.isEmpty()) {
            return null;
        }
        return ALIASES.get(key);
    }

    /** 规范名（或任意别名）对应的中文显示名；不认识的原样返回。 */
    public static String displayName(String slot) {
        String canonical = normalize(slot);
        for (int i = 0; i < CANONICAL.length; i++) {
            if (CANONICAL[i].equals(canonical)) {
                return DISPLAY_NAMES[i];
            }
        }
        return slot;
    }

    /** 给玩家看的合法栏位提示。 */
    public static String usageHint() {
        return "weapon(武器) / armor(防具) / accessory(饰品)";
    }
}
