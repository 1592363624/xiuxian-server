package com.mtxgdn.game.entity;
import java.util.List;


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
public class CombatResult {
    private boolean success;
    private boolean pending;
    private String winner;
    private boolean challengerWon;
    private String challengerName;
    private String targetName;
    private int challengerRemainingHp;
    private int targetRemainingHp;
    private int totalRounds;
    private long expReward;
    private long goldReward;
    /** 险地 PK 从败者身上掠夺到的灵石（危险度 <3 的地点恒为 0）。 */
    private long spiritStonesLooted;
    private List<String> battleLog;
    private String message;

    public CombatResult() {}

    public static CombatResult failure(String message) {
        CombatResult r = new CombatResult();
        r.success = false;
        r.message = message;
        return r;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public boolean isPending() { return pending; }
    public void setPending(boolean pending) { this.pending = pending; }
    public String getWinner() { return winner; }
    public void setWinner(String winner) { this.winner = winner; }
    public boolean isChallengerWon() { return challengerWon; }
    public void setChallengerWon(boolean challengerWon) { this.challengerWon = challengerWon; }
    public String getChallengerName() { return challengerName; }
    public void setChallengerName(String challengerName) { this.challengerName = challengerName; }
    public String getTargetName() { return targetName; }
    public void setTargetName(String targetName) { this.targetName = targetName; }
    public int getChallengerRemainingHp() { return challengerRemainingHp; }
    public void setChallengerRemainingHp(int challengerRemainingHp) { this.challengerRemainingHp = challengerRemainingHp; }
    public int getTargetRemainingHp() { return targetRemainingHp; }
    public void setTargetRemainingHp(int targetRemainingHp) { this.targetRemainingHp = targetRemainingHp; }
    public int getTotalRounds() { return totalRounds; }
    public void setTotalRounds(int totalRounds) { this.totalRounds = totalRounds; }
    public long getExpReward() { return expReward; }
    public void setExpReward(long expReward) { this.expReward = expReward; }
    public long getGoldReward() { return goldReward; }
    public void setGoldReward(long goldReward) { this.goldReward = goldReward; }
    public long getSpiritStonesLooted() { return spiritStonesLooted; }
    public void setSpiritStonesLooted(long spiritStonesLooted) { this.spiritStonesLooted = spiritStonesLooted; }
    public List<String> getBattleLog() { return battleLog; }
    public void setBattleLog(List<String> battleLog) { this.battleLog = battleLog; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
