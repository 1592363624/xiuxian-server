package com.mtxgdn.minecraft.adapter;

import com.mtxgdn.common.command.CommandContext;
import com.mtxgdn.game.entity.PlayerInfo;
import com.mtxgdn.common.service.ServiceRegistry;

/**
 * Minecraft 指令上下文。
 * <p>
 * 与 {@code OneBotCommandContext} 结构对称，但 senderId = mcUuid，
 * groupId 始终为 null（Minecraft 聊天暂时不分群组，公私聊通过 messageType 区分）。
 * <p>
 * MC 中命令通过 {@code /} 前缀自然输入（客户端不拦截未知指令，服务端日志记录后由适配器解析），
 * 因此提示信息统一使用 {@code /} 前缀。
 */
public class MinecraftCommandContext extends CommandContext {

    private final String mcName;
    private final String mcUuid;
    private final MinecraftMessageSender sender;
    private final String cmdPrefix; // "/xiuxian " or "/"

    public MinecraftCommandContext(String mcName, String mcUuid, String arg,
                                   MinecraftMessageSender sender, String cmdPrefix) {
        super(mcUuid, mcName, arg);
        this.mcName = mcName;
        this.mcUuid = mcUuid;
        this.sender = sender;
        this.cmdPrefix = cmdPrefix;
    }

    public String getMcName() {
        return mcName;
    }

    public String getMcUuid() {
        return mcUuid;
    }

    public MinecraftMessageSender getMessageSender() {
        return sender;
    }

    @Override
    public boolean isGroup() {
        // MC 适配器暂时没有群的概念，所有消息都是类似私聊
        return false;
    }

    @Override
    public void reply(String message) {
        sender.replyToSource(mcName, mcUuid, message);
    }

    @Override
    public void replyPrivate(String message) {
        sender.sendPrivateMsg(mcName, message);
    }

    /** 向全体在线玩家广播 */
    public void sendBroadcast(String message) {
        sender.sendBroadcast(message);
    }

    // ==================== 覆写提示信息为 . 前缀 ====================

    @Override
    public Long requireBinding() {
        MinecraftPlayerBinding mcB = new MinecraftPlayerBindingService().findByMcUuid(mcUuid);
        if (mcB != null) {
            return mcB.getUserId();
        }
        reply("请先注册或绑定账号。\n注册: " + cmdPrefix + "register <角色名>\n绑定: " + cmdPrefix + "bind");
        return null;
    }

    @Override
    public PlayerInfo requirePlayer(Long userId) {
        PlayerInfo player = ServiceRegistry.getPlayerService().getPlayerByUserId(userId);
        if (player == null) {
            reply("你还没有创建修仙角色。\n请使用 " + cmdPrefix + "register <角色名> 注册角色");
            return null;
        }
        return player;
    }
}
