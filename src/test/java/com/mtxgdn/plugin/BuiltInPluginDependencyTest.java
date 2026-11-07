package com.mtxgdn.plugin;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 内置模块依赖的回归测试。
 *
 * <p>内置模块与外置插件是两套体系：外置插件每个有独立 {@code PluginContext}，
 * 内置模块共用一个。依赖关系也必须各管各的，这里锁住内置这一侧的行为。</p>
 *
 * <p><b>测试隔离</b>：模块启停会把开关写进 plugins.yml，
 * 用 {@link PluginSwitchTestSupport} 把探针模块的开关限制在临时文件里，
 * 避免残留到共享配置里导致"下次运行才失败"。</p>
 */
class BuiltInPluginDependencyTest {

    private final PluginSwitchTestSupport switches = PluginSwitchTestSupport.create();
    private final BuiltInPluginRegistry registry = BuiltInPluginRegistry.getInstance();

    @BeforeEach
    void isolateSwitches() {
        switches.isolate();
    }

    @AfterEach
    void restoreSwitches() {
        switches.restore();
    }

    /** 记录自身是否被启用，用于断言顺序与拒用。 */
    private static final class Probe extends BuiltInPlugin {
        private final String name;
        private final Set<String> deps;
        private final List<String> trace;
        private boolean enabled;

        Probe(String name, Set<String> deps, List<String> trace) {
            this.name = name;
            this.deps = deps;
            this.trace = trace;
        }

        @Override public String getName() { return name; }
        @Override public String getVersion() { return "1.0.0"; }
        @Override public Set<String> getDependencies() { return deps; }

        @Override
        public void onEnable(PluginContext context) {
            enabled = true;
            trace.add(name);
        }

        @Override
        public void onDisable(PluginContext context) {
            enabled = false;
        }

        boolean isEnabledFlag() { return enabled; }
    }

    private PluginContext context() {
        return PluginContext.createSharedContext(new File("data"), getClass().getClassLoader());
    }

    @Test
    void 依赖的模块先于依赖方启用() {
        List<String> trace = new ArrayList<>();
        Probe cave = new Probe("dep_test_cave", Set.of(), trace);
        Probe formation = new Probe("dep_test_formation", Set.of("dep_test_cave"), trace);
        registry.register(cave);
        registry.register(formation);

        registry.enableAll(context());

        assertEquals(List.of("dep_test_cave", "dep_test_formation"), trace,
                "被依赖方必须先启用，否则依赖方在 onEnable 里拿不到它的东西");
        assertTrue(formation.isEnabledFlag());

        registry.disablePlugin("dep_test_formation");
        registry.disablePlugin("dep_test_cave");
    }

    @Test
    void 依赖未注册的模块时拒绝启用并说明原因() {
        List<String> trace = new ArrayList<>();
        Probe orphan = new Probe("dep_test_orphan", Set.of("no_such_module"), trace);
        registry.register(orphan);

        registry.enableAll(context());

        assertFalse(orphan.isEnabledFlag(), "依赖不存在时不该启用");
        assertTrue(trace.isEmpty(), "onEnable 不该被调用");
        String reason = registry.getDependencyBlockReason("dep_test_orphan");
        assertTrue(reason != null && reason.contains("no_such_module"),
                "原因要点名缺哪个模块: " + reason);
    }

    @Test
    void 依赖被停用的模块时拒绝启用() {
        List<String> trace = new ArrayList<>();
        Probe base = new Probe("dep_test_base", Set.of(), trace);
        Probe dependent = new Probe("dep_test_dependent", Set.of("dep_test_base"), trace);
        registry.register(base);
        registry.register(dependent);

        registry.enableAll(context());
        assertTrue(dependent.isEnabledFlag(), "前置：依赖满足时应能启用");

        registry.disablePlugin("dep_test_dependent");
        registry.disablePlugin("dep_test_base");

        // 基础模块被停用后，依赖方不该还能被拉起
        String reason = registry.getDependencyBlockReason("dep_test_dependent");
        assertTrue(reason != null && reason.contains("dep_test_base"),
                "应指明依赖模块未启用: " + reason);
        assertFalse(registry.enablePlugin("dep_test_dependent"), "依赖未就绪时启用应失败");
    }

    @Test
    void 停用不会连锁带走依赖方但会记录依赖关系() {
        List<String> trace = new ArrayList<>();
        Probe base = new Probe("dep_test_base2", Set.of(), trace);
        Probe dependent = new Probe("dep_test_dependent2", Set.of("dep_test_base2"), trace);
        registry.register(base);
        registry.register(dependent);

        registry.enableAll(context());
        assertTrue(dependent.isEnabledFlag());

        assertTrue(registry.getActiveDependents("dep_test_base2").contains("dep_test_dependent2"),
                "应能列出谁依赖我，供停用时告警");

        registry.disablePlugin("dep_test_base2");
        assertTrue(dependent.isEnabledFlag(),
                "不连锁停用：一次操作带走半个服比'某功能悄悄少了'更难善后");

        registry.disablePlugin("dep_test_dependent2");
    }

    @Test
    void 无依赖时全部正常启用_不因改动影响既有模块() {
        List<String> trace = new ArrayList<>();
        Probe a = new Probe("dep_test_plain_a", Set.of(), trace);
        Probe b = new Probe("dep_test_plain_b", Set.of(), trace);
        registry.register(a);
        registry.register(b);

        registry.enableAll(context());

        assertTrue(a.isEnabledFlag() && b.isEnabledFlag());
        assertNull(registry.getDependencyBlockReason("dep_test_plain_a"));

        registry.disablePlugin("dep_test_plain_a");
        registry.disablePlugin("dep_test_plain_b");
    }

    @Test
    void 阵法模块硬依赖洞府模块() {
        // 直接校验真实清单：FormationService.placeFormation 第一道检查就是"有没有洞府"
        assertEquals(Set.of("cave"), new com.mtxgdn.plugin.builtin.FormationPlugin().getDependencies());
    }

    @Test
    void 宗门战模块硬依赖宗门模块() {
        assertEquals(Set.of("sect"),
                new com.mtxgdn.plugin.experimental.SectWarPlugin().getDependencies());
    }
}
