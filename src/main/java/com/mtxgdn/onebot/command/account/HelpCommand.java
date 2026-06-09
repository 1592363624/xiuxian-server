package com.mtxgdn.onebot.command.account;

import com.mtxgdn.common.command.Command;
import com.mtxgdn.common.command.CommandContext;
import com.mtxgdn.common.command.CommandRegistry;
import com.mtxgdn.onebot.QqBinding;
import com.mtxgdn.onebot.QqBindingService;
import java.util.*;

public class HelpCommand extends Command {

    public HelpCommand() {
        super(new String[]{"help", "帮助"},
                "查看所有可用指令",
                "/help",
                "账号");
    }

    @Override
    public void execute(CommandContext ctx) {
        QqBinding b = new QqBindingService().findByQq(ctx.getSenderId());
        Long userId = b != null ? b.getUserId() : null;

        // 按分类动态分组，按 Command.getCategoryOrder() 排序
        Map<String, List<Command>> categories = new LinkedHashMap<>();
        for (Command cmd : CommandRegistry.getAllUnique()) {
            if (!cmd.shouldShowInHelp(userId)) continue;
            categories.computeIfAbsent(cmd.getCategory(), k -> new ArrayList<>()).add(cmd);
        }

        // 按优先级排序分类
        List<String> orderedCats = new ArrayList<>(categories.keySet());
        orderedCats.sort(Comparator.comparingInt(cat -> {
            // 取该分类下第一个命令的优先级
            List<Command> cmds = categories.get(cat);
            return cmds.isEmpty() ? Integer.MAX_VALUE : cmds.get(0).getCategoryOrder();
        }));

        StringBuilder sb = new StringBuilder();
        sb.append("════ 修仙世界 · QQ Bot 指令 ════\n");

        for (String cat : orderedCats) {
            List<Command> cmds = categories.get(cat);
            if (cmds.isEmpty()) continue;
            sb.append("\n▍").append(cat).append("\n");
            for (Command cmd : cmds) {
                sb.append(formatCommand(cmd)).append("\n");
            }
        }

        sb.append("\n══════════════════════════\n");
        sb.append("提示: 所有指令支持中英文，如 /状态 或 /status");
        ctx.reply(sb.toString());
    }

    private String formatCommand(Command cmd) {
        String usage = cmd.getUsage();
        String desc = cmd.getDescription();
        if (desc == null || desc.isEmpty()) desc = usage;
        if (usage.length() < 13) {
            return String.format("  %-13s %s", usage, desc);
        } else {
            return String.format("  %s  -  %s", usage, desc);
        }
    }
}
