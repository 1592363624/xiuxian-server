package com.mtxgdn.game.entity;

import com.mtxgdn.api.GuideApi;
import com.mtxgdn.common.service.ServiceRegistry;
import com.mtxgdn.game.entity.PlayerInfo;
import com.mtxgdn.game.entity.SpiritualRoot;

/**
 * 由 {NewbieGuideService} 的内部嵌套类提取而来。
 * <p>原来写成 {$svc}.{$nest}，接口无法在不 import 服务实现类的前提下引用它，
 * 因此提到 entity 包 —— 它本来就是纯数据，和服务的生命周期无关。</p>
 */
public class GuideResult {
    public String message;
}
