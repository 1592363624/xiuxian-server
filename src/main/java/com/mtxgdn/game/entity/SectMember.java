package com.mtxgdn.game.entity;

public class SectMember {

    private long id;
    private long sectId;
    private long playerId;
    private String playerName;
    private String role;
    private long contribution;
    private int playerRealm;
    private String playerRealmName;
    private int playerLevel;
    private String joinedAt;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getSectId() { return sectId; }
    public void setSectId(long sectId) { this.sectId = sectId; }

    public long getPlayerId() { return playerId; }
    public void setPlayerId(long playerId) { this.playerId = playerId; }

    public String getPlayerName() { return playerName; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public boolean isLeader() { return ROLE_LEADER.equals(role); }
    public boolean isViceLeader() { return ROLE_VICE_LEADER.equals(role); }
    public boolean isElder() { return ROLE_ELDER.equals(role); }
    public boolean isInnerMember() { return ROLE_INNER_MEMBER.equals(role); }
    public boolean isOuterMember() { return ROLE_OUTER_MEMBER.equals(role); }

    /** 管理权限：宗主/副宗主/长老 */
    public boolean canManage() { return isLeader() || isViceLeader() || isElder(); }

    /** 能否踢出/任命对方（需先通过 canManage） */
    public boolean canKickOrAppoint(SectMember target) {
        int myRank = getRoleRank();
        int targetRank = target.getRoleRank();
        return myRank < targetRank; // 排名数字越小权力越大，只能操作下级
    }

    /** 角色等级（数字越小越高） */
    public int getRoleRank() {
        return switch (role) {
            case ROLE_LEADER -> 0;
            case ROLE_VICE_LEADER -> 1;
            case ROLE_ELDER -> 2;
            case ROLE_INNER_MEMBER -> 3;
            case ROLE_OUTER_MEMBER -> 4;
            default -> 4;
        };
    }

    public long getContribution() { return contribution; }
    public void setContribution(long contribution) { this.contribution = contribution; }

    public int getPlayerRealm() { return playerRealm; }
    public void setPlayerRealm(int playerRealm) { this.playerRealm = playerRealm; }

    public String getPlayerRealmName() { return playerRealmName; }
    public void setPlayerRealmName(String playerRealmName) { this.playerRealmName = playerRealmName; }

    public int getPlayerLevel() { return playerLevel; }
    public void setPlayerLevel(int playerLevel) { this.playerLevel = playerLevel; }

    public String getJoinedAt() { return joinedAt; }
    public void setJoinedAt(String joinedAt) { this.joinedAt = joinedAt; }

    public static final String ROLE_LEADER = "LEADER";
    public static final String ROLE_VICE_LEADER = "VICE_LEADER";
    public static final String ROLE_ELDER = "ELDER";
    public static final String ROLE_INNER_MEMBER = "INNER_MEMBER";
    public static final String ROLE_OUTER_MEMBER = "OUTER_MEMBER";

    public static String getRoleDisplayName(String role) {
        return switch (role) {
            case ROLE_LEADER -> "宗主";
            case ROLE_VICE_LEADER -> "副宗主";
            case ROLE_ELDER -> "长老";
            case ROLE_INNER_MEMBER -> "内门弟子";
            case ROLE_OUTER_MEMBER -> "外门弟子";
            default -> role;
        };
    }
}
