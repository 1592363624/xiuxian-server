package com.mtxgdn.security;

import com.google.gson.JsonObject;
import com.mtxgdn.util.JsonBody;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 请求体解析的健壮性测试。
 *
 * <p><b>背景</b>：项目里有 59 处直接写 {@code JsonParser.parseString(body)
 * .getAsJsonObject()}，绝大多数没包 try/catch。下面三类输入都会抛异常，
 * 被 {@code GenericExceptionMapper} 转成 <b>500 + 一整条堆栈写进日志</b>：</p>
 *
 * <ul>
 *   <li>请求体不是合法 JSON —— {@code JsonSyntaxException}；</li>
 *   <li>合法 JSON 但不是对象（{@code [1,2,3]}）——
 *       {@code IllegalStateException: Not a JSON Object}；</li>
 *   <li>缺必填字段却写 {@code json.get("x").getAsLong()} ——
 *       {@code get} 返回 {@code null}，随即 NPE。</li>
 * </ul>
 *
 * <p>第三类最糟：它把「客户端少传一个字段」报成「服务器内部错误」，
 * 排查时完全指错方向。而且这三种输入攻击者零成本 —— 一个空 body
 * 就能让服务端写一条堆栈。</p>
 */
class JsonBodyTest {

    // ==================== 解析：非法输入不抛异常 ====================

    @Test
    @DisplayName("非法 JSON 返回 null 而不是抛异常")
    void malformedJsonReturnsNull() {
        assertNull(JsonBody.parse("{"));
        assertNull(JsonBody.parse("not json at all"));
        assertNull(JsonBody.parse("{\"a\": }"));
    }

    @Test
    @DisplayName("合法 JSON 但不是对象（数组/字符串/数字）返回 null")
    void nonObjectJsonReturnsNull() {
        assertNull(JsonBody.parse("[1,2,3]"), "数组不应被当成对象");
        assertNull(JsonBody.parse("\"just a string\""));
        assertNull(JsonBody.parse("42"));
        assertNull(JsonBody.parse("null"));
    }

    @Test
    @DisplayName("空 body / null / 空白返回 null 而不抛异常")
    void emptyBodyReturnsNull() {
        assertNull(JsonBody.parse(null));
        assertNull(JsonBody.parse(""));
        assertNull(JsonBody.parse("   "));
    }

    @Test
    @DisplayName("合法对象能正常解析出字段")
    void validObjectParsed() {
        JsonObject json = JsonBody.parse("{\"itemKey\":\"mtxgdn:sword\",\"quantity\":3}");
        assertNotNull(json, "合法对象应解析成功");
        assertEquals("mtxgdn:sword", JsonBody.getString(json, "itemKey", ""));
        assertEquals(3, JsonBody.getInt(json, "quantity", 1));
    }

    // ==================== 字段读取：缺失不抛 NPE ====================

    @Test
    @DisplayName("缺失必填字段时 hasAll 返回 false（调用方据此回参数错误而非 500）")
    void hasAllDetectsMissingFields() {
        JsonObject empty = JsonBody.parse("{}");
        assertFalse(JsonBody.hasAll(empty, "listingId"),
                "空对象应及时发现缺少 listingId");
        assertFalse(JsonBody.hasAll(empty, "itemKey", "priceSpiritStones"),
                "两个必填项都缺时应返回 false");
        assertFalse(JsonBody.hasAll(null, "anything"), "null 对象应返回 false");
    }

    @Test
    @DisplayName("字段齐全时 hasAll 返回 true")
    void hasAllPassesWhenComplete() {
        JsonObject json = JsonBody.parse("{\"itemKey\":\"a\",\"priceSpiritStones\":100}");
        assertTrue(JsonBody.hasAll(json, "itemKey", "priceSpiritStones"));
    }

    @Test
    @DisplayName("显式 null 值等同于缺失（防止把 JSON null 当成有效值）")
    void explicitNullTreatedAsMissing() {
        JsonObject json = JsonBody.parse("{\"listingId\":null}");
        assertFalse(JsonBody.hasAll(json, "listingId"),
                "JSON null 不应被当作存在");
        assertEquals(0, JsonBody.getLong(json, "listingId", 0),
                "读 null 字段应回落默认值");
    }

    @Test
    @DisplayName("缺失字段读取返回默认值，不抛 NPE")
    void missingFieldReturnsFallback() {
        JsonObject json = JsonBody.parse("{}");
        assertEquals("", JsonBody.getString(json, "itemKey", ""));
        assertEquals(0L, JsonBody.getLong(json, "listingId", 0));
        assertEquals(1, JsonBody.getInt(json, "quantity", 1));
        assertFalse(JsonBody.getBoolean(json, "flag", false));
    }

    @Test
    @DisplayName("字段类型不符时回落默认值，不抛异常")
    void wrongTypeReturnsFallback() {
        JsonObject json = JsonBody.parse("{\"amount\":\"not-a-number\",\"name\":123}");
        assertEquals(0L, JsonBody.getLong(json, "amount", 0),
                "字符串数字无法解析时应回落默认值");
        // 数字转字符串 Gson 是允许的，这里只要求不抛异常
        assertNotNull(JsonBody.getString(json, "name", ""));
    }

    @Test
    @DisplayName("json 本身为 null 时所有读取都安全")
    void nullObjectSafe() {
        assertEquals("", JsonBody.getString(null, "x", ""));
        assertEquals(-1L, JsonBody.getLong(null, "x", -1L));
        assertEquals(-1, JsonBody.getInt(null, "x", -1));
        assertFalse(JsonBody.getBoolean(null, "x", false));
    }

    @Test
    @DisplayName("攻击载荷：空 body 打到买/取消挂单，不应产生 500")
    void emptyBodyDoesNotBreakMarketEndpoints() {
        // 模拟 /market/buy 与 /market/cancel 的入口判断
        JsonObject json = JsonBody.parse("");
        assertNull(json);
        assertFalse(JsonBody.hasAll(json, "listingId"),
                "空 body 必须在进 service 之前被判为参数缺失");
    }
}
