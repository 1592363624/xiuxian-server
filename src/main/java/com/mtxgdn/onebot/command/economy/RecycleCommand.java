package com.mtxgdn.onebot.command.economy;

import com.mtxgdn.common.command.Command;
import com.mtxgdn.common.command.CommandContext;
import com.mtxgdn.game.entity.PlayerInfo;
import com.mtxgdn.game.item.ItemRegistry;
import com.mtxgdn.common.service.ServiceRegistry;

public class RecycleCommand extends Command {
    public RecycleCommand() {
        super(new String[]{"回收", "recycle"}, "将物品回收为灵石", "/回收 <物品名> [数量]", "经济", null);
    }

    @Override
    public void execute(CommandContext ctx) {
        Long userId = ctx.requireBinding();
        if (userId == null) return;
        PlayerInfo p = ctx.requirePlayer(userId);
        if (p == null) return;

        String[] parts = ctx.getArg().trim().split("\\s+", 2);
        if (parts[0].isEmpty()) {
            ctx.reply("用法: /回收 <物品名> [数量]\n提示: 回收价为原价的 30%");
            return;
        }

        String itemName = parts[0];
        int quantity = 1;
        if (parts.length > 1) {
            try { quantity = Integer.parseInt(parts[1]); } catch (NumberFormatException e) {
                ctx.reply("数量无效"); return;
            }
        }
        if (quantity <= 0) { ctx.reply("数量必须大于 0"); return; }

        var item = ItemRegistry.resolve(itemName);
        if (item == null) { ctx.reply("物品不存在，请使用 /物品列表 查看可回收物品"); return; }

        var eco = ServiceRegistry.getEconomyService();
        var result = eco.recycleItem(p.getId(), itemName, quantity);
        ctx.reply((String) result.get("message"));
    }
}
