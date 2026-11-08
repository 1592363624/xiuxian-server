package com.mtxgdn.plugin.builtin;

import com.mtxgdn.plugin.BuiltInPlugin;
import com.mtxgdn.plugin.PluginContext;

/**
 * 洞府玩法模块。
 * <p>
 * 配置键: plugins.cave（默认 true）
 * 功能: 灵力收集、洞府升级
 *
 * <p><b>关于阵法的依赖方向（改动前务必读）</b>：</p>
 *
 * <p>{@code CaveService} 确实会调 {@code FormationService} 取三个加成
 * （灵力收集倍率、修炼加成、心魔抗性）。看起来应该声明
 * {@code getSoftDependencies() = Set.of("formation")}，但<b>不能这么写</b> ——
 * 阵法模块已经硬依赖洞府（{@code formation → cave}），
 * 再补一条 {@code cave → formation} 就构成环，依赖解析器会把两个模块<b>一起拒载</b>，
 * 于是阵法和洞府同时消失。</p>
 *
 * <p>不需要声明也不会出问题的原因是：那三处调用都发生在<b>运行时</b>
 * （玩家收灵气/打坐时），不是启用期；而 {@code ServiceRegistry} 里的服务
 * 与模块开关是两件事 —— 模块被停用时服务实例依然存在，只是阵法表里没有活动阵法，
 * 三个加成方法自然返回 0。</p>
 *
 * <p>如果将来确实需要在启用期拿到阵法服务，请把它做成
 * {@code PluginServiceRegistry} 里的能力探测（拿不到就降级），而不是加一条依赖边。</p>
 */
public class CavePlugin extends BuiltInPlugin {

    @Override
    public String getName() {
        return "cave";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getDescription() {
        return "洞府系统：灵力收集、洞府修炼";
    }

    @Override
    public void onEnable(PluginContext context) {
        context.getLogger().info("洞府系统已启用");
    }

    @Override
    public void onDisable(PluginContext context) {
        context.getLogger().info("洞府系统已停用");
    }
}