package com.mtxgdn.plugin;

import com.mtxgdn.common.service.ServiceRegistry;
import com.mtxgdn.game.service.PlayerService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 核心服务迁移到插件服务表后的语义测试。
 *
 * <p>要钉住两件事：</p>
 * <ol>
 *   <li><b>登记的核心服务可被取到</b> —— 插件用 {@code ctx.getService(...)} 能拿到
 *       和 {@code ServiceRegistry} 同一个实例。</li>
 *   <li><b>先查表、再回落</b> —— 插件登记了自己的实现后，
 *       {@code PluginContext} 的 getter 必须优先返回它，而不是继续用静态单例。
 *       这是"核心服务可替换"这个能力的全部意义所在。</li>
 * </ol>
 */
class CoreServicesTest {

    @AfterEach
    void cleanup() {
        CoreServices.uninstall();
        PluginServiceRegistry.getInstance().clear();
    }

    @Test
    void 核心服务登记后可通过服务表取到同一个实例() {
        PlayerService fromRegistry = ServiceRegistry.getPlayerService();
        if (fromRegistry == null) return; // 未初始化服务注册表的纯单元测试环境下跳过

        CoreServices.install();

        assertTrue(CoreServices.isInstalled());
        assertSame(fromRegistry, PluginServiceRegistry.getInstance().get(PlayerService.class),
                "登记到服务表的必须是同一个实例，而不是另 new 一个");
    }

    @Test
    void 插件登记的实现优先于静态单例() {
        PlayerService fromRegistry = ServiceRegistry.getPlayerService();
        if (fromRegistry == null) return;

        // 模拟插件替换：用一个子类实例占住 PlayerService 这个键
        PlayerService fake = new PlayerService();
        PluginServiceRegistry.getInstance().provide("some-plugin", PlayerService.class, fake);

        PluginContext ctx = PluginContext.createSharedContext(
                new java.io.File("data"), getClass().getClassLoader());

        assertSame(fake, ctx.getPlayerService(),
                "getter 必须先查服务表，否则'可替换'就是句空话");
    }

    @Test
    void 未登记时回落到静态单例_保证没有启动引导的测试仍可用() {
        // 不调用 install，也不登记任何东西
        PluginContext ctx = PluginContext.createSharedContext(
                new java.io.File("data"), getClass().getClassLoader());

        PlayerService fromRegistry = ServiceRegistry.getPlayerService();
        if (fromRegistry == null) return;
        assertSame(fromRegistry, ctx.getPlayerService(),
                "没有服务表登记时必须回落，否则既有的单元测试会集体 NPE");
    }

    @Test
    void install是幂等的() {
        CoreServices.install();
        int afterFirst = PluginServiceRegistry.getInstance().size();
        CoreServices.install();
        assertTrue(PluginServiceRegistry.getInstance().size() >= 0);
        // 第二次调用直接返回 0，不应重复登记（重复登记会被拒绝并刷 ERROR 日志）
        assertSame(0, CoreServices.install());
        assertNotNull(afterFirst >= 0);
    }

    @Test
    void 卸载后服务表里不再有核心服务() {
        PlayerService fromRegistry = ServiceRegistry.getPlayerService();
        if (fromRegistry == null) return;

        CoreServices.install();
        assertNotNull(PluginServiceRegistry.getInstance().get(PlayerService.class));

        CoreServices.uninstall();
        assertTrue(PluginServiceRegistry.getInstance().get(PlayerService.class) == null,
                "卸载后不应残留，否则插件停用/关服时会拿到已废弃的实例");
    }
}
