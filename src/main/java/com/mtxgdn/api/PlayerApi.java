package com.mtxgdn.api;

import com.mtxgdn.entity.Player;
import com.mtxgdn.game.cultivation.BeginCultivationResult;
import com.mtxgdn.game.cultivation.FinishCultivationResult;
import com.mtxgdn.game.cultivation.HealResult;
import com.mtxgdn.game.entity.PlayerInfo;
import com.mtxgdn.game.entity.SpiritualRoot;
import java.util.List;

/**
 * PlayerService 的对外接口。
 *
 * <p>由脚本从实现类机械抽取（仅剔除构造函数、静态方法、以及对 JDBC 与
 * 其它服务类型的引用）。目的是让插件层不再依赖 com.mtxgdn.game.service 的具体类，
 * 从而可以替换实现、可以在测试里塞假实现。</p>
 *
 * <p><b>注意</b>：这是当前实现的镜像，不是精心设计的稳定契约。
 * 后续应按实际使用收窄 —— 接口每多一个方法，实现类就少一分改动自由。</p>
 */
public interface PlayerApi {

    PlayerInfo getPlayerByUserId(long userId);

    Player getPlayerById(long playerId);

    PlayerInfo getPlayerInfoById(long playerId);

    boolean setLanguage(long playerId, String language);

    Player getPlayerByUserIdRaw(long userId);

    PlayerInfo createPlayer(long userId, String name);

    void updatePlayer(long playerId, Player player);

    boolean updatePlayerName(long playerId, String newName);

    void addExperience(long playerId, long exp);

    boolean removeExperience(long playerId, long amount);

    void updateCultivationProgress(long playerId, int progress);

    void setCultivating(long playerId, boolean cultivating);

    int calculateCultivationPerSecond(Player player);

    long calculateCultivationExp(Player player, long elapsedSeconds);

    BeginCultivationResult beginCultivation(long playerId);

    FinishCultivationResult finishCultivation(long playerId);

    void startCultivation(long playerId, long startTime);

    boolean stopCultivation(long playerId);

    void setTutorialStep(long playerId, int step);

    void setTutorialTips(long playerId, int tips);

    boolean existsByUserId(long userId);

    void addHp(long playerId, int amount);

    void damageHp(long playerId, int amount, int floor);

    HealResult healPlayer(long playerId);

    void addMp(long playerId, int amount);

    void addGold(long playerId, long amount);

    void addAttack(long playerId, int amount);

    void addDefense(long playerId, int amount);

    void addSpeed(long playerId, int amount);

    void addSpirit(long playerId, int amount);

    void updateSpiritualRoot(long playerId, SpiritualRoot newRoot);

    void updateLastSecretRealmTime(long playerId, long lastSecretRealmTime);

    void updateLastExplorationTime(long playerId, long lastExplorationTime);

    void updateBattleStrategy(long playerId, String strategy);

    void updateLastOfflineTime(long playerId, long lastOfflineTime);

    boolean claimOfflineTime(long playerId, long expectedLastOfflineTime);

    void incrementSecretRealmCount(long playerId);

    void incrementPvpWins(long playerId);

    void incrementCraftCount(long playerId);

    List<PlayerInfo> getAllPlayers(int limit, int offset);

    List<PlayerInfo> searchPlayersByName(String name, int limit, int offset);

    int getPlayerCount();

    int getUserCount();

    List<PlayerInfo> getTopByRealm(int limit);

    List<PlayerInfo> getTopByPower(int limit);

    List<PlayerInfo> getTopByWealth(int limit);

    int getFinalAttack(Player player);

    int getFinalDefense(Player player);

    int getFinalMaxHp(Player player);

    int getFinalMaxMp(Player player);

    int getFinalSpeed(Player player);

    int getFinalSpirit(Player player);

    double getFinalCultivationSpeed(Player player);

    double getFinalExpBonus(Player player);

    double getFinalDropRateBonus(Player player);

    boolean deletePlayer(long playerId);

}
