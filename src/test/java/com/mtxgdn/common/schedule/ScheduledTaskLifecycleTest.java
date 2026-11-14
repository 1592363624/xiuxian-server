package com.mtxgdn.common.schedule;

import com.mtxgdn.common.service.ServiceRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 后台定时任务的生命周期必须归容器，且不能在连接池关闭后继续查库。
 *
 * <p><b>改造前的缺陷形态</b>：仓库里有十来个服务在<b>静态初始化块</b>里直接起
 * "每 N 秒扫一遍"的守护任务，而：</p>
 *
 * <ul>
 *   <li>静态块里的调度器与"服务实例"无关 —— 换掉 {@code ServiceContainer} 时旧实例被丢弃，
 *       线程还活着，继续往"正在重建的库"里写；{@code TestBase} 里那段
 *       "先逐个 stop、再删库"的注释就是这个问题的症状；</li>
 *   <li>启动时机由"类第一次被加载"决定，而不是由启动流程决定，测试里
 *       {@code new PlayerService(ServiceRegistry.container())} 也会顺手起一个后台线程；</li>
 *   <li>关服钩子只停了 3 个服务，其余没人停，关服后继续查已关闭的连接池，
 *       刷一屏 "Connection is closed" 把真正的关闭日志淹掉。</li>
 * </ul>
 *
 * <p><b>现在的约定</b>（本测试盯住的就是这几条）：</p>
 * <ol>
 *   <li>起后台任务的服务实现 {@link com.mtxgdn.common.service.BackgroundTaskOwner}；</li>
 *   <li>这些服务里<b>不允许</b>再出现 static 的 {@code ScheduledExecutorService} 字段，
 *       也不允许在静态块里注册任务 —— 构造实例不能有"顺手起线程"的副作用；</li>
 *   <li>{@code ServiceContainer.startBackgroundTasks()} 把它们全部启动，
 *       {@code close()} 按逆序全部停止，于是"一套实例对应一批线程"；</li>
 *   <li>每个 tick 仍然先问 {@code DatabaseManager.isReady()}；</li>
 *   <li>线程是 daemon 且带可读名字。</li>
 * </ol>
 */
class ScheduledTaskLifecycleTest {

    private static final Path SERVICES = Paths.get("src/main/java/com/mtxgdn/game/service");
    private static final Path CONTAINER =
            Paths.get("src/main/java/com/mtxgdn/common/service/ServiceContainer.java");
    private static final Path OWNER =
            Paths.get("src/main/java/com/mtxgdn/common/service/BackgroundTaskOwner.java");

    /**
     * 起后台任务的服务。
     *
     * <p>{@code SeasonService} 不在这个集合里：它目前仍是"全局时钟"（状态与方法全 static，
     * 48 处静态调用点），调度器由容器的 {@code startScheduler()/stopScheduler()} 启停，
     * 但实例本身不实现 {@code BackgroundTaskOwner}。等它收编成实例服务时，
     * 应当从 {@link #STATIC_SCHEDULER_ALLOWLIST} 里删掉并加进本集合。</p>
     */
    private static final List<String> TASK_OWNERS = List.of(
            "PlayerService",
            "BuffService",
            "CaveService",
            "FormationService",
            "TradeService",
            "EconomyService",
            "SectService");

    /**
     * 允许保留 static 调度器的服务 —— 只能是"还没变成实例服务"的全局时钟。
     *
     * <p>这个白名单是硬约束：往里面加一项，等于承认那个服务还没有生命周期归属。</p>
     */
    private static final List<String> STATIC_SCHEDULER_ALLOWLIST = List.of("SeasonService");

    @Test
    @DisplayName("起后台任务的服务都必须实现 BackgroundTaskOwner")
    void scheduledServicesImplementBackgroundTaskOwner() throws IOException {
        List<String> missing = new ArrayList<>();
        for (String service : TASK_OWNERS) {
            String code = stripComments(read(service));
            if (!code.contains("BackgroundTaskOwner")) {
                missing.add(service);
            }
        }
        assertTrue(missing.isEmpty(),
                "这些服务起了后台任务却没实现 BackgroundTaskOwner，容器无法统一启停：\n"
                        + String.join("\n", missing));
    }

    @Test
    @DisplayName("后台任务服务里不允许有 static 调度器字段")
    void noStaticSchedulerFieldsInTaskOwners() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (String service : TASK_OWNERS) {
            String code = stripComments(read(service));
            Matcher m = Pattern.compile(
                    "(?m)^\\s*(?:private|public|protected)?\\s*static\\s+(?:final\\s+)?"
                            + "ScheduledExecutorService\\s+(\\w+)").matcher(code);
            while (m.find()) {
                offenders.add(service + " 仍有 static 调度器字段 " + m.group(1)
                        + "：静态调度器换容器换不掉，旧线程会继续写新库");
            }
        }
        assertTrue(offenders.isEmpty(), String.join("\n", offenders));
    }

    @Test
    @DisplayName("后台任务服务里不允许在静态块中注册定时任务")
    void noSchedulerRegistrationInStaticBlocks() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (String service : TASK_OWNERS) {
            String code = stripComments(read(service));
            for (String block : staticBlocks(code)) {
                if (block.contains("scheduleAtFixedRate(") || block.contains("scheduleWithFixedDelay(")) {
                    offenders.add(service + " 在静态块里注册了定时任务："
                            + "构造/类加载不应有副作用，启动权归 ServiceContainer");
                }
            }
        }
        assertTrue(offenders.isEmpty(), String.join("\n", offenders));
    }

    @Test
    @DisplayName("容器必须启动并停止每一个后台任务服务")
    void containerStartsAndStopsEveryTaskOwner() throws IOException {
        String code = stripComments(Files.readString(CONTAINER, StandardCharsets.UTF_8));

        List<String> missingStart = new ArrayList<>();
        for (String service : TASK_OWNERS) {
            // startTask(getXxxService()) —— 具体写法允许变化，但必须提到该服务
            if (!code.contains("get" + service + "()")) {
                missingStart.add(service);
            }
        }
        assertTrue(missingStart.isEmpty(),
                "ServiceContainer.startBackgroundTasks() 没有启动这些服务：\n"
                        + String.join("\n", missingStart));

        assertTrue(code.contains("startBackgroundTasks()"),
                "ServiceContainer 里找不到 startBackgroundTasks() 调用");
        assertTrue(code.contains("stopBackgroundTasks()"),
                "ServiceContainer 里找不到 stopBackgroundTasks() 调用 —— 关容器时没人停任务");
    }

    @Test
    @DisplayName("关服路径必须关掉容器（而不是逐个服务点 shutdown）")
    void shutdownPathClosesContainer() throws IOException {
        String main = stripComments(Files.readString(
                Paths.get("src/main/java/com/mtxgdn/Main.java"), StandardCharsets.UTF_8));
        assertTrue(main.contains(".close()"),
                "Main 的关服钩子没有调用 ServiceContainer.close()，"
                        + "后台任务会活过连接池关闭");
        assertTrue(main.contains("ServiceRegistry.container()"),
                "Main 的关服钩子没有取容器 —— 逐个服务点 shutdown 的写法漏过 "
                        + "Economy/Season/Sect/Trade");
    }

    @Test
    @DisplayName("每个定时 tick 方法都要先检查连接池是否可用")
    void everyTickGuardsOnDatabaseReady() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (String service : allScheduledServices()) {
            String code = stripComments(read(service));
            for (String method : registeredTickMethods(code, service)) {
                String body = methodBody(code, method);
                if (body == null) {
                    offenders.add(service + "." + method + "：找不到方法体，测试无法确认守卫是否存在");
                    continue;
                }
                if (!body.contains("DatabaseManager.isReady()")) {
                    offenders.add(service + "." + method
                            + "：tick 开头没有 DatabaseManager.isReady() 守卫，"
                            + "连接池关掉之后它还会继续查库并刷异常");
                }
            }
        }
        assertTrue(offenders.isEmpty(),
                "定时 tick 必须先检查连接池：\n" + String.join("\n", offenders));
    }

    @Test
    @DisplayName("扫描到的 tick 方法不能为空，否则上面的断言等于空转")
    void scanIsNotEmpty() throws IOException {
        int found = 0;
        for (String service : allScheduledServices()) {
            String code = stripComments(read(service));
            found += registeredTickMethods(code, service).size();
        }
        assertTrue(found > 0, "一个 tick 方法都没扫到，守卫断言等于空转");
    }

    @Test
    @DisplayName("定时任务的线程必须是 daemon 且带可读名字")
    void schedulerThreadsAreDaemonAndNamed() throws IOException {
        List<String> offenders = new ArrayList<>();
        try (var walk = Files.walk(SERVICES)) {
            for (Path file : walk.filter(p -> p.toString().endsWith(".java")).toList()) {
                String code = stripComments(Files.readString(file, StandardCharsets.UTF_8));
                if (!code.contains("ScheduledExecutorService")) {
                    continue;
                }
                boolean usesHelper = code.contains("Schedulers.newDaemonSingle(");
                boolean explicitDaemon = code.contains("setDaemon(true)");
                boolean createsExecutor = code.contains("Executors.newSingleThreadScheduledExecutor(")
                        || code.contains("newScheduledThreadPool(");
                if (createsExecutor && !explicitDaemon) {
                    offenders.add(file.getFileName() + " 自己 new 了执行器却没设 setDaemon(true)"
                            + "（非 daemon 线程会阻止 JVM 退出，忘了 shutdown 就关不掉）");
                } else if (!createsExecutor && !usesHelper && !explicitDaemon
                        && code.contains("scheduleAtFixedRate(")) {
                    offenders.add(file.getFileName() + " 起了定时任务，但执行器既不是 daemon 也不是 "
                            + "Schedulers.newDaemonSingle 建的");
                }
            }
        }
        assertTrue(offenders.isEmpty(),
                "定时任务线程必须 daemon 且带名字：\n" + String.join("\n", offenders));
    }

    @Test
    @DisplayName("生命周期接口本身必须存在（防止有人把契约删了让测试空转）")
    void backgroundTaskOwnerContractExists() throws IOException {
        String code = stripComments(Files.readString(OWNER, StandardCharsets.UTF_8));
        assertTrue(code.contains("void startBackgroundTasks()"), "BackgroundTaskOwner 缺少 startBackgroundTasks()");
        assertTrue(code.contains("void stopBackgroundTasks()"), "BackgroundTaskOwner 缺少 stopBackgroundTasks()");
        assertFalse(code.isEmpty(), "源码为空");
    }

    @Test
    @DisplayName("守卫方法本身要存在（防止有人把 isReady 改名后测试空转）")
    void databaseManagerExposesReadinessCheck() throws IOException {
        String code = stripComments(Files.readString(
                Paths.get("src/main/java/com/mtxgdn/db/DatabaseManager.java"), StandardCharsets.UTF_8));
        assertTrue(code.contains("public static boolean isReady()"),
                "DatabaseManager.isReady() 不见了 —— 定时任务的守卫会全部失效");
        assertFalse(code.isEmpty(), "源码为空");
    }

    // ==================== 夹具 ====================

    private static List<String> allScheduledServices() {
        List<String> all = new ArrayList<>(TASK_OWNERS);
        all.addAll(STATIC_SCHEDULER_ALLOWLIST);
        return all;
    }

    private static String read(String service) throws IOException {
        return Files.readString(SERVICES.resolve(service + ".java"), StandardCharsets.UTF_8);
    }

    /**
     * 找出一个类里注册的定时任务方法名。
     *
     * <p>方法引用（{@code Cls::method}）和 lambda（{@code () -> method(...)}）两种写法都要认，
     * 否则把 tick 改成 lambda 就能绕过守卫断言。</p>
     */
    private static Set<String> registeredTickMethods(String code, String service) {
        Set<String> found = new LinkedHashSet<>();
        Matcher ref = Pattern.compile("schedule(?:AtFixedRate|WithFixedDelay)\\s*\\(\\s*"
                + service + "\\s*::\\s*(\\w+)").matcher(code);
        while (ref.find()) {
            found.add(ref.group(1));
        }
        Matcher lambda = Pattern.compile("schedule(?:AtFixedRate|WithFixedDelay)\\s*\\(\\s*"
                + "\\(\\)\\s*->\\s*(\\w+)\\s*\\(").matcher(code);
        while (lambda.find()) {
            found.add(lambda.group(1));
        }
        return found;
    }

    /** 抽出所有 {@code static { ... }} 块的内容。 */
    private static List<String> staticBlocks(String code) {
        List<String> blocks = new ArrayList<>();
        Matcher m = Pattern.compile("\\bstatic\\s*\\{").matcher(code);
        while (m.find()) {
            int start = m.end() - 1;
            int depth = 0;
            for (int i = start; i < code.length(); i++) {
                char c = code.charAt(i);
                if (c == '{') {
                    depth++;
                } else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        blocks.add(code.substring(start, i + 1));
                        break;
                    }
                }
            }
        }
        return blocks;
    }

    /** 用花括号配对取方法体；找不到返回 null。 */
    private static String methodBody(String code, String methodName) {
        Matcher decl = Pattern.compile("\\b" + Pattern.quote(methodName) + "\\s*\\(\\s*\\)\\s*\\{")
                .matcher(code);
        if (!decl.find()) {
            return null;
        }
        int start = decl.end() - 1;
        int depth = 0;
        for (int i = start; i < code.length(); i++) {
            char c = code.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return code.substring(start, i + 1);
                }
            }
        }
        return null;
    }

    /** 去注释：注释里的示例不算数，也不能拿注释当断言锚点。 */
    private static String stripComments(String code) {
        String noBlock = code.replaceAll("(?s)/\\*.*?\\*/", "");
        StringBuilder sb = new StringBuilder();
        for (String line : noBlock.split("\\R")) {
            int idx = line.indexOf("//");
            sb.append(idx >= 0 ? line.substring(0, idx) : line).append('\n');
        }
        return sb.toString();
    }
}
