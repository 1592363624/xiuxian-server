package com.mtxgdn.api;

import com.mtxgdn.game.item.EnhanceResult;

/**
 * EnhanceService 的对外接口。
 *
 * <p>由脚本从实现类机械抽取（仅剔除构造函数、静态方法、以及对 JDBC 与
 * 其它服务类型的引用）。目的是让插件层不再依赖 com.mtxgdn.game.service 的具体类，
 * 从而可以替换实现、可以在测试里塞假实现。</p>
 *
 * <p><b>注意</b>：这是当前实现的镜像，不是精心设计的稳定契约。
 * 后续应按实际使用收窄 —— 接口每多一个方法，实现类就少一分改动自由。</p>
 */
public interface EnhanceApi {

    int getEnhanceLevel(long playerId, String slot);

    String getEquippedItemKey(long playerId, String slot);

    EnhanceResult enhanceItem(long playerId, String slot);

}
