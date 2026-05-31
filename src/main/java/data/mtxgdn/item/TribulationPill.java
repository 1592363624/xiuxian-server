package data.mtxgdn.item;

import com.mtxgdn.game.item.EmptyEffect;
import com.mtxgdn.game.item.Item;
import com.mtxgdn.game.item.ItemType;
import com.mtxgdn.game.item.ItemRarity;

public class TribulationPill extends Item {
    public TribulationPill() {
        super("mtxgdn", "tribulation_pill", ItemType.CONSUMABLE, ItemRarity.EPIC,
            5, 10000, true, 3, EmptyEffect.INSTANCE);
    }
}
