package com.mtxgdn.game.entity;

/**
 * 由 OfflineRewardService 的内部嵌套类提取而来。
 */
public class OfflineRewardResult {
    public boolean hasReward;
    public boolean wasCultivating;
    public int offlineSeconds;
    public int offlineMinutes;
    public int offlineHours;
    public long expGained;
    public long rawExpGained;
    public int hpRecovered;
    public int mpRecovered;
    public boolean heartDemonTriggered;
    public String heartDemonSeverity;
    public String heartDemonNarrative;
    public long heartDemonExpLost;

    public static OfflineRewardResult noReward() {
        OfflineRewardResult r = new OfflineRewardResult();
        r.hasReward = false;
        return r;
    }
}
