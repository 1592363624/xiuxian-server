package com.mtxgdn.plugin;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 依赖解析的单元测试。
 *
 * <p>这一层的价值在于它能被纯函数式地测试：不需要 jar、不需要类加载器、不需要数据库。
 * 启动顺序这类问题一旦上线就很难复现，所以必须在计划层就锁住。</p>
 */
class PluginDependencyResolverTest {

    private static PluginInfo info(String name, List<String> depends, List<String> softDepends) {
        return new PluginInfo(name, "1.0.0", "test", "", "Test" + name, depends, softDepends);
    }

    private static PluginInfo info(String name, List<String> depends) {
        return info(name, depends, List.of());
    }

    @Test
    void 无依赖时保持输入顺序() {
        PluginDependencyResolver.Plan plan = PluginDependencyResolver.resolve(Arrays.asList(
                info("a", List.of()), info("b", List.of()), info("c", List.of())));

        assertEquals(List.of("a", "b", "c"), plan.getLoadOrder());
        assertFalse(plan.hasRejected());
    }

    @Test
    void 被依赖方一定排在依赖方之前_即便输入顺序相反() {
        // c 依赖 b，b 依赖 a，但输入是 c,b,a —— 这正是"文件系统顺序不可靠"的场景
        PluginDependencyResolver.Plan plan = PluginDependencyResolver.resolve(Arrays.asList(
                info("c", List.of("b")),
                info("b", List.of("a")),
                info("a", List.of())));

        assertEquals(List.of("a", "b", "c"), plan.getLoadOrder());
        assertFalse(plan.hasRejected());
    }

    @Test
    void 硬依赖缺失时拒绝加载并说明原因() {
        PluginDependencyResolver.Plan plan = PluginDependencyResolver.resolve(Arrays.asList(
                info("sectWar", List.of("sect")),
                info("economy", List.of())));

        assertEquals(List.of("economy"), plan.getLoadOrder());
        assertTrue(plan.getRejected().containsKey("sectWar"));
        assertTrue(plan.getRejected().get("sectWar").contains("sect"),
                "报错必须点名缺哪个插件，否则等于没报: " + plan.getRejected().get("sectWar"));
    }

    @Test
    void 硬依赖缺失会传递给依赖链() {
        // c → b → a，a 缺失：b 和 c 都必须被拒，不能只拒 b
        PluginDependencyResolver.Plan plan = PluginDependencyResolver.resolve(Arrays.asList(
                info("c", List.of("b")),
                info("b", List.of("a"))));

        assertTrue(plan.getLoadOrder().isEmpty(), "整条链都该被拒: " + plan.getLoadOrder());
        assertTrue(plan.getRejected().containsKey("b"));
        assertTrue(plan.getRejected().containsKey("c"),
                "依赖了被拒插件的插件也必须被拒: " + plan.getRejected());
    }

    @Test
    void 软依赖缺失不影响加载() {
        PluginDependencyResolver.Plan plan = PluginDependencyResolver.resolve(Arrays.asList(
                info("sectWar", List.of(), List.of("economy"))));

        assertEquals(List.of("sectWar"), plan.getLoadOrder());
        assertFalse(plan.hasRejected());
    }

    @Test
    void 软依赖存在时仍然参与排序() {
        PluginDependencyResolver.Plan plan = PluginDependencyResolver.resolve(Arrays.asList(
                info("sectWar", List.of(), List.of("economy")),
                info("economy", List.of())));

        assertEquals(List.of("economy", "sectWar"), plan.getLoadOrder());
    }

    @Test
    void 成环时全部拒绝并给出环路径() {
        PluginDependencyResolver.Plan plan = PluginDependencyResolver.resolve(Arrays.asList(
                info("a", List.of("b")),
                info("b", List.of("a"))));

        assertTrue(plan.getLoadOrder().isEmpty(), "成环的插件不该有合法顺序: " + plan.getLoadOrder());
        Map<String, String> rejected = plan.getRejected();
        assertTrue(rejected.containsKey("a") && rejected.containsKey("b"));
        assertTrue(rejected.get("a").contains("成环"), "报错应指明是环: " + rejected.get("a"));
        assertTrue(rejected.get("a").contains("a") && rejected.get("a").contains("b"),
                "报错应给出环上的插件名: " + rejected.get("a"));
    }

    @Test
    void 三节点环也能识别() {
        PluginDependencyResolver.Plan plan = PluginDependencyResolver.resolve(Arrays.asList(
                info("a", List.of("b")),
                info("b", List.of("c")),
                info("c", List.of("a"))));

        assertTrue(plan.getLoadOrder().isEmpty());
        assertEquals(3, plan.getRejected().size());
    }

    @Test
    void 环外的插件照常加载_不因别人的环被连坐() {
        PluginDependencyResolver.Plan plan = PluginDependencyResolver.resolve(Arrays.asList(
                info("a", List.of("b")),
                info("b", List.of("a")),
                info("harmless", List.of())));

        assertEquals(List.of("harmless"), plan.getLoadOrder());
        assertFalse(plan.getRejected().containsKey("harmless"));
    }

    @Test
    void 自依赖按成环处理() {
        PluginDependencyResolver.Plan plan = PluginDependencyResolver.resolve(Arrays.asList(
                info("loop", List.of("loop"))));

        assertTrue(plan.getLoadOrder().isEmpty());
        assertTrue(plan.getRejected().containsKey("loop"));
    }

    @Test
    void 同级插件保持输入顺序以保证可复现() {
        // a 和 d 都依赖 root，二者之间无先后约束，顺序应与输入一致
        PluginDependencyResolver.Plan plan = PluginDependencyResolver.resolve(Arrays.asList(
                info("root", List.of()),
                info("a", List.of("root")),
                info("d", List.of("root"))));

        assertEquals(List.of("root", "a", "d"), plan.getLoadOrder());
    }

    @Test
    void 空输入不炸() {
        assertTrue(PluginDependencyResolver.resolve(List.of()).getLoadOrder().isEmpty());
        assertTrue(PluginDependencyResolver.resolve(null).getLoadOrder().isEmpty());
    }

    @Test
    void 重名插件只保留第一个() {
        PluginDependencyResolver.Plan plan = PluginDependencyResolver.resolve(Arrays.asList(
                info("dup", List.of()),
                info("dup", List.of("missing"))));

        assertEquals(List.of("dup"), plan.getLoadOrder(), "重名时不该因为后一个的依赖把前一个也拖累");
        assertFalse(plan.hasRejected());
    }
}
