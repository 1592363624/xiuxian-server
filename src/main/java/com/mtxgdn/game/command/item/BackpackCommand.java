package com.mtxgdn.game.command.item;
import com.mtxgdn.game.entity.InventoryEntry;


import com.mtxgdn.common.command.Command;
import com.mtxgdn.common.command.CommandContext;
import com.mtxgdn.game.entity.PlayerInfo;
import com.mtxgdn.game.item.Item;
import java.util.List;

public class BackpackCommand extends Command {
    public BackpackCommand() {
        super(new String[]{"背包", "backpack"}, "查看背包中的物品", "/背包", "背包", "game.inventory.view");
        
        setI18nKeys("cmd.backpack.desc", "cmd.backpack.usage");
    setModule("inventory");
    }

    @Override
    public void execute(CommandContext ctx) {
        PlayerInfo p = requirePlayerInfo(ctx);
        if (p == null) return;

        var guideService = services().getGuideService();
        var itemService = services().getItemService();
        List<InventoryEntry> inventory = itemService.getInventory((int) p.getId());
        int usedSlots = itemService.getInventoryUsedSlots((int) p.getId());
        int capacity = itemService.getInventoryCapacity((int) p.getId());

        StringBuilder sb = new StringBuilder();
        sb.append("===== 背包 =====\n");
        sb.append("容量: ").append(usedSlots).append("/").append(capacity).append("\n\n");

        if (inventory.isEmpty()) {
            sb.append("空空如也...");
        } else {
            // 带序号：常见物品不必打全名/全 key，/使用 <序号> | /装备 <序号> <部位> 都能用
            int index = 1;
            for (InventoryEntry entry : inventory) {
                Item item = entry.getItem();
                String desc = item.getDescription();
                sb.append("[").append(index++).append("] ")
                        .append(item.getName()).append(" x").append(entry.getQuantity());
                if (!desc.isEmpty() && !desc.equals(item.getDescKey())) {
                    sb.append("\n  └ ").append(desc);
                }
                sb.append("\n");
            }
            sb.append("\n使用: /使用 <序号或名称>   装备: /装备 <序号或key> <部位>");
        }

        ctx.reply(sb.toString());
        String discTip = guideService.checkDiscovery((int) p.getId(), p, "backpack");
        if (discTip != null) ctx.reply(discTip);
    }
}
