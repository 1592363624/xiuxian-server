package com.mtxgdn.game.command.combat;

import com.mtxgdn.common.command.Command;
import com.mtxgdn.common.command.CommandContext;
import com.mtxgdn.game.entity.Monster;
import com.mtxgdn.game.entity.PlayerInfo;
import com.mtxgdn.game.entity.PveCombatResult;

import java.util.Arrays;
import java.util.List;

/**
 * {@code /战斗} —— 挑战随机野外怪物。
 *
 * <p>从旧的「决斗/切磋/fight/战斗/讨伐/boss」多名字指令里拆出来的：
 * 那条指令把 PVP 切磋和三种 PVE 战斗塞进同一个 command，帮助里只能列出一段
 * 混在一起的用法规格（而且 i18n 里写的是字面 {@code \n}，显示成了一行乱码）。
 * 现在 PVP 归 {@link PvpCommand}（{@code /挑战}），三种 PVE 各归各的指令。</p>
 *
 * <p>怪物与掉落表逐字保留旧实现，不改数值。</p>
 */
public class BattleCommand extends Command {

    private static final List<Monster> MONSTERS = Arrays.asList(
            new Monster("野猪精", 80, 20, 50, 3, 3, false,
                    new String[]{"mtxgdn:spirit_grass_seed", "mtxgdn:healing_pill"}, 0.3, 100, 20, 5, "一只体型庞大的野猪精，獠牙锋利"),
            new Monster("青狼", 100, 30, 70, 4, 4, false,
                    new String[]{"mtxgdn:spirit_grass_seed", "mtxgdn:healing_pill", "mtxgdn:mana_pill"}, 0.4, 150, 30, 8, "青面獠牙的巨狼，眼神凶狠"),
            new Monster("黑熊妖", 150, 60, 100, 5, 5, false,
                    new String[]{"mtxgdn:healing_pill", "mtxgdn:mana_pill", "mtxgdn:spirit_recovery_pill"}, 0.35, 200, 40, 10, "力大无穷的黑熊精"),
            new Monster("白骨精", 180, 40, 150, 6, 6, false,
                    new String[]{"mtxgdn:mana_pill", "mtxgdn:spirit_recovery_pill", "mtxgdn:cultivation_elixir"}, 0.3, 250, 50, 15, "阴森恐怖的骷髅妖怪")
    );

    public BattleCommand() {
        super(new String[]{"战斗", "battle", "fight"},
                "挑战野外怪物",
                "/战斗 - 随机挑战一只野外怪物",
                "战斗");
        setI18nKeys("cmd.battle.desc", "cmd.battle.usage");
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

        Monster m = MONSTERS.get((int) (Math.random() * MONSTERS.size()));
        // 复制一份：战斗会改 hp 等字段，静态列表里的实例不能被打脏
        Monster monster = new Monster(m.getName(), m.getHp(), m.getAttack(), m.getDefense(),
                m.getSpeed(), m.getRealm(), m.isBoss(), m.getLootTable(), m.getLootChance(),
                m.getExpReward(), m.getGoldReward(), m.getSpiritStoneReward(), m.getDescription());

        PveCombatResult result = services().getCombatService().pveFight(player.getId(), monster);
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
