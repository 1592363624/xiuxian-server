package com.mtxgdn.game.entity;


/**
 * 由 {TradeService} 的内部嵌套类提取而来。
 * <p>原来写成 {$svc}.{$nest}，接口无法在不 import 服务实现类的前提下引用它，
 * 因此提到 entity 包 —— 它本来就是纯数据，和服务的生命周期无关。</p>
 */
public class TradeListing {
    public long id;
    public long sellerPlayerId;
    public String itemKey;
    public int quantity;
    public long priceSpiritStones;
    public long fee;
    public String createdAt;
    /** 到期时间戳（毫秒）；0 表示永不过期（历史遗留挂单）。 */
    public long expiresAt;

    /** 剩余有效毫秒数；永不过期返回 -1。 */
    public long remainingMs() {
        return expiresAt <= 0 ? -1L : Math.max(0L, expiresAt - System.currentTimeMillis());
    }

    /** 单价（总价 ÷ 数量）。 */
    public long unitPrice() {
        return quantity > 0 ? Math.max(1L, priceSpiritStones / quantity) : priceSpiritStones;
    }
}
