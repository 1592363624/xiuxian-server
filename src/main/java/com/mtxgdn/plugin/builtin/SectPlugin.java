package com.mtxgdn.plugin.builtin;

import com.mtxgdn.plugin.BuiltInPlugin;
import com.mtxgdn.plugin.PluginContext;
import com.mtxgdn.plugin.api.SectQueryApi;

import java.util.Map;

/**
 * 宗门玩法模块。
 * <p>
 * 配置键: plugins.sect（默认 true）
 * 功能: 创建宗门、宗门管理、宗门仓库、宗门战争
 * <p>
 * 对外提供 {@link SectQueryApi}：其它模块/插件据此判断玩家的宗门归属，
 * 无需 import {@code SectService}。宗门模块被停用时该服务随之注销，
 * 消费方探测到 {@code null} 后自行关闭相关功能。
 */
public class SectPlugin extends BuiltInPlugin {

    @Override
    public String getName() {
        return "sect";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getDescription() {
        return "宗门系统：建宗立派、宗门仓库、宗门战争";
    }

    @Override
    public void onEnable(PluginContext context) {
        context.getLogger().info("宗门系统已启用");
    }

    @Override
    public Map<Class<?>, Object> getProvidedServices() {
        return Map.of(SectQueryApi.class, new SectQueryProvider());
    }

    @Override
    public void onDisable(PluginContext context) {
        context.getLogger().info("宗门系统已停用");
    }
}