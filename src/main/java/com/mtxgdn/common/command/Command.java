package com.mtxgdn.common.command;

import com.mtxgdn.permission.PermissionService;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public abstract class Command {

    /**
     * 分类排序优先级表。数字越小越靠前，未列出的分类默认排在最后。
     * 新增分类只需在此 map 中添加一行即可，无需改 HelpCommand。
     */
    private static final Map<String, Integer> CATEGORY_ORDER = Map.ofEntries(
            Map.entry("账号", 1),
            Map.entry("我的角色", 2),
            Map.entry("修炼", 3),
            Map.entry("战斗", 4),
            Map.entry("背包", 5),
            Map.entry("探索", 6),
            Map.entry("坊市", 7),
            Map.entry("宗门", 8),
            Map.entry("经济", 9),
            Map.entry("社交", 10),
            Map.entry("管理", 11)
    );

    private final String[] names;
    private final String description;
    private final String usage;
    private final String category;
    private final String permission;
    private final boolean privateOnly;

    protected Command(String[] names, String description, String usage,
                      String category, String permission, boolean privateOnly) {
        this.names = names;
        this.description = description;
        this.usage = usage;
        this.category = category;
        this.permission = permission;
        this.privateOnly = privateOnly;
        CommandRegistry.register(this);
    }

    protected Command(String[] names, String description, String usage,
                      String category, String permission) {
        this(names, description, usage, category, permission, false);
    }

    protected Command(String[] names, String description, String usage, String category) {
        this(names, description, usage, category, null, false);
    }

    public String[] getNames() {
        return names;
    }

    public String getDescription() {
        return description;
    }

    public String getUsage() {
        return usage;
    }

    public String getCategory() {
        return category;
    }

    public String getPermission() {
        return permission;
    }

    public boolean isPrivateOnly() {
        return privateOnly;
    }

    public boolean shouldShowInHelp(Long userId) {
        if (permission == null) {
            return true;
        }
        if (userId == null) {
            return false;
        }
        return PermissionService.hasPermission(userId, permission);
    }

    /**
     * 获取分类排序优先级。数字越小越靠前。
     * 默认从 {@link #CATEGORY_ORDER} 查找，未注册的分类返回 Integer.MAX_VALUE（排最后）。
     */
    public int getCategoryOrder() {
        return CATEGORY_ORDER.getOrDefault(category, Integer.MAX_VALUE);
    }

    /**
     * 子类覆写此方法来定义 REST API 路由，由 UnifiedRestResource 自动注册。
     * 返回空列表即仅作为 OneBot 指令（默认行为）。
     */
    public List<RouteDefinition> getRestEndpoints() {
        return Collections.emptyList();
    }

    public abstract void execute(CommandContext ctx);
}
