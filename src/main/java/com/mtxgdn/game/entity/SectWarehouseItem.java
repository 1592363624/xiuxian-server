package com.mtxgdn.game.entity;

public class SectWarehouseItem {

    private long id;
    private long sectId;
    private String itemKey;
    private int quantity;
    private Long donatedByPlayerId;
    private String donatedByName;
    private String createdAt;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getSectId() { return sectId; }
    public void setSectId(long sectId) { this.sectId = sectId; }

    public String getItemKey() { return itemKey; }
    public void setItemKey(String itemKey) { this.itemKey = itemKey; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public Long getDonatedByPlayerId() { return donatedByPlayerId; }
    public void setDonatedByPlayerId(Long donatedByPlayerId) { this.donatedByPlayerId = donatedByPlayerId; }

    public String getDonatedByName() { return donatedByName; }
    public void setDonatedByName(String donatedByName) { this.donatedByName = donatedByName; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
