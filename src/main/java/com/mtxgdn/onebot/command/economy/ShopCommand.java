package com.mtxgdn.onebot.command.economy;

import com.mtxgdn.common.command.Command;
import com.mtxgdn.common.command.CommandContext;
import com.mtxgdn.game.entity.PlayerInfo;
import com.mtxgdn.common.service.ServiceRegistry;

public class ShopCommand extends Command {
    public ShopCommand() {
        super(new String[]{"商店", "shop", "灵石商店"}, "灵石商店购买物品", "/商店 [编号] — 无编号列出商品，带编号购买", "经济", null);
    }

    @Override
    public void execute(CommandContext ctx) {
        Long userId = ctx.requireBinding();
        if (userId == null) return;
        PlayerInfo p = ctx.requirePlayer(userId);
        if (p == null) return;

        var eco = ServiceRegistry.getEconomyService();
        String arg = ctx.getArg().trim();

        if (arg.isEmpty()) {
            // 列出所有商品
            String[][] items = eco.getShopItems();
            long stones = ServiceRegistry.getItemService().getSpiritStoneCount(p.getId());

            StringBuilder sb = new StringBuilder();
            sb.append("🏪 灵石商店\n");
            sb.append("你的灵石: ").append(stones).append("\n\n");

            for (int i = 0; i < items.length; i++) {
                sb.append(i + 1).append(". ")
                  .append(items[i][0]).append(" — ")
                  .append(items[i][2]).append(" 灵石");
                if (i < items.length - 1) sb.append("\n");
            }
            sb.append("\n\n购买: /商店 <编号>");
            ctx.reply(sb.toString());
            return;
        }

        try {
            int index = Integer.parseInt(arg);
            var result = eco.buyFromShop(p.getId(), index);
            ctx.reply((String) result.get("message"));
        } catch (NumberFormatException e) {
            ctx.reply("请输入商品编号，如 /商店 1");
        }
    }
}
