package com.mtxgdn.onebot;

public class OneBotGroupConfig {

    private Long id;
    private Long groupId;
    private boolean autoMuteEnabled;
    private int muteDurationDays;
    private String createdAt;
    private String updatedAt;

    public OneBotGroupConfig() {
        this.autoMuteEnabled = false;
        this.muteDurationDays = 29;
    }

    public OneBotGroupConfig(Long groupId) {
        this.groupId = groupId;
        this.autoMuteEnabled = false;
        this.muteDurationDays = 29;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }

    public boolean isAutoMuteEnabled() {
        return autoMuteEnabled;
    }

    public void setAutoMuteEnabled(boolean autoMuteEnabled) {
        this.autoMuteEnabled = autoMuteEnabled;
    }

    public int getMuteDurationDays() {
        return muteDurationDays;
    }

    public void setMuteDurationDays(int muteDurationDays) {
        this.muteDurationDays = muteDurationDays;
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