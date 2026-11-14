package com.mtxgdn.common.service;

import com.mtxgdn.game.service.*;
import com.mtxgdn.minecraft.adapter.MinecraftPlayerBindingService;
import com.mtxgdn.util.GameLogger;

/**
 * 服务访问的静态门面。
 *
 * <p>服务的构造与接线已经搬到 {@link ServiceContainer}，这里只剩一层委托。
 * 保留静态方法是为了不让四百多处调用点在同一次改动里全部重写，
 * 但它同时是<b>技术债</b>：静态状态让测试之间互相污染、让依赖关系看不出来。</p>
 *
 * <p>迁移方向：新代码通过构造参数或 {@link ServiceContainer} 拿服务；
 * 老代码保持不动，直到被改到为止。</p>
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

    public static PlayerService getPlayerService() {
        return current.getPlayerService();
    }

    public static RealmService getRealmService() {
        return current.getRealmService();
    }

    public static ItemService getItemService() {
        return current.getItemService();
    }

    public static SecretRealmService getSecretRealmService() {
        return current.getSecretRealmService();
    }

    public static ExplorationService getExplorationService() {
        return current.getExplorationService();
    }

    public static NewbieGuideService getGuideService() {
        return current.getGuideService();
    }

    public static SkillService getSkillService() {
        return current.getSkillService();
    }

    public static CombatService getCombatService() {
        return current.getCombatService();
    }

    public static DailyService getDailyService() {
        return current.getDailyService();
    }

    public static TradeService getTradeService() {
        return current.getTradeService();
    }

    public static HeartDemonService getHeartDemonService() {
        return current.getHeartDemonService();
    }

    public static ChatService getChatService() {
        return current.getChatService();
    }

    public static FriendService getFriendService() {
        return current.getFriendService();
    }

    public static EnhanceService getEnhanceService() {
        return current.getEnhanceService();
    }

    public static CraftingService getCraftingService() {
        return current.getCraftingService();
    }

    public static TechniqueService getTechniqueService() {
        return current.getTechniqueService();
    }

    public static SectService getSectService() {
        return current.getSectService();
    }

    public static EconomyService getEconomyService() {
        return current.getEconomyService();
    }

    public static EnergyService getEnergyService() {
        return current.getEnergyService();
    }

    public static TitleService getTitleService() {
        return current.getTitleService();
    }

    public static CaveService getCaveService() {
        return current.getCaveService();
    }

    public static FormationService getFormationService() {
        return current.getFormationService();
    }

    public static BuffService getBuffService() {
        return current.getBuffService();
    }

    public static FarmService getFarmService() {
        return current.getFarmService();
    }

    public static MinecraftPlayerBindingService getMcBindingService() {
        return current.getMcBindingService();
    }

    public static MapService getMapService() {
        return current.getMapService();
    }

    public static WorldBossService getWorldBossService() {
        return current.getWorldBossService();
    }

    public static DurabilityService getDurabilityService() {
        return current.getDurabilityService();
    }

    public static OfflineRewardService getOfflineRewardService() {
        return current.getOfflineRewardService();
    }

    public static RedeemCodeService getRedeemCodeService() {
        return current.getRedeemCodeService();
    }

}
