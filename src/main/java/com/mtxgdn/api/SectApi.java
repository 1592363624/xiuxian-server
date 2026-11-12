package com.mtxgdn.api;
import com.mtxgdn.game.entity.SuccessionOutcome;


import com.mtxgdn.common.result.OperationResult;
import com.mtxgdn.game.entity.Sect;
import com.mtxgdn.game.entity.SectApplication;
import com.mtxgdn.game.entity.SectMember;
import com.mtxgdn.game.entity.SectWarehouseItem;
import com.mtxgdn.game.item.Item;
import com.mtxgdn.game.sect.ContestResult;
import com.mtxgdn.game.sect.SalaryPayResult;
import com.mtxgdn.game.sect.SectActionResult;
import com.mtxgdn.game.sect.StepDownResult;
import com.mtxgdn.game.sect.TreasuryDonateResult;
import com.mtxgdn.game.sect.TreasuryInfo;
import com.mtxgdn.game.sect.WarehouseDonateResult;
import com.mtxgdn.game.sect.WarResult;
import java.util.List;

/**
 * SectService 的对外接口。
 *
 * <p>由脚本从实现类机械抽取（仅剔除构造函数、静态方法、以及对 JDBC 与
 * 其它服务类型的引用）。目的是让插件层不再依赖 com.mtxgdn.game.service 的具体类，
 * 从而可以替换实现、可以在测试里塞假实现。</p>
 *
 * <p><b>注意</b>：这是当前实现的镜像，不是精心设计的稳定契约。
 * 后续应按实际使用收窄 —— 接口每多一个方法，实现类就少一分改动自由。</p>
 */
public interface SectApi {

    List<Sect> getAllSects();

    Sect getSectById(long sectId);

    Sect getSectByName(String name);

    Sect getPlayerSect(long playerId);

    List<Sect> getTopSects(int limit);

    List<SectMember> getSectMembers(long sectId);

    SectMember getMember(long sectId, long playerId);

    SectMember getPlayerMember(long playerId);

    SectActionResult createSect(long playerId, String sectName, String description);

    SectActionResult levelUp(long playerId);

    SectActionResult transferLeader(long fromPlayerId, long toPlayerId);

    SectActionResult disbandSect(long playerId);

    OperationResult applyToSect(long playerId, long sectId, String message);

    List<SectApplication> getPendingApplications(long sectId);

    OperationResult approveApplication(long approverPlayerId, long applicationId, boolean approved);

    OperationResult leaveSect(long playerId);

    OperationResult kickMember(long kickerPlayerId, long targetPlayerId);

    OperationResult appointMember(long appointerPlayerId, long targetPlayerId, String newRole);

    OperationResult addContribution(long sectId, long playerId, long amount);

    List<SectWarehouseItem> getWarehouse(long sectId);

    WarehouseDonateResult donateToWarehouse(long playerId, long sectId, String itemKey, int quantity);

    OperationResult withdrawFromWarehouse(long playerId, long sectId, String itemKey, int quantity);

    long getItemContributionCost(Item item);

    WarResult declareWar(long attackerPlayerId, long defenderSectId);

    StepDownResult stepDown(long playerId);

    SuccessionOutcome resolveSuccession(long sectId, long now);

    ContestResult challengeLeader(long playerId, boolean accept);

    String leadershipStatus(Sect sect);

    int sweepLeaderSuccession(long now);

    TreasuryDonateResult donateStonesToTreasury(long playerId, long amount);

    TreasuryInfo getTreasuryInfo(long playerId);

    OperationResult setSalary(long playerId, long perMember);

    SalaryPayResult paySalariesNow(long playerId);

    int sweepSalaries(long now);

}
