package com.mtxgdn.plugin;
import com.mtxgdn.common.service.ServiceRegistry;
import com.mtxgdn.game.service.PlayerService;

import com.mtxgdn.api.*;
import com.mtxgdn.common.service.ServiceLookup;
import com.mtxgdn.util.GameLogger;

/**
 * 把核心服务登记进插件服务表，让插件用同一种方式取得"核心能力"和"别的插件的能力"。
 *
 * <p>此前插件拿核心服务有两条路：{@code PluginContext} 上二十多个写死的 getter，
 * 或者直接用 {@code ServiceRegistry} 的静态单例。前者是硬编码的类依赖 ——
 * 想换个实现、想在测试里塞个假实现，都无从下手；后者更糟，插件作者不该知道
 * 服务端的内部组织方式。</p>
 *
 * <p>登记之后统一成：</p>
 * <pre>
 *   PlayerService players = ctx.getService(PlayerService.class);
 * </pre>
 *
 * <p><b>为什么保留回落</b>：{@code PluginContext} 的 getter 仍然会在服务表查不到时
 * 回落到 {@code ServiceRegistry}。原因是大量单元测试直接 new 服务、调 getter，
 * 没有走启动引导；若改成严格模式，这些测试会拿到 null 然后一片 NPE，
 * 而那与本次改动的目的（解耦）毫无关系。回退让迁移可以分步进行，
 * 也让"插件替换核心服务"成为一个可选能力，而不是一个必须完成的前提。</p>
 */
public final class CoreServices {

    /** 核心服务的归属标识，与任何插件名、内置模块名都不冲突。 */
    public static final String OWNER = "core";

    private static final GameLogger LOG = GameLogger.getLogger(CoreServices.class);

    private static boolean installed = false;

    private CoreServices() { }

    /**
     * 登记全部核心服务。
     *
     * <p>必须在 {@code ServiceRegistry} 初始化之后、插件启用之前调用
     * （见 {@code Main.doMain}）。重复调用是幂等的。</p>
     *
     * @return 实际登记成功的数量
     */
    /**
     * @param services 启动流程装配好的服务查询面；由 {@code Main} 显式传入
     */
    public static synchronized int install(ServiceLookup services) {
        if (installed) return 0;
        PluginServiceRegistry reg = PluginServiceRegistry.getInstance();

        provide(reg, PlayerApi.class, services.getPlayerService());
        provide(reg, RealmApi.class, services.getRealmService());
        provide(reg, ItemApi.class, services.getItemService());
        provide(reg, SecretRealmApi.class, services.getSecretRealmService());
        provide(reg, ExplorationApi.class, services.getExplorationService());
        provide(reg, GuideApi.class, services.getGuideService());
        provide(reg, SkillApi.class, services.getSkillService());
        provide(reg, CombatApi.class, services.getCombatService());
        provide(reg, DailyApi.class, services.getDailyService());
        provide(reg, TradeApi.class, services.getTradeService());
        provide(reg, HeartDemonApi.class, services.getHeartDemonService());
        provide(reg, ChatApi.class, services.getChatService());
        provide(reg, FriendApi.class, services.getFriendService());
        provide(reg, EnhanceApi.class, services.getEnhanceService());
        provide(reg, CraftingApi.class, services.getCraftingService());
        provide(reg, TechniqueApi.class, services.getTechniqueService());
        provide(reg, SectApi.class, services.getSectService());
        provide(reg, EconomyApi.class, services.getEconomyService());
        provide(reg, EnergyApi.class, services.getEnergyService());
        provide(reg, TitleApi.class, services.getTitleService());
        provide(reg, CaveApi.class, services.getCaveService());
        provide(reg, FormationApi.class, services.getFormationService());
        provide(reg, BuffApi.class, services.getBuffService());
        provide(reg, FarmApi.class, services.getFarmService());
        provide(reg, MapApi.class, services.getMapService());
        provide(reg, WorldBossApi.class, services.getWorldBossService());
        provide(reg, DurabilityApi.class, services.getDurabilityService());
        provide(reg, OfflineRewardApi.class, services.getOfflineRewardService());
        provide(reg, RedeemCodeApi.class, services.getRedeemCodeService());

        // 刻意不登记 MinecraftPlayerBindingService：它位于 com.mtxgdn.minecraft.adapter，
        // 登记它就等于让插件层重新依赖适配器层，与"适配器可插拔"的隔离方向相反。
        // 需要它的插件应当通过适配器自己的扩展点获取。

        installed = true;
        LOG.info("核心服务已登记到插件服务表: " + reg.size() + " 项" + "（归属 " + OWNER + "）");
        return reg.size();
    }

    /**
     * 注销核心服务。仅用于关服与测试清理 —— 正常运行时核心服务应始终可用。
     */
    public static synchronized void uninstall() {
        PluginServiceRegistry.getInstance().unregisterAll(OWNER);
        installed = false;
    }

    public static synchronized boolean isInstalled() {
        return installed;
    }

    /**
     * 单个登记。
     *
     * <p>服务为 {@code null} 时静默跳过而不是报错：部分单元测试只初始化了自己
     * 用到的几个服务，其余为 null。为这种场景刷一屏 ERROR 只会淹没真问题。</p>
     */
    private static <T> void provide(PluginServiceRegistry reg, Class<T> api, T instance) {
        if (instance == null) return;
        reg.provide(OWNER, api, instance);
    }
}
