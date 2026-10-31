package com.mtxgdn.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * REST 请求体的安全解析与字段读取。
 *
 * <p><b>为什么需要</b>：项目里有 59 处直接写 {@code JsonParser.parseString(body)
 * .getAsJsonObject()}，绝大多数没有包 try/catch。这三类输入都会让它们抛异常，
 * 被 {@code GenericExceptionMapper} 转成 <b>500 + 一整条堆栈写进日志</b>：</p>
 *
 * <ul>
 *   <li>请求体不是合法 JSON（{@code "{"}）→ {@code JsonSyntaxException}；</li>
 *   <li>请求体是合法 JSON 但不是对象（{@code [1,2,3]}）→
 *       {@code IllegalStateException: Not a JSON Object}；</li>
 *   <li>缺必填字段（{@code {}}）却写 {@code json.get("x").getAsLong()} →
 *       {@code json.get("x")} 返回 {@code null}，随即 NPE。</li>
 * </ul>
 *
 * <p>前两类是纯噪音（攻击者零成本，服务端写堆栈）；第三类更糟 ——
 * 它把「客户端少传了一个字段」报成「服务器内部错误」，排查时完全指错方向。</p>
 *
 * <p>本类把三种情况统一收敛成「返回 null / 默认值」，调用方据此回
 * {@code PARAM_MISSING} 或 {@code MESSAGE_PARSE_ERROR}，而不是 500。</p>
 *
 * <p><b>只做解析，不做业务校验</b>：数量为正、金额上限这类规则属于业务语义，
 * 仍归各 service 负责（例如 {@code TradeService.listItem} 的 quantity/price 夹取）。
 * 这里只保证「读不到的字段不会炸」。</p>
 */
public final class JsonBody {

    private JsonBody() {
    }

    /**
     * 把请求体解析成 JSON 对象。
     *
     * @param body 原始请求体（可为 {@code null} / 空串 / 非法 JSON）
     * @return 解析成功时返回对象；否则返回 {@code null}，调用方应回参数错误
     */
    public static JsonObject parse(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonElement element = JsonParser.parseString(body);
            if (element == null || !element.isJsonObject()) {
                return null;
            }
            return element.getAsJsonObject();
        } catch (RuntimeException e) {
            // JsonSyntaxException / IllegalStateException 都收敛成 null
            return null;
        }
    }

    /**
     * 读字符串字段，缺失或类型不符时返回 {@code fallback}。
     */
    public static String getString(JsonObject json, String field, String fallback) {
        if (json == null || !json.has(field) || json.get(field).isJsonNull()) {
            return fallback;
        }
        try {
            return json.get(field).getAsString();
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    /**
     * 读 long 字段，缺失或类型不符时返回 {@code fallback}。
     *
     * <p>不用 {@code getAsLong()} 直接读 {@code "abc"} 会抛异常，
     * 这里统一收敛。</p>
     */
    public static long getLong(JsonObject json, String field, long fallback) {
        if (json == null || !json.has(field) || json.get(field).isJsonNull()) {
            return fallback;
        }
        try {
            return json.get(field).getAsLong();
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    /**
     * 读 int 字段，缺失或类型不符时返回 {@code fallback}。
     */
    public static int getInt(JsonObject json, String field, int fallback) {
        if (json == null || !json.has(field) || json.get(field).isJsonNull()) {
            return fallback;
        }
        try {
            return json.get(field).getAsInt();
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    /**
     * 读布尔字段，缺失或类型不符时返回 {@code fallback}。
     */
    public static boolean getBoolean(JsonObject json, String field, boolean fallback) {
        if (json == null || !json.has(field) || json.get(field).isJsonNull()) {
            return fallback;
        }
        try {
            return json.get(field).getAsBoolean();
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    /**
     * 判断必填字段是否齐全。
     *
     * <p>用法：{@code if (!JsonBody.hasAll(json, "itemKey", "priceSpiritStones")) return 参数错误;}</p>
     */
    public static boolean hasAll(JsonObject json, String... fields) {
        if (json == null) {
            return false;
        }
        for (String f : fields) {
            if (!json.has(f) || json.get(f).isJsonNull()) {
                return false;
            }
        }
        return true;
    }
}
