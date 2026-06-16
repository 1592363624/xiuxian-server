package com.mtxgdn.onebot.command.economy;

import com.mtxgdn.common.command.Command;
import com.mtxgdn.common.command.CommandContext;
import com.mtxgdn.game.entity.PlayerInfo;
import com.mtxgdn.common.service.ServiceRegistry;

public class SignInCommand extends Command {
    public SignInCommand() {
        super(new String[]{"签到", "signin"}, "每日签到领取灵石奖励", "/签到", "经济", null);
    }

    @Override
    public void execute(CommandContext ctx) {
        Long userId = ctx.requireBinding();
        if (userId == null) return;
        PlayerInfo p = ctx.requirePlayer(userId);
        if (p == null) return;

        var eco = ServiceRegistry.getEconomyService();
        var result = eco.signIn(p.getId());

        StringBuilder sb = new StringBuilder();
        if ((boolean) result.get("success")) {
            int streak = (int) result.get("streak");
            sb.append("☀ 签到成功！\n");
            sb.append("连续签到第 ").append(streak).append(" 天\n");
            sb.append(result.get("reward"));

            // 展示签到进度条
            sb.append("\n\n📅 本周进度: ");
            for (int i = 1; i <= 7; i++) {
                if (i <= streak) sb.append("●");
                else sb.append("○");
            }
        } else {
            sb.append(result.get("message"));
        }
        ctx.reply(sb.toString());
    }
}
