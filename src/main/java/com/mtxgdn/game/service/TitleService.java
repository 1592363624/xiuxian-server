package com.mtxgdn.game.service;

import com.mtxgdn.db.DatabaseManager;
import com.mtxgdn.game.entity.Title;
import com.mtxgdn.game.title.TitleRegistry;

import java.sql.*;
import java.util.*;

public class TitleService {

    public TitleService() {
        TitleRegistry.init();
    }

    /** 授予称号 */
    public Map<String, Object> grantTitle(long playerId, String titleKey) {
        Map<String, Object> result = new LinkedHashMap<>();
        Title title = TitleRegistry.get(titleKey);
        if (title == null) {
            result.put("success", false);
            result.put("message", "称号不存在: " + titleKey);
            return result;
        }

        if (hasTitle(playerId, titleKey)) {
            result.put("success", false);
            result.put("message", "已拥有该称号");
            return result;
        }

        String sql = "INSERT INTO player_titles (player_id, title_key, is_equipped) VALUES (?, ?, ?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, playerId);
            ps.setString(2, titleKey);
            ps.setBoolean(3, false);
            ps.executeUpdate();
            result.put("success", true);
            result.put("message", "成功授予称号: " + title.getName());
            result.put("titleKey", titleKey);
            result.put("titleName", title.getName());
        } catch (SQLException e) {
            result.put("success", false);
            result.put("message", "授予称号失败: " + e.getMessage());
        }
        return result;
    }

    /** 撤销称号 */
    public Map<String, Object> revokeTitle(long playerId, String titleKey) {
        Map<String, Object> result = new LinkedHashMap<>();
        String sql = "DELETE FROM player_titles WHERE player_id = ? AND title_key = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, playerId);
            ps.setString(2, titleKey);
            int rows = ps.executeUpdate();
            if (rows > 0) {
                Title title = TitleRegistry.get(titleKey);
                result.put("success", true);
                result.put("message", "已撤销称号: " + (title != null ? title.getName() : titleKey));
            } else {
                result.put("success", false);
                result.put("message", "玩家未拥有该称号");
            }
        } catch (SQLException e) {
            result.put("success", false);
            result.put("message", "撤销称号失败: " + e.getMessage());
        }
        return result;
    }

    /** 装备称号 */
    public Map<String, Object> equipTitle(long playerId, String titleKey) {
        Map<String, Object> result = new LinkedHashMap<>();
        Title title = TitleRegistry.get(titleKey);
        if (title == null) {
            result.put("success", false);
            result.put("message", "称号不存在: " + titleKey);
            return result;
        }

        if (!hasTitle(playerId, titleKey)) {
            result.put("success", false);
            result.put("message", "你未拥有该称号");
            return result;
        }

        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // 卸下当前装备的称号
                String unequipSql = "UPDATE player_titles SET is_equipped = ? WHERE player_id = ? AND is_equipped = ?";
                try (PreparedStatement ps = conn.prepareStatement(unequipSql)) {
                    ps.setBoolean(1, false);
                    ps.setLong(2, playerId);
                    ps.setBoolean(3, true);
                    ps.executeUpdate();
                }

                // 装备新称号
                String equipSql = "UPDATE player_titles SET is_equipped = ? WHERE player_id = ? AND title_key = ?";
                try (PreparedStatement ps = conn.prepareStatement(equipSql)) {
                    ps.setBoolean(1, true);
                    ps.setLong(2, playerId);
                    ps.setString(3, titleKey);
                    ps.executeUpdate();
                }

                conn.commit();
                result.put("success", true);
                result.put("message", "已装备称号: " + title.getName());
                result.put("titleKey", titleKey);
                result.put("titleName", title.getName());
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            result.put("success", false);
            result.put("message", "装备称号失败: " + e.getMessage());
        }
        return result;
    }

    /** 卸下称号 */
    public Map<String, Object> unequipTitle(long playerId) {
        Map<String, Object> result = new LinkedHashMap<>();
        String sql = "UPDATE player_titles SET is_equipped = ? WHERE player_id = ? AND is_equipped = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBoolean(1, false);
            ps.setLong(2, playerId);
            ps.setBoolean(3, true);
            int rows = ps.executeUpdate();
            result.put("success", true);
            if (rows > 0) {
                result.put("message", "已卸下称号");
            } else {
                result.put("message", "当前未装备任何称号");
            }
        } catch (SQLException e) {
            result.put("success", false);
            result.put("message", "卸下称号失败: " + e.getMessage());
        }
        return result;
    }

    /** 获取玩家所有称号 */
    public List<Map<String, Object>> getPlayerTitles(long playerId) {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT title_key, is_equipped, acquired_at FROM player_titles WHERE player_id = ? ORDER BY acquired_at DESC";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, playerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String key = rs.getString("title_key");
                    Title title = TitleRegistry.get(key);
                    if (title == null) continue;
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("titleKey", key);
                    map.put("name", title.getName());
                    map.put("description", title.getDescription());
                    map.put("rarity", title.getRarity().name());
                    map.put("rarityLabel", title.getRarityLabel());
                    map.put("isEquipped", rs.getBoolean("is_equipped"));
                    map.put("acquiredAt", rs.getString("acquired_at"));
                    map.put("attackBonus", title.getAttackBonus());
                    map.put("defenseBonus", title.getDefenseBonus());
                    map.put("hpBonus", title.getHpBonus());
                    map.put("mpBonus", title.getMpBonus());
                    map.put("speedBonus", title.getSpeedBonus());
                    map.put("spiritBonus", title.getSpiritBonus());
                    map.put("cultivationSpeedBonus", title.getCultivationSpeedBonus());
                    map.put("expBonus", title.getExpBonus());
                    map.put("dropRateBonus", title.getDropRateBonus());
                    list.add(map);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    /** 获取当前装备的称号 */
    public Title getEquippedTitle(long playerId) {
        String sql = "SELECT title_key FROM player_titles WHERE player_id = ? AND is_equipped = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, playerId);
            ps.setBoolean(2, true);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return TitleRegistry.get(rs.getString("title_key"));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public boolean hasTitle(long playerId, String titleKey) {
        String sql = "SELECT 1 FROM player_titles WHERE player_id = ? AND title_key = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, playerId);
            ps.setString(2, titleKey);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            return false;
        }
    }
}
