package data.mtxgdn.item;

import com.mtxgdn.game.item.EmptyEffect;
import com.mtxgdn.game.item.Item;
import com.mtxgdn.game.item.ItemType;
import com.mtxgdn.game.item.ItemRarity;

public class SpiritSpringWater extends Item {
    public SpiritSpringWater() {
        super("mtxgdn", "spirit_spring_water", ItemType.MATERIAL, ItemRarity.UNCOMMON,
            999, 30, true, 0, EmptyEffect.INSTANCE);
    }
}
