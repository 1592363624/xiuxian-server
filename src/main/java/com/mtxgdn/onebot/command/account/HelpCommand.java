package com.mtxgdn.onebot.command.account;

import com.mtxgdn.common.command.Command;
import com.mtxgdn.common.command.CommandContext;
import com.mtxgdn.common.command.CommandRegistry;
import com.mtxgdn.onebot.QqBinding;
import com.mtxgdn.onebot.QqBindingService;
import java.util.*;

public class HelpCommand extends Command {

    private static final List<String> CATEGORY_ORDER = List.of(
            "账号", "我的角色", "修炼", "战斗", "背包", "探索", "坊市", "宗门", "社交", "管理"
    );

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

        // 按固定顺序分组
        Map<String, List<String>> categories = new LinkedHashMap<>();
        for (String cat : CATEGORY_ORDER) {
            categories.put(cat, new ArrayList<>());
        }
        // 收集未在顺序中的分类
        Map<String, List<String>> extra = new LinkedHashMap<>();

        for (Command cmd : CommandRegistry.getAllUnique()) {
            if (!cmd.shouldShowInHelp(userId)) continue;
            String cat = cmd.getCategory();
            String item = formatCommand(cmd);
            if (categories.containsKey(cat)) {
                categories.get(cat).add(item);
            } else {
                extra.computeIfAbsent(cat, k -> new ArrayList<>()).add(item);
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("════ 修仙世界 · QQ Bot 指令 ════\n");

        for (Map.Entry<String, List<String>> entry : categories.entrySet()) {
            List<String> items = entry.getValue();
            if (items.isEmpty()) continue;
            sb.append("\n▍").append(entry.getKey()).append("\n");
            for (String line : items) sb.append(line).append("\n");
        }

        for (Map.Entry<String, List<String>> entry : extra.entrySet()) {
            sb.append("\n▍").append(entry.getKey()).append("\n");
            for (String line : entry.getValue()) sb.append(line).append("\n");
        }

        sb.append("\n══════════════════════════\n");
        sb.append("提示: 所有指令支持中英文，如 /状态 或 /status");
        ctx.reply(sb.toString());
    }

    private String formatCommand(Command cmd) {
        String usage = cmd.getUsage();
        String desc = cmd.getDescription();
        if (desc == null || desc.isEmpty()) desc = usage;
        // 对齐: usage 20 字符宽
        if (usage.length() < 13) {
            return String.format("  %-13s %s", usage, desc);
        } else {
            return String.format("  %s  -  %s", usage, desc);
        }
    }
}
