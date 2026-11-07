package com.mtxgdn.plugin;

import com.mtxgdn.util.GameLogger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 插件服务注册表：让插件把能力暴露给其它插件，而不必互相 import 实现类。
 *
 * <p><b>为什么事件总线不够</b>：{@code PluginEventManager} 是单向广播，
 * 载体是 {@code Map<String, Object>}。它能表达"宗门战开始了，谁想反应就反应"，
 * 但表达不了"这个玩家在哪个宗门"——后者需要一个有返回值、有类型的调用。
 * 没有这条路径时，插件想知道别人的数据，只剩两个选择：</p>
 * <ul>
 *   <li>直接调 {@code ServiceRegistry} 的全局单例 —— 那不是插件，是硬编码；</li>
 *   <li>把数据塞进事件的 {@code Map} 里靠字符串键互相对齐 —— 改个字段名就断。</li>
 * </ul>
 *
 * <p><b>用法</b>（提供方在自己的 onEnable 里）：</p>
 * <pre>
 *   public void onEnable(PluginContext ctx) {
 *       ctx.provideService(SectQueryApi.class, new MySectQuery());
 *   }
 * </pre>
 * <p><b>用法</b>（消费方，配合 {@code softDepends} 做能力探测）：</p>
 * <pre>
 *   SectQueryApi sect = ctx.getService(SectQueryApi.class);
 *   if (sect != null) { // 有宗门插件，启用联动
 *   }
 * </pre>
 *
 * <p><b>三条约定</b>：</p>
 * <ol>
 *   <li><b>按接口登记</b>：键是接口 {@code Class}，不是实现类。消费方只认识接口，
 *       提供方换实现、甚至整个插件被禁掉，消费方都能编译通过并优雅降级。</li>
 *   <li><b>拿不到返回 null</b>：{@link #get} 不抛异常。软依赖缺失是正常路径，
 *       不是错误 —— 把"插件没装"当异常抛，会让每个消费方都写一层 try/catch。</li>
 *   <li><b>同键重复登记时后者被拒</b>：两个插件争抢同一个接口是配置错误。
 *       让后来者静默覆盖，会让先注册者的调用方莫名其妙拿到别人的实现；
 *       保留先注册者并大声报错，问题在启动日志里一眼就能看到。</li>
 * </ol>
 *
 * <p>服务随提供方停用而注销（{@link #unregisterAll(String)}），
 * 由 {@code PluginManager.discardPlugin} 统一触发。</p>
 */
public final class PluginServiceRegistry {

    private static final GameLogger LOG = GameLogger.getLogger(PluginServiceRegistry.class);
    private static final PluginServiceRegistry INSTANCE = new PluginServiceRegistry();

    /** 接口 → 登记项。 */
    private final Map<Class<?>, Entry> services = new ConcurrentHashMap<>();

    public static PluginServiceRegistry getInstance() { return INSTANCE; }

    private PluginServiceRegistry() { }

    /** 一条登记记录：谁提供的、提供的是什么。 */
    public static final class Entry {
        private final String owner;
        private final Object instance;

        Entry(String owner, Object instance) {
            this.owner = owner;
            this.instance = instance;
        }

        public String getOwner() { return owner; }
        public Object getInstance() { return instance; }

        @Override
        public String toString() { return owner + " → " + instance.getClass().getName(); }
    }

    /**
     * 登记一个服务。
     *
     * @param owner    提供方标识（插件名，或内置模块名）
     * @param api      服务接口
     * @param instance 实现实例，必须可赋值给 {@code api}
     * @return true = 登记成功；false = 键已被占用（已报错）或参数非法
     */
    public boolean provide(String owner, Class<?> api, Object instance) {
        if (owner == null || owner.isEmpty() || api == null || instance == null) {
            LOG.warn("服务登记参数不完整，已忽略: owner=" + owner + ", api=" + api);
            return false;
        }
        if (!api.isInstance(instance)) {
            LOG.error("服务登记类型不匹配: " + api.getName() + " ← " + instance.getClass().getName()
                    + "（提供方 " + owner + "）");
            return false;
        }
        Entry previous = services.putIfAbsent(api, new Entry(owner, instance));
        if (previous != null) {
            LOG.error("服务接口已被占用: " + api.getName()
                    + "，已由 " + previous.getOwner() + " 提供；"
                    + "拒绝 " + owner + " 的重复登记（两个插件争抢同一接口属于配置错误）");
            return false;
        }
        LOG.debug("服务已登记: " + api.getName() + " ← " + owner);
        return true;
    }

    /**
     * 获取服务实现。
     *
     * @return 实现实例；没有提供方时返回 {@code null}（这是正常路径，不是错误）
     */
    public <T> T get(Class<T> api) {
        if (api == null) return null;
        Entry e = services.get(api);
        return e == null ? null : api.cast(e.instance);
    }

    /** 是否有提供方。 */
    public boolean has(Class<?> api) {
        return api != null && services.containsKey(api);
    }

    /** 注销某个接口（通常不需要手动调用，随提供方停用自动清理）。 */
    public boolean unprovide(Class<?> api) {
        return api != null && services.remove(api) != null;
    }

    /** 注销某个提供方登记的全部服务。 */
    public int unregisterAll(String owner) {
        if (owner == null || owner.isEmpty()) return 0;
        int removed = 0;
        for (Map.Entry<Class<?>, Entry> e : new ArrayList<>(services.entrySet())) {
            if (owner.equals(e.getValue().getOwner())) {
                // 用 remove(key, value) 的语义避免误删同键下别人刚登记的新实现
                if (services.remove(e.getKey(), e.getValue())) removed++;
            }
        }
        if (removed > 0) LOG.debug("已注销 " + owner + " 的 " + removed + " 个服务");
        return removed;
    }

    /** 当前登记情况快照（接口名 → "owner → 实现类"），用于状态接口与排查。 */
    public Map<String, String> snapshot() {
        Map<String, String> out = new LinkedHashMap<>();
        List<Class<?>> keys = new ArrayList<>(services.keySet());
        keys.sort((a, b) -> a.getName().compareTo(b.getName()));
        for (Class<?> k : keys) {
            Entry e = services.get(k);
            if (e != null) out.put(k.getName(), e.getOwner() + " → " + e.instance.getClass().getName());
        }
        return Collections.unmodifiableMap(out);
    }

    /** 清空（仅测试与关服使用）。 */
    public void clear() {
        services.clear();
    }

    /** 当前登记数量（仅测试与状态展示使用）。 */
    public int size() { return services.size(); }
}
