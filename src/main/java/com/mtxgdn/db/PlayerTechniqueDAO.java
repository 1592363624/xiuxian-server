package com.mtxgdn.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * 自创功法的持久化。
 *
 * <p>与 {@link TechniqueDAO} 分开的理由：那边是「官方功法定义」的 CRUD，
 * 这里只负责「玩家造出来的」那一类行。混在一起会让 500 多行的 DAO 更难读，
 * 而且自创功法多出归属（{@code creator_user_id}）与词条（{@code effect_spec}）
 * 两个概念，查询模式也不同（几乎都是「按创建者查」）。</p>
 *
 * <p><b>表还是同一张 {@code techniques}</b> —— 自创功法必须能被
 * {@code TechniqueService} 像官方功法一样学习、装备、升级，走同一套加成聚合。
 * 单独建表等于复制一整套逻辑，两条路早晚不一致。</p>
 */
public class PlayerTechniqueDAO {

    /**
     * 插入一门自创功法，返回自增 id（拿不到返回 0）。
     *
     * <p>写死的三列：{@code player_made = 1}（标记来源）、
     * {@code upgrade_base_cost_*} 由调用方按预算折算后传入。</p>
     */
    public long insert(long creatorUserId, String name, String description, int requiredRealm,
                       long learnCostGold, long learnCostSpiritStones,
                       int upgradeBaseCostGold, int upgradeBaseCostSpiritStones,
                       String type, int maxLevel,
                       int hpBonus, int mpBonus, int attackBonus, int defenseBonus,
                       int speedBonus, int spiritBonus,
                       double cultivationSpeedBonus, double expBonus,
                       double combatDamageBonus, double damageReduction,
                       String effectSpec) throws SQLException {
        String sql = """
                INSERT INTO techniques (name, description, required_realm, learn_cost_gold,
                    learn_cost_spirit_stones, upgrade_base_cost_gold, upgrade_base_cost_spirit_stones,
                    type, max_level, hp_bonus, mp_bonus, attack_bonus, defense_bonus, speed_bonus,
                    spirit_bonus, cultivation_speed_bonus, exp_bonus, combat_damage_bonus,
                    damage_reduction, creator_user_id, player_made, effect_spec)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?)
                """;
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, description == null ? "" : description);
            ps.setInt(3, requiredRealm);
            ps.setLong(4, learnCostGold);
            ps.setLong(5, learnCostSpiritStones);
            ps.setInt(6, upgradeBaseCostGold);
            ps.setInt(7, upgradeBaseCostSpiritStones);
            ps.setString(8, type);
            ps.setInt(9, maxLevel);
            ps.setInt(10, hpBonus);
            ps.setInt(11, mpBonus);
            ps.setInt(12, attackBonus);
            ps.setInt(13, defenseBonus);
            ps.setInt(14, speedBonus);
            ps.setInt(15, spiritBonus);
            ps.setDouble(16, cultivationSpeedBonus);
            ps.setDouble(17, expBonus);
            ps.setDouble(18, combatDamageBonus);
            ps.setDouble(19, damageReduction);
            ps.setLong(20, creatorUserId);
            ps.setString(21, effectSpec == null ? "" : effectSpec);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : 0L;
            }
        }
    }

    /**
     * 这个名字是否已被占用（官方功法 + 所有玩家的自创）。
     *
     * <p><b>必须查全表而不是只查自己的</b>：否则玩家可以造一门叫「鸿蒙紫气经」的
     * 功法冒充官方内容，或者抢注别人功法的名字。</p>
     *
     * <p>比较用 {@code LOWER()} 归一，避免「太虚经」与「太虚经 」之外的大小写
     * 变体绕过 —— 中文没有大小写，但玩家可以夹英文名。</p>
     */
    public boolean nameExists(String name) throws SQLException {
        String sql = "SELECT 1 FROM techniques WHERE LOWER(name) = LOWER(?) LIMIT 1";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** 某玩家已创建了几门自创功法（用于数量上限） */
    public int countByCreator(long creatorUserId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM techniques WHERE creator_user_id = ? AND player_made = 1";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, creatorUserId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /** 某玩家创建的全部自创功法 id，按创建顺序 */
    public List<Long> listIdsByCreator(long creatorUserId) throws SQLException {
        String sql = "SELECT id FROM techniques WHERE creator_user_id = ? AND player_made = 1 ORDER BY id";
        List<Long> ids = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, creatorUserId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getLong(1));
                }
            }
        }
        return ids;
    }

    /** 读一门功法的词条规格；不是自创功法或没有词条返回空串 */
    public String findEffectSpec(long techniqueId) throws SQLException {
        String sql = "SELECT effect_spec FROM techniques WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, techniqueId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String s = rs.getString(1);
                    return s == null ? "" : s;
                }
            }
        }
        return "";
    }

    /** 一门功法的创建者（{@code players.id}）；不是自创功法或不存在返回 0。 */
    public long findCreatorId(long techniqueId) throws SQLException {
        try (Connection conn = DatabaseManager.getConnection()) {
            return findCreatorId(conn, techniqueId);
        }
    }

    /** 事务内版本：见 {@link #findCreatorId(long)}。 */
    public long findCreatorId(Connection conn, long techniqueId) throws SQLException {
        String sql = "SELECT creator_user_id FROM techniques WHERE id = ? AND player_made = 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, techniqueId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }

    /** 学得这门功法的玩家数（含创建者自己）。 */
    public int countLearners(long techniqueId) throws SQLException {
        try (Connection conn = DatabaseManager.getConnection()) {
            return countLearners(conn, techniqueId);
        }
    }

    /** 事务内版本：见 {@link #countLearners(long)}。 */
    public int countLearners(Connection conn, long techniqueId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM players_techniques WHERE technique_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, techniqueId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /** 正在运转（装备中）这门功法的人数。 */
    public int countEquippedLearners(long techniqueId) throws SQLException {
        try (Connection conn = DatabaseManager.getConnection()) {
            return countEquippedLearners(conn, techniqueId);
        }
    }

    /** 事务内版本：见 {@link #countEquippedLearners(long)}。 */
    public int countEquippedLearners(Connection conn, long techniqueId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM players_techniques WHERE technique_id = ? AND is_equipped = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, techniqueId);
            ps.setBoolean(2, true);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /** 某玩家是否学过这门功法。 */
    public boolean hasLearner(Connection conn, long playerId, long techniqueId) throws SQLException {
        String sql = "SELECT 1 FROM players_techniques WHERE player_id = ? AND technique_id = ? LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, playerId);
            ps.setLong(2, techniqueId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * 删除某玩家对某功法的学习记录（事务内）。
     *
     * <p>只用于「创建者自己学过这门功法、且没有装备」时随删除一起清理。
     * 不能拿它来清别人的记录 —— 删除流程在有人学过时直接拒绝。</p>
     */
    public boolean removeLearner(Connection conn, long playerId, long techniqueId) throws SQLException {
        String sql = "DELETE FROM players_techniques WHERE player_id = ? AND technique_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, playerId);
            ps.setLong(2, techniqueId);
            return ps.executeUpdate() > 0;
        }
    }

    /** 事务内版本：见 {@link #deleteOwned(long, long)}。 */
    public boolean deleteOwned(Connection conn, long techniqueId, long creatorUserId) throws SQLException {
        String sql = "DELETE FROM techniques WHERE id = ? AND creator_user_id = ? AND player_made = 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, techniqueId);
            ps.setLong(2, creatorUserId);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * 删除一门自创功法 —— <b>只能删自己造的</b>。
     *
     * <p>把 {@code creator_user_id} 写进 WHERE 而不是先查再删：
     * 前者是一条原子语句，后者在并发下存在「查完发现是自己的、
     * 删之前归属被改」的窗口。虽然目前没有改归属的功能，
     * 但把权限判断放进 SQL 是不需要额外成本的正确做法。</p>
     *
     * @return 是否真的删掉了一行
     */
    public boolean deleteOwned(long techniqueId, long creatorUserId) throws SQLException {
        String sql = "DELETE FROM techniques WHERE id = ? AND creator_user_id = ? AND player_made = 1";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, techniqueId);
            ps.setLong(2, creatorUserId);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * 某玩家**已装备**功法的词条规格。
     *
     * <p>只取 {@code is_equipped} 为真的行：词条和面板加成一样，
     * 应该只在装备时生效。否则玩家会「造一门带词条的功法，学完就卸下」，
     * 白拿机制 —— 那等于绕过装备位限制。</p>
     *
     * <p>用 INNER JOIN 而不是两次查询：这不是热路径（有 30 秒缓存兜底），
     * 但一次查询拿完能少一次往返。</p>
     */
    public List<String> listEquippedEffectSpecs(long playerId) throws SQLException {
        String sql = """
                SELECT t.effect_spec FROM techniques t
                INNER JOIN players_techniques pt ON t.id = pt.technique_id
                WHERE pt.player_id = ? AND pt.is_equipped = ?
                """;
        List<String> specs = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, playerId);
            ps.setBoolean(2, true);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String s = rs.getString(1);
                    if (s != null && !s.isEmpty()) {
                        specs.add(s);
                    }
                }
            }
        }
        return specs;
    }
}
