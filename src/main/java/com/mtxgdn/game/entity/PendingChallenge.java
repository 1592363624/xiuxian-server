package com.mtxgdn.game.entity;

import com.mtxgdn.api.CombatApi;
import com.mtxgdn.common.service.ServiceRegistry;
import com.mtxgdn.db.CombatDAO;
import com.mtxgdn.entity.Player;
import com.mtxgdn.game.entity.BossForm;
import com.mtxgdn.game.entity.MapLocation;
import com.mtxgdn.game.entity.Monster;
import com.mtxgdn.game.entity.PveCombatResult;
import com.mtxgdn.game.entity.RaidCombatResult;
import com.mtxgdn.game.entity.Skill;
import com.mtxgdn.game.entity.SpiritualRoot;
import com.mtxgdn.game.item.ItemRegistry;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 由 {CombatService} 的内部嵌套类提取而来。
 * <p>原来写成 {$svc}.{$nest}，接口无法在不 import 服务实现类的前提下引用它，
 * 因此提到 entity 包 —— 它本来就是纯数据，和服务的生命周期无关。</p>
 */
public class PendingChallenge {
    public final long challengerPlayerId;
    public final String challengerName;
    public final long targetPlayerId;
    public final String targetName;
    public final long createdAt;
    public PendingChallenge(long challengerPlayerId, String challengerName, long targetPlayerId, String targetName) {
        this.challengerPlayerId = challengerPlayerId;
        this.challengerName = challengerName;
        this.targetPlayerId = targetPlayerId;
        this.targetName = targetName;
        this.createdAt = System.currentTimeMillis();
    }
    /** 与 CombatService 原来的私有常量保持一致。 */
    private static final long CHALLENGE_TIMEOUT_MS = 30_000;

    public boolean isExpired() { return System.currentTimeMillis() - createdAt > CHALLENGE_TIMEOUT_MS; }
}
