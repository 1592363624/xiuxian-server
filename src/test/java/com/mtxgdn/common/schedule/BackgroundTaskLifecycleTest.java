package com.mtxgdn.common.schedule;

import com.mtxgdn.common.service.ServiceContainer;
import com.mtxgdn.common.service.ServiceRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 后台任务生命周期的<b>运行时</b>契约测试。
 *
 * <p>{@link ScheduledTaskLifecycleTest} 读源码、钉住"写法"；本类真的把任务跑起来，
 * 钉住"行为" —— 两者缺一不可：只读源码看不出闭包是否真的停在旧实例上，
 * 只跑行为则容易在静态块被重新引入时静默通过（因为行为恰好没变）。</p>
 *
 * <p>核心承诺：<b>{@code close()} 之后，这个容器启动的线程必须消失</b>。
 * 这正是改造前做不到的事 —— 静态块里的调度器没有归属，换容器换不掉它。</p>
 */
class BackgroundTaskLifecycleTest {

    /** 容器启动后会出现的线程名（与各服务里 {@code Schedulers.newDaemonSingle(name)} 对齐）。 */
    private static final Set<String> EXPECTED_THREADS = Set.of(
            "player-auto-heal",
            "buff-expire-sweeper",
            "cave-spirit-energy",
            "formation-tick",
            "trade-market-sweeper",
            "auction-settler",
            "sect-succession-sweeper",
            "global-season-tick");

    @AfterEach
    void cleanup() {
        // 无论断言成功失败，都不给后续测试留下线程
        ServiceRegistry.reset();
    }

    @Test
    @DisplayName("startBackgroundTasks 启动全部任务，close 后线程全部消失")
    void closeStopsEveryStartedThread() throws Exception {
        ServiceContainer container = new ServiceContainer();
        try {
            container.startBackgroundTasks();
            assertTrue(container.areBackgroundTasksStarted(), "启动后应处于已启动状态");

            for (String name : EXPECTED_THREADS) {
                assertTrue(threadExists(name),
                        "启动后找不到线程 " + name + "，说明该服务的任务没有被容器拉起来");
            }

            container.close();

            for (String name : EXPECTED_THREADS) {
                assertTrue(threadGoneWithin(name, 2000),
                        "close() 之后线程 " + name + " 仍然存活 —— "
                                + "调度器没被容器停掉，换库后它会继续往新库写");
            }

            assertFalse(container.areBackgroundTasksStarted(), "close 后应复位为未启动");
        } finally {
            container.close();
        }
    }

    @Test
    @DisplayName("重复 start/stop 必须幂等（测试会连续 reset 多次）")
    void lifecycleIsIdempotent() {
        ServiceContainer container = new ServiceContainer();
        try {
            container.startBackgroundTasks();
            Set<String> first = managedThreads();
            container.startBackgroundTasks();
            assertEquals(first, managedThreads(),
                    "第二次 start 又建了一套线程：关容器时会漏掉其中一套");

            // 重复 close：不得抛异常，第二次应该是干净的空操作
            container.close();
            container.close();
            assertFalse(threadExists("player-auto-heal"), "重复 stop 不应抛异常，且线程应已停止");
        } finally {
            container.close();
        }
    }

    @Test
    @DisplayName("换容器（reset）必须停掉旧容器启动的任务")
    void resetStopsPreviousContainerThreads() throws Exception {
        ServiceContainer old = new ServiceContainer();
        ServiceRegistry.install(old);
        old.startBackgroundTasks();
        assertTrue(threadExists("player-auto-heal"), "旧容器应已启动任务");

        ServiceRegistry.reset();

        assertTrue(threadGoneWithin("player-auto-heal", 2000),
                "reset() 之后旧容器的线程还活着 —— 这正是改造前 TestBase 要手工逐个 shutdown 的原因");
    }

    // ==================== 夹具 ====================

    private static Set<String> managedThreads() {
        return Thread.getAllStackTraces().keySet().stream()
                .map(Thread::getName)
                .filter(n -> EXPECTED_THREADS.stream().anyMatch(n::startsWith))
                .collect(Collectors.toSet());
    }

    private static boolean threadExists(String name) {
        assertNotNull(name);
        return Thread.getAllStackTraces().keySet().stream()
                .anyMatch(t -> t.getName().startsWith(name) && t.isAlive());
    }

    /** 在等待窗口内观察线程是否消失；返回 {@code true} 表示确实消失了。 */
    private static boolean threadGoneWithin(String name, long millis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + millis;
        while (System.currentTimeMillis() < deadline) {
            if (!threadExists(name)) return true;
            Thread.sleep(25);
        }
        return !threadExists(name);
    }
}
