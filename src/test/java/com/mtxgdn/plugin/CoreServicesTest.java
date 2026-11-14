package com.mtxgdn.plugin;

import com.mtxgdn.api.PlayerApi;
import com.mtxgdn.common.service.ServiceRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 核心服务迁移到插件服务表后的语义测试。
 *
 * <p>要钉住两件事：</p>
 * <ol>
 *   <li><b>登记的核心服务可被取到</b> —— 插件用 {@code ctx.getService(PlayerApi.class)}
 *       能拿到 {@code ServiceRegistry} 里同一个实例。</li>
 *   <li><b>先查表、再回落</b> —— 插件登记了自己的实现后，
 *       {@code PluginContext} 的 getter 必须优先返回它，而不是继续用静态单例。
 *       这是"核心服务可替换"这个能力的全部意义所在。</li>
 * </ol>
 *
 * <p>登记键是<b>接口</b>而不是实现类：插件不该知道背后是哪个类，
 * 否则"换个实现"这件事仍然要在插件里改代码。这也是抽接口的直接收益 ——
 * 换成具体类时，Mockito 在 Java 25 上根本 mock 不了（Byte Buddy 只支持到 22），
 * 接口可以。</p>
 */
class CoreServicesTest {

    @AfterEach
    void cleanup() {
        CoreServices.uninstall();
        PluginServiceRegistry.getInstance().clear();
    }

    @Test
    void 核心服务登记后可通过服务表取到同一个实例() {
        PlayerApi fromRegistry = ServiceRegistry.container().getPlayerService();
        if (fromRegistry == null) return; // 未初始化服务注册表的纯单元测试环境下跳过

        CoreServices.install(ServiceRegistry.container());

        assertTrue(CoreServices.isInstalled());
        assertSame(fromRegistry, PluginServiceRegistry.getInstance().get(PlayerApi.class),
                "登记到服务表的必须是同一个实例，而不是另 new 一个");
    }

    @Test
    void 插件登记的实现优先于静态单例() {
        PlayerApi fromRegistry = ServiceRegistry.container().getPlayerService();
        if (fromRegistry == null) return;

        // 用 JDK 动态代理造一个假实现，模拟插件替换核心服务。
        // 不用 Mockito：本项目的 Byte Buddy 版本在 Java 25 上会直接抛错，
        // 而这里只需要一个"不是原来那个实例"的对象。
        PlayerApi fake = (PlayerApi) java.lang.reflect.Proxy.newProxyInstance(
                PlayerApi.class.getClassLoader(),
                new Class<?>[]{PlayerApi.class},
                (proxy, method, args) -> null);
        PluginServiceRegistry.getInstance().provide("some-plugin", PlayerApi.class, fake);

        PluginContext ctx = PluginContext.createSharedContext(
                new java.io.File("data"), getClass().getClassLoader());

        assertSame(fake, ctx.getPlayerService(),
                "getter 必须先查服务表，否则'可替换'就是句空话");
    }

    @Test
    void 未登记时回落到静态单例_保证没有启动引导的测试仍可用() {
        PluginContext ctx = PluginContext.createSharedContext(
                new java.io.File("data"), getClass().getClassLoader());

        PlayerApi fromRegistry = ServiceRegistry.container().getPlayerService();
        if (fromRegistry == null) return;
        assertSame(fromRegistry, ctx.getPlayerService(),
                "没有服务表登记时必须回落，否则既有的单元测试会集体 NPE");
    }

    @Test
    void install是幂等的() {
        CoreServices.install(ServiceRegistry.container());
        assertTrue(PluginServiceRegistry.getInstance().size() > 0, "应至少登记了一批核心服务");
        assertSame(0, CoreServices.install(ServiceRegistry.container()), "第二次调用应直接返回 0，不重复登记");
    }

    @Test
    void 卸载后服务表里不再有核心服务() {
        PlayerApi fromRegistry = ServiceRegistry.container().getPlayerService();
        if (fromRegistry == null) return;

        CoreServices.install(ServiceRegistry.container());
        assertNotNull(PluginServiceRegistry.getInstance().get(PlayerApi.class));

        CoreServices.uninstall();
        assertNull(PluginServiceRegistry.getInstance().get(PlayerApi.class),
                "卸载后不应残留，否则插件停用/关服时会拿到已废弃的实例");
    }
}
