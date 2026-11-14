package com.mtxgdn.game.entity;

import com.mtxgdn.game.item.Item;

/**
 * 由 {ItemService} 的内部嵌套类提取而来。
 * <p>原来写成 {$svc}.{$nest}，接口无法在不 import 服务实现类的前提下引用它，
 * 因此提到 entity 包 —— 它本来就是纯数据，和服务的生命周期无关。</p>
 */
public class InventoryEntry {
    private final Item item;
    private final long quantity;

    public InventoryEntry(Item item, long quantity) {
        this.item = item;
        this.quantity = quantity;
    }

    public Item getItem() {
        return item;
    }

    public long getQuantity() {
        return quantity;
    }
}
