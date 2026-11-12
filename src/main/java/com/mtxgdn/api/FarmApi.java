package com.mtxgdn.api;

import com.mtxgdn.game.entity.FarmPlot;
import com.mtxgdn.game.farm.FarmResult;
import java.util.List;

/**
 * FarmService 的对外接口。
 *
 * <p>由脚本从实现类机械抽取（剔除构造函数、静态方法、对 JDBC 与其它服务类型的引用）。
 * 目的是让插件层不再依赖 com.mtxgdn.game.service 的具体类。</p>
 */
public interface FarmApi {

    List<FarmPlot> getPlots(long playerId);

    FarmResult plant(long playerId, int plotIndex, String seedKey);

    FarmResult water(long playerId, int plotIndex);

    FarmResult fertilize(long playerId, int plotIndex);

    FarmResult fertilize(long playerId, int plotIndex, String fertilizerKey);

    FarmResult harvest(long playerId, int plotIndex);

    FarmResult clearPlot(long playerId, int plotIndex);

    FarmResult usePesticide(long playerId, int plotIndex);

    FarmResult expandPlot(long playerId);

    FarmResult waterAll(long playerId);

    FarmResult fertilizeAll(long playerId);

    FarmResult fertilizeAll(long playerId, String fertilizerKey);

    FarmResult harvestAll(long playerId);

}
