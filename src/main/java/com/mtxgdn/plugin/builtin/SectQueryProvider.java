package com.mtxgdn.plugin.builtin;
import com.mtxgdn.common.service.ServiceRegistry;

import com.mtxgdn.game.entity.Sect;
import com.mtxgdn.game.entity.SectMember;
import com.mtxgdn.game.service.SectService;
import com.mtxgdn.plugin.api.SectQueryApi;

import java.util.List;

/**
 * {@link SectQueryApi} 的实现：宗门模块对外暴露的只读视图。
 *
 * <p>这层适配器的意义在于<b>隔离</b>：{@code SectService} 的字段和方法可以自由演进，
 * 只要本类维持住五个只读方法的语义，依赖它的插件就不会编译不过。</p>
 *
 * <p>所有方法都对 {@code null} 做了兜底 —— 宗门模块刚启用、缓存未就绪、
 * 或玩家已退出时，查询方应该拿到"没有宗门"而不是一个 NPE。</p>
 */
final class SectQueryProvider implements SectQueryApi {

    private static SectService sectService() {
        return com.mtxgdn.common.service.ServiceRegistry.container().getSectService();
    }

    @Override
    public long getSectIdOf(long playerId) {
        SectService service = sectService();
        if (service == null) return 0;
        Sect sect = service.getPlayerSect(playerId);
        return sect == null ? 0 : sect.getId();
    }

    @Override
    public String getSectNameOf(long playerId) {
        SectService service = sectService();
        if (service == null) return null;
        Sect sect = service.getPlayerSect(playerId);
        return sect == null ? null : sect.getName();
    }

    @Override
    public int getSectLevel(long sectId) {
        SectService service = sectService();
        if (service == null || sectId <= 0) return 0;
        Sect sect = service.getSectById(sectId);
        return sect == null ? 0 : sect.getLevel();
    }

    @Override
    public int getMemberCount(long sectId) {
        SectService service = sectService();
        if (service == null || sectId <= 0) return 0;
        List<SectMember> members = service.getSectMembers(sectId);
        return members == null ? 0 : members.size();
    }

    @Override
    public boolean areSectMates(long playerIdA, long playerIdB) {
        long a = getSectIdOf(playerIdA);
        // 没有宗门的人不该因为"都返回 0"而被判为同门
        return a != 0 && a == getSectIdOf(playerIdB);
    }
}
