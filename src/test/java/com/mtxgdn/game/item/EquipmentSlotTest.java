package com.mtxgdn.game.item;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 装备栏位归一化的纯逻辑测试。
 *
 * <p>背景：{@code /装备 灵剑 武器} 曾把「武器」原样写进数据库，导致
 * `/已装备`、`/卸下 weapon`、`/强化 weapon` 都找不到这件装备。
 * 栏位必须先归一化成 {@code weapon/armor/accessory}。</p>
 */
class EquipmentSlotTest {

    @Test
    @DisplayName("规范名与中文/英文别名都能归一化")
    void normalizesAliases() {
        for (String input : new String[]{"weapon", "WEAPON", " Weapon ", "武器", "主手", "兵器"}) {
            assertEquals(EquipmentSlot.WEAPON, EquipmentSlot.normalize(input), "输入: " + input);
        }
        for (String input : new String[]{"armor", "Armor", "armour", "防具", "护甲", "衣服"}) {
            assertEquals(EquipmentSlot.ARMOR, EquipmentSlot.normalize(input), "输入: " + input);
        }
        for (String input : new String[]{"accessory", "ACCESSORY", "饰品", "首饰", "配件"}) {
            assertEquals(EquipmentSlot.ACCESSORY, EquipmentSlot.normalize(input), "输入: " + input);
        }
    }

    @Test
    @DisplayName("不认识/空/空白的栏位返回 null（调用方必须拒绝，不能写库）")
    void rejectsUnknownSlots() {
        assertNull(EquipmentSlot.normalize(null));
        assertNull(EquipmentSlot.normalize(""));
        assertNull(EquipmentSlot.normalize("   "));
        assertNull(EquipmentSlot.normalize("头盔"));
        assertNull(EquipmentSlot.normalize("shoe"));
    }

    @Test
    @DisplayName("显示名：规范名与别名都映射到中文")
    void displayNames() {
        assertEquals("武器", EquipmentSlot.displayName("weapon"));
        assertEquals("武器", EquipmentSlot.displayName("武器"));
        assertEquals("防具", EquipmentSlot.displayName("armor"));
        assertEquals("饰品", EquipmentSlot.displayName("accessory"));
        assertEquals("头盔", EquipmentSlot.displayName("头盔"), "不认识的应原样返回");
    }
}
