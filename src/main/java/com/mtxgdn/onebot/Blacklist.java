package com.mtxgdn.onebot;

public class Blacklist {

    private Long id;
    // qqNumber 和 userId 二选一，不能同时填写
    private String qqNumber;
    private Long userId;
    private String reason;
    private Long bannedBy;
    private String createdAt;

    public Blacklist() {
    }

    /**
     * @param qqNumber QQ号（与 userId 二选一，直接填写QQ号时用）
     * @param userId   用户ID（与 qqNumber 二选一，会自动查绑定拿到QQ号禁言）
     */
    public Blacklist(String qqNumber, Long userId, String reason, Long bannedBy) {
        if (qqNumber != null && !qqNumber.isEmpty() && userId != null) {
            throw new IllegalArgumentException("qqNumber 和 userId 只能二选一");
        }
        if ((qqNumber == null || qqNumber.isEmpty()) && userId == null) {
            throw new IllegalArgumentException("qqNumber 和 userId 必须填写其中一个");
        }
        this.qqNumber = qqNumber;
        this.userId = userId;
        this.reason = reason;
        this.bannedBy = bannedBy;
    }

    /** qqNumber 和 userId 是否二选一已正确填写 */
    public boolean isValid() {
        boolean hasQq = qqNumber != null && !qqNumber.isEmpty();
        boolean hasUid = userId != null;
        return hasQq != hasUid;
    }

    /** 是QQ号模式（非用户ID模式） */
    public boolean isQqMode() {
        return qqNumber != null && !qqNumber.isEmpty();
    }

    /** 是用户ID模式（非QQ号模式） */
    public boolean isUserMode() {
        return userId != null;
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