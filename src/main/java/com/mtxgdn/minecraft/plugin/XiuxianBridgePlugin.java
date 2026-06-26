package com.mtxgdn.minecraft.plugin;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * 修仙世界 MC 辅助插件。
 * <p>
 * 注册 {@code /xiuxian} 命令，收到后将子命令转发到修仙主服务端（localhost:8080），
 * 拿到回复后通过 Bukkit API 反馈给玩家。完全避免 "Unknown command" 错误。
 * <p>
 * 玩家用法：
 * <pre>
 * /xiuxian status
 * /xiuxian register 张三
 * /xiuxian cultivate
 * /xiuxian help
 * </pre>
 */
public class XiuxianBridgePlugin extends JavaPlugin implements CommandExecutor {

    private static final Gson gson = new Gson();
    private static final String API_URL = "http://localhost:8080/api/mc-command";

    @Override
    public void onEnable() {
        // 注册 /xiuxian 为根命令
        getCommand("xiuxian").setExecutor(this);
        getLogger().info("[修仙] 桥接插件已加载。用法: /xiuxian <子命令> [参数]");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c[修仙] 仅玩家可用。");
            return true;
        }

        Player player = (Player) sender;
        String mcName = player.getName();
        String mcUuid = player.getUniqueId().toString();

        if (args.length == 0) {
            player.sendMessage("§6[修仙] §r用法: /xiuxian <子命令> [参数]\n输入 §e/xiuxian help §r查看所有可用指令");
            return true;
        }

        String subCmd = args[0];
        StringBuilder argsBuilder = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            if (i > 1) argsBuilder.append(" ");
            argsBuilder.append(args[i]);
        }
        String cmdArgs = argsBuilder.toString();

        // 异步转发到主服务端
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            try {
                JsonObject request = new JsonObject();
                request.addProperty("mcName", mcName);
                request.addProperty("mcUuid", mcUuid);
                request.addProperty("command", subCmd);
                request.addProperty("args", cmdArgs);

                JsonObject response = postJson(API_URL, request);
                if (response == null) {
                    player.sendMessage("§c[修仙] §r主服务端未响应，请稍后重试。");
                    return;
                }

                boolean ok = response.has("ok") && response.get("ok").getAsBoolean();
                String msg = response.has("response") ? response.get("response").getAsString()
                        : response.has("error") ? "§c" + response.get("error").getAsString()
                        : "未知错误";

                player.sendMessage("§6[修仙] §r" + msg);
            } catch (Exception e) {
                player.sendMessage("§c[修仙] §r通信异常: " + e.getMessage());
                getLogger().warning("转发命令失败: " + e.getMessage());
            }
        });

        return true;
    }

    private JsonObject postJson(String url, JsonObject body) throws Exception {
        byte[] data = body.toString().getBytes(StandardCharsets.UTF_8);

        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(3000);
        conn.setReadTimeout(10000);
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

        try (OutputStream os = conn.getOutputStream()) {
            os.write(data);
            os.flush();
        }

        if (conn.getResponseCode() != 200) {
            return null;
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            return gson.fromJson(reader, JsonObject.class);
        }
    }
}
