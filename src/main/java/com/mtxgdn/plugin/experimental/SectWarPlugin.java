package com.mtxgdn.plugin.experimental;

import com.mtxgdn.plugin.BuiltInPlugin;
import com.mtxgdn.plugin.PluginContext;
import com.mtxgdn.plugin.PluginServiceRegistry;
import com.mtxgdn.plugin.api.SectQueryApi;

import java.util.Set;

/**
 * 实验性插件：宗门战。
 * <p>
 * 配置键: {@code plugins.experimental_sect_war}（默认 false，见 config/plugins.yml）
 * 功能: 宗门之间的攻防战（REST {@code POST /api/game/sect/war/{sectId}}、WebSocket {@code sect_war}）
 * <p>
 * 由旧 {@code config/experimental.yml} 的 {@code experimental.sect_war} 迁移而来；
 * 停止本插件即整体关闭宗门战，相关指令/接口会被门控拒绝。
 * <p>
 * 硬依赖 {@code sect}：没有宗门模块就没有宗门，也就没有"宗门之间的战争"。
 * 启用时顺带演示联动 —— 通过服务表探测 {@link SectQueryApi}，
 * 确认宗门模块确实把查询能力挂上来了。
 */
public class SectWarPlugin extends BuiltInPlugin {

    @Override
    public Set<String> getDependencies() {
        return Set.of("sect");
    }

    @Override
    public String getName() {
        return "experimental_sect_war";
    }

    @Override
    public String getVersion() {
        return "0.1.0";
    }

    @Override
    public String getDescription() {
        return "实验性：宗门战（宗门间攻防战）";
    }

    @Override
    public String getCategory() {
        return "experimental";
    }

    /** 实验性内容默认关闭。 */
    @Override
    public boolean getDefaultEnabled() {
        return false;
    }

    @Override
    public void onEnable(PluginContext context) {
        context.getLogger().info("实验性功能已启用: 宗门战");
        // 联动探测：宗门模块是否真的把查询能力挂上来了。
        // 拿到 null 说明宗门模块虽启用但未提供该接口（版本不匹配），
        // 这里只提示，不阻断 —— 宗门战目前的实现还不依赖它，将来接入时再改成硬性要求。
        SectQueryApi sectApi = PluginServiceRegistry.getInstance().get(SectQueryApi.class);
        if (sectApi == null) {
            context.getLogger().warn("宗门战：未发现 SectQueryApi，宗门归属相关功能将不可用");
        }
    }

    @Override
    public void onDisable(PluginContext context) {
        context.getLogger().info("实验性功能已停用: 宗门战");
    }
}
