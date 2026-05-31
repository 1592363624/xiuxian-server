package data.mtxgdn.item;

import com.mtxgdn.game.item.EmptyEffect;
import com.mtxgdn.game.item.Item;
import com.mtxgdn.game.item.ItemType;
import com.mtxgdn.game.item.ItemRarity;

public class EnhanceStone extends Item {
    public EnhanceStone() {
        super("mtxgdn", "enhance_stone", ItemType.MATERIAL, ItemRarity.COMMON,
            9999, 50, true, 0, EmptyEffect.INSTANCE);
    }
}
