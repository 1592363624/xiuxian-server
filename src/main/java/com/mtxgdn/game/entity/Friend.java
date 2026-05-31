package com.mtxgdn.game.entity;

public class Friend {

    private long id;
    private long playerId;
    private String playerName;
    private long friendPlayerId;
    private String friendName;
    private String friendRealm;
    private String status;
    private String createdAt;
    private String updatedAt;

    public Friend() {
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getPlayerId() {
        return playerId;
    }

    public void setPlayerId(long playerId) {
        this.playerId = playerId;
    }

    public String getPlayerName() {
        return playerName;
    }

    public void setPlayerName(String playerName) {
        this.playerName = playerName;
    }

    public long getFriendPlayerId() {
        return friendPlayerId;
    }

    public void setFriendPlayerId(long friendPlayerId) {
        this.friendPlayerId = friendPlayerId;
    }

    public String getFriendName() {
        return friendName;
    }

    public void setFriendName(String friendName) {
        this.friendName = friendName;
    }

    public String getFriendRealm() {
        return friendRealm;
    }

    public void setFriendRealm(String friendRealm) {
        this.friendRealm = friendRealm;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }
}
