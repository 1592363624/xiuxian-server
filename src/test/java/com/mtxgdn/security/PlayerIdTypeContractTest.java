package com.mtxgdn.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 玩家 id 的类型契约测试：整条链路必须是 {@code long}。
 *
 * <p><b>为什么值得专门钉一条测试</b>：{@code players.id} 是自增 {@code BIGINT}
 * （SQLite 下 {@code INTEGER PRIMARY KEY} 实际也是 64 位），所有 service 方法的
 * {@code playerId} 参数都是 {@code long}。但 REST 层原本有个方法写成：</p>
 *
 * <pre>
 *   static int getPlayerIdByUserId(long userId) {
 *       return (int) player.getId();     // ← 64 位收窄成 32 位
 *   }
 * </pre>
 *
 * <p>这个 {@code int} 随后被隐式扩宽回 {@code long} 传给 service，所以
 * <b>编译期完全无声</b>，运行时也只有在 id 超过 2^31-1 时才暴露 ——
 * 届时 id 会回绕成负数或另一个玩家的 id，玩家就可能操作到别人的数据
 * （越权读背包、越权操作他人农田等）。</p>
 *
 * <p>这种"低概率 + 无声 + 后果是越权"的组合，最适合用反射把类型契约钉死：
 * 一旦有人把它改回 {@code int}，测试立刻失败，而不是等数据库涨到 21 亿行。</p>
 */
class PlayerIdTypeContractTest {

    @Test
    @DisplayName("GameSupport.getPlayerIdByUserId 必须返回 long，不得用 int 截断")
    void getPlayerIdByUserIdReturnsLong() {
        Method m;
        try {
            Class<?> clazz = Class.forName("com.mtxgdn.rest.GameSupport");
            m = clazz.getDeclaredMethod("getPlayerIdByUserId", long.class);
        } catch (Exception e) {
            fail("找不到 GameSupport.getPlayerIdByUserId(long)，测试需要更新: " + e.getMessage());
            return;
        }

        assertEquals(long.class, m.getReturnType(),
                "getPlayerIdByUserId 必须返回 long：players.id 是 BIGINT，"
                        + "返回 int 会在 id 超过 2^31-1 时截断成负数或他人 id，导致越权");
    }

    @Test
    @DisplayName("PlayerInfo.getId 必须是 long（持久层 id 不得被收窄）")
    void playerInfoIdIsLong() throws Exception {
        Class<?> clazz = Class.forName("com.mtxgdn.game.entity.PlayerInfo");
        Method m = clazz.getMethod("getId");
        assertEquals(long.class, m.getReturnType(),
                "PlayerInfo.getId 必须是 long，与 players.id 的 BIGINT 保持一致");
    }
}
