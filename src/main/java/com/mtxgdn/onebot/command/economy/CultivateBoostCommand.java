package com.mtxgdn.onebot.command.economy;

import com.mtxgdn.common.command.Command;
import com.mtxgdn.common.command.CommandContext;
import com.mtxgdn.game.entity.PlayerInfo;
import com.mtxgdn.common.service.ServiceRegistry;

public class CultivateBoostCommand extends Command {
    public CultivateBoostCommand() {
        super(new String[]{"修炼加速", "boost"}, "燃烧灵石加速修炼", "/修炼加速 <灵石数量> （每100灵石=1小时×1.5倍）", "经济", null);
    }

    @Override
    public void execute(CommandContext ctx) {
        Long userId = ctx.requireBinding();
        if (userId == null) return;
        PlayerInfo p = ctx.requirePlayer(userId);
        if (p == null) return;

        String arg = ctx.getArg().trim();
        if (arg.isEmpty()) {
            long stones = ServiceRegistry.getItemService().getSpiritStoneCount(p.getId());
            ctx.reply("⚡ 灵石修炼加速\n"
                    + "每 100 灵石 = 1 小时 ×1.5 倍修炼效率\n"
                    + "当前灵石: " + stones + "\n"
                    + "用法: /修炼加速 <灵石数量>\n"
                    + "注意: 必须先 /修炼 后才能使用");
            return;
        }

        try {
            int stonesToBurn = Integer.parseInt(arg);
            var eco = ServiceRegistry.getEconomyService();
            var result = eco.boostCultivation(p.getId(), stonesToBurn);
            ctx.reply((String) result.get("message"));
        } catch (NumberFormatException e) {
            ctx.reply("请输入有效的灵石数量");
        }
    }
}
