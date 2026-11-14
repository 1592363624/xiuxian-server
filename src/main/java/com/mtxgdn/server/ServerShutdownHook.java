package com.mtxgdn.server;

import com.mtxgdn.plugin.PluginManager;
import com.mtxgdn.util.GameLogger;

import java.util.ArrayList;
import java.util.List;

public class ServerShutdownHook {

    private static final GameLogger LOG = GameLogger.getLogger(ServerShutdownHook.class);

    private final List<ShutdownTask> tasks = new ArrayList<>();

    public void addTask(Runnable task) {
        tasks.add(() -> {
            try {
                task.run();
            } catch (Exception e) {
                LOG.warn("关闭任务执行异常: " + e.getMessage());
            }
        });
    }

    public void register() {
        Runtime.getRuntime().addShutdownHook(new Thread(this::execute, "shutdown-hook"));
    }

    private void execute() {
        LOG.info("服务器正在优雅关闭...");

        for (ShutdownTask task : tasks) {
            try {
                task.shutdown();
            } catch (Exception e) {
                LOG.warn("关闭任务执行失败: " + e.getMessage());
            }
        }

        LOG.info("服务器已关闭");
    }

    public static ServerShutdownHook createDefault() {
        ServerShutdownHook hook = new ServerShutdownHook();

        hook.addTask(() -> PluginManager.getInstance().disablePlugins());
        hook.addTask(() -> PluginManager.getInstance().stopFileWatcher());
        // 后台定时任务的停止统一由 ServiceContainer.close() 负责（Main 的关服钩子里调用）：
        // 逐个服务点 shutdown 的写法漏过 Economy/Season/Sect/Trade，表现为关服后
        // 这些任务还在查已关闭的连接池。容器持有"启动过哪些任务"的清单，不会漏。

        return hook;
    }

    @FunctionalInterface
    public interface ShutdownTask {
        void shutdown() throws Exception;
    }
}