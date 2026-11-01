package com.mtxgdn.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * OAuth 第三方授权（{@code oauth_grants} 表）的读写入口。
 *
 * <p>从 {@code OAuthGrantStore} 抽出来的 DAO。每个意图一对重载，沿用项目约定：</p>
 * <ul>
 *   <li>{@code xxx(...)}：自己开一条短连接，异常降级不抛；</li>
 *   <li>{@code xxx(Connection conn, ...)}：只用调用方连接，直接抛 {@link SQLException}。</li>
 * </ul>
 *
 * <h3>为什么这里也是"不抛异常"</h3>
 * <p>和 {@link TokenBlacklistDAO} 同理 —— 它在认证链路上。
 * 数据库抖动不该让玩家无法授权，也不该让一次鉴权把有效 token 误判成无效。
 * 所以自取连接的重载吞掉异常后降级：</p>
 * <ul>
 *   <li><b>写入失败</b> → 授权只在内存生效，重启后需要重新授权（可接受）；</li>
 *   <li><b>读取失败</b> → 返回空/保守值，由调用方决定如何兜底。</li>
 * </ul>
 *
 * <h3>{@code revoked} 用标记而不是删行</h3>
 * <p>撤销授权时不删记录，而是置 {@code revoked = 1}。删行会丢失
 * "这个应用曾经被授权过"的事实，排查「玩家说没授权但应用拿到了 token」
 * 这类问题时无从追溯。代价是需要清理策略，由 {@link #purgeExpired} 负责。</p>
 */
public class OAuthGrantsDAO {

    /**
     * 一条授权记录。
     *
     * @param grantId    稳定标识（{@code userId:clientId}）
     * @param userId     授权的玩家
     * @param username   玩家名
     * @param clientId   被授权的应用
     * @param scopes     空格分隔的 scope 串（与 JWT 表示一致）
     * @param revoked    是否已撤销
     * @param createdAt  授权时间
     * @param lastUsedAt 最近使用时间
     * @param expiresAt  最晚有效期（清理用）
     */
    public record Row(String grantId, long userId, String username, String clientId,
                      String scopes, boolean revoked, long createdAt, long lastUsedAt, long expiresAt) {
    }

    // ==================== 写入 ====================

    /**
     * 插入或更新一条授权（自取连接版）。
     *
     * <p>同一玩家对同一应用只保留一条，所以是 upsert：
     * 重新授权时覆盖 scope、清掉 revoked 标记、刷新时间。</p>
     */
    public void upsert(String grantId, long userId, String username, String clientId,
                       String scopes, long createdAt, long lastUsedAt, long expiresAt) {
        try (Connection conn = DatabaseManager.getConnection()) {
            upsert(conn, grantId, userId, username, clientId, scopes, createdAt, lastUsedAt, expiresAt);
        } catch (SQLException e) {
            System.err.println("[OAuthGrant] 警告：授权记录落库失败，重启后需重新授权: " + e.getMessage());
        } catch (RuntimeException e) {
            System.err.println("[OAuthGrant] 警告：数据库未就绪，授权仅存内存: " + e.getMessage());
        }
    }

    /**
     * 事务内版本：只用调用方连接。
     *
     * <p><b>{@code created_at} 也要一起更新</b>，不能只更新别的列。
     * 原因是内存里每次授权都新建一个 Grant（createdAt = 现在），
     * 若库里保留旧值，两边就不一致 —— 重启后页面上的授权时间会
     * "跳回"很久以前，玩家会困惑「我刚刚重新授权的怎么显示三个月前」，
     * 而且 {@code expires_at} 的清理期限也会漂移。</p>
     *
     * <p>语义上「重新授权」就是新的一次授权：玩家又看了一眼权限并再次同意，
     * 把时间刷新才符合直觉。</p>
     */
    public void upsert(Connection conn, String grantId, long userId, String username, String clientId,
                       String scopes, long createdAt, long lastUsedAt, long expiresAt) throws SQLException {
        String sql = DatabaseManager.isSqlite()
                ? "INSERT INTO oauth_grants (grant_id, user_id, username, client_id, scopes, revoked,"
                + " created_at, last_used_at, expires_at) VALUES (?, ?, ?, ?, ?, 0, ?, ?, ?)"
                + " ON CONFLICT(grant_id) DO UPDATE SET scopes = excluded.scopes,"
                + " username = excluded.username, revoked = 0, created_at = excluded.created_at,"
                + " last_used_at = excluded.last_used_at, expires_at = excluded.expires_at"
                : "INSERT INTO oauth_grants (grant_id, user_id, username, client_id, scopes, revoked,"
                + " created_at, last_used_at, expires_at) VALUES (?, ?, ?, ?, ?, 0, ?, ?, ?)"
                + " ON DUPLICATE KEY UPDATE scopes = VALUES(scopes), username = VALUES(username),"
                + " revoked = 0, created_at = VALUES(created_at), last_used_at = VALUES(last_used_at),"
                + " expires_at = VALUES(expires_at)";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, grantId);
            ps.setLong(2, userId);
            ps.setString(3, username);
            ps.setString(4, clientId);
            ps.setString(5, scopes);
            ps.setLong(6, createdAt);
            ps.setLong(7, lastUsedAt);
            ps.setLong(8, expiresAt);
            ps.executeUpdate();
        }
    }

    /** 标记撤销（自取连接版） */
    public void markRevoked(String grantId) {
        try (Connection conn = DatabaseManager.getConnection()) {
            markRevoked(conn, grantId);
        } catch (SQLException | RuntimeException e) {
            System.err.println("[OAuthGrant] 警告：撤销落库失败: " + e.getMessage());
        }
    }

    /** 事务内版本。 */
    public void markRevoked(Connection conn, String grantId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE oauth_grants SET revoked = 1 WHERE grant_id = ?")) {
            ps.setString(1, grantId);
            ps.executeUpdate();
        }
    }

    /** 刷新最近使用时间（自取连接版）。失败不告警 —— 这只是统计信息。 */
    public void touch(String grantId, long lastUsedAt) {
        try (Connection conn = DatabaseManager.getConnection()) {
            touch(conn, grantId, lastUsedAt);
        } catch (SQLException | RuntimeException e) {
            // 统计字段，失败无所谓
        }
    }

    /** 事务内版本。 */
    public void touch(Connection conn, String grantId, long lastUsedAt) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE oauth_grants SET last_used_at = ? WHERE grant_id = ?")) {
            ps.setLong(1, lastUsedAt);
            ps.setString(2, grantId);
            ps.executeUpdate();
        }
    }

    // ==================== 查询 ====================

    /** 按 grantId 查（自取连接版）；查不到或出错返回 {@code null} */
    public Row find(String grantId) {
        try (Connection conn = DatabaseManager.getConnection()) {
            return find(conn, grantId);
        } catch (SQLException | RuntimeException e) {
            System.err.println("[OAuthGrant] 警告：授权查询失败: " + e.getMessage());
            return null;
        }
    }

    /** 事务内版本。 */
    public Row find(Connection conn, String grantId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT grant_id, user_id, username, client_id, scopes, revoked,"
                        + " created_at, last_used_at, expires_at FROM oauth_grants WHERE grant_id = ?")) {
            ps.setString(1, grantId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    /**
     * 某玩家的全部有效授权（自取连接版）。
     *
     * <p>按最近使用时间倒序 —— 「已授权应用」页把常用的排前面。</p>
     */
    public List<Row> findActiveByUser(long userId) {
        try (Connection conn = DatabaseManager.getConnection()) {
            return findActiveByUser(conn, userId);
        } catch (SQLException | RuntimeException e) {
            System.err.println("[OAuthGrant] 警告：授权列表查询失败: " + e.getMessage());
            return List.of();
        }
    }

    /** 事务内版本。 */
    public List<Row> findActiveByUser(Connection conn, long userId) throws SQLException {
        List<Row> rows = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT grant_id, user_id, username, client_id, scopes, revoked,"
                        + " created_at, last_used_at, expires_at FROM oauth_grants"
                        + " WHERE user_id = ? AND revoked = 0 ORDER BY last_used_at DESC")) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(map(rs));
                }
            }
        }
        return rows;
    }

    /** 启动时载入全部有效授权（自取连接版） */
    public List<Row> findAllActive() {
        try (Connection conn = DatabaseManager.getConnection()) {
            return findAllActive(conn);
        } catch (SQLException | RuntimeException e) {
            System.err.println("[OAuthGrant] 警告：授权初始化加载失败: " + e.getMessage());
            return List.of();
        }
    }

    /** 事务内版本。 */
    public List<Row> findAllActive(Connection conn) throws SQLException {
        List<Row> rows = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT grant_id, user_id, username, client_id, scopes, revoked,"
                        + " created_at, last_used_at, expires_at FROM oauth_grants WHERE revoked = 0");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                rows.add(map(rs));
            }
        }
        return rows;
    }

    // ==================== 清理 ====================

    /**
     * 删除早已撤销且过期的记录（自取连接版）。
     *
     * <p>撤销记录要留一段时间便于追溯，所以不能撤销即删。
     * 这里用 {@code expires_at} 作为保留期限：refresh_token 都过期了，
     * 这条记录就只剩历史价值。</p>
     *
     * @return 删除的行数；失败返回 0
     */
    public int purgeExpired(long now) {
        try (Connection conn = DatabaseManager.getConnection()) {
            return purgeExpired(conn, now);
        } catch (SQLException | RuntimeException e) {
            return 0;
        }
    }

    /** 事务内版本。 */
    public int purgeExpired(Connection conn, long now) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM oauth_grants WHERE revoked = 1 AND expires_at > 0 AND expires_at < ?")) {
            ps.setLong(1, now);
            return ps.executeUpdate();
        }
    }

    private static Row map(ResultSet rs) throws SQLException {
        return new Row(
                rs.getString("grant_id"),
                rs.getLong("user_id"),
                rs.getString("username"),
                rs.getString("client_id"),
                rs.getString("scopes"),
                rs.getInt("revoked") != 0,
                rs.getLong("created_at"),
                rs.getLong("last_used_at"),
                rs.getLong("expires_at"));
    }
}
