package com.mtxgdn.game.command.item;

import com.mtxgdn.game.item.EquipResult;
import com.mtxgdn.common.command.Command;
import com.mtxgdn.common.command.CommandContext;
import com.mtxgdn.common.command.CommandUtils;
import com.mtxgdn.game.entity.PlayerInfo;
import com.mtxgdn.i18n.I18n;

public class EquipCommand extends Command {
    public EquipCommand() {
        super(new String[]{"装备", "equip"}, "穿戴装备到指定部位", "/装备 <物品key> <部位>", "背包", "game.equipment.equip");
        
        setI18nKeys("cmd.equip.desc", "cmd.equip.usage");
    setModule("inventory");
    }

    @Override
    public void execute(CommandContext ctx) {
        PlayerInfo p = requirePlayerInfo(ctx);
        if (p == null) return;

        String[] parts = splitArgs(ctx.getArg(), 2);
        if (!CommandUtils.hasEnoughArgs(parts, 2) || CommandUtils.isBlank(parts[0]) || CommandUtils.isBlank(parts[1])) {
            ctx.reply(I18n.t("equip.usage"));
            return;
        }

        var itemService = services().getItemService();
        // 允许 /装备 1 weapon（背包序号）；不是数字或越界时按原样当 key 处理
        String itemRef = itemService.resolveInventoryItemReference(p.getId(), parts[0].trim());
        EquipResult result = itemService.equipItem((int) p.getId(), itemRef, parts[1].trim().toLowerCase());
        String msg = result.message() != null ? result.message() : "";
        if (msg.isEmpty()) msg = "装备失败，请检查物品key和部位是否正确。";
        String discTip = services().getGuideService().checkDiscovery((int) p.getId(), p, "equip");
        if (discTip != null) msg += "\n\n" + discTip;
        ctx.reply(msg);
    }
}
