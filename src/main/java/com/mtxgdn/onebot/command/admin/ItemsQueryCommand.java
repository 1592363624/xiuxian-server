package com.mtxgdn.onebot.command.admin;

import com.mtxgdn.common.command.Command;
import com.mtxgdn.common.command.CommandContext;
import com.mtxgdn.game.item.Item;
import com.mtxgdn.game.item.ItemRegistry;
import com.mtxgdn.permission.PermissionService;
import com.mtxgdn.onebot.QqBinding;
import com.mtxgdn.onebot.QqBindingService;

import java.util.ArrayList;
import java.util.List;
import com.mtxgdn.i18n.I18n;

public class ItemsQueryCommand extends Command {
    public ItemsQueryCommand() {
        super(new String[]{"全物品列表", "items", "allitems"},
                "查看所有物品列表（仅私聊，支持分页）",
                "/全物品列表 [页数=1]",
                "管理", "admin.status", true);
    
        setI18nKeys("cmd.items_query.desc", "cmd.items_query.usage");
    }

    @Override
    public void execute(CommandContext ctx) {
        QqBinding b = ctx.resolveBoundBinding();
        if (b == null) {
            ctx.reply(I18n.t("admin.please_bind_your_account_first_2"));
            return;
        }
        if (!PermissionService.hasPermission(b.getUserId(), "admin.status")) {
            ctx.reply(I18n.t("admin.insufficient_permission_3"));
            return;
        }

        int page = 1;
        int pageSize = 20;
        String arg = ctx.getArg();
        if (arg != null && !arg.isBlank()) {
            try {
                page = Integer.parseInt(arg.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        // 页码必须 >= 1：page=0 会让 start=(0-1)*20=-20，随后
        // allItems.get(-20) 抛 IndexOutOfBoundsException；page 为负数同理。
        // 夹到 1 而不是报错，是因为这里只是查询命令，给个合理默认更友好。
        if (page < 1) {
            page = 1;
        }

        List<Item> allItems = new ArrayList<>(ItemRegistry.getAll());
        int total = allItems.size();
        int totalPages = (total + pageSize - 1) / pageSize;
        // 超过末页时也夹回最后一页，避免显示"第 999/3 页"却没有任何内容
        if (totalPages > 0 && page > totalPages) {
            page = totalPages;
        }
        int start = (page - 1) * pageSize;
        int end = Math.min(start + pageSize, total);

        StringBuilder sb = new StringBuilder();
        sb.append("===== 物品列表 (第").append(page).append("/").append(totalPages).append("页) =====\n");
        sb.append("共 ").append(total).append(" 种物品\n\n");

        for (int i = start; i < end; i++) {
            Item item = allItems.get(i);
            sb.append("[").append(item.getType().name()).append("] ")
                    .append(item.getFullKey())
                    .append(" - ").append(item.getName())
                    .append(" (").append(item.getRarity().name()).append(")\n");
        }

        if (totalPages > 1) {
            sb.append("\n输入 /全物品列表 ").append(page + 1 > totalPages ? 1 : page + 1).append(" 查看下一页");
        }
        ctx.reply(sb.toString());
    }
}
