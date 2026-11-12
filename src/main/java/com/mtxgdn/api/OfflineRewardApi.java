package com.mtxgdn.api;
import com.mtxgdn.game.entity.OfflineRewardResult;


/**
 * OfflineRewardService 的对外接口。
 *
 * <p>由脚本从实现类机械抽取（剔除构造函数、静态方法、对 JDBC 与其它服务类型的引用）。
 * 目的是让插件层不再依赖 com.mtxgdn.game.service 的具体类。</p>
 */
public interface OfflineRewardApi {

    OfflineRewardResult processOfflineRewards(long userId);

}
