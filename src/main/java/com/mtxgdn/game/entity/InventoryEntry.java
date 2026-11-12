package com.mtxgdn.game.entity;

import com.mtxgdn.api.ItemApi;
import com.mtxgdn.common.service.ServiceRegistry;
import com.mtxgdn.db.DatabaseManager;
import com.mtxgdn.db.ItemDAO;
import com.mtxgdn.entity.Player;
import com.mtxgdn.game.item.BuffEffect;
import com.mtxgdn.game.item.CurrencyEffect;
import com.mtxgdn.game.item.EquipResult;
import com.mtxgdn.game.item.ExchangeResult;
import com.mtxgdn.game.item.InventoryCapacityEffect;
import com.mtxgdn.game.item.Item;
import com.mtxgdn.game.item.ItemEffect;
import com.mtxgdn.game.item.ItemRegistry;
import com.mtxgdn.game.item.UseItemResult;
import com.mtxgdn.i18n.I18n;
import com.mtxgdn.util.AppConfig;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
