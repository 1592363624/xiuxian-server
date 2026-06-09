package com.mtxgdn.onebot.command.sect;

import com.mtxgdn.common.command.Command;
import com.mtxgdn.common.command.CommandContext;
import com.mtxgdn.common.command.RouteDefinition;
import com.mtxgdn.common.GameMessage;
import com.mtxgdn.game.entity.PlayerInfo;
import com.mtxgdn.game.entity.Sect;
import com.mtxgdn.game.entity.SectApplication;
import com.mtxgdn.game.entity.SectMember;
import com.mtxgdn.game.entity.SectWarehouseItem;
import com.mtxgdn.game.item.Item;
import com.mtxgdn.game.item.ItemRegistry;
import com.mtxgdn.game.service.SectService;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.List;

public class SectCommand extends Command {
    private static final SectService sectService = new SectService();

    public SectCommand() {
        super(new String[]{"sect", "宗门"},
                "宗门管理：输入 /宗门 help 查看所有子命令",
                "/宗门 <子命令> [参数]", "宗门", "game.sect.manage");
    }

    @Override
    public void execute(CommandContext ctx) {
        Long userId = ctx.requireBinding();
        if (userId == null) return;
        if (!ctx.checkPermission("game.sect.manage")) return;
        PlayerInfo p = ctx.requirePlayer(userId);
        if (p == null) return;

        String[] parts = ctx.getArg().split("\\s+", 3);
        String subCmd = parts.length > 0 ? parts[0].trim().toLowerCase() : "";
        switch (subCmd) {
            case "create", "创建": handleCreate(ctx, p, parts); break;
            case "join", "加入": handleJoin(ctx, p, parts); break;
            case "list", "列表": handleList(ctx, p); break;
            case "info", "信息": handleInfo(ctx, p, parts); break;
            case "members", "成员": handleMembers(ctx, p); break;
            case "apply", "申请": handleApply(ctx, p, parts); break;
            case "approve", "通过": handleApprove(ctx, p, parts); break;
            case "reject", "拒绝": handleReject(ctx, p, parts); break;
            case "leave", "退出": handleLeave(ctx, p); break;
            case "kick", "踢出": handleKick(ctx, p, parts); break;
            case "appoint", "任命": handleAppoint(ctx, p, parts); break;
            case "donate", "捐献": handleDonate(ctx, p, parts); break;
            case "warehouse", "仓库": handleWarehouse(ctx, p); break;
            case "take", "取出": handleTake(ctx, p, parts); break;
            case "disband", "解散": handleDisband(ctx, p); break;
            case "top", "排行": handleTop(ctx, p); break;
            case "pending", "申请列表": handlePendingList(ctx, p); break;
            case "help", "帮助", "?":
                ctx.reply(buildHelp());
                break;
            default:
                ctx.reply(buildOverview(p));
        }
    }

    private String buildOverview(PlayerInfo p) {
        Sect mySect = sectService.getPlayerSect(p.getId());
        if (mySect != null) {
            SectMember me = sectService.getMember(mySect.getId(), p.getId());
            StringBuilder sb = new StringBuilder();
            sb.append("════ 【").append(mySect.getName()).append("】 ════\n");
            sb.append("声望: ").append(mySect.getPrestige());
            sb.append("  成员: ").append(mySect.getMemberCount()).append("/").append(Sect.getMaxMembersForLevel(mySect.getLevel()));
            if (me != null) {
                sb.append("  职位: ").append(SectMember.getRoleDisplayName(me.getRole()));
                sb.append("  贡献: ").append(me.getContribution());
            }
            sb.append("\n\n▍快捷操作\n");
            sb.append("  /宗门 info        宗门详情\n");
            sb.append("  /宗门 members     成员列表\n");
            sb.append("  /宗门 donate      捐献物品\n");
            sb.append("  /宗门 warehouse   宗门仓库\n");
            if (me != null && me.canManage()) {
                sb.append("  /宗门 pending     审批申请\n");
                sb.append("  /宗门 kick        管理成员\n");
                sb.append("  /宗门 take        取出物品\n");
            }
            sb.append("\n输入 /宗门 help 查看全部命令");
            return sb.toString();
        } else {
            return """
════ 宗门系统 ════

▍宗门大厅
  /宗门 list           浏览天下宗门
  /宗门 top            宗门声望排行
  /宗门 create <名称>  开创宗门（需筑基期+100灵石）

▍加入宗门
  /宗门 apply  <名称>  申请加入
  /宗门 join   <名称>  同上
  /宗门 info   <名称>  查看宗门详情

输入 /宗门 help 查看全部命令""";
        }
    }

    private String buildHelp() {
        return """
===== 宗门系统 =====
/宗门 create <名称> [描述]  创建宗门（需要筑基期以上，100灵石）
/宗门 join <宗门名>          申请加入宗门
/宗门 list                   宗门列表
/宗门 info [名称]            查看宗门信息（不填则查看自己的宗门）
/宗门 members                查看宗门成员
/宗门 apply <宗门名>         申请加入宗门
/宗门 pending                查看待处理申请（宗主/长老）
/宗门 approve <玩家名>       通过申请
/宗门 reject <玩家名>        拒绝申请
/宗门 leave                  退出宗门
/宗门 kick <玩家名>          踢出成员（宗主/长老）
/宗门 appoint <玩家名> <长老|弟子>  任命职位（宗主）
/宗门 donate <物品key> <数量>  向宗门仓库捐献
/宗门 warehouse              查看宗门仓库
/宗门 take <物品key> <数量>  从仓库取出（宗主/长老）
/宗门 disband                解散宗门（宗主）
/宗门 top                    宗门排行""";
    }

    private void handleCreate(CommandContext ctx, PlayerInfo p, String[] parts) {
        String name = parts.length > 1 ? parts[1].trim() : "";
        String desc = parts.length > 2 ? parts[2].trim() : "";
        if (name.isEmpty()) { ctx.reply("用法: /宗门 create <宗门名> [描述]"); return; }
        var result = sectService.createSect(p.getId(), name, desc);
        ctx.reply((String) result.get("message"));
    }

    private void handleJoin(CommandContext ctx, PlayerInfo p, String[] parts) {
        String name = parts.length > 1 ? parts[1].trim() : "";
        if (name.isEmpty()) { ctx.reply("用法: /宗门 join <宗门名>"); return; }
        Sect sect = sectService.getSectByName(name);
        if (sect == null) { ctx.reply("找不到宗门【" + name + "】"); return; }
        String msg = parts.length > 2 ? parts[2] : "";
        var result = sectService.applyToSect(p.getId(), sect.getId(), msg);
        ctx.reply((String) result.get("message"));
    }

    private void handleList(CommandContext ctx, PlayerInfo p) {
        List<Sect> sects = sectService.getAllSects();
        if (sects.isEmpty()) { ctx.reply("天下尚无宗门。\n使用 /宗门 create <名称> 开创一个宗门！"); return; }
        StringBuilder sb = new StringBuilder("===== 宗门列表（共 ").append(sects.size()).append(" 个）=====\n");
        int rank = 1;
        for (Sect s : sects) {
            sb.append(String.format("%d. 【%s】 宗主:%s  声望:%d  成员:%d/%d\n",
                    rank++, s.getName(), s.getLeaderName() != null ? s.getLeaderName() : "未知",
                    s.getPrestige(), s.getMemberCount(), Sect.getMaxMembersForLevel(s.getLevel())));
        }
        ctx.reply(sb.toString());
    }

    private void handleInfo(CommandContext ctx, PlayerInfo p, String[] parts) {
        Sect sect;
        if (parts.length > 1 && !parts[1].isEmpty()) {
            sect = sectService.getSectByName(parts[1].trim());
        } else {
            sect = sectService.getPlayerSect(p.getId());
        }
        if (sect == null) { ctx.reply("宗门不存在或你尚未加入宗门。\n使用 /宗门 list 查看所有宗门"); return; }

        SectMember me = sectService.getMember(sect.getId(), p.getId());
        StringBuilder sb = new StringBuilder();
        sb.append("===== 【").append(sect.getName()).append("】 =====\n");
        sb.append("描述: ").append(sect.getDescription()).append("\n");
        sb.append("宗主: ").append(sect.getLeaderName() != null ? sect.getLeaderName() : "未知").append("\n");
        sb.append("等级: ").append(sect.getLevel()).append("级\n");
        sb.append("声望: ").append(sect.getPrestige()).append("\n");
        sb.append("成员: ").append(sect.getMemberCount()).append("/").append(Sect.getMaxMembersForLevel(sect.getLevel())).append("\n");
        if (me != null) {
            sb.append("你的职位: ").append(SectMember.getRoleDisplayName(me.getRole()));
            sb.append("  贡献: ").append(me.getContribution()).append("\n");
        }
        if (me == null && sectService.getPlayerSect(p.getId()) == null) {
            sb.append("\n使用 /宗门 join ").append(sect.getName()).append(" 申请加入");
        }
        ctx.reply(sb.toString());
    }

    private void handleMembers(CommandContext ctx, PlayerInfo p) {
        Sect sect = sectService.getPlayerSect(p.getId());
        if (sect == null) { ctx.reply("你还没有加入宗门"); return; }

        List<SectMember> members = sectService.getSectMembers(sect.getId());
        StringBuilder sb = new StringBuilder();
        sb.append("===== 【").append(sect.getName()).append("】成员（共 ").append(members.size()).append(" 人）=====\n");
        int index = 1;
        for (SectMember m : members) {
            String role = SectMember.getRoleDisplayName(m.getRole());
            String realmName = m.getPlayerRealmName();
            if (realmName == null || realmName.isEmpty()) {
                realmName = "练气" + m.getPlayerRealm() + "层";
            }
            sb.append(String.format("%d. %s [%s] [%s]  贡献:%d\n",
                    index++, m.getPlayerName(), role, realmName, m.getContribution()));
        }
        ctx.reply(sb.toString());
    }

    private void handleApply(CommandContext ctx, PlayerInfo p, String[] parts) {
        String name = parts.length > 1 ? parts[1].trim() : "";
        if (name.isEmpty()) { ctx.reply("用法: /宗门 apply <宗门名>"); return; }
        Sect sect = sectService.getSectByName(name);
        if (sect == null) { ctx.reply("找不到宗门【" + name + "】"); return; }
        var result = sectService.applyToSect(p.getId(), sect.getId(), "");
        ctx.reply((String) result.get("message"));
    }

    private void handlePendingList(CommandContext ctx, PlayerInfo p) {
        SectMember member = sectService.getPlayerMember(p.getId());
        if (member == null) { ctx.reply("你还没有加入宗门"); return; }
        if (!member.canManage()) { ctx.reply("只有宗主和长老才能查看申请列表"); return; }

        List<SectApplication> apps = sectService.getPendingApplications(member.getSectId());
        if (apps.isEmpty()) { ctx.reply("目前没有待处理的入宗申请"); return; }
        StringBuilder sb = new StringBuilder("===== 待处理的入宗申请 =====\n");
        for (int i = 0; i < apps.size(); i++) {
            SectApplication a = apps.get(i);
            sb.append(String.format("%d. %s%s\n", i + 1, a.getPlayerName(),
                    a.getMessage() != null && !a.getMessage().isEmpty() ? " 留言:" + a.getMessage() : ""));
        }
        sb.append("\n使用 /宗门 approve <玩家名> / reject <玩家名> 处理申请");
        ctx.reply(sb.toString());
    }

    private void handleApprove(CommandContext ctx, PlayerInfo p, String[] parts) {
        String targetName = parts.length > 1 ? parts[1].trim() : "";
        if (targetName.isEmpty()) { ctx.reply("用法: /宗门 approve <玩家名>"); return; }
        SectMember me = sectService.getPlayerMember(p.getId());
        if (me == null) { ctx.reply("你还没有加入宗门"); return; }

        List<SectApplication> apps = sectService.getPendingApplications(me.getSectId());
        SectApplication target = null;
        for (SectApplication a : apps) {
            if (a.getPlayerName().equals(targetName)) { target = a; break; }
        }
        if (target == null) { ctx.reply("找不到玩家【" + targetName + "】的申请，请确认名字无误"); return; }
        var result = sectService.approveApplication(p.getId(), target.getId(), true);
        ctx.reply((String) result.get("message"));
    }

    private void handleReject(CommandContext ctx, PlayerInfo p, String[] parts) {
        String targetName = parts.length > 1 ? parts[1].trim() : "";
        if (targetName.isEmpty()) { ctx.reply("用法: /宗门 reject <玩家名>"); return; }
        SectMember me = sectService.getPlayerMember(p.getId());
        if (me == null) { ctx.reply("你还没有加入宗门"); return; }

        List<SectApplication> apps = sectService.getPendingApplications(me.getSectId());
        SectApplication target = null;
        for (SectApplication a : apps) {
            if (a.getPlayerName().equals(targetName)) { target = a; break; }
        }
        if (target == null) { ctx.reply("找不到玩家【" + targetName + "】的申请"); return; }
        var result = sectService.approveApplication(p.getId(), target.getId(), false);
        ctx.reply((String) result.get("message"));
    }

    private void handleLeave(CommandContext ctx, PlayerInfo p) {
        var result = sectService.leaveSect(p.getId());
        ctx.reply((String) result.get("message"));
    }

    private void handleKick(CommandContext ctx, PlayerInfo p, String[] parts) {
        String targetName = parts.length > 1 ? parts[1].trim() : "";
        if (targetName.isEmpty()) { ctx.reply("用法: /宗门 kick <玩家名>"); return; }

        SectMember me = sectService.getPlayerMember(p.getId());
        if (me == null) { ctx.reply("你还没有加入宗门"); return; }

        List<SectMember> members = sectService.getSectMembers(me.getSectId());
        SectMember target = null;
        for (SectMember m : members) {
            if (m.getPlayerName().equals(targetName)) { target = m; break; }
        }
        if (target == null) { ctx.reply("找不到玩家【" + targetName + "】"); return; }

        var result = sectService.kickMember(p.getId(), target.getPlayerId());
        ctx.reply((String) result.get("message"));
    }

    private void handleAppoint(CommandContext ctx, PlayerInfo p, String[] parts) {
        if (parts.length < 3) { ctx.reply("用法: /宗门 appoint <玩家名> <长老|弟子>"); return; }
        String targetName = parts[1].trim();
        String role = parts[2].trim();

        SectMember me = sectService.getPlayerMember(p.getId());
        if (me == null) { ctx.reply("你还没有加入宗门"); return; }

        List<SectMember> members = sectService.getSectMembers(me.getSectId());
        SectMember target = null;
        for (SectMember m : members) {
            if (m.getPlayerName().equals(targetName)) { target = m; break; }
        }
        if (target == null) { ctx.reply("找不到玩家【" + targetName + "】"); return; }

        var result = sectService.appointMember(p.getId(), target.getPlayerId(), role);
        ctx.reply((String) result.get("message"));
    }

    private void handleDonate(CommandContext ctx, PlayerInfo p, String[] parts) {
        SectMember me = sectService.getPlayerMember(p.getId());
        if (me == null) { ctx.reply("你还没有加入宗门"); return; }

        if (parts.length < 3) { ctx.reply("用法: /宗门 donate <物品key> <数量>"); return; }
        String itemKey = parts[1].trim();
        int quantity;
        try { quantity = Integer.parseInt(parts[2].trim()); } catch (NumberFormatException e) {
            ctx.reply("数量必须是数字"); return;
        }

        var result = sectService.donateToWarehouse(p.getId(), me.getSectId(), itemKey, quantity);
        ctx.reply((String) result.get("message"));
    }

    private void handleWarehouse(CommandContext ctx, PlayerInfo p) {
        SectMember me = sectService.getPlayerMember(p.getId());
        if (me == null) { ctx.reply("你还没有加入宗门"); return; }

        List<SectWarehouseItem> items = sectService.getWarehouse(me.getSectId());
        if (items.isEmpty()) { ctx.reply("宗门仓库空空如也。\n使用 /宗门 donate <物品key> <数量> 捐献物品"); return; }
        StringBuilder sb = new StringBuilder("===== 宗门仓库 =====\n");
        int i = 1;
        for (SectWarehouseItem item : items) {
            Item it = ItemRegistry.get(item.getItemKey());
            String name = it != null ? it.getName() : item.getItemKey();
            sb.append(String.format("%d. %s x%d", i++, name, item.getQuantity()));
            if (item.getDonatedByName() != null) {
                sb.append(" (捐赠:").append(item.getDonatedByName()).append(")");
            }
            sb.append("\n");
        }
        ctx.reply(sb.toString());
    }

    private void handleTake(CommandContext ctx, PlayerInfo p, String[] parts) {
        SectMember me = sectService.getPlayerMember(p.getId());
        if (me == null) { ctx.reply("你还没有加入宗门"); return; }

        if (parts.length < 3) { ctx.reply("用法: /宗门 take <物品key> <数量>"); return; }
        String itemKey = parts[1].trim();
        int quantity;
        try { quantity = Integer.parseInt(parts[2].trim()); } catch (NumberFormatException e) {
            ctx.reply("数量必须是数字"); return;
        }
        var result = sectService.withdrawFromWarehouse(p.getId(), me.getSectId(), itemKey, quantity);
        ctx.reply((String) result.get("message"));
    }

    private void handleDisband(CommandContext ctx, PlayerInfo p) {
        var result = sectService.disbandSect(p.getId());
        ctx.reply((String) result.get("message"));
    }

    private void handleTop(CommandContext ctx, PlayerInfo p) {
        List<Sect> sects = sectService.getTopSects(10);
        if (sects.isEmpty()) { ctx.reply("天下尚无宗门榜单。快去开创第一个宗门！"); return; }
        StringBuilder sb = new StringBuilder("===== 宗门声望排行 =====\n");
        for (int i = 0; i < sects.size(); i++) {
            Sect s = sects.get(i);
            sb.append(String.format("%d. 【%s】 声望:%d  成员:%d  宗主:%s\n",
                    i + 1, s.getName(), s.getPrestige(), s.getMemberCount(),
                    s.getLeaderName() != null ? s.getLeaderName() : "未知"));
        }
        ctx.reply(sb.toString());
    }

    // ==================== REST API 端点 ====================

    @Override
    public List<RouteDefinition> getRestEndpoints() {
        return List.of(
            // GET /sect/list - 查看所有宗门
            RouteDefinition.get("sect/list", "game.sect.manage", ctx -> {
                List<Sect> sects = sectService.getAllSects();
                JsonArray arr = new JsonArray();
                for (Sect s : sects) {
                    JsonObject o = new JsonObject();
                    o.addProperty("id", s.getId());
                    o.addProperty("name", s.getName());
                    o.addProperty("description", s.getDescription());
                    o.addProperty("leaderPlayerId", s.getLeaderPlayerId());
                    o.addProperty("leaderName", s.getLeaderName());
                    o.addProperty("level", s.getLevel());
                    o.addProperty("prestige", s.getPrestige());
                    o.addProperty("memberCount", s.getMemberCount());
                    o.addProperty("maxMembers", Sect.getMaxMembersForLevel(s.getLevel()));
                    arr.add(o);
                }
                JsonObject data = new JsonObject();
                data.add("sects", arr);
                return GameMessage.restOk("获取成功", data);
            }),

            // GET /sect/info - 查看我的宗门
            RouteDefinition.get("sect/info", "game.sect.manage", ctx -> {
                Sect sec = sectService.getPlayerSect(ctx.playerId());
                if (sec == null) return GameMessage.restOk("尚未加入宗门", null);
                return buildSectJson(sec, ctx.playerId());
            }),

            // GET /sect/info/{sectId} - 查看指定宗门
            RouteDefinition.get("sect/info/{sectId}", "game.sect.manage", ctx -> {
                long sectId = ctx.pathParamLong("sectId");
                Sect sec = sectService.getSectById(sectId);
                if (sec == null) return GameMessage.restError(400, "宗门不存在");
                return buildSectJson(sec, ctx.playerId());
            })
        );
    }

    private JsonObject buildSectJson(Sect sect, int playerId) {
        JsonObject data = new JsonObject();
        data.addProperty("id", sect.getId());
        data.addProperty("name", sect.getName());
        data.addProperty("description", sect.getDescription());
        data.addProperty("leaderPlayerId", sect.getLeaderPlayerId());
        data.addProperty("leaderName", sect.getLeaderName());
        data.addProperty("level", sect.getLevel());
        data.addProperty("prestige", sect.getPrestige());
        data.addProperty("memberCount", sect.getMemberCount());
        data.addProperty("maxMembers", Sect.getMaxMembersForLevel(sect.getLevel()));
        SectMember me = sectService.getMember(sect.getId(), playerId);
        if (me != null) {
            data.addProperty("myRole", me.getRole());
            data.addProperty("myRoleDisplay", SectMember.getRoleDisplayName(me.getRole()));
            data.addProperty("myContribution", me.getContribution());
        }
        return GameMessage.restOk("获取成功", data);
    }
}
