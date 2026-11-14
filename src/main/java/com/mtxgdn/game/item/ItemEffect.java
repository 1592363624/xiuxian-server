package com.mtxgdn.game.item;

import com.mtxgdn.game.service.PlayerService;
import com.mtxgdn.game.service.ItemService;

/**
 * 物品使用效果。
 *
 * <p>效果的 {@code execute} 直接收到 {@link PlayerService} / {@link ItemService}，
 * 其余服务（Buff / Skill / Map…）通过第 4 个参数 {@link com.mtxgdn.common.service.ServiceLookup}
 * 取 —— 效果对象由物品定义在配置加载期构造，没有容器可注入，所以由调用方
 * （{@code ItemService}，它自己持有查询面）把查询面传进来。</p>
 */
public abstract class ItemEffect {

    public abstract String execute(long playerId, PlayerService playerService, ItemService itemService,
                                   com.mtxgdn.common.service.ServiceLookup services);

    public boolean isInstantEffect() {
        return true;
    }
}
