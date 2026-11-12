package com.mtxgdn.game.entity;

import com.mtxgdn.api.HeartDemonApi;
import com.mtxgdn.common.service.ServiceRegistry;
import com.mtxgdn.entity.Player;
import com.mtxgdn.game.entity.SpiritualRoot;
import java.util.Random;

/**
 * 由 {HeartDemonService} 的内部嵌套类提取而来。
 * <p>原来写成 {$svc}.{$nest}，接口无法在不 import 服务实现类的前提下引用它，
 * 因此提到 entity 包 —— 它本来就是纯数据，和服务的生命周期无关。</p>
 */
public class HeartDemonResult {
    public boolean triggered;
    public String severity;
    public String narrative;
    public long rawExpGained;
    public long netExpChange;
    public long expLost;
    public double roll;
}
