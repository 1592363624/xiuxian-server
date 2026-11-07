package com.mtxgdn.plugin;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 插件服务注册表的单元测试。
 *
 * <p>重点锁住三条约定：拿不到返回 null（不抛异常）、同键重复登记保留先来者、
 * 注销按提供方隔离（不会误伤别人的服务）。</p>
 */
class PluginServiceRegistryTest {

    interface Greeter {
        String greet();
    }

    interface Counter {
        int next();
    }

    private final PluginServiceRegistry registry = PluginServiceRegistry.getInstance();

    @AfterEach
    void cleanup() {
        registry.clear();
    }

    @Test
    void 登记后可以取回同一个实例() {
        Greeter impl = () -> "hi";
        assertTrue(registry.provide("hello-plugin", Greeter.class, impl));

        assertSame(impl, registry.get(Greeter.class));
        assertEquals("hi", registry.get(Greeter.class).greet());
        assertTrue(registry.has(Greeter.class));
    }

    @Test
    void 没有提供方时返回null而不是抛异常() {
        assertNull(registry.get(Greeter.class), "软依赖缺失是正常路径，不能抛异常");
        assertFalse(registry.has(Greeter.class));
        assertNull(registry.get(null));
    }

    @Test
    void 重复登记同一接口时保留先来者并拒绝后来者() {
        Greeter first = () -> "first";
        Greeter second = () -> "second";

        assertTrue(registry.provide("a", Greeter.class, first));
        assertFalse(registry.provide("b", Greeter.class, second),
                "争抢同一接口必须被拒绝");

        assertSame(first, registry.get(Greeter.class), "先注册者应保留");
        assertEquals("a", registry.snapshot().values().iterator().next().split(" → ")[0]);
    }

    @Test
    void 实现类型与接口不匹配时拒绝登记() {
        assertFalse(registry.provide("bad", Greeter.class, Integer.valueOf(1)));
        assertNull(registry.get(Greeter.class));
    }

    @Test
    void 注销按提供方隔离() {
        Greeter greeter = () -> "hi";
        Counter counter = () -> 1;
        registry.provide("hello-plugin", Greeter.class, greeter);
        registry.provide("count-plugin", Counter.class, counter);

        assertEquals(1, registry.unregisterAll("hello-plugin"));

        assertNull(registry.get(Greeter.class), "该插件自己的服务应被注销");
        assertSame(counter, registry.get(Counter.class), "不该误伤别人的服务");
    }

    @Test
    void 注销不存在的提供方不报错() {
        assertEquals(0, registry.unregisterAll("nobody"));
        assertEquals(0, registry.unregisterAll(null));
        assertEquals(0, registry.unregisterAll(""));
    }

    @Test
    void 注销后可以重新登记_支持插件热重载() {
        Greeter first = () -> "v1";
        Greeter second = () -> "v2";

        registry.provide("p", Greeter.class, first);
        registry.unregisterAll("p");
        assertTrue(registry.provide("p", Greeter.class, second));

        assertSame(second, registry.get(Greeter.class));
    }

    @Test
    void 快照包含接口与提供方() {
        registry.provide("sect", Greeter.class, (Greeter) () -> "hi");

        String entry = registry.snapshot().get(Greeter.class.getName());
        assertTrue(entry != null && entry.startsWith("sect → "),
                "快照应能看出是谁提供的: " + entry);
    }

    @Test
    void 参数不完整时拒绝登记() {
        assertFalse(registry.provide(null, Greeter.class, (Greeter) () -> "x"));
        assertFalse(registry.provide("p", null, (Greeter) () -> "x"));
        assertFalse(registry.provide("p", Greeter.class, null));
        assertEquals(0, registry.size());
    }
}
