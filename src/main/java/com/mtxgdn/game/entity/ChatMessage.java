package com.mtxgdn.game.entity;

public class ChatMessage {

    private long id;
    private String channel;
    private long senderPlayerId;
    private String senderName;
    private Long receiverPlayerId;
    private String receiverName;
    private String content;
    private String createdAt;

    public ChatMessage() {
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public long getSenderPlayerId() {
        return senderPlayerId;
    }

    public void setSenderPlayerId(long senderPlayerId) {
        this.senderPlayerId = senderPlayerId;
    }

    public String getSenderName() {
        return senderName;
    }

    public void setSenderName(String senderName) {
        this.senderName = senderName;
    }

    public Long getReceiverPlayerId() {
        return receiverPlayerId;
    }

    public void setReceiverPlayerId(Long receiverPlayerId) {
        this.receiverPlayerId = receiverPlayerId;
    }

    public String getReceiverName() {
        return receiverName;
    }

    public void setReceiverName(String receiverName) {
        this.receiverName = receiverName;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}
