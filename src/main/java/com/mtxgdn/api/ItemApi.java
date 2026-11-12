package com.mtxgdn.api;
import com.mtxgdn.game.entity.InventoryEntry;


import com.mtxgdn.game.item.EquipResult;
import com.mtxgdn.game.item.ExchangeResult;
import com.mtxgdn.game.item.UseItemResult;
import java.util.List;
import java.util.Map;

/**
 * ItemService 的对外接口。
 *
 * <p>由脚本从实现类机械抽取（仅剔除构造函数、静态方法、以及对 JDBC 与
 * 其它服务类型的引用）。目的是让插件层不再依赖 com.mtxgdn.game.service 的具体类，
 * 从而可以替换实现、可以在测试里塞假实现。</p>
 *
 * <p><b>注意</b>：这是当前实现的镜像，不是精心设计的稳定契约。
 * 后续应按实际使用收窄 —— 接口每多一个方法，实现类就少一分改动自由。</p>
 */
public interface ItemApi {

    int getInventoryCapacity(long playerId);

    int getInventoryUsedSlots(long playerId);

    boolean isInventoryFull(long playerId);

    boolean addItem(long playerId, String itemKey, long quantity);

    boolean removeItem(long playerId, String itemKey, long quantity);

    boolean addItems(long playerId, Map<String, Long> items);

    boolean removeItems(long playerId, Map<String, Long> items);

    List<InventoryEntry> getInventory(long playerId);

    long getItemCount(long playerId, String itemKey);

    boolean hasItem(long playerId, String itemKey, long quantity);

    long getSpiritStoneCount(long playerId);

    long getSpiritStoneCount(long playerId, int grade);

    long getTotalSpiritStonesInLowGrade(long playerId);

    boolean removeSpiritStones(long playerId, long amount);

    boolean removeSpiritStones(long playerId, long amount, int grade);

    boolean addSpiritStones(long playerId, long amount);

    boolean addSpiritStones(long playerId, long amount, int grade);

    ExchangeResult exchangeSpiritStones(long playerId, int fromGrade, int toGrade, long amount);

    EquipResult equipItem(long playerId, String itemKey, String slot);

    EquipResult unequipItem(long playerId, String slot);

    String getEquippedItem(long playerId, String slot);

    Map<String, String> getEquipment(long playerId);

    UseItemResult useItem(long playerId, String itemKey);

}
