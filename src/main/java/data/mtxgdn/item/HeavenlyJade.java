package data.mtxgdn.item;

import com.mtxgdn.game.item.EmptyEffect;
import com.mtxgdn.game.item.Item;
import com.mtxgdn.game.item.ItemType;
import com.mtxgdn.game.item.ItemRarity;

public class HeavenlyJade extends Item {
    public HeavenlyJade() {
        super("mtxgdn", "heavenly_jade", ItemType.MATERIAL, ItemRarity.EPIC,
            99, 5000, true, 2, EmptyEffect.INSTANCE);
    }
}
