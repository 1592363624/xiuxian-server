package com.mtxgdn.util;

/**
 * 分页参数的安全夹取。
 *
 * <p><b>为什么需要</b>：{@code LIMIT ? OFFSET ?} 里的两个值如果直接来自请求参数，
 * 攻击者只要传 {@code limit=2147483647} 就能一次拉走全表 —— 每一行都要
 * 经过 {@code ResultSet} 读取、对象构造、JSON 序列化，在 1 核小机器上
 * 足以把进程拖垮。更隐蔽的是 <b>{@code limit=-1}</b>：SQLite 把负的 LIMIT
 * 解释为「<b>不限制条数</b>」，所以传负数不但不报错，效果还是全表。</p>
 *
 * <p>{@code offset} 同样要夹：{@code OFFSET 2000000000} 不会返回数据，
 * 但数据库必须先扫描/跳过这么多行才能得出"空集"，是纯粹的 CPU 消耗。</p>
 *
 * <p>WebSocket 侧早就用 {@code WsSupport.clampLimit} 处理过排行接口，
 * 但 REST 侧没有跟上，导致 {@code /api/game/players}、{@code /players/search}、
 * {@code /game/rank} 三个接口一直是原样透传。这里把规则集中到一处，
 * 避免下次再出现"某个通道修了、另一个通道忘了"。</p>
 *
 * <p>取值语义与 {@code WsSupport.clampLimit} 保持一致：</p>
 * <ul>
 *   <li>{@code limit <= 0} → 用默认值（负数不能当"不限条数"）</li>
 *   <li>{@code limit > max} → 夹到 {@code max}</li>
 *   <li>{@code offset < 0} → 当作 0</li>
 * </ul>
 */
public final class PageRequest {

    /** 未显式指定时的默认每页条数。 */
    public static final int DEFAULT_LIMIT = 20;

    /** 单次查询允许返回的最大条数。 */
    public static final int MAX_LIMIT = 100;

    private PageRequest() {
    }

    /**
     * 把外部传入的 {@code limit} 夹到合法区间。
     *
     * @param limit 原始值（可能为负数、0 或极大值）
     * @return 落在 {@code (0, max]} 内的值；非法时回落到 {@code defaultValue}
     */
    public static int clampLimit(int limit, int defaultValue, int max) {
        int safeDefault = defaultValue <= 0 ? DEFAULT_LIMIT : defaultValue;
        int safeMax = max <= 0 ? MAX_LIMIT : max;
        if (limit <= 0) {
            return Math.min(safeDefault, safeMax);
        }
        return Math.min(limit, safeMax);
    }

    /**
     * 用默认上限夹取 {@code limit}。
     *
     * @param limit 原始值
     * @return 落在 {@code (0, 100]} 内的值
     */
    public static int clampLimit(int limit) {
        return clampLimit(limit, DEFAULT_LIMIT, MAX_LIMIT);
    }

    /**
     * 把外部传入的 {@code offset} 夹到合法区间。
     *
     * @param offset 原始值（可能为负数）
     * @return 不小于 0 的偏移量
     */
    public static int clampOffset(int offset) {
        return Math.max(offset, 0);
    }

    /**
     * 夹取分页参数并返回可直接绑定到 SQL 的一对值。
     *
     * @param limit  原始每页条数
     * @param offset 原始偏移量
     * @return {@code [limit, offset]}，第一项在 {@code (0, 100]}，第二项 {@code >= 0}
     */
    public static int[] clamp(int limit, int offset) {
        return new int[]{clampLimit(limit), clampOffset(offset)};
    }
}
