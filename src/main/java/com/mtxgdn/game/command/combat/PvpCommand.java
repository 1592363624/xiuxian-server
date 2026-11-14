package com.mtxgdn.game.command.combat;
import com.mtxgdn.game.entity.CombatResult;


import com.mtxgdn.common.command.Command;
import com.mtxgdn.common.command.CommandContext;
import com.mtxgdn.common.command.CommandUtils;
import com.mtxgdn.game.entity.PlayerInfo;
import com.mtxgdn.identity.QqBindingService;
import com.mtxgdn.identity.QqBinding;

import java.util.List;
import com.mtxgdn.i18n.I18n;

public class PvpCommand extends Command {

    public PvpCommand() {
        super(new String[]{"挑战", "pvp", "决斗", "切磋"}, "挑战其他修士", "/挑战 <角色名>\n/挑战 接受 — 接受正在进行的切磋\n/挑战 拒绝 — 拒绝切磋", "战斗", "game.pvp.challenge");
        
        setI18nKeys("cmd.pvp.desc", "cmd.pvp.usage");
    setModule("pvp");

        registerSub("接受", (ctx, p, parts) -> {
            var combatService = services().getCombatService();
            var result = combatService.acceptChallenge(p.getId());
            if (!result.isSuccess()) {
                ctx.reply(result.getMessage());
                return;
            }
            sendBattleReport(ctx, p, result, p.getName());
        });

        registerSub("拒绝", (ctx, p, parts) -> {
            var combatService = services().getCombatService();
            String challengerName = combatService.rejectChallenge(p.getId());
            if (challengerName == null) {
                ctx.reply(I18n.t("combat.no_pending_challenges"));
            } else {
                ctx.reply(I18n.t("combat.you_declined_the_sparring_challenge", challengerName));
            }
        });
    }

    @Override
    public void execute(CommandContext ctx) {
        PlayerInfo p = requirePlayerInfo(ctx);
        if (p == null) return;

        String arg = ctx.getArg();
        if (CommandUtils.isBlank(arg)) {
            String tactic = getTacticDisplay(p);
            ctx.reply(I18n.t("combat.usage_2", tactic));
            return;
        }

        String[] parts = arg.trim().split("\\s+", 2);
        String sub = parts[0].toLowerCase();
        if ("接受".equals(sub) || "accept".equals(sub) || "拒绝".equals(sub) || "reject".equals(sub)) {
            super.execute(ctx);
            return;
        }

        String targetName = arg.trim();
        var playerService = services().getPlayerService();
        List<PlayerInfo> targets = playerService.searchPlayersByName(targetName, 1, 0);
        if (targets.isEmpty()) { ctx.reply(I18n.t("combat.player_not_found", targetName)); return; }
        PlayerInfo target = targets.get(0);
        if (target.getId() == p.getId()) { ctx.reply(I18n.t("combat.you_cannot_challenge_yourself")); return; }

        var combatService = services().getCombatService();
        var challengeResult = combatService.createChallenge(p.getId(), target.getId());

        if (!challengeResult.isSuccess()) {
            ctx.reply(challengeResult.getMessage());
            return;
        }

        // 通知挑战者
        String challengerTactic = getTacticDisplay(p);
        ctx.reply(I18n.t("combat.you_challenged_to_a_sparring", target.getName(), challengerTactic));

        // 通知被挑战者
        notifyTarget(ctx, target, p.getName());
    }

    private void notifyTarget(CommandContext ctx, PlayerInfo target, String challengerName) {
        try {
            QqBinding official = new com.mtxgdn.identity.QqOfficialBindingService().findByUserId(target.getUserId());
            QqBinding onebot = new QqBindingService().findByUserId(target.getUserId());
            if (official == null && onebot == null) return;

            var playerService = services().getPlayerService();
            var targetPlayer = playerService.getPlayerByUserIdRaw(target.getUserId());
            String targetTactic = "";
            if (targetPlayer != null && targetPlayer.getBattleStrategy() != null) {
                targetTactic = getTacticDisplay(targetPlayer);
            }
            String msg = "⚔ 【" + challengerName + "】向你发起了切磋挑战！" + targetTactic + "\n"
                    + "回复 /挑战 接受 迎战，/挑战 拒绝 回避（30秒超时自动取消）";

            // 通知交给端实现：QQ 官方端按 AppID 隔离的 openid 查绑定，
            // OneBot 端按 QQ 号查绑定 —— 收件人解析是端的细节，
            // 命令只说"给这个人发一条"，不再关心当前跑在哪个通道上。
            // （两个绑定查询保留在上方，作为"对方是否可达"的前置判断。）
            if (official == null && onebot == null) return;
            ctx.sendPrivateTo(onebot != null ? onebot.getQqNumber()
                    : (official != null ? official.getQqNumber() : null), msg);
        } catch (Exception ignored) {}
    }

    private void sendBattleReport(CommandContext ctx, PlayerInfo p, CombatResult result, String myName) {
        List<String> log = result.getBattleLog();
        if (log == null || log.isEmpty()) return;

        // 分条发送：汇合回合，每组最多 4 行叙事
        StringBuilder sb = new StringBuilder();
        int lineCount = 0;

        for (int i = 0; i < log.size(); i++) {
            String line = log.get(i);
            // 换段标志：空行或回合分隔
            boolean isBreak = line.isEmpty() || (line.startsWith("-- 第") && line.contains("回合 --"));
            boolean isEnd = line.startsWith("🏆") || line.startsWith("🤝");
            boolean isReward = line.startsWith("获得") || line.startsWith("惜败");

            if ((isBreak || isEnd || isReward) && sb.length() > 0) {
                ctx.reply(sb.toString().trim());
                sb = new StringBuilder();
                lineCount = 0;
            }

            sb.append(line).append("\n");
            lineCount++;

            // 每 5 行切一条
            if (lineCount >= 5 && !isEnd && !isReward) {
                ctx.reply(sb.toString().trim());
                sb = new StringBuilder();
                lineCount = 0;
            }
        }

        if (sb.length() > 0) {
            ctx.reply(sb.toString().trim());
        }
    }

    private String getTacticDisplay(PlayerInfo p) {
        if (p == null) return "";
        var ps = services().getPlayerService();
        var player = ps.getPlayerByUserIdRaw(p.getUserId());
        if (player == null) return "";
        String tactic = player.getBattleStrategy();
        if (tactic == null) tactic = "balanced";
        return switch (tactic) {
            case "aggressive" -> "\n🔥 当前战术：猛攻（优先使用最强技能）";
            case "defensive" -> "\n🛡 当前战术：防守（保留法力，稳扎稳打）";
            default -> "\n⚖ 当前战术：均衡";
        };
    }

    private String getTacticDisplay(com.mtxgdn.entity.Player player) {
        if (player == null) return "";
        String tactic = player.getBattleStrategy();
        if (tactic == null) tactic = "balanced";
        return switch (tactic) {
            case "aggressive" -> "\n  🔥 战术：猛攻";
            case "defensive" -> "\n  🛡 战术：防守";
            default -> "\n  ⚖ 战术：均衡";
        };
    }
}
