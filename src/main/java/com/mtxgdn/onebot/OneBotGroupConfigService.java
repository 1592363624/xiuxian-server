package com.mtxgdn.onebot;

import com.mtxgdn.db.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class OneBotGroupConfigService {

    public OneBotGroupConfig findByGroupId(Long groupId) {
        String sql = "SELECT id, group_id, auto_mute_enabled, mute_duration_days, created_at, updated_at FROM onebot_group_config WHERE group_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, groupId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    OneBotGroupConfig config = new OneBotGroupConfig();
                    config.setId(rs.getLong("id"));
                    config.setGroupId(rs.getLong("group_id"));
                    config.setAutoMuteEnabled(rs.getBoolean("auto_mute_enabled"));
                    config.setMuteDurationDays(rs.getInt("mute_duration_days"));
                    config.setCreatedAt(rs.getString("created_at"));
                    config.setUpdatedAt(rs.getString("updated_at"));
                    return config;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("查询群组配置失败", e);
        }
        return null;
    }

    public List<OneBotGroupConfig> getAllConfigs() {
        String sql = "SELECT id, group_id, auto_mute_enabled, mute_duration_days, created_at, updated_at FROM onebot_group_config ORDER BY id DESC";
        List<OneBotGroupConfig> list = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                OneBotGroupConfig config = new OneBotGroupConfig();
                config.setId(rs.getLong("id"));
                config.setGroupId(rs.getLong("group_id"));
                config.setAutoMuteEnabled(rs.getBoolean("auto_mute_enabled"));
                config.setMuteDurationDays(rs.getInt("mute_duration_days"));
                config.setCreatedAt(rs.getString("created_at"));
                config.setUpdatedAt(rs.getString("updated_at"));
                list.add(config);
            }
        } catch (SQLException e) {
            throw new RuntimeException("查询群组配置列表失败", e);
        }
        return list;
    }

    public OneBotGroupConfig getOrCreateConfig(Long groupId) {
        OneBotGroupConfig config = findByGroupId(groupId);
        if (config == null) {
            config = new OneBotGroupConfig(groupId);
            saveConfig(config);
        }
        return config;
    }

    public void saveConfig(OneBotGroupConfig config) {
        if (config.getId() == null) {
            insertConfig(config);
        } else {
            updateConfig(config);
        }
    }

    private void insertConfig(OneBotGroupConfig config) {
        String sql = "INSERT INTO onebot_group_config (group_id, auto_mute_enabled, mute_duration_days) VALUES (?, ?, ?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            stmt.setLong(1, config.getGroupId());
            stmt.setBoolean(2, config.isAutoMuteEnabled());
            stmt.setInt(3, config.getMuteDurationDays());
            stmt.executeUpdate();
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    config.setId(rs.getLong(1));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("插入群组配置失败", e);
        }
    }

    private void updateConfig(OneBotGroupConfig config) {
        String sql = "UPDATE onebot_group_config SET auto_mute_enabled = ?, mute_duration_days = ? WHERE group_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setBoolean(1, config.isAutoMuteEnabled());
            stmt.setInt(2, config.getMuteDurationDays());
            stmt.setLong(3, config.getGroupId());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("更新群组配置失败", e);
        }
    }

    public void setAutoMute(Long groupId, boolean enabled) {
        OneBotGroupConfig config = getOrCreateConfig(groupId);
        config.setAutoMuteEnabled(enabled);
        saveConfig(config);
    }

    public void setMuteDuration(Long groupId, int days) {
        OneBotGroupConfig config = getOrCreateConfig(groupId);
        config.setMuteDurationDays(days);
        saveConfig(config);
    }

    public void deleteConfig(Long groupId) {
        String sql = "DELETE FROM onebot_group_config WHERE group_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, groupId);
            int affected = stmt.executeUpdate();
            if (affected == 0) {
                throw new RuntimeException("群组配置不存在");
            }
        } catch (SQLException e) {
            throw new RuntimeException("删除群组配置失败", e);
        }
    }

    public boolean isAutoMuteEnabled(Long groupId) {
        OneBotGroupConfig config = findByGroupId(groupId);
        return config != null && config.isAutoMuteEnabled();
    }

    public int getMuteDuration(Long groupId) {
        OneBotGroupConfig config = findByGroupId(groupId);
        return config != null ? config.getMuteDurationDays() : 29;
    }
}