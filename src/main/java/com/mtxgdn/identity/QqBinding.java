package com.mtxgdn.identity;

/**
 * 外部账号与游戏账号的绑定关系。
 *
 * <p>之前放在 onebot 包里，导致核心层（CommandContext、两个 DAO）为了这一个类
 * 反向依赖协议层 —— 绑定本身是「账号身份」的领域概念，QQ 号只是当前
 * 恰好存在的身份之一；将来 Minecraft 端、WebUI 端接入时复用的是同一张表、
 * 同一套绑定语义，而不是各自再造一个 XxxBinding。</p>
 *
 * <p>挪到 identity 包后：核心层只依赖 identity，协议层（qq/onebot）依赖
 * identity，依赖方向重新变回单向。</p>
 *
 * <p>类名保留 QqBinding：数据库表、字段、对外 API 都以 QQ 号为现实主体，
 * 改名是无谓的噪音；包位置表达的是归属，不改变事实。</p>
 */
public class QqBinding {

    private Long id;
    private String qqNumber;
    /** QQ 官方通道：机器人 AppID（OneBot/QQ号 场景为空）；历史兼容行为空字符串 */
    private String appId;
    private Long userId;
    private String createdAt;

    public QqBinding() {
    }

    public QqBinding(String qqNumber, Long userId) {
        this.qqNumber = qqNumber;
        this.userId = userId;
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

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}
