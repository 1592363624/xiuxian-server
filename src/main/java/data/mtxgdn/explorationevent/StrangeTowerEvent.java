package data.mtxgdn.explorationevent;

import com.mtxgdn.entity.Player;
import com.mtxgdn.game.entity.ExplorationResult;
import com.mtxgdn.game.explorationevent.ExplorationEvent;
import com.mtxgdn.game.service.ItemService;
import com.mtxgdn.game.service.PlayerService;

import java.util.List;
import java.util.Random;

public class StrangeTowerEvent extends ExplorationEvent {
    public StrangeTowerEvent() {
        super("mtxgdn", "strange_tower", 4);
    }

    @Override
    public void execute(Player player, PlayerService playerService,
                         ItemService itemService, Random random,
                         ExplorationResult result, List<String> log) {
        result.setEventType("strange_tower");
        result.setEventDescription("奇遇古塔");

        double roll = random.nextDouble();
        if (roll < 0.3) {
            String rareItem = random.nextDouble() < 0.5 ? "heavenly_jade" : "tribulation_pill";
            itemService.addItem(player.getId(), rareItem, 1);
            result.setItemGained(rareItem);
            result.setItemQuantity(1);
            log.add("🗼 云雾缭绕中，一座九层古塔映入眼帘。你踏入其中，机关重重...");
            log.add("在塔顶密室中，你发现了传说中的宝物！");
            result.setMessage("勇闯古塔，获得至宝！");
        } else if (roll < 0.6) {
            long exp = (player.getRealm() + 1) * 300L;
            playerService.addExperience(player.getId(), exp);
            result.setExpGained(exp);
            log.add("🗼 古塔塔壁上刻满了上古符文，你潜心参悟...");
            log.add("灵光一闪，修为大进！获得了 " + exp + " 点经验。");
            result.setMessage("参悟古塔符文，获得 " + exp + " 点经验");
        } else if (roll < 0.8) {
            int stones = random.nextInt(3, 8);
            itemService.addItem(player.getId(), "enhance_stone", stones);
            result.setItemGained("enhance_stone");
            result.setItemQuantity(stones);
            log.add("🗼 古塔地宫堆满了珍贵的矿物，你捡到了 " + stones + " 颗强化石！");
            result.setMessage("古塔寻宝，获得 " + stones + " 颗强化石");
        } else {
            long spiritStones = (player.getRealm() + 1) * 200L;
            itemService.addSpiritStones(player.getId(), spiritStones);
            result.setSpiritStonesGained(spiritStones);
            long gold = (player.getRealm() + 1) * 500L;
            playerService.addGold(player.getId(), gold);
            result.setGoldGained(gold);
            log.add("🗼 古塔底层散落着历代闯塔者留下的财宝...");
            log.add("获得了 " + spiritStones + " 灵石和 " + gold + " 金币！");
            result.setMessage("古塔搜刮，获得灵石和金币");
        }
    }
}
