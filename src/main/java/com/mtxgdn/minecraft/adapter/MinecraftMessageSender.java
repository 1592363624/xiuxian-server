package com.mtxgdn.minecraft.adapter;

/**
 * Minecraft 消息发送接口。
 * 由 {@link MinecraftAdapter} 实现，通过向 MC 服务端 stdin 写入 tellraw/msg 指令完成。
 */
public interface MinecraftMessageSender {

    /**
     * 回复到来源：玩家私聊或全体广播（取决于玩家是否在线）。
     */
    void replyToSource(String mcName, String minecraftUuid, String message);

    /**
     * 给指定玩家发私聊消息。
     */
    void sendPrivateMsg(String mcName, String message);

    /**
     * 向全体在线玩家广播。
     */
    void sendBroadcast(String message);

    /**
     * 向全体广播 title 大字。
     */
    void sendTitle(String mcName, String title, String subtitle);
}
