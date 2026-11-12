package com.mtxgdn.api;

import com.mtxgdn.common.result.OperationResult;
import com.mtxgdn.game.entity.Formation;
import com.mtxgdn.game.formation.FormationUpgradeResult;
import com.mtxgdn.game.item.UseItemResult;

/**
 * FormationService 的对外接口。
 *
 * <p>由脚本从实现类机械抽取（剔除构造函数、静态方法、对 JDBC 与其它服务类型的引用）。
 * 目的是让插件层不再依赖 com.mtxgdn.game.service 的具体类。</p>
 */
public interface FormationApi {

    Formation getActiveFormation(long playerId);

    OperationResult placeFormation(long playerId, String formationKey, int level);

    FormationUpgradeResult upgradeFormation(long playerId);

    OperationResult removeFormation(long playerId);

    int getTotalSpiritEnergyBoost(long playerId);

    int getTotalCultivationBonus(long playerId);

    int getTotalHeartDemonResist(long playerId);

    int getTotalDefenseBonus(long playerId);

    UseItemResult placeFormationFromTreasure(long playerId, String treasureFullKey);

}
