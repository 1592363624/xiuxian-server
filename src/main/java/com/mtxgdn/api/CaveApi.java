package com.mtxgdn.api;

import com.mtxgdn.game.cave.CaveCollectResult;
import com.mtxgdn.game.cave.CaveCreateResult;
import com.mtxgdn.game.cave.CaveLevelUpResult;
import com.mtxgdn.game.cave.CaveMeditateResult;
import com.mtxgdn.game.entity.Cave;

/**
 * CaveService 的对外接口。
 *
 * <p>由脚本从实现类机械抽取（剔除构造函数、静态方法、对 JDBC 与其它服务类型的引用）。
 * 目的是让插件层不再依赖 com.mtxgdn.game.service 的具体类。</p>
 */
public interface CaveApi {

    Cave getCaveByPlayerId(long playerId);

    CaveCreateResult createCave(long playerId, String caveName);

    CaveCollectResult collectSpiritEnergy(long playerId);

    CaveMeditateResult meditate(long playerId);

    CaveLevelUpResult levelUp(long playerId);

    int getStorageBonus(long playerId);

}
