package com.mtxgdn.api;

import com.mtxgdn.game.entity.RedeemCode;
import java.util.List;
import java.util.Map;

/**
 * RedeemCodeService 的对外接口。
 *
 * <p>由脚本从实现类机械抽取（剔除构造函数、静态方法、对 JDBC 与其它服务类型的引用）。
 * 目的是让插件层不再依赖 com.mtxgdn.game.service 的具体类。</p>
 */
public interface RedeemCodeApi {

    RedeemCode createCode(String code, String name, Map<String, Integer> items, long gold, long spiritStones, long exp, int maxUses, String expiresAt, String createdBy);

    RedeemCode findByCode(String code);

    RedeemCode findById(long id);

    List<RedeemCode> listAll();

    boolean updateCode(long id, String name, Map<String, Integer> items, long gold, long spiritStones, long exp, int maxUses, String expiresAt);

    boolean deleteCode(long id);

    String doRedeem(String codeStr, long playerId);

}
