package com.mtxgdn.game.command.item;

import com.mtxgdn.TestBase;
import com.mtxgdn.common.command.CommandContext;
import com.mtxgdn.common.service.ServiceRegistry;
import com.mtxgdn.db.DatabaseManager;
import com.mtxgdn.game.entity.InventoryEntry;
import com.mtxgdn.game.entity.PlayerInfo;
import com.mtxgdn.game.item.ItemScanner;
import com.mtxgdn.game.service.ItemService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 背包序号（索引）回归。
 *
 * <p><b>需求</b>：{@code /背包} 的列表要带序号，且 {@code /使用 <序号>}、
 * {@code /装备 <序号> <部位>} 能直接引用，常见物品不必打全名或全 key。</p>
 *
 * <p>钉住三件事：</p>
 * <ol>
 *   <li>序号与背包列表顺序一致（1 起，按 item_key 排序）；</li>
 *   <li>非数字 / 越界输入原样返回，交给按 key 的既有路径报错，不抛异常；</li>
 *   <li>{@code /背包} 的渲染里真的出现 {@code [1]} 序号与使用提示。</li>
 * </ol>
 */
class BackpackIndexTest extends TestBase {

    private static final String USER_PREFIX = "backpack_index_user_";
    private static final String HEALING_PILL = "mtxgdn:healing_pill";
    private static final String MANA_PILL = "mtxgdn:mana_pill";

    private final ItemService itemService = ServiceRegistry.container().getItemService();
    private final com.mtxgdn.game.service.PlayerService playerService =
            ServiceRegistry.container().getPlayerService();

    private PlayerInfo player;

    @BeforeAll
    static void registerItems() {
        ItemScanner.scanAndRegister();
    }

    @BeforeEach
    void setUp() throws Exception {
        cleanup();
        long userId = insertUser();
        player = playerService.createPlayer(userId, "背包序号测试者" + System.nanoTime());
        assertTrue(player != null, "测试角色创建失败");
        assertTrue(itemService.addItem(player.getId(), HEALING_PILL, 2), "放入回血丹应成功");
        assertTrue(itemService.addItem(player.getId(), MANA_PILL, 1), "放入回灵丹应成功");
    }

    @AfterEach
    void tearDown() throws Exception {
        cleanup();
    }

    @Test
    @DisplayName("数字序号按背包展示顺序解析成 itemKey")
    void resolvesIndexToItemKey() {
        List<InventoryEntry> inventory = itemService.getInventory(player.getId());
        assertTrue(inventory.size() >= 2, "背包里应至少有两类物品");

        for (int i = 0; i < inventory.size(); i++) {
            String expectedKey = inventory.get(i).getItem().getKey();
            assertEquals(expectedKey,
                    itemService.resolveInventoryItemReference(player.getId(), String.valueOf(i + 1)),
                    "第 " + (i + 1) + " 个序号应解析成列表同位置的 itemKey");
        }
    }

    @Test
    @DisplayName("非数字 / 越界输入原样返回（不抛异常、不猜）")
    void nonNumericOrOutOfRangePassesThrough() {
        assertEquals("回血丹",
                itemService.resolveInventoryItemReference(player.getId(), "回血丹"));
        assertEquals("999",
                itemService.resolveInventoryItemReference(player.getId(), "999"));
        assertEquals("0",
                itemService.resolveInventoryItemReference(player.getId(), "0"));
        assertEquals("",
                itemService.resolveInventoryItemReference(player.getId(), "  "));
    }

    @Test
    @DisplayName("/背包 渲染带序号与使用提示")
    void backpackRendersIndexes() {
        CapturingContext ctx = new CapturingContext(player);
        new BackpackCommand().execute(ctx);
        // 第一条是背包本体；后面可能还有新手引导提示，别拿最后一条断言
        String reply = ctx.replies.isEmpty() ? "" : ctx.replies.get(0);
        assertTrue(reply.contains("[1]"), "背包列表应带序号，实际输出:\n" + reply);
        assertTrue(reply.contains("使用: /使用 <序号或名称>"),
                "应提示可用序号，实际输出:\n" + reply);
        assertTrue(reply.contains("容量:"), "原有容量行不应丢失");
    }

    // ==================== 夹具 ====================

    private long insertUser() throws Exception {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO users (username, password) VALUES (?, ?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, USER_PREFIX + System.nanoTime());
            ps.setString(2, "x");
            ps.executeUpdate();
            try (var keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : 0L;
            }
        }
    }

    private void cleanup() throws Exception {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM players WHERE user_id IN (SELECT id FROM users WHERE username LIKE ?)")) {
            ps.setString(1, USER_PREFIX + "%");
            ps.executeUpdate();
        }
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM users WHERE username LIKE ?")) {
            ps.setString(1, USER_PREFIX + "%");
            ps.executeUpdate();
        }
    }

    /** 捕获指令回复的最小上下文（不依赖任何适配器）。 */
    private static final class CapturingContext extends CommandContext {
        private final PlayerInfo player;
        private final List<String> replies = new ArrayList<>();

        CapturingContext(PlayerInfo player) {
            super("backpack-index-sender", player.getName(), "");
            this.player = player;
        }

        @Override public boolean isGroup() { return false; }
        @Override public void reply(String message) { replies.add(message); }
        @Override public void replyPrivate(String message) { replies.add(message); }
        @Override public Long requireBinding() { return player.getUserId(); }
        @Override public PlayerInfo requirePlayer(Long userId) { return player; }
        @Override public boolean checkPermission(String code) { return true; }
    }
}
