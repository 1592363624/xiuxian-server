package com.mtxgdn.game.technique;

import com.mtxgdn.db.PlayerTechniqueDAO;
import com.mtxgdn.entity.Player;

import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 玩家自创功法（L1 参数化创造）。
 *
 * <h3>一句话说清这条链路</h3>
 * <p>玩家把「悟道预算」分配到各项属性上 → 服务端<b>重算并校验</b> →
 * 落成 {@code techniques} 表里的一行 → 之后它就和官方功法完全一样：
 * 能被学习、装备、升级，加成走同一套聚合逻辑。</p>
 *
 * <p><b>正因为复用了既有通路，这个功能不需要改战斗和修炼代码。</b>
 * 这也是它风险低的原因 —— 自创功法能产生的影响，不会超出官方功法
 * 已经存在的那些数值范围。</p>
 *
 * <h3>为什么校验必须在服务端重算</h3>
 * <p>前端会实时显示「还能花多少点」，那只是体验。真正的裁决在这里：
 * 客户端传来的任何一个数字都可能是改过的。所以 {@link Request} 只接受
 * <b>分配单位数</b>，折算后的数值、消耗、是否超预算，全部由服务端算。</p>
 */
public class TechniqueCreationService {

    /** 每个玩家最多保留几门自创功法 */
    public static final int MAX_PER_PLAYER = 3;

    /** 名字长度（按字符数，中文一个字算一个） */
    private static final int NAME_MIN = 2;
    private static final int NAME_MAX = 12;
    private static final int DESC_MAX = 64;

    /** 允许玩家选择的功法类型 */
    private static final Set<String> ALLOWED_TYPES =
            Set.of("CULTIVATION", "ATTACK", "DEFENSE", "UTILITY");

    /**
     * 禁止在名字里出现的词 —— 防止冒充官方内容。
     *
     * <p>这不是完整的审核方案（真正的 UGC 治理需要举报 + 人工下架），
     * 但能挡掉最常见、也最容易骗到人的那类冒充。</p>
     */
    private static final String[] RESERVED_WORDS = {
            "官方", "系統", "系统", "管理员", "管理員", "gm", "admin", "客服", "活动", "活動"
    };

    /**
     * 创造请求。
     *
     * <p>只含<b>玩家能决定的东西</b>：名字、描述、类型、以及属性分配。
     * 消耗、造出的实际数值、是否合法，一概不接受客户端输入。</p>
     */
    public record Request(String name, String description, String type,
                          TechniqueBudget.Plan plan, String effects) {

        /** 不带词条的简写 —— L1 阶段只有数值分配 */
        public Request(String name, String description, String type, TechniqueBudget.Plan plan) {
            this(name, description, type, plan, "");
        }
    }

    /**
     * 创造结果。
     *
     * <p>单独定义而不复用 {@code OperationResult}：这里要带回新功法的 id
     * 与消耗明细，前端要拿它做「查看刚造出的功法」的跳转。</p>
     */
    public record CreationResult(boolean success, String message, long techniqueId,
                                 double cost, double budget) {
    }

    private final PlayerTechniqueDAO dao;

    public TechniqueCreationService() {
        this(new PlayerTechniqueDAO());
    }

    public TechniqueCreationService(PlayerTechniqueDAO dao) {
        this.dao = dao;
    }

    /**
     * 创造一门功法。
     *
     * <p>校验顺序有意为之：<b>先查便宜的、确定性的条件</b>（名字、类型、数量），
     * 再算预算。这样玩家不会因为「名字重复」这种一句话就能改的问题，
     * 先看到一堆预算计算细节。</p>
     */
    public CreationResult create(Player player, Request req) {
        if (player == null) {
            return fail("未找到角色");
        }
        if (req == null || req.plan() == null || req.plan().isEmpty()) {
            return fail("请先分配功法属性");
        }

        // ── 名字校验
        String name = req.name() == null ? "" : req.name().trim();
        String nameProblem = validateName(name);
        if (nameProblem != null) {
            return fail(nameProblem);
        }

        // ── 描述校验（可以为空）
        String desc = req.description() == null ? "" : req.description().trim();
        if (desc.length() > DESC_MAX) {
            return fail("描述最多 " + DESC_MAX + " 个字");
        }
        if (containsControlChar(desc)) {
            return fail("描述含有非法字符");
        }

        // ── 类型
        String type = req.type() == null ? "" : req.type().trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_TYPES.contains(type)) {
            return fail("请选择一种功法类型");
        }

        int realm = Math.max(0, player.getRealm());

        try {
            // ── 数量上限
            int count = dao.countByCreator(player.getId());
            if (count >= MAX_PER_PLAYER) {
                return fail("最多保留 " + MAX_PER_PLAYER + " 门自创功法，请先删掉一门");
            }

            // ── 名字全表唯一（含官方功法），防止冒充与抢注
            if (dao.nameExists(name)) {
                return fail("已有同名功法，换个名字吧");
            }

            // ── 词条解析：看不懂的一律拒绝，不静默忽略
            TechniqueEffectSpec.ParseResult effectParse = TechniqueEffectSpec.parse(req.effects());
            if (!effectParse.ok()) {
                return fail(effectParse.problems().get(0));
            }
            TechniqueEffectSpec effects = effectParse.spec();

            // ── 预算裁决（唯一可信的判断）。词条与数值抢同一个池子。
            TechniqueBudget.Result check =
                    TechniqueBudget.evaluate(realm, req.plan(), effects.cost());
            if (!check.ok()) {
                return new CreationResult(false, check.problems().get(0), 0L,
                        check.cost(), check.budget());
            }

            // ── 数值换算 + 落库
            long id = dao.insert(
                    player.getId(), name, desc, realm,
                    learnGoldFor(check.cost()), learnSpiritFor(check.cost()),
                    upgradeGoldFor(check.cost()), upgradeSpiritFor(check.cost()),
                    type, 10,
                    req.plan().intOf(TechniqueBudget.Stat.HP),
                    req.plan().intOf(TechniqueBudget.Stat.MP),
                    req.plan().intOf(TechniqueBudget.Stat.ATTACK),
                    req.plan().intOf(TechniqueBudget.Stat.DEFENSE),
                    req.plan().intOf(TechniqueBudget.Stat.SPEED),
                    req.plan().intOf(TechniqueBudget.Stat.SPIRIT),
                    req.plan().doubleOf(TechniqueBudget.Stat.CULTIVATION_SPEED),
                    req.plan().doubleOf(TechniqueBudget.Stat.EXP),
                    req.plan().doubleOf(TechniqueBudget.Stat.COMBAT_DAMAGE),
                    req.plan().doubleOf(TechniqueBudget.Stat.DAMAGE_REDUCTION),
                    effects.toStorageString());

            if (id <= 0) {
                return fail("功法创造失败，请稍后再试");
            }
            return new CreationResult(true, "《" + name + "》已问世", id, check.cost(), check.budget());
        } catch (SQLException e) {
            return fail("功法创造失败：" + e.getMessage());
        }
    }

    /** 我创建的自创功法 id 列表 */
    public List<Long> listMine(Player player) {
        if (player == null) {
            return List.of();
        }
        try {
            return dao.listIdsByCreator(player.getId());
        } catch (SQLException e) {
            return List.of();
        }
    }

    /** 删除一门自创功法（只能删自己的） */
    public CreationResult delete(Player player, long techniqueId) {
        if (player == null) {
            return fail("未找到角色");
        }
        try {
            return dao.deleteOwned(techniqueId, player.getId())
                    ? new CreationResult(true, "已遗忘这门功法", techniqueId, 0, 0)
                    : fail("没有找到这门功法");
        } catch (SQLException e) {
            return fail("删除失败：" + e.getMessage());
        }
    }

    // ==================== 内部 ====================

    /**
     * 名字校验，返回问题描述；没问题返回 null。
     *
     * <p>长度按 <b>code point</b> 数而不是 {@code length()}：后者对中文与
     * emoji 都不准（emoji 算两个 char），用 {@code length()} 会让
     * 「两个字 + 一个表情」这种正常名字被判超长。</p>
     */
    private String validateName(String name) {
        if (name.isEmpty()) {
            return "请给功法起个名字";
        }
        int len = name.codePointCount(0, name.length());
        if (len < NAME_MIN || len > NAME_MAX) {
            return "名字需要 " + NAME_MIN + "~" + NAME_MAX + " 个字";
        }
        if (containsControlChar(name)) {
            return "名字含有非法字符";
        }
        String lower = name.toLowerCase(Locale.ROOT);
        for (String word : RESERVED_WORDS) {
            if (lower.contains(word)) {
                return "名字里不能含有「" + word + "」";
            }
        }
        return null;
    }

    private boolean containsControlChar(String s) {
        return s.codePoints().anyMatch(c -> Character.isISOControl(c));
    }

    // 学习/升级消耗按「造它花了多少预算」缩放，
    // 这样强大的功法自然更贵，不需要另设一张价格表。
    private long learnGoldFor(double cost) {
        return (long) Math.max(0, cost * 40);
    }

    private long learnSpiritFor(double cost) {
        return (long) Math.max(0, cost * 10);
    }

    private int upgradeGoldFor(double cost) {
        return (int) Math.max(1, cost * 6);
    }

    private int upgradeSpiritFor(double cost) {
        return (int) Math.max(1, cost * 2);
    }

    private CreationResult fail(String message) {
        return new CreationResult(false, message, 0L, 0, 0);
    }
}
