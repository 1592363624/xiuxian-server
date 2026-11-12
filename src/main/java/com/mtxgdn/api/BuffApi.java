package com.mtxgdn.api;

import com.mtxgdn.game.buff.ActiveBuffsResult;

/**
 * BuffService 的对外接口。
 *
 * <p>由脚本从实现类机械抽取（剔除构造函数、静态方法、对 JDBC 与其它服务类型的引用）。
 * 目的是让插件层不再依赖 com.mtxgdn.game.service 的具体类。</p>
 */
public interface BuffApi {

    void addExpBoost(long playerId, int percent, int durationSeconds);

    double getExpMultiplier(long playerId);

    void addReflectionBuff(long playerId, int percent, int durationSeconds);

    int getReflectionPercent(long playerId);

    void removeAllBuffs(long playerId);

    void addBuff(long playerId, String buffId, int attackBonus, int defenseBonus, int speedBonus, int spiritBonus, int durationSeconds);

    void removeBuff(long playerId, String buffId);

    ActiveBuffsResult getActiveBuffs(long playerId);

    void clearAllBuffs(long playerId);

    int getTotalAttackBonus(long playerId);

    int getTotalDefenseBonus(long playerId);

    int getTotalSpeedBonus(long playerId);

    int getTotalSpiritBonus(long playerId);

    void reloadBuffsOnStartup(long playerId);

}
