package com.mtxgdn.plugin;

/**
 * 内置插件基类。jar 内的玩法模块通过继承此类实现。
 * <p>
 * 与外部插件不同，内置插件：
 * <ul>
 *   <li>直接编译在主 jar 中，无需单独打包</li>
 *   <li>通过 {@code config/plugins.yml} 控制启用/禁用（键 {@code plugins.&lt;name&gt;}）</li>
 *   <li>拥有唯一名称，用于配置和状态查询</li>
 * </ul>
 */
public abstract class BuiltInPlugin implements Plugin {

    /** 获取插件名称（配置键、日志使用）。 */
    public abstract String getName();

    /** 获取插件版本。 */
    public abstract String getVersion();

    /** 获取插件描述。 */
    public String getDescription() {
        return "";
    }

    /** 获取插件类别（WebUI 展示分组用）。玩法模块 gameplay、消息通道 adapter、实验性功能 experimental。 */
    public String getCategory() {
        return "gameplay";
    }

    /**
     * 配置文件中缺失本插件开关时的默认值。
     * <p>绝大多数模块默认启用；实验性插件可覆写为 false，保持“实验性内容默认关闭”的语义。
     */
    public boolean getDefaultEnabled() {
        return true;
    }

    /**
     * 本模块对外提供的能力（接口 → 实现），登记到 {@link PluginServiceRegistry}。
     *
     * <p>内置模块与外置插件不同：它们共用一个 {@code PluginContext}，
     * 没法靠 context 判断"是谁在登记"，所以归属由注册表按模块名代为标注。
     * 这样停用某个模块时，只注销它自己提供的那几个服务。</p>
     *
     * <p>默认不提供任何服务。需要被其它模块/插件调用的模块覆写本方法，
     * 并只暴露<b>接口</b> —— 直接暴露实现类等于把内部结构变成公共契约。</p>
     */
    public java.util.Map<Class<?>, Object> getProvidedServices() {
        return java.util.Collections.emptyMap();
    }

    /**
     * 硬依赖的内置模块名：未注册、或注册了但被配置停用时，本模块也不会启用。
     *
     * <p>只声明<b>代码能证明</b>的依赖。比如阵法模块在 {@code placeFormation} 里
     * 明确要求玩家已有洞府，洞府模块关掉时阵法功能必然全废 —— 这就是真实的硬依赖。
     * 反过来，"都需要玩家数据"这类不算：玩家是核心服务，不是可开关的模块，
     * 把它写成依赖只会让所有模块都挂在一个开关上，失去意义。</p>
     *
     * <p>本字段只影响启用顺序与前置检查，不会改变模块自身的行为。</p>
     */
    public java.util.Set<String> getDependencies() {
        return java.util.Collections.emptySet();
    }

    /**
     * 软依赖的内置模块名：缺失时本模块照常启用，由模块自己降级。
     * 常用于"配合某某模块更好用"的场景，配合 {@link PluginServiceRegistry} 做能力探测。
     */
    public java.util.Set<String> getSoftDependencies() {
        return java.util.Collections.emptySet();
    }

    /** 配置键（{@code config/plugins.yml} 中的键名）对应本插件名。 */
    @Override
    public String toString() {
        return getName();
    }
}
