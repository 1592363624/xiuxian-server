package com.mtxgdn.common.service;

import com.mtxgdn.game.service.*;
import com.mtxgdn.util.GameLogger;

/**
 * 容器的静态门面。
 *
 * <p><b>只剩三个方法</b>：{@link #container()} / {@link #install(ServiceContainer)} /
 * {@link #reset()}。历史上这里还有 30 个 {@code getXxxService()} 静态 delegator，
 * 四百多个调用点靠它们拿服务 —— 那是一层看不见的全局状态，测试之间互相污染、
 * 依赖关系也看不出来。迁移后：</p>
 *
 * <ul>
 *   <li>服务之间、指令、REST 资源、WebSocket、适配器、物品效果都通过
 *       {@link ServiceLookup}（构造参数或框架边界的显式绑定）取服务；</li>
 *   <li>静态门面只给"进程级单例"用：启动流程、框架边界、测试夹具。
 *       业务代码里再出现 {@code ServiceRegistry.container()} 应被视为异常。</li>
 * </ul>
 *
 * <p>为什么连静态 getter 一起删掉，而不是留着"方便"：只要还留着一个
 * {@code getXxxService()}，新代码就会继续用它，迁移会永远停在半路。
 * 删掉之后，任何绕过注入的写法在编译期就过不去。</p>
 */
public final class ServiceRegistry {

    private static final GameLogger LOG = GameLogger.getLogger(ServiceRegistry.class);

    private static volatile ServiceContainer current = new ServiceContainer();

    private ServiceRegistry() {
    }

    /** 当前容器。首次访问时创建，之后一直复用同一个实例。 */
    public static ServiceContainer container() {
        return current;
    }

    /**
     * 换掉当前容器，并注销旧容器的换库回调。
     *
     * <p>给测试用：每个测试类可以拿一个干净容器，而不是共享整个 JVM 生命周期里
     * 那一套服务实例。也允许将来把某个子系统换成另一套实现。</p>
     */
    public static void install(ServiceContainer container) {
        if (container == null) {
            throw new IllegalArgumentException("container 不能为 null");
        }
        ServiceContainer previous = current;
        current = container;
        if (previous != null && previous != container) {
            previous.close();
        }
    }

    /**
     * 丢弃当前容器，下次访问时重建一套。
     *
     * <p>旧容器会被 {@link ServiceContainer#close()}：它启动过的后台任务按逆序停止，
     * 换库回调也一并摘掉。因此 {@code reset()} 之后的进程是"零后台线程"状态 ——
     * 测试里删库、重建连接池不会再被旧调度器插进来写。</p>
     *
     * <p>新容器<b>不会</b>自动启动后台任务：那是启动流程（{@code Main}）的事，
     * 不是构造容器的事。需要任务时显式调 {@link ServiceContainer#startBackgroundTasks()}。</p>
     */
    public static void reset() {
        install(new ServiceContainer());
    }

}
