package com.mtxgdn.plugin;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 插件依赖解析：拓扑排序 + 环检测 + 硬依赖缺失判定。
 *
 * <p>为什么需要它：插件加载顺序原本是 {@code pluginsDir.listFiles()} 的顺序，
 * 也就是文件系统顺序。依赖别人的插件如果恰巧排在前面，它的 {@code onEnable}
 * 里就查不到对方注册的物品/事件，表现为"功能没生效但不报错"——
 * 换台机器、换个 U 盘拷贝，行为就可能变。这种不确定性不该存在于启动流程里。</p>
 *
 * <p>规则：</p>
 * <ul>
 *   <li><b>硬依赖缺失</b> → 该插件不进加载列表，且<b>传递</b>：依赖它的插件也不进。</li>
 *   <li><b>软依赖缺失</b> → 不影响加载，只在排序上忽略这条边。</li>
 *   <li><b>依赖成环</b> → 环上的插件和依赖环的插件都不进加载列表，
 *       因为不存在合法的初始化顺序。</li>
 *   <li>自依赖（{@code depends} 里写了自己的名字）按成环处理。</li>
 * </ul>
 *
 * <p>本类是纯函数式的：不读文件、不加载类、不改全局状态，只吃元数据吐计划。
 * 这样它可以在测试里被完整覆盖，而不需要真的准备 jar。</p>
 */
public final class PluginDependencyResolver {

    private PluginDependencyResolver() { }

    /** 解析结果：能加载的按依赖序排列，不能加载的带原因。 */
    public static final class Plan {
        private final List<String> loadOrder;
        private final Map<String, String> rejected;

        Plan(List<String> loadOrder, Map<String, String> rejected) {
            this.loadOrder = Collections.unmodifiableList(loadOrder);
            this.rejected = Collections.unmodifiableMap(rejected);
        }

        /** 可以直接按顺序初始化的插件名（依赖方一定排在被依赖方之后）。 */
        public List<String> getLoadOrder() { return loadOrder; }

        /** 被拒绝加载的插件名 → 人类可读的原因。 */
        public Map<String, String> getRejected() { return rejected; }

        public boolean hasRejected() { return !rejected.isEmpty(); }

        @Override
        public String toString() {
            if (rejected.isEmpty()) return "依赖解析: " + loadOrder.size() + " 个插件可加载";
            return "依赖解析: " + loadOrder.size() + " 个可加载, " + rejected.size() + " 个被拒";
        }
    }

    /**
     * 解析依赖关系。
     *
     * @param infos 本次扫描到的全部插件元数据（顺序只影响同级插件的相对次序，不影响正确性）
     */
    public static Plan resolve(Collection<PluginInfo> infos) {
        Map<String, PluginInfo> byName = new LinkedHashMap<>();
        if (infos != null) {
            for (PluginInfo info : infos) {
                if (info == null || info.getName() == null || info.getName().isEmpty()) continue;
                byName.putIfAbsent(info.getName(), info);
            }
        }

        Map<String, String> rejected = new LinkedHashMap<>();

        // 1. 自依赖直接判死（写成环会得到一句莫名其妙的环描述，不如直说）
        for (Map.Entry<String, PluginInfo> e : byName.entrySet()) {
            if (e.getValue().getDepends().contains(e.getKey())) {
                rejected.put(e.getKey(), "依赖了自己");
            }
        }

        // 2. 硬依赖缺失，并做传递闭包：A 依赖 B、B 被拒，则 A 也被拒。
        //    反复扫描到不再有新拒绝为止；插件数最多几十个，代价可以忽略。
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Map.Entry<String, PluginInfo> e : byName.entrySet()) {
                String name = e.getKey();
                if (rejected.containsKey(name)) continue;
                for (String dep : e.getValue().getDepends()) {
                    if (dep == null || dep.isEmpty()) continue;
                    String reason = null;
                    if (rejected.containsKey(dep)) {
                        reason = "所需插件 " + dep + " 未能加载（" + rejected.get(dep) + "）";
                    } else if (!byName.containsKey(dep)) {
                        reason = "缺少必需插件 " + dep;
                    }
                    if (reason != null) {
                        rejected.put(name, reason);
                        changed = true;
                        break;
                    }
                }
            }
        }

        // 3. 在剩下的插件上做拓扑排序（Kahn）。
        //    硬依赖和软依赖都变成排序边：软依赖缺失时边不存在，但存在时必须先初始化。
        Map<String, Integer> indegree = new LinkedHashMap<>();
        for (String name : byName.keySet()) {
            if (!rejected.containsKey(name)) indegree.put(name, 0);
        }
        Map<String, Set<String>> dependents = new LinkedHashMap<>();
        for (String name : new ArrayList<>(indegree.keySet())) {
            PluginInfo info = byName.get(name);
            Set<String> deps = new LinkedHashSet<>();
            for (String d : info.getDepends()) {
                if (d != null && indegree.containsKey(d) && !d.equals(name)) deps.add(d);
            }
            for (String d : info.getSoftDepends()) {
                if (d != null && indegree.containsKey(d) && !d.equals(name)) deps.add(d);
            }
            for (String d : deps) {
                dependents.computeIfAbsent(d, k -> new LinkedHashSet<>()).add(name);
                indegree.merge(name, 1, Integer::sum);
            }
        }

        Deque<String> ready = new ArrayDeque<>();
        for (Map.Entry<String, Integer> e : indegree.entrySet()) {
            if (e.getValue() == 0) ready.add(e.getKey());
        }
        List<String> order = new ArrayList<>();
        while (!ready.isEmpty()) {
            String cur = ready.poll();
            order.add(cur);
            for (String dependent : dependents.getOrDefault(cur, Collections.emptySet())) {
                if (indegree.merge(dependent, -1, Integer::sum) == 0) {
                    ready.add(dependent);
                }
            }
        }

        // 4. 排不进去的就是环上的、或者依赖了环上插件的
        List<String> leftover = new ArrayList<>();
        for (Map.Entry<String, Integer> e : indegree.entrySet()) {
            if (e.getValue() > 0) leftover.add(e.getKey());
        }
        if (!leftover.isEmpty()) {
            String cycle = describeCycle(leftover, dependents);
            for (String name : leftover) {
                rejected.put(name, cycle != null
                        ? "依赖成环：" + cycle
                        : "依赖无法满足（可能是环的一部分）");
            }
        }

        return new Plan(order, rejected);
    }

    /**
     * 在剩余节点里找出一个具体的环，用于给出可操作的报错信息。
     * 只说"有环"而不说是哪几个插件，等于没报错。
     */
    private static String describeCycle(List<String> leftover,
                                        Map<String, Set<String>> dependents) {
        Set<String> scope = new LinkedHashSet<>(leftover);
        Map<String, Boolean> done = new LinkedHashMap<>();

        for (String start : leftover) {
            List<String> path = new ArrayList<>();
            String cycle = dfsCycle(start, scope, dependents, done, path);
            if (cycle != null) return cycle;
        }
        return null;
    }

    private static String dfsCycle(String node, Set<String> scope,
                                   Map<String, Set<String>> dependents,
                                   Map<String, Boolean> done, List<String> path) {
        if (Boolean.TRUE.equals(done.get(node))) return null;
        int at = path.indexOf(node);
        if (at >= 0) {
            List<String> cyclePath = new ArrayList<>(path.subList(at, path.size()));
            cyclePath.add(node);
            return String.join(" → ", cyclePath);
        }
        path.add(node);
        for (String next : dependents.getOrDefault(node, Collections.emptySet())) {
            if (!scope.contains(next)) continue;
            String found = dfsCycle(next, scope, dependents, done, path);
            if (found != null) {
                path.remove(path.size() - 1);
                return found;
            }
        }
        path.remove(path.size() - 1);
        done.put(node, Boolean.TRUE);
        return null;
    }
}
