package com.mtxgdn.onebot;

public class Blacklist {

    private Long id;
    private String qqNumber;
    private Long userId;
    private String reason;
    private Long bannedBy;
    private String createdAt;

    public Blacklist() {
    }

    public Blacklist(String qqNumber, Long userId, String reason, Long bannedBy) {
        this.qqNumber = qqNumber;
        this.userId = userId;
        this.reason = reason;
        this.bannedBy = bannedBy;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getQqNumber() {
        return qqNumber;
    }

    public void setQqNumber(String qqNumber) {
        this.qqNumber = qqNumber;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Long getBannedBy() {
        return bannedBy;
    }

    public void setBannedBy(Long bannedBy) {
        this.bannedBy = bannedBy;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}