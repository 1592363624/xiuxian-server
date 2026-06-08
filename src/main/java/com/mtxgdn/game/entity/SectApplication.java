package com.mtxgdn.game.entity;

public class SectApplication {

    private long id;
    private long sectId;
    private long playerId;
    private String playerName;
    private String message;
    private String status;
    private String createdAt;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getSectId() { return sectId; }
    public void setSectId(long sectId) { this.sectId = sectId; }

    public long getPlayerId() { return playerId; }
    public void setPlayerId(long playerId) { this.playerId = playerId; }

    public String getPlayerName() { return playerName; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
