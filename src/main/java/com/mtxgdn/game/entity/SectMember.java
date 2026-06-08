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
    public boolean isLeader() { return "LEADER".equals(role); }
    public boolean isElder() { return "ELDER".equals(role); }
    public boolean canManage() { return isLeader() || isElder(); }

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
    public static final String ROLE_ELDER = "ELDER";
    public static final String ROLE_MEMBER = "MEMBER";

    public static String getRoleDisplayName(String role) {
        return switch (role) {
            case ROLE_LEADER -> "宗主";
            case ROLE_ELDER -> "长老";
            case ROLE_MEMBER -> "弟子";
            default -> role;
        };
    }
}
