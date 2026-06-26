package com.mtxgdn.onebot;

import com.mtxgdn.db.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class BlacklistService {

    public boolean isBlacklisted(String qqNumber) {
        String sql = "SELECT id FROM blacklist WHERE qq_number = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, qqNumber);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException("查询黑名单失败", e);
        }
    }

    public boolean isBlacklistedByUserId(Long userId) {
        String sql = "SELECT id FROM blacklist WHERE user_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException("查询黑名单失败", e);
        }
    }

    public Blacklist findByQq(String qqNumber) {
        String sql = "SELECT id, qq_number, user_id, reason, banned_by, created_at FROM blacklist WHERE qq_number = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, qqNumber);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Blacklist blacklist = new Blacklist();
                    blacklist.setId(rs.getLong("id"));
                    blacklist.setQqNumber(rs.getString("qq_number"));
                    blacklist.setUserId(rs.getLong("user_id"));
                    blacklist.setReason(rs.getString("reason"));
                    blacklist.setBannedBy(rs.getLong("banned_by"));
                    blacklist.setCreatedAt(rs.getString("created_at"));
                    return blacklist;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("查询黑名单失败", e);
        }
        return null;
    }

    public Blacklist findByUserId(Long userId) {
        String sql = "SELECT id, qq_number, user_id, reason, banned_by, created_at FROM blacklist WHERE user_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Blacklist blacklist = new Blacklist();
                    blacklist.setId(rs.getLong("id"));
                    blacklist.setQqNumber(rs.getString("qq_number"));
                    blacklist.setUserId(rs.getLong("user_id"));
                    blacklist.setReason(rs.getString("reason"));
                    blacklist.setBannedBy(rs.getLong("banned_by"));
                    blacklist.setCreatedAt(rs.getString("created_at"));
                    return blacklist;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("查询黑名单失败", e);
        }
        return null;
    }

    public List<Blacklist> getAllBlacklist() {
        String sql = "SELECT id, qq_number, user_id, reason, banned_by, created_at FROM blacklist ORDER BY id DESC";
        List<Blacklist> list = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                Blacklist blacklist = new Blacklist();
                blacklist.setId(rs.getLong("id"));
                blacklist.setQqNumber(rs.getString("qq_number"));
                blacklist.setUserId(rs.getLong("user_id"));
                blacklist.setReason(rs.getString("reason"));
                blacklist.setBannedBy(rs.getLong("banned_by"));
                blacklist.setCreatedAt(rs.getString("created_at"));
                list.add(blacklist);
            }
        } catch (SQLException e) {
            throw new RuntimeException("查询黑名单列表失败", e);
        }
        return list;
    }

    public void addToBlacklist(String qqNumber, Long userId, String reason, Long bannedBy) {
        if (isBlacklisted(qqNumber)) {
            throw new RuntimeException("该QQ号已在黑名单中");
        }

        String sql = "INSERT INTO blacklist (qq_number, user_id, reason, banned_by) VALUES (?, ?, ?, ?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, qqNumber);
            if (userId != null) {
                stmt.setLong(2, userId);
            } else {
                stmt.setNull(2, java.sql.Types.BIGINT);
            }
            stmt.setString(3, reason);
            if (bannedBy != null) {
                stmt.setLong(4, bannedBy);
            } else {
                stmt.setNull(4, java.sql.Types.BIGINT);
            }
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("添加黑名单失败", e);
        }
    }

    public void removeFromBlacklist(String qqNumber) {
        String sql = "DELETE FROM blacklist WHERE qq_number = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, qqNumber);
            int affected = stmt.executeUpdate();
            if (affected == 0) {
                throw new RuntimeException("该QQ号不在黑名单中");
            }
        } catch (SQLException e) {
            throw new RuntimeException("移除黑名单失败", e);
        }
    }

    public void removeFromBlacklistByUserId(Long userId) {
        String sql = "DELETE FROM blacklist WHERE user_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            int affected = stmt.executeUpdate();
            if (affected == 0) {
                throw new RuntimeException("该用户不在黑名单中");
            }
        } catch (SQLException e) {
            throw new RuntimeException("移除黑名单失败", e);
        }
    }
}