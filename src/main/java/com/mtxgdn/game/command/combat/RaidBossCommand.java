package com.mtxgdn.game.command.combat;

import com.mtxgdn.common.command.Command;
import com.mtxgdn.common.command.CommandContext;
import com.mtxgdn.common.command.CommandUtils;
import com.mtxgdn.game.entity.BossForm;
import com.mtxgdn.game.entity.PlayerInfo;
import com.mtxgdn.game.entity.RaidCombatResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * {@code /boss} —— 发起团队 Boss 战（至少 2 人）。
 *
 * <p>从旧的「决斗/切磋/fight/战斗/讨伐/boss」多名字指令里拆出来的；
 * 三形态 Boss 数值与组队规则逐字保留旧实现。注意这里收集的是
 * <b>playerId</b>（{@code PlayerInfo.getId()}），不是 userId ——
 * 旧实现踩过这个坑（混入 userId 会让 {@code raidBossFight} 取错角色）。</p>
 */
public class RaidBossCommand extends Command {

    private static final List<BossForm> BOSS_FORMS = Arrays.asList(
            new BossForm("千年妖王·人形", "妖王的人形形态，看似普通实则蕴含强大力量", 1.0, 1.0, 1.0, 1.0, 0.1, new String[0], 0.5),
            new BossForm("千年妖王·兽形", "妖王现出兽形，气势大增", 1.2, 1.3, 1.1, 0.9, 0.15, new String[0], 0.6),
            new BossForm("千年妖王·狂暴", "妖王进入狂暴状态，攻击力大幅提升", 1.5, 1.5, 1.3, 0.7, 0.2, new String[0], 0.8)
    );

    public RaidBossCommand() {
        super(new String[]{"boss", "团本"},
                "发起团队Boss战",
                "/boss <玩家1> <玩家2> ... - 团队Boss战（至少 2 人）",
                "战斗");
        setI18nKeys("cmd.raid_boss.desc", "cmd.raid_boss.usage");
        setModule("pvp");
    }

    @Override
    public void execute(CommandContext ctx) {
        PlayerInfo player = requirePlayerInfo(ctx);
        if (player == null) return;
        if (player.getHp() <= 0) {
            ctx.reply("你的生命值不足，无法参加Boss战");
            return;
        }

        List<Long> playerIds = new ArrayList<>();
        playerIds.add(player.getId());

        String arg = ctx.getArg();
        if (!CommandUtils.isBlank(arg)) {
            var playerService = services().getPlayerService();
            for (String name : arg.split("\\s+")) {
                if (name.isBlank()) continue;
                List<PlayerInfo> targets = playerService.searchPlayersByName(name, 1, 0);
                if (!targets.isEmpty() && !playerIds.contains(targets.get(0).getId())) {
                    playerIds.add(targets.get(0).getId());
                }
            }
        }

        if (playerIds.size() < 2) {
            ctx.reply("团队Boss战需要至少2名玩家！用法：/boss <玩家1> <玩家2> ...");
            return;
        }

        RaidCombatResult result = services().getCombatService().raidBossFight(playerIds, BOSS_FORMS);
        StringBuilder sb = new StringBuilder();
        if (result.getBattleLog() != null) {
            for (String line : result.getBattleLog()) {
                sb.append(line).append("\n");
            }
        }
        sb.append(result.getMessage());
        ctx.reply(sb.toString());
    }
}
