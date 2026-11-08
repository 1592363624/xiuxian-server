package com.mtxgdn.plugin;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 内置模块依赖图的整体守卫。
 *
 * <p>单个模块的依赖是否正确只能人工核实，但<b>整张图是否可用</b>可以自动守住：
 * 一旦有人加了一条成环的边，或被依赖的模块名写错，启动时就会静默少掉一批模块。
 * 这个测试让那种错误在提交前就红。</p>
 */
class BuiltInPluginGraphTest {

    private static List<PluginInfo> catalogInfos() {
        List<PluginInfo> infos = new ArrayList<>();
        for (BuiltInPlugin module : PluginCatalog.createAll()) {
            infos.add(new PluginInfo(module.getName(), module.getVersion(), "", "", "",
                    new ArrayList<>(module.getDependencies()),
                    new ArrayList<>(module.getSoftDependencies())));
        }
        return infos;
    }

    @Test
    void 完整模块清单无环且零拒载() {
        PluginDependencyResolver.Plan plan = PluginDependencyResolver.resolve(catalogInfos());

        assertFalse(plan.hasRejected(),
                "依赖图不可用，会导致启动时静默少模块: " + plan.getRejected());
        assertEquals(PluginCatalog.createAll().size(), plan.getLoadOrder().size(),
                "所有模块都应可加载");
    }

    @Test
    void 依赖目标必须是真实存在的模块() {
        List<PluginInfo> infos = catalogInfos();
        Map<String, PluginInfo> byName = new LinkedHashMap<>();
        for (PluginInfo i : infos) byName.put(i.getName(), i);

        List<String> unknown = new ArrayList<>();
        for (PluginInfo i : infos) {
            for (String dep : i.getDepends()) {
                if (!byName.containsKey(dep)) unknown.add(i.getName() + " → " + dep);
            }
            for (String dep : i.getSoftDepends()) {
                if (!byName.containsKey(dep)) unknown.add(i.getName() + " ~> " + dep);
            }
        }
        assertTrue(unknown.isEmpty(), "依赖名写错了: " + unknown);
    }

    @Test
    void 模块不会依赖自己() {
        List<String> selfRefs = new ArrayList<>();
        for (BuiltInPlugin module : PluginCatalog.createAll()) {
            if (module.getDependencies().contains(module.getName())) selfRefs.add(module.getName());
            if (module.getSoftDependencies().contains(module.getName())) selfRefs.add(module.getName());
        }
        assertTrue(selfRefs.isEmpty(), "自依赖会被判成环: " + selfRefs);
    }

    @Test
    void 硬依赖必须排在依赖方之前() {
        PluginDependencyResolver.Plan plan = PluginDependencyResolver.resolve(catalogInfos());
        List<String> order = plan.getLoadOrder();

        for (BuiltInPlugin module : PluginCatalog.createAll()) {
            if (!order.contains(module.getName())) continue;
            int idx = order.indexOf(module.getName());
            for (String dep : module.getDependencies()) {
                int depIdx = order.indexOf(dep);
                assertTrue(depIdx >= 0 && depIdx < idx,
                        "硬依赖必须在前: " + module.getName() + " 依赖 " + dep
                                + "，实际顺序 " + order);
            }
        }
    }

    @Test
    void 洞府与阵法之间只声明一个方向_否则会成环两败() {
        // 阵法硬依赖洞府（布阵必须已有洞府），洞府不声明对阵法依赖
        // （洞府只是运行时读阵法加成取不到就为 0）。这条断言是防回归的护栏：
        // 若有人"顺手"给洞府补上 formation 依赖，上面那个无环测试会红，
        // 但这里明确指出是哪一对，省得对着报错猜。
        BuiltInPlugin formation = find("formation");
        BuiltInPlugin cave = find("cave");
        assertTrue(formation.getDependencies().contains("cave"), "阵法应硬依赖洞府");
        assertFalse(cave.getDependencies().contains("formation"), "洞府不能硬依赖阵法");
        assertFalse(cave.getSoftDependencies().contains("formation"), "洞府也不能软依赖阵法");
    }

    private static BuiltInPlugin find(String name) {
        for (BuiltInPlugin m : PluginCatalog.createAll()) {
            if (m.getName().equals(name)) return m;
        }
        throw new AssertionError("未找到模块: " + name);
    }
}
