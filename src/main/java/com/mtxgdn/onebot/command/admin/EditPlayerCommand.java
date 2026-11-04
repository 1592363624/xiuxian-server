package com.mtxgdn.onebot.command.admin;

import com.mtxgdn.common.command.Command;
import com.mtxgdn.common.command.CommandContext;
import com.mtxgdn.common.service.ServiceRegistry;
import com.mtxgdn.game.service.PlayerService;
import com.mtxgdn.permission.PermissionService;
import com.mtxgdn.identity.QqBinding;
import com.mtxgdn.identity.QqBindingService;
import com.mtxgdn.i18n.I18n;

public class EditPlayerCommand extends Command {

    /**
     * 整数属性的上限。
     *
     * <p>取 10 亿：既远高于任何正常玩法能达到的数值，又留足空间让
     * 后续 {@code 属性 * 倍率} 之类的 {@code int} 运算不会溢出。</p>
     */
    private static final int MAX_INT_ATTR = 1_000_000_000;

    /** 等级上限（等级只是展示用，不影响境界推进）。 */
    private static final int MAX_LEVEL = 1000;

    /** 货币类属性的上限：同一量级，避免 {@code gold * 2} 之类的运算溢出 long。 */
    private static final long MAX_LONG_CURRENCY = 1_000_000_000_000L;

    public EditPlayerCommand() {
        super(new String[]{"编辑玩家", "editplayer", "修改玩家"},
                "修改玩家属性（仅私聊）",
                "/编辑玩家 <玩家ID> <属性>=<值> ...",
                "管理", "admin.users.manage", true);
    
        setI18nKeys("cmd.edit_player.desc", "cmd.edit_player.usage");
    }

    /**
     * 校验数值落在合法区间内。
     *
     * <p><b>为什么这个指令需要专门的范围校验</b>：它把值直接写进
     * {@code players} 表，中间没有任何领域层校验。所以
     * {@code realm=999} 会被原样落库，而下游是按境界 id 去配置表里取值的 ——
     * 取不到就是空指针或零速修炼；{@code gold=-1} 会让玩家进入负金币状态，
     * 之后所有 {@code 余额 < 价格} 的比较全部失真。这类坏数据一旦入库
     * 比崩溃更难收拾，因此在写之前就拦住。</p>
     *
     * @throws IllegalArgumentException 超出区间时抛出，由调用方转成可读提示
     */
    private static int requireRange(String key, int value, int min, int max) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(
                    "范围错误: " + key + "=" + value + "（允许 " + min + "~" + max + "）");
        }
        return value;
    }

    /** long 版本的范围校验，语义同 {@link #requireRange(String, int, int, int)}。 */
    private static long requireRange(String key, long value, long min, long max) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(
                    "范围错误: " + key + "=" + value + "（允许 " + min + "~" + max + "）");
        }
        return value;
    }

    @Override
    public void execute(CommandContext ctx) {
        QqBinding b = ctx.resolveBoundBinding();
        if (b == null) {
            ctx.reply(I18n.t("admin.please_bind_your_account_first_2"));
            return;
        }
        if (!PermissionService.hasPermission(b.getUserId(), "admin.users.manage")) {
            ctx.reply(I18n.t("admin.insufficient_permission_3"));
            return;
        }

        String arg = ctx.getArg();
        if (arg == null || arg.isBlank()) {
            ctx.reply(I18n.t("admin.edit_player.usage"));
            return;
        }

        String[] parts = arg.trim().split("\\s+");
        if (parts.length < 2) {
            ctx.reply(I18n.t("admin.missing_arguments_2"));
            return;
        }

        long playerId;
        try {
            playerId = Long.parseLong(parts[0]);
        } catch (NumberFormatException e) {
            ctx.reply(I18n.t("admin.player_id_must"));
            return;
        }

        PlayerService playerService = ServiceRegistry.getPlayerService();
        var p = playerService.getPlayerById(playerId);
        if (p == null) {
            ctx.reply(I18n.t("admin.player_not_found_id", playerId));
            return;
        }

        StringBuilder changes = new StringBuilder();
        changes.append("修改 ").append(p.getName()).append(" (ID:").append(playerId).append("):\n");

        for (int i = 1; i < parts.length; i++) {
            String part = parts[i];
            int eqIdx = part.indexOf('=');
            if (eqIdx <= 0) continue;
            String key = part.substring(0, eqIdx);
            String valStr = part.substring(eqIdx + 1);

            try {
                switch (key) {
                    case "hp":
                        p.setHp(requireRange(key, Integer.parseInt(valStr), 0, MAX_INT_ATTR));
                        changes.append("  生命 = ").append(valStr).append("\n");
                        break;
                    case "maxHp":
                        p.setMaxHp(requireRange(key, Integer.parseInt(valStr), 1, MAX_INT_ATTR));
                        changes.append("  最大生命 = ").append(valStr).append("\n");
                        break;
                    case "mp":
                        p.setMp(requireRange(key, Integer.parseInt(valStr), 0, MAX_INT_ATTR));
                        changes.append("  法力 = ").append(valStr).append("\n");
                        break;
                    case "maxMp":
                        p.setMaxMp(requireRange(key, Integer.parseInt(valStr), 1, MAX_INT_ATTR));
                        changes.append("  最大法力 = ").append(valStr).append("\n");
                        break;
                    case "attack":
                        p.setAttack(requireRange(key, Integer.parseInt(valStr), 0, MAX_INT_ATTR));
                        changes.append("  攻击 = ").append(valStr).append("\n");
                        break;
                    case "defense":
                        p.setDefense(requireRange(key, Integer.parseInt(valStr), 0, MAX_INT_ATTR));
                        changes.append("  防御 = ").append(valStr).append("\n");
                        break;
                    case "speed":
                        p.setSpeed(requireRange(key, Integer.parseInt(valStr), 0, MAX_INT_ATTR));
                        changes.append("  速度 = ").append(valStr).append("\n");
                        break;
                    case "spirit":
                        p.setSpirit(requireRange(key, Integer.parseInt(valStr), 0, MAX_INT_ATTR));
                        changes.append("  神识 = ").append(valStr).append("\n");
                        break;
                    case "level":
                        p.setLevel(requireRange(key, Integer.parseInt(valStr), 1, MAX_LEVEL));
                        changes.append("  等级 = ").append(valStr).append("\n");
                        break;
                    case "gold":
                        p.setGold(requireRange(key, Long.parseLong(valStr), 0, MAX_LONG_CURRENCY));
                        changes.append("  金币 = ").append(valStr).append("\n");
                        break;
                    case "experience":
                        p.setExperience(requireRange(key, Long.parseLong(valStr), 0, MAX_LONG_CURRENCY));
                        changes.append("  灵力 = ").append(valStr).append("\n");
                        break;
                    case "realm":
                        // 上界取配置文件里的最大境界 id，而不是"境界条数"：
                        // 境界 id 不一定从 1 连续排布，用条数当上界会放行不存在的 id。
                        p.setRealm(requireRange(key, Integer.parseInt(valStr), 0,
                                com.mtxgdn.game.config.GameConfigLoader.getMaxRealmId()));
                        changes.append("  境界 = ").append(valStr).append("\n");
                        break;
                    default:
                        changes.append("  未知属性: ").append(key).append("\n");
                        break;
                }
            } catch (IllegalArgumentException e) {
                changes.append("  ").append(e.getMessage()).append("\n");
            }
        }

        // 写库前做一次内部一致性修正：hp/mp 不得超过对应上限。
        // 管理员可能只改了 maxHp 而把当前值留在更高的位置（/编辑玩家 1 maxHp=10
        // 而原 hp=100），落库后玩家会处于"血量超过上限"的非法状态 ——
        // 后续按比例计算伤害/回复时会得到怪异结果。这里夹住并明确告知。
        if (p.getHp() > p.getMaxHp()) {
            changes.append("  注意: 生命 ").append(p.getHp())
                    .append(" 超过上限，已夹到 ").append(p.getMaxHp()).append("\n");
            p.setHp(p.getMaxHp());
        }
        if (p.getMp() > p.getMaxMp()) {
            changes.append("  注意: 法力 ").append(p.getMp())
                    .append(" 超过上限，已夹到 ").append(p.getMaxMp()).append("\n");
            p.setMp(p.getMaxMp());
        }

        playerService.updatePlayer(playerId, p);
        ctx.reply(changes.toString());
    }
}
