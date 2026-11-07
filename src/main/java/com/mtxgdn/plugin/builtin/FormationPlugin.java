package com.mtxgdn.plugin.builtin;

import com.mtxgdn.plugin.BuiltInPlugin;
import com.mtxgdn.plugin.PluginContext;

import java.util.Set;

/**
 * 阵法玩法模块。
 * <p>
 * 配置键: plugins.formation（默认 true）
 * 功能: 阵法布置、阵法加成
 * <p>
 * 硬依赖 {@code cave}：{@code FormationService.placeFormation} 的第一道检查就是
 * "该玩家有没有洞府"，没有洞府一律拒绝。洞府模块关掉时阵法一个都摆不出来，
 * 与其让它空转，不如在启动日志里直说原因。
 */
public class FormationPlugin extends BuiltInPlugin {

    @Override
    public Set<String> getDependencies() {
        return Set.of("cave");
    }

    @Override
    public String getName() {
        return "formation";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getDescription() {
        return "阵法系统：阵法加成";
    }

    @Override
    public void onEnable(PluginContext context) {
        context.getLogger().info("阵法系统已启用");
    }

    @Override
    public void onDisable(PluginContext context) {
        context.getLogger().info("阵法系统已停用");
    }
}