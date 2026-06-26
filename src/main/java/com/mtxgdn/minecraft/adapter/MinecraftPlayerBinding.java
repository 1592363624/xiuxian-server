package com.mtxgdn.minecraft.adapter;

public class MinecraftPlayerBinding {

    private Long id;
    private String mcUuid;
    private String mcName;
    private Long userId;
    private String createdAt;

    public MinecraftPlayerBinding() {
    }

    public MinecraftPlayerBinding(String mcUuid, String mcName, Long userId) {
        this.mcUuid = mcUuid;
        this.mcName = mcName;
        this.userId = userId;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getMcUuid() { return mcUuid; }
    public void setMcUuid(String mcUuid) { this.mcUuid = mcUuid; }

    public String getMcName() { return mcName; }
    public void setMcName(String mcName) { this.mcName = mcName; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
