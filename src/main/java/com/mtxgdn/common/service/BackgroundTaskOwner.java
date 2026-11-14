package com.mtxgdn.common.service;

/**
 * 后台定时任务的生命周期归属。
 *
 * <p><b>为什么需要这个接口</b>：{@code PlayerService} / {@code BuffService} 等 8 个服务
 * 原来在自己的 <b>static 初始化块</b>里启动调度器。这带来两个后果：</p>
 *
 * <ul>
 *   <li>换 {@link ServiceContainer} 不会停掉旧容器的调度线程 —— 旧实例被丢弃，
 *       线程还活着，继续往"正在重建的库"里写。{@code TestBase} 里那一大段
 *       "先停调度器、再删库"的注释就是这个问题的症状；</li>
 *   <li>调度器的启动时机由"类第一次被加载"决定，而不是由启动流程决定，
 *       测试里 {@code new PlayerService()} 也会顺手起一个线程。</li>
 * </ul>
 *
 * <p>现在改成：<b>构造时不启动，容器显式调用 {@link #startBackgroundTasks()}</b>；
 * 容器 {@code close()} 时按逆序调用 {@link #stopBackgroundTasks()}。这样
 * "一套服务实例"与"一批后台线程"一一对应，测试里 {@code new ServiceContainer()}
 * 就是真正干净的实例，不会凭空多出线程。</p>
 *
 * <p>实现必须是<b>幂等</b>的：重复 start 不重复注册任务，重复 stop 不抛异常。
 * 测试会连续 {@code reset()} 多次，幂等是硬要求。</p>
 */
public interface BackgroundTaskOwner {

    /**
     * 启动本服务的后台任务。由 {@link ServiceContainer#startBackgroundTasks()} 调用。
     * 重复调用应当无副作用。
     */
    void startBackgroundTasks();

    /**
     * 停止本服务的后台任务。由 {@link ServiceContainer#close()} 调用。
     * 重复调用应当无副作用，且未启动过时调用也必须安全。
     */
    void stopBackgroundTasks();
}
