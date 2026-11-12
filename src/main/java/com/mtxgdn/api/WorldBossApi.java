package com.mtxgdn.api;

import com.mtxgdn.game.entity.WorldBoss;
import com.mtxgdn.game.entity.WorldBossDamage;
import com.mtxgdn.game.worldboss.WorldBossChallengeResult;
import java.util.List;

/**
 * WorldBossService 的对外接口。
 *
 * <p>由脚本从实现类机械抽取（剔除构造函数、静态方法、对 JDBC 与其它服务类型的引用）。
 * 目的是让插件层不再依赖 com.mtxgdn.game.service 的具体类。</p>
 */
public interface WorldBossApi {

    List<WorldBoss> getPool(String scope, long now);

    List<WorldBoss> refreshPool(String scope, long now);

    WorldBoss getBossById(long bossId);

    WorldBossChallengeResult challenge(long playerId, String scope, String query, long now);

    WorldBossDamage getDamage(long bossId, long playerId);

    List<WorldBossDamage> getLeaderboard(long bossId, int limit);

}
