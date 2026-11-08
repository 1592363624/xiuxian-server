package com.mtxgdn.plugin.builtin;

import com.mtxgdn.plugin.BuiltInPlugin;
import com.mtxgdn.plugin.PluginContext;

import java.util.Set;

/**
 * 角色基础玩法模块。
 * <p>
 * 配置键: plugins.player（默认 true）
 * 功能: 角色状态、疗伤、灵根查看
 * <p>
 * 软依赖 {@code title}：角色信息面板会一并读取称号。称号模块关闭时少一行展示，
 * 其余照常 —— 纯展示性依赖，连排序都不需要，只为在启动图上画出真实关系。
 */
public class PlayerPlugin extends BuiltInPlugin {

    @Override
    public Set<String> getSoftDependencies() {
        return Set.of("title");
    }

    @Override
    public String getName() {
        return "player";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getDescription() {
        return "角色系统：状态、疗伤、灵根";
    }

    @Override
    public void onEnable(PluginContext context) {
        context.getLogger().info("角色系统已启用");
    }

    @Override
    public void onDisable(PluginContext context) {
        context.getLogger().info("角色系统已停用");
    }
}