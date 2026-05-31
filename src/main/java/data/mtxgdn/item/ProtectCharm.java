package data.mtxgdn.item;

import com.mtxgdn.game.item.EmptyEffect;
import com.mtxgdn.game.item.Item;
import com.mtxgdn.game.item.ItemType;
import com.mtxgdn.game.item.ItemRarity;

public class ProtectCharm extends Item {
    public ProtectCharm() {
        super("mtxgdn", "protect_charm", ItemType.CONSUMABLE, ItemRarity.RARE,
            10, 2000, true, 1, EmptyEffect.INSTANCE);
    }
}
