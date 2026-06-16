package com.mtxgdn.onebot.command.economy;

import com.mtxgdn.common.command.Command;
import com.mtxgdn.common.command.CommandContext;
import com.mtxgdn.game.entity.PlayerInfo;
import com.mtxgdn.common.service.ServiceRegistry;

public class ExchangeCommand extends Command {
    public ExchangeCommand() {
        super(new String[]{"兑换", "exchange"}, "金币与灵石双向兑换", "/兑换 灵石 <数量> — 灵石→金币\n/兑换 金币 <数量> — 金币→灵石", "经济", null);

        registerSub("灵石", (ctx, p, parts) -> {
            doExchange(ctx, p, true, parts);
        });
        registerSub("金币", (ctx, p, parts) -> {
            doExchange(ctx, p, false, parts);
        });
    }

    @Override
    public void execute(CommandContext ctx) {
        Long userId = ctx.requireBinding();
        if (userId == null) return;
        PlayerInfo p = ctx.requirePlayer(userId);
        if (p == null) return;

        ctx.reply("💰 灵石兑换\n"
                + "10 金币 = 1 灵石（金币→灵石）\n"
                + "1 灵石 = 5 金币（灵石→金币）\n\n"
                + "用法:\n"
                + "/兑换 灵石 <数量>\n"
                + "/兑换 金币 <数量>");
    }

    private void doExchange(CommandContext ctx, PlayerInfo p, boolean isStoneToGold, String[] parts) {
        long amount;
        try {
            if (parts.length < 2) { ctx.reply("请输入数量"); return; }
            amount = Long.parseLong(parts[1]);
        } catch (NumberFormatException e) {
            ctx.reply("数量无效"); return;
        }

        var eco = ServiceRegistry.getEconomyService();
        var result = isStoneToGold
                ? eco.exchangeStonesToGold(p.getId(), amount)
                : eco.exchangeGoldToStones(p.getId(), amount);
        ctx.reply((String) result.get("message"));
    }
}
