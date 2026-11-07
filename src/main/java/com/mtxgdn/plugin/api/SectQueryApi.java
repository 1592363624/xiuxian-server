package com.mtxgdn.plugin.api;

/**
 * 宗门归属查询能力：供其它模块/插件判断"这个玩家属于哪个宗门"。
 *
 * <p>这是插件服务注册表（{@code PluginServiceRegistry}）的示范接口：
 * 消费方只依赖本接口，不需要 import {@code SectService}，也不需要知道宗门模块的内部结构。</p>
 *
 * <p><b>为什么全是只读方法</b>：跨插件调用很容易在无意间绕过对方的校验。
 * 查询是幂等的、无副作用的，所以可以放心开放；入宗、退位、解散这类写操作
 * 必须走宗门模块自己的指令/接口，那里有完整的权限与事务处理。</p>
 *
 * <p><b>取不到怎么办</b>：{@code PluginContext.getService(SectQueryApi.class)}
 * 在宗门模块被停用时返回 {@code null}。消费方应当据此关闭依赖宗门的功能，
 * 而不是假设它一定存在。</p>
 */
public interface SectQueryApi {

    /** 玩家所属宗门 id；无宗门返回 0。 */
    long getSectIdOf(long playerId);

    /** 玩家所属宗门名；无宗门返回 null。 */
    String getSectNameOf(long playerId);

    /** 宗门等级；宗门不存在返回 0。 */
    int getSectLevel(long sectId);

    /** 宗门当前成员数；宗门不存在返回 0。 */
    int getMemberCount(long sectId);

    /** 两个玩家是否同属一个宗门（都无宗门时为 false —— "都没宗门"不构成同门关系）。 */
    boolean areSectMates(long playerIdA, long playerIdB);
}
