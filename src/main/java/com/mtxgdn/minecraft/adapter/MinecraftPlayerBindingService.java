package com.mtxgdn.minecraft.adapter;

import com.mtxgdn.db.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class MinecraftPlayerBindingService {

    public MinecraftPlayerBinding findByMcUuid(String mcUuid) {
        String sql = "SELECT id, mc_uuid, mc_name, user_id, created_at FROM mc_bindings WHERE mc_uuid = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, mcUuid);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapBinding(rs);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("查询MC绑定失败", e);
        }
        return null;
    }

    public MinecraftPlayerBinding findByMcName(String mcName) {
        String sql = "SELECT id, mc_uuid, mc_name, user_id, created_at FROM mc_bindings WHERE mc_name = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, mcName);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapBinding(rs);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("查询MC绑定失败", e);
        }
        return null;
    }

    public MinecraftPlayerBinding findByUserId(Long userId) {
        String sql = "SELECT id, mc_uuid, mc_name, user_id, created_at FROM mc_bindings WHERE user_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapBinding(rs);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("查询用户MC绑定失败", e);
        }
        return null;
    }

    public void bind(String mcUuid, String mcName, Long userId) {
        MinecraftPlayerBinding existingByUuid = findByMcUuid(mcUuid);
        if (existingByUuid != null) {
            throw new RuntimeException("该MC玩家已绑定用户: " + existingByUuid.getUserId());
        }
        MinecraftPlayerBinding existingByUser = findByUserId(userId);
        if (existingByUser != null) {
            throw new RuntimeException("该用户已绑定MC玩家: " + existingByUser.getMcName());
        }
        String sql = "INSERT INTO mc_bindings (mc_uuid, mc_name, user_id) VALUES (?, ?, ?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, mcUuid);
            stmt.setString(2, mcName);
            stmt.setLong(3, userId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("绑定MC失败", e);
        }
    }

    public void unbindByMcUuid(String mcUuid) {
        String sql = "DELETE FROM mc_bindings WHERE mc_uuid = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, mcUuid);
            int affected = stmt.executeUpdate();
            if (affected == 0) {
                throw new RuntimeException("该MC玩家未绑定任何用户");
            }
        } catch (SQLException e) {
            throw new RuntimeException("解绑MC失败", e);
        }
    }

    public void updateMcName(String mcUuid, String newName) {
        String sql = "UPDATE mc_bindings SET mc_name = ? WHERE mc_uuid = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, newName);
            stmt.setString(2, mcUuid);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("更新MC昵称失败", e);
        }
    }

    private MinecraftPlayerBinding mapBinding(ResultSet rs) throws SQLException {
        MinecraftPlayerBinding binding = new MinecraftPlayerBinding();
        binding.setId(rs.getLong("id"));
        binding.setMcUuid(rs.getString("mc_uuid"));
        binding.setMcName(rs.getString("mc_name"));
        binding.setUserId(rs.getLong("user_id"));
        binding.setCreatedAt(rs.getString("created_at"));
        return binding;
    }
}
