package com.mtxgdn.plugin.builtin;

import com.mtxgdn.plugin.BuiltInPlugin;
import com.mtxgdn.plugin.PluginContext;

import java.util.Set;

/**
 * 团队玩法模块。
 * <p>
 * 配置键: plugins.team（默认 true）
 * 功能: 创建团队、组队副本
 * <p>
 * 软依赖 {@code secret_realm}：「组队副本」直接把玩家送进秘境副本
 * （{@code TeamCommand} 调 {@code SecretRealmService.enterRaid}）。
 * 秘境模块关闭时该子命令不可用，建队/邀请/退队照常 —— 所以是软依赖。
 */
public class TeamPlugin extends BuiltInPlugin {

    @Override
    public Set<String> getSoftDependencies() {
        return Set.of("secret_realm");
    }

    @Override
    public String getName() {
        return "team";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getDescription() {
        return "团队系统：创建团队、组队副本";
    }

    @Override
    public void onEnable(PluginContext context) {
        context.getLogger().info("团队系统已启用");
    }

    @Override
    public void onDisable(PluginContext context) {
        context.getLogger().info("团队系统已停用");
    }
}
