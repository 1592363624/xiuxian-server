package com.mtxgdn.game.command.combat;

import com.mtxgdn.common.command.Command;
import com.mtxgdn.common.command.CommandContext;
import com.mtxgdn.game.entity.Monster;
import com.mtxgdn.game.entity.PlayerInfo;
import com.mtxgdn.game.entity.PveCombatResult;

/**
 * {@code /讨伐} —— 挑战个人 Boss（千年妖王）。
 *
 * <p>从旧的「决斗/切磋/fight/战斗/讨伐/boss」多名字指令里拆出来的；
 * 数值与掉落逐字保留旧实现。团队版是 {@code /boss}（{@link RaidBossCommand}）。</p>
 */
public class SubjugateCommand extends Command {

    public SubjugateCommand() {
        super(new String[]{"讨伐", "subjugate"},
                "挑战Boss",
                "/讨伐 - 挑战千年妖王",
                "战斗");
        setI18nKeys("cmd.subjugate.desc", "cmd.subjugate.usage");
        setModule("pvp");
    }

    @Override
    public void execute(CommandContext ctx) {
        PlayerInfo player = requirePlayerInfo(ctx);
        if (player == null) return;
        if (player.getHp() <= 0) {
            ctx.reply("你的生命值不足，无法战斗");
            return;
        }

        Monster boss = new Monster("千年妖王", 500, 200, 100, 80, 5, true,
                new String[]{"mtxgdn:cultivation_elixir", "mtxgdn:spirit_sword"}, 0.5, 500, 100, 50,
                "千年妖王，修为深厚，已修炼出人形");

        PveCombatResult result = services().getCombatService().pveFight(player.getId(), boss);
        ctx.reply(formatPveResult(result));
    }

    private static String formatPveResult(PveCombatResult result) {
        StringBuilder sb = new StringBuilder();
        if (result.getBattleLog() != null) {
            for (String line : result.getBattleLog()) {
                sb.append(line).append("\n");
            }
        }
        sb.append(result.getMessage());
        return sb.toString();
    }
}
