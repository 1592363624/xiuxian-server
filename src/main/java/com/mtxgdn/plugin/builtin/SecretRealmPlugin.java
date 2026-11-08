package com.mtxgdn.plugin.builtin;

import com.mtxgdn.plugin.BuiltInPlugin;
import com.mtxgdn.plugin.PluginContext;

import java.util.Set;

/**
 * 秘境玩法模块。
 * <p>
 * 配置键: plugins.secret_realm（默认 true）
 * 功能: 秘境列表、进入秘境、秘境探索奖励
 * <p>
 * 软依赖两条，都是"缺了就少一块回报/上下文"，秘境本身照常可玩：
 * <ul>
 *   <li>{@code map} —— 读取玩家当前地界以决定可用秘境（地点为 0 时退回通用列表）</li>
 *   <li>{@code title} —— 首领被击败时给最后一击者授「副本征服者」称号</li>
 * </ul>
 */
public class SecretRealmPlugin extends BuiltInPlugin {

    @Override
    public Set<String> getSoftDependencies() {
        return Set.of("map", "title");
    }

    @Override
    public String getName() {
        return "secret_realm";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getDescription() {
        return "秘境探索系统：多层次秘境、高境界秘境差异化";
    }

    @Override
    public void onEnable(PluginContext context) {
        context.getLogger().info("秘境系统已启用");
    }

    @Override
    public void onDisable(PluginContext context) {
        context.getLogger().info("秘境系统已停用");
    }
}