package data.mtxgdn.item;

import com.mtxgdn.game.item.EmptyEffect;
import com.mtxgdn.game.item.Item;
import com.mtxgdn.game.item.ItemType;
import com.mtxgdn.game.item.ItemRarity;

public class BeastCore extends Item {
    public BeastCore() {
        super("mtxgdn", "beast_core", ItemType.MATERIAL, ItemRarity.UNCOMMON,
            999, 60, true, 1, EmptyEffect.INSTANCE);
    }
}
