package com.mtxgdn.plugin.builtin;

import com.mtxgdn.plugin.BuiltInPlugin;
import com.mtxgdn.plugin.PluginContext;

import java.util.Set;

/**
 * 修炼玩法模块。
 * <p>
 * 配置键: plugins.cultivation（默认 true）
 * 功能: 打坐修炼、突破境界、转世重修、每日天象与晨修
 * <p>
 * 软依赖 {@code title}：转世重修会授予对应称号（{@code ReincarnateCommand}
 * 调 {@code TitleService}）。称号模块关闭时少一个纪念性回报，转世本身照常 ——
 * 所以是软依赖，不能写成硬依赖把整个修炼模块拖下水。
 */
public class CultivationPlugin extends BuiltInPlugin {

    @Override
    public Set<String> getSoftDependencies() {
        return Set.of("title");
    }

    @Override
    public String getName() {
        return "cultivation";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getDescription() {
        return "修炼系统：打坐、突破、转世、天象晨修";
    }

    @Override
    public void onEnable(PluginContext context) {
        context.getLogger().info("修炼系统已启用");
    }

    @Override
    public void onDisable(PluginContext context) {
        context.getLogger().info("修炼系统已停用");
    }
}