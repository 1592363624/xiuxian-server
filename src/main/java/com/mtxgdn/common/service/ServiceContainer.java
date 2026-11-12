package com.mtxgdn.common.service;

/*
 * 服务的构造与接线集中在这里，一个实例一套服务。
 *
 * 为什么要有这个东西：原来 29 个服务是 ServiceRegistry 里的 29 个 static 字段，
 * 构造顺序、相互依赖（RealmService 需要 PlayerService 等）都散在静态初始化里，
 * 既没法替换，也没法在测试之间重置 —— 整个测试进程共用一个 JVM，
 * 服务实例从不销毁，TestBase 里那一大段"停调度器"的说明就是这个问题的症状。
 *
 * 现在构造逻辑只有这一份，ServiceRegistry 退化成指向"当前容器"的静态门面。
 * 门面还在，是为了不让 450 处调用点在同一次提交里全部改掉；
 * 但容器已经是可实例化、可替换、可重置的实体，后续迁移有明确目标。
 */


import com.mtxgdn.game.service.*;
import com.mtxgdn.minecraft.adapter.MinecraftPlayerBindingService;

public class ServiceContainer {

    private volatile PlayerService playerService;
    private volatile RealmService realmService;
    private volatile ItemService itemService;
    private volatile SecretRealmService secretRealmService;
    private volatile ExplorationService explorationService;
    private volatile NewbieGuideService guideService;
    private volatile SkillService skillService;
    private volatile CombatService combatService;
    private volatile DailyService dailyService;
    private volatile TradeService tradeService;
    private volatile HeartDemonService heartDemonService;
    private volatile ChatService chatService;
    private volatile FriendService friendService;
    private volatile EnhanceService enhanceService;
    private volatile CraftingService craftingService;
    private volatile TechniqueService techniqueService;
    private volatile SectService sectService;
    private volatile EconomyService economyService;
    private volatile EnergyService energyService;
    private volatile TitleService titleService;
    private volatile CaveService caveService;
    private volatile FormationService formationService;
    private volatile BuffService buffService;
    private volatile FarmService farmService;
    private volatile MapService mapService;
    private volatile WorldBossService worldBossService;
    private volatile DurabilityService durabilityService;
    private volatile MinecraftPlayerBindingService mcBindingService;
    private volatile OfflineRewardService offlineRewardService;
    private volatile RedeemCodeService redeemCodeService;

    public ServiceContainer() {
            com.mtxgdn.db.DatabaseManager.addReinitListener(reinitListener);
}

    public PlayerService getPlayerService() {
        if (playerService == null) {
            synchronized (this) {
                if (playerService == null) {
                    playerService = new PlayerService();
                }
            }
        }
        return playerService;
    }

    public RealmService getRealmService() {
        if (realmService == null) {
            synchronized (this) {
                if (realmService == null) {
                    realmService = new RealmService(getPlayerService());
                }
            }
        }
        return realmService;
    }

    public ItemService getItemService() {
        if (itemService == null) {
            synchronized (this) {
                if (itemService == null) {
                    itemService = new ItemService();
                }
            }
        }
        return itemService;
    }

    public SecretRealmService getSecretRealmService() {
        if (secretRealmService == null) {
            synchronized (this) {
                if (secretRealmService == null) {
                    secretRealmService = new SecretRealmService(getPlayerService());
                }
            }
        }
        return secretRealmService;
    }

    public ExplorationService getExplorationService() {
        if (explorationService == null) {
            synchronized (this) {
                if (explorationService == null) {
                    explorationService = new ExplorationService(getPlayerService());
                }
            }
        }
        return explorationService;
    }

    public NewbieGuideService getGuideService() {
        if (guideService == null) {
            synchronized (this) {
                if (guideService == null) {
                    guideService = new NewbieGuideService();
                }
            }
        }
        return guideService;
    }

    public SkillService getSkillService() {
        if (skillService == null) {
            synchronized (this) {
                if (skillService == null) {
                    skillService = new SkillService();
                }
            }
        }
        return skillService;
    }

    public CombatService getCombatService() {
        if (combatService == null) {
            synchronized (this) {
                if (combatService == null) {
                    combatService = new CombatService();
                }
            }
        }
        return combatService;
    }

    public DailyService getDailyService() {
        if (dailyService == null) {
            synchronized (this) {
                if (dailyService == null) {
                    dailyService = new DailyService();
                }
            }
        }
        return dailyService;
    }

    public TradeService getTradeService() {
        if (tradeService == null) {
            synchronized (this) {
                if (tradeService == null) {
                    tradeService = new TradeService();
                }
            }
        }
        return tradeService;
    }

    public HeartDemonService getHeartDemonService() {
        if (heartDemonService == null) {
            synchronized (this) {
                if (heartDemonService == null) {
                    heartDemonService = new HeartDemonService();
                }
            }
        }
        return heartDemonService;
    }

    public ChatService getChatService() {
        if (chatService == null) {
            synchronized (this) {
                if (chatService == null) {
                    chatService = new ChatService();
                }
            }
        }
        return chatService;
    }

    public FriendService getFriendService() {
        if (friendService == null) {
            synchronized (this) {
                if (friendService == null) {
                    friendService = new FriendService();
                }
            }
        }
        return friendService;
    }

    public EnhanceService getEnhanceService() {
        if (enhanceService == null) {
            synchronized (this) {
                if (enhanceService == null) {
                    enhanceService = new EnhanceService();
                }
            }
        }
        return enhanceService;
    }

    public CraftingService getCraftingService() {
        if (craftingService == null) {
            synchronized (this) {
                if (craftingService == null) {
                    craftingService = new CraftingService();
                }
            }
        }
        return craftingService;
    }

    public TechniqueService getTechniqueService() {
        if (techniqueService == null) {
            synchronized (this) {
                if (techniqueService == null) {
                    techniqueService = new TechniqueService();
                }
            }
        }
        return techniqueService;
    }

    public SectService getSectService() {
        if (sectService == null) {
            synchronized (this) {
                if (sectService == null) {
                    sectService = new SectService();
                }
            }
        }
        return sectService;
    }

    public EconomyService getEconomyService() {
        if (economyService == null) {
            synchronized (this) {
                if (economyService == null) {
                    economyService = new EconomyService();
                }
            }
        }
        return economyService;
    }

    public EnergyService getEnergyService() {
        if (energyService == null) {
            synchronized (this) {
                if (energyService == null) {
                    energyService = new EnergyService();
                }
            }
        }
        return energyService;
    }

    public TitleService getTitleService() {
        if (titleService == null) {
            synchronized (this) {
                if (titleService == null) {
                    titleService = new TitleService();
                }
            }
        }
        return titleService;
    }

    public CaveService getCaveService() {
        if (caveService == null) {
            synchronized (this) {
                if (caveService == null) {
                    caveService = new CaveService();
                }
            }
        }
        return caveService;
    }

    public FormationService getFormationService() {
        if (formationService == null) {
            synchronized (this) {
                if (formationService == null) {
                    formationService = new FormationService();
                }
            }
        }
        return formationService;
    }

    public BuffService getBuffService() {
        if (buffService == null) {
            synchronized (this) {
                if (buffService == null) {
                    buffService = new BuffService();
                }
            }
        }
        return buffService;
    }

    public FarmService getFarmService() {
        if (farmService == null) {
            synchronized (this) {
                if (farmService == null) {
                    farmService = new FarmService();
                }
            }
        }
        return farmService;
    }

    public MinecraftPlayerBindingService getMcBindingService() {
        if (mcBindingService == null) {
            synchronized (this) {
                if (mcBindingService == null) {
                    mcBindingService = new MinecraftPlayerBindingService();
                }
            }
        }
        return mcBindingService;
    }

    public MapService getMapService() {
        if (mapService == null) {
            synchronized (this) {
                if (mapService == null) {
                    mapService = new MapService();
                }
            }
        }
        return mapService;
    }

    public WorldBossService getWorldBossService() {
        if (worldBossService == null) {
            synchronized (this) {
                if (worldBossService == null) {
                    worldBossService = new WorldBossService();
                }
            }
        }
        return worldBossService;
    }

    public DurabilityService getDurabilityService() {
        if (durabilityService == null) {
            synchronized (this) {
                if (durabilityService == null) {
                    durabilityService = new DurabilityService();
                }
            }
        }
        return durabilityService;
    }

    /**
     * 离线奖励服务。
     *
     * <p>它本身无状态，但历史上被 {@code new OfflineRewardService()} 在
     * 「每次读取玩家状态」「每条指令」的路径上反复构造 —— 每次构造都会新建
     * 一个 {@link ItemService} 与 {@link PlayerService}，等于每来一条消息就多
     * 开一批对象。统一走注册表即可消除这类无意义开销。</p>
     */
    public OfflineRewardService getOfflineRewardService() {
        if (offlineRewardService == null) {
            synchronized (this) {
                if (offlineRewardService == null) {
                    offlineRewardService = new OfflineRewardService();
                }
            }
        }
        return offlineRewardService;
    }

    /** 兑换码服务（无状态，但同样避免重复构造）。 */
    public RedeemCodeService getRedeemCodeService() {
        if (redeemCodeService == null) {
            synchronized (this) {
                if (redeemCodeService == null) {
                    redeemCodeService = new RedeemCodeService();
                }
            }
        }
        return redeemCodeService;
    }

    /**
     * 换库时让各处内存缓存失效。
     *
     * <p><b>为什么需要</b>：{@code DatabaseManager.init()} 可重复调用（测试里每个测试类
     * 调一次，运行时切换配置也会走到它）。它把连接池换到新库，但那些
     * "首次访问时从库里读一遍、之后一直用内存副本"的单例并不知道库已经换了，
     * 仍然拿着旧库的内容 —— 而旧库可能已经被删掉了。</p>
     *
     * <p>登记放在这里而不是各自类的静态块里，有两个原因：</p>
     * <ul>
     *   <li>{@code MapService} 的构造器要回调 {@code getPlayerService()}，
     *       在它自己的静态块里再调 {@code getMapService()} 会构成类初始化循环；</li>
     *   <li>{@code PermissionService} 属于服务层，不该 import {@code DatabaseManager}
     *       （{@code PermissionDaoExtractionTest.serviceLayerContainsNoJdbc} 禁止）。</li>
     * </ul>
     *
     * <p>回调读的是<b>当前</b>字段而不是捕获某个实例：换库可能发生在实例创建之前
     * （启动早期），那时应当什么都不做 —— 实例还没建，自然也没有缓存要清。</p>
     *
     * <p><b>从静态块改成实例字段</b>：原来这段是 {@code static { }} 块，容器可以被
     * 替换之后就成了漏洞 —— 每换一个容器就多登记一个回调，全都指向已经废弃的实例，
     * 换库时对着旧实例清缓存，新实例反而没清。现在每个容器在构造时登记、
     * 在 {@link #close()} 时注销，数量与服务实例一一对应。</p>
     */
    private final Runnable reinitListener = () -> {
        MapService service = mapService;
        if (service != null) {
            service.invalidateCache();
        }
        com.mtxgdn.permission.PermissionService.invalidateAllCachesForNewDatabase();
        // 吊销名单是"启动时从库里读一遍、之后一直在内存里"。换库后必须丢掉旧库的内容，
        // 否则旧库吊销的 token 会挡住新库的正常登录；反过来更糟 ——
        // 新库已吊销的 token 在内存里查不到，会被当成有效。
        com.mtxgdn.util.TokenBlacklist.clear();
    };

    /**
     * 注销本容器的换库回调。
     *
     * <p>换掉容器前必须调用，否则旧回调会一直挂在 {@code DatabaseManager} 上。</p>
     */
    public void close() {
        com.mtxgdn.db.DatabaseManager.removeReinitListener(reinitListener);
    }
}