package com.mtxgdn.api;

import com.mtxgdn.game.economy.AuctionResult;
import com.mtxgdn.game.economy.BankDepositResult;
import com.mtxgdn.game.economy.BankInfo;
import com.mtxgdn.game.economy.BankWithdrawResult;
import com.mtxgdn.game.economy.BoostResult;
import com.mtxgdn.game.economy.CurrencyExchangeResult;
import com.mtxgdn.game.economy.EconomyStats;
import com.mtxgdn.game.economy.PurifyResult;
import com.mtxgdn.game.economy.RecycleResult;
import com.mtxgdn.game.economy.ShopPurchaseResult;
import com.mtxgdn.game.economy.SignInResult;
import java.util.List;
import java.util.Map;

/**
 * EconomyService 的对外接口。
 *
 * <p>由脚本从实现类机械抽取（仅剔除构造函数、静态方法、以及对 JDBC 与
 * 其它服务类型的引用）。目的是让插件层不再依赖 com.mtxgdn.game.service 的具体类，
 * 从而可以替换实现、可以在测试里塞假实现。</p>
 *
 * <p><b>注意</b>：这是当前实现的镜像，不是精心设计的稳定契约。
 * 后续应按实际使用收窄 —— 接口每多一个方法，实现类就少一分改动自由。</p>
 */
public interface EconomyApi {

    void sweepExpiredAuctions();

    SignInResult signIn(long playerId);
    RecycleResult recycleItem(long playerId, String itemKey, int quantity);

    ShopPurchaseResult buyFromShop(long playerId, int shopIndex);

    String[][] getShopItems();

    BoostResult boostCultivation(long playerId, int stonesToBurn);

    CurrencyExchangeResult exchangeGoldToStones(long playerId, long goldAmount);

    CurrencyExchangeResult exchangeStonesToGold(long playerId, long stoneAmount);

    BankDepositResult bankDeposit(long playerId, String type, long amount);

    BankWithdrawResult bankWithdraw(long playerId, long depositId);

    BankInfo getBankInfo(long playerId);

    EconomyStats getEconomyStats();

    AuctionResult createAuction(long playerId, String itemKey, int quantity, long startPrice, int hours);

    AuctionResult placeBid(long bidderPlayerId, long listingId, long amount);

    List<Map<String, Object>> getActiveAuctionItems();

    List<Map<String, Object>> getPlayerAuctionItems(long playerId);

    PurifyResult purifySpiritStone(long playerId, int grade);

    String getPurifyHelp(long playerId);

}
