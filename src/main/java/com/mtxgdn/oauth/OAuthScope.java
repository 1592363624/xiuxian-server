package com.mtxgdn.oauth;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 第三方应用可以申请的数据权限（scope）。
 *
 * <h3>为什么不是简单的 read / write</h3>
 * <p>最初的设想是只分「能读」和「能写」两档。但游戏数据的读写代价完全不对等：
 * 读背包只是看一眼；「写背包」等于能凭空塞装备，「写经济」等于能刷灵石。
 * 用一个笼统的 {@code write} 覆盖所有写操作，等于把"想标记已读消息"的应用
 * 和"能销毁物品"的应用放在同一档权限里。</p>
 *
 * <p>所以这里按<b>资源域</b>切分，每个域内再分只读/读写：</p>
 *
 * <pre>
 *   player:read     读角色信息（等级、境界、属性、在线状态）
 *   player:write    改角色信息（仅限非破坏性字段，见下）
 *   inventory:read  读背包、装备、技能
 *   inventory:write 使用/装备/强化物品（<b>不允许</b>增删物品）
 *   economy:read    读坊市行情、自己的挂单
 *   economy:write   上架/购买/撤单（涉及灵石，高风险）
 *   sect:read       读宗门信息与成员
 *   sect:write      宗门内部操作（入退宗、审批、捐献）
 *   ...
 * </pre>
 *
 * <h3>哪些能力<b>永远不开放</b>给第三方</h3>
 * <p>以下操作即使拿到对应域的最高权限也不允许通过 OAuth 执行 ——
 * 它们要么不可逆，要么会让第三方获得超出玩家本人的能力：</p>
 * <ul>
 *   <li>{@code /player/data/restore} —— 覆盖存档，可用来复制或毁号</li>
 *   <li>{@code /item/add} —— 凭空发物品</li>
 *   <li>{@code /sect/kick}、{@code /sect/appoint} —— 影响其他玩家</li>
 *   <li>任何涉及其他玩家数据的写操作</li>
 * </ul>
 *
 * <p>这类端点在 {@link #NEVER_DELEGATED} 里显式列出并配有测试，
 * 防止将来新增端点时被"顺手"归进某个域。</p>
 */
public final class OAuthScope {

    // ==================== scope 名 ====================

    /** 读角色信息 */
    public static final String PLAYER_READ = "player:read";
    /** 读背包/装备/技能 */
    public static final String INVENTORY_READ = "inventory:read";
    /** 使用/装备/强化（不动物品数量以外的东西） */
    public static final String INVENTORY_WRITE = "inventory:write";
    /** 读坊市、自己的挂单 */
    public static final String ECONOMY_READ = "economy:read";
    /** 上架/购买/撤单 */
    public static final String ECONOMY_WRITE = "economy:write";
    /** 读宗门信息与成员列表 */
    public static final String SECT_READ = "sect:read";
    /** 入宗/退宗/申请/捐献 */
    public static final String SECT_WRITE = "sect:write";
    /** 读修炼/境界/功法进度 */
    public static final String CULTIVATION_READ = "cultivation:read";
    /** 打坐/修炼相关写操作 */
    public static final String CULTIVATION_WRITE = "cultivation:write";
    /** 读社交（好友、私信、队伍） */
    public static final String SOCIAL_READ = "social:read";
    /** 发私信、组队 */
    public static final String SOCIAL_WRITE = "social:write";
    /** 读游戏公共数据（排行榜、物品表、地图），不含任何玩家私有数据 */
    public static final String PUBLIC_READ = "public:read";

    /**
     * 全部合法 scope。
     *
     * <p>顺序即同意页的显示顺序：先公共、再角色、再背包/经济/宗门，最后社交 ——
     * 大致按"敏感度递增"排列，让玩家先看到低风险的。</p>
     */
    private static final Map<String, Definition> REGISTRY = new LinkedHashMap<>();

    private OAuthScope() {
    }

    static {
        define(PUBLIC_READ, "读取游戏公开数据", "排行榜、物品图鉴、地图、门派名录等所有人可见的信息", Risk.LOW, "🌐");
        define(PLAYER_READ, "读取角色信息", "等级、境界、属性、当前位置、在线状态", Risk.LOW, "👤");
        define(CULTIVATION_READ, "读取修炼进度", "修为、功法、突破记录", Risk.LOW, "🧘");
        define(INVENTORY_READ, "读取背包与装备", "持有的物品、装备、已学技能", Risk.MEDIUM, "🎒");
        define(ECONOMY_READ, "读取坊市信息", "市场行情、自己的挂单", Risk.MEDIUM, "🏪");
        define(SECT_READ, "读取宗门信息", "所属宗门、宗门成员列表", Risk.MEDIUM, "🏯");
        define(SOCIAL_READ, "读取社交信息", "好友列表、队伍信息", Risk.MEDIUM, "💬");
        define(CULTIVATION_WRITE, "执行修炼操作", "打坐、领取修炼收益", Risk.HIGH, "🧘");
        define(INVENTORY_WRITE, "操作装备与技能", "装备/卸下/强化、使用消耗品、学习技能（不能增删物品）", Risk.HIGH, "🎒");
        define(ECONOMY_WRITE, "进行坊市交易", "上架、购买、撤单 —— 会消耗或获得灵石", Risk.HIGH, "🏪");
        define(SECT_WRITE, "执行宗门操作", "申请入宗、退出宗门、捐献（不能踢人、不能任命）", Risk.HIGH, "🏯");
        define(SOCIAL_WRITE, "进行社交操作", "发送私信、组队邀请", Risk.HIGH, "💬");
    }

    private static void define(String name, String title, String description, Risk risk, String icon) {
        REGISTRY.put(name, new Definition(name, title, description, risk, icon));
    }

    /** 风险等级，供同意页用颜色区分 */
    public enum Risk {
        LOW, MEDIUM, HIGH
    }

    /** 一个 scope 的展示信息 */
    public record Definition(String name, String title, String description, Risk risk, String icon) {
    }

    // ==================== 查询 ====================

    /** 全部合法 scope 名（顺序稳定） */
    public static Set<String> all() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(REGISTRY.keySet()));
    }

    /** 全部 scope 的定义，按声明顺序 */
    public static List<Definition> definitions() {
        return Collections.unmodifiableList(new ArrayList<>(REGISTRY.values()));
    }

    /** 某个 scope 的展示信息；不存在返回 null */
    public static Definition get(String name) {
        return REGISTRY.get(name);
    }

    public static boolean isValid(String name) {
        return REGISTRY.containsKey(name);
    }

    /** 该 scope 是否允许写操作 */
    public static boolean isWrite(String name) {
        return name != null && name.endsWith(":write");
    }

    /** 该 scope 所属的资源域（如 {@code inventory:read} → {@code inventory}） */
    public static String domainOf(String name) {
        if (name == null) {
            return "";
        }
        int colon = name.indexOf(':');
        return colon < 0 ? name : name.substring(0, colon);
    }

    /**
     * 从一组 scope 里取出所有资源域。
     *
     * <p>用于判断「这个 token 能碰哪些资源」—— 只要某个域出现，
     * 就说明被授权访问该域下的端点（具体读还是写由 scope 本身决定）。</p>
     */
    public static Set<String> domainsOf(Iterable<String> scopes) {
        Set<String> domains = new LinkedHashSet<>();
        for (String s : scopes) {
            String d = domainOf(s);
            if (!d.isEmpty()) {
                domains.add(d);
            }
        }
        return domains;
    }

    /**
     * 校验一组 scope 是否全部合法。
     *
     * <p>第三方申请 scope 时用。任何一个不认识的名字都算失败 ——
     * 不能"忽略不认识的、保留认识的"，否则第三方以为拿到了权限，
     * 实际没有，排查起来非常费劲。</p>
     */
    public static boolean allValid(Iterable<String> scopes) {
        for (String s : scopes) {
            if (!isValid(s)) {
                return false;
            }
        }
        return true;
    }

    /** 把空格分隔的 scope 串解析成集合（忽略空白项） */
    public static Set<String> parse(String scopeString) {
        Set<String> result = new LinkedHashSet<>();
        if (scopeString == null || scopeString.isBlank()) {
            return result;
        }
        for (String s : scopeString.trim().split("\\s+")) {
            if (!s.isBlank()) {
                result.add(s);
            }
        }
        return result;
    }

    /** 把 scope 集合拼回空格分隔的串（顺序按注册表，保证稳定） */
    public static String join(Iterable<String> scopes) {
        Set<String> given = new LinkedHashSet<>();
        for (String s : scopes) {
            given.add(s);
        }
        List<String> ordered = new ArrayList<>();
        for (String name : REGISTRY.keySet()) {
            if (given.contains(name)) {
                ordered.add(name);
            }
        }
        // 保底：注册表外的（理论上不会出现）也带上，避免静默丢权限
        for (String s : given) {
            if (!REGISTRY.containsKey(s)) {
                ordered.add(s);
            }
        }
        return String.join(" ", ordered);
    }

    /**
     * 永远不允许第三方通过 OAuth 调用的端点。
     *
     * <p>这些路径即使 token 的 scope 覆盖了对应资源域也必须拒绝。它们是：</p>
     * <ul>
     *   <li>存档覆盖类 —— 可用来复制/毁掉账号；</li>
     *   <li>凭空发物品 —— 直接破坏经济；</li>
     *   <li>影响其他玩家的管理操作 —— 踢人、任命。</li>
     * </ul>
     *
     * <p>用完整路径（相对 {@code /api}）列出，前缀匹配。</p>
     */
    public static final List<String> NEVER_DELEGATED = List.of(
            "game/player/data/restore",  // 覆盖存档
            "game/player/data/export",   // 导出存档（含全部私有数据，属越权读取）
            "game/item/add",             // 凭空发物品
            "game/sect/kick",            // 踢人（影响他人）
            "game/sect/appoint",         // 任命（影响他人）
            "game/sect/reject",          // 审批拒绝（影响他人）
            "game/sect/approve",         // 审批通过（影响他人）
            "game/sect/transfer",        // 转让宗门
            "game/sect/dissolve"         // 解散宗门
    );

    /**
     * 该路径是否属于"永不委托"的清单。
     *
     * @param apiPath 相对 {@code /api} 的路径，如 {@code game/sect/kick/12}
     */
    public static boolean isNeverDelegated(String apiPath) {
        if (apiPath == null) {
            return false;
        }
        String p = apiPath.startsWith("/") ? apiPath.substring(1) : apiPath;
        for (String forbidden : NEVER_DELEGATED) {
            if (p.equals(forbidden) || p.startsWith(forbidden + "/")) {
                return true;
            }
        }
        return false;
    }
}
