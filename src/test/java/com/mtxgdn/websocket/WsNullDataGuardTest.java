package com.mtxgdn.websocket;

import com.google.gson.JsonObject;
import com.mtxgdn.TestBase;
import com.mtxgdn.common.GameErrorCode;
import com.mtxgdn.common.GameMessage;
import org.glassfish.grizzly.GrizzlyFuture;
import org.glassfish.grizzly.websockets.DataFrame;
import org.glassfish.grizzly.websockets.WebSocket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WebSocket 处理器对 {@code data == null} 的防御。
 *
 * <p><b>背景</b>：客户端可以发一个只有 {@code type} 没有 {@code data} 的帧，
 * 例如 {@code {"type":"map_travel","msgId":1}}。反序列化后 {@code data} 是
 * {@code null}，而下面这些方法<b>第一句就是</b> {@code data.has(...)}，
 * 直接 NPE：</p>
 *
 * <ul>
 *   <li>{@code WsSectHandler.handleSectTransfer}</li>
 *   <li>{@code WsSectHandler.handleSectWar}</li>
 *   <li>{@code WsMapHandler.handleMapTravel}</li>
 * </ul>
 *
 * <p>同一批文件里其它 action 全都写了 {@code data != null && ...}，
 * 这三处是漏的 —— 属于遗漏而非设计。</p>
 *
 * <p><b>危害程度</b>：{@code GameWebSocketApp.onMessage} 有 try/catch，
 * 会把 NPE 转成 {@code MESSAGE_PARSE_ERROR} 且<b>不断开连接</b>，
 * 所以不是拒绝服务。但它把"客户端少传字段"记成了一条异常，
 * 日志里全是噪音，真正的问题反而看不见。而且这与同文件其它 action
 * 的行为不一致 —— 同一种错误输入应产生同一种回答。</p>
 */
class WsNullDataGuardTest extends TestBase {

    private RecordingSocket socket;
    private WsSectHandler sectHandler;
    private WsMapHandler mapHandler;
    private Long userId;
    private long playerId;

    /** 只记录、不真发消息的假连接。 */
    private static final class RecordingSocket implements WebSocket {
        private final List<String> sent = new ArrayList<>();

        @Override
        public GrizzlyFuture<DataFrame> send(String data) {
            sent.add(data);
            return null;
        }

        @Override
        public GrizzlyFuture<DataFrame> send(byte[] bytes) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void broadcast(Iterable<? extends WebSocket> recipients, String data) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void broadcast(Iterable<? extends WebSocket> recipients, byte[] bytes) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void broadcastFragment(Iterable<? extends WebSocket> recipients, String data, boolean last) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void broadcastFragment(Iterable<? extends WebSocket> recipients, byte[] bytes, boolean last) {
            throw new UnsupportedOperationException();
        }

        @Override
        public GrizzlyFuture<DataFrame> sendPing(byte[] bytes) {
            throw new UnsupportedOperationException();
        }

        @Override
        public GrizzlyFuture<DataFrame> sendPong(byte[] bytes) {
            throw new UnsupportedOperationException();
        }

        @Override
        public GrizzlyFuture<DataFrame> stream(boolean last, String fragment) {
            throw new UnsupportedOperationException();
        }

        @Override
        public GrizzlyFuture<DataFrame> stream(boolean last, byte[] bytes, int offset, int length) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void close() {
        }

        @Override
        public void close(int code) {
        }

        @Override
        public void close(int code, String reason) {
        }

        @Override
        public boolean isConnected() {
            return true;
        }

        @Override
        public void onConnect() {
        }

        @Override
        public void onMessage(String text) {
        }

        @Override
        public void onMessage(byte[] bytes) {
        }

        @Override
        public void onFragment(boolean last, String fragment) {
        }

        @Override
        public void onFragment(boolean last, byte[] bytes) {
        }

        @Override
        public void onClose(DataFrame frame) {
        }

        @Override
        public void onPing(DataFrame frame) {
        }

        @Override
        public void onPong(DataFrame frame) {
        }

        @Override
        public boolean add(org.glassfish.grizzly.websockets.WebSocketListener listener) {
            return false;
        }

        @Override
        public boolean remove(org.glassfish.grizzly.websockets.WebSocketListener listener) {
            return false;
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        socket = new RecordingSocket();
        // WsContext 只有两个"发给别人"的方法，这两个不参与本测试
        WsContext ctx = new WsContext() {
            @Override
            public void broadcast(GameMessage message) {
            }

            @Override
            public void sendToUser(Long uid, GameMessage message) {
            }
        };
        sectHandler = new WsSectHandler(ctx);
        mapHandler = new WsMapHandler(ctx);

        long[] ids = createUserAndPlayer("wsnulldata" + System.nanoTime());
        userId = ids[0];
        playerId = ids[1];
    }

    @Test
    @DisplayName("map_travel 在 data 为 null 时回 PARAM_MISSING，不抛 NPE")
    void mapTravelNullData() {
        assertDoesNotThrow(() -> mapHandler.handleMapTravel(socket, 1L, userId, null),
                "data 为 null 不应抛 NPE");
        assertSentParamMissing();
    }

    @Test
    @DisplayName("sect_transfer 在 data 为 null 时回 PARAM_MISSING，不抛 NPE")
    void sectTransferNullData() {
        assertDoesNotThrow(() -> sectHandler.handleSectTransfer(socket, 2L, userId, null),
                "data 为 null 不应抛 NPE");
        assertSentParamMissing();
    }

    @Test
    @DisplayName("sect_war 在 data 为 null 时回 PARAM_MISSING，不抛 NPE")
    void sectWarNullData() {
        assertDoesNotThrow(() -> sectHandler.handleSectWar(socket, 3L, userId, null),
                "data 为 null 不应抛 NPE");
        assertSentParamMissing();
    }

    @Test
    @DisplayName("空对象 {} 同样回 PARAM_MISSING（字段缺失路径与 null 路径一致）")
    void emptyObjectTreatedLikeNull() {
        JsonObject empty = new JsonObject();

        assertDoesNotThrow(() -> mapHandler.handleMapTravel(socket, 4L, userId, empty));
        assertEquals(1, socket.sent.size(), "应回一条错误消息");
        socket.sent.clear();

        assertDoesNotThrow(() -> sectHandler.handleSectTransfer(socket, 5L, userId, empty));
        assertEquals(1, socket.sent.size());

        socket.sent.clear();
        assertDoesNotThrow(() -> sectHandler.handleSectWar(socket, 6L, userId, empty));
        assertEquals(1, socket.sent.size());
    }

    @Test
    @DisplayName("targetSectId=0 / locationId=0 走同一条参数校验，不进业务层")
    void zeroIdRejected() {
        JsonObject travel = new JsonObject();
        travel.addProperty("locationId", 0);
        mapHandler.handleMapTravel(socket, 7L, userId, travel);
        assertSentParamMissing();

        socket.sent.clear();
        JsonObject war = new JsonObject();
        war.addProperty("targetSectId", 0);
        sectHandler.handleSectWar(socket, 8L, userId, war);
        assertSentParamMissing();
    }

    /** 断言最后一条回复是 PARAM_MISSING。 */
    private void assertSentParamMissing() {
        assertEquals(1, socket.sent.size(), "应恰好回一条消息");
        String json = socket.sent.get(0);
        assertTrue(json.contains(String.valueOf(GameErrorCode.PARAM_MISSING.getCode())),
                "应回 PARAM_MISSING（" + GameErrorCode.PARAM_MISSING.getCode() + "），实际: " + json);
    }

    private static long[] createUserAndPlayer(String username) throws Exception {
        try (java.sql.Connection conn = com.mtxgdn.db.DatabaseManager.getConnection()) {
            try (java.sql.PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO users (username, password) VALUES (?, ?)")) {
                ps.setString(1, username);
                ps.setString(2, "x");
                ps.executeUpdate();
            }
            long uid;
            try (java.sql.PreparedStatement ps = conn.prepareStatement(
                    "SELECT id FROM users WHERE username = ?")) {
                ps.setString(1, username);
                try (java.sql.ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    uid = rs.getLong(1);
                }
            }
            try (java.sql.PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO players (user_id, name, spiritual_root, realm, sub_realm, "
                            + "hp, max_hp, mp, max_mp, attack, defense, speed, spirit, experience, "
                            + "gold, last_breakthrough_time) "
                            + "VALUES (?, ?, 'FIRE_WOOD_EARTH', 0, 0, 100, 100, 50, 50, 10, 10, 10, 10, 0, 0, 0)")) {
                ps.setLong(1, uid);
                ps.setString(2, username);
                ps.executeUpdate();
            }
            try (java.sql.PreparedStatement ps = conn.prepareStatement(
                    "SELECT id FROM players WHERE user_id = ?")) {
                ps.setLong(1, uid);
                try (java.sql.ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    return new long[]{uid, rs.getLong(1)};
                }
            }
        }
    }
}
