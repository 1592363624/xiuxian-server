package com.mtxgdn.game.entity;

import com.mtxgdn.api.SectApi;
import com.mtxgdn.common.result.OperationResult;
import com.mtxgdn.common.service.ServiceRegistry;
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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.Map;

/**
 * 由 {SectService} 的内部嵌套类提取而来。
 * <p>原来写成 {$svc}.{$nest}，接口无法在不 import 服务实现类的前提下引用它，
 * 因此提到 entity 包 —— 它本来就是纯数据，和服务的生命周期无关。</p>
 */
public class SuccessionOutcome {
    public final boolean succeeded;
    public final long successorId;
    public final String successorName;
    public final long rivalId;
    public final String rivalName;
    /** >0 表示同时开启了竞争期，值为截止时间戳。 */
    public final long contestUntil;
    public final String note;

    public SuccessionOutcome(boolean succeeded, long successorId, String successorName,
                              long rivalId, String rivalName, long contestUntil, String note) {
        this.succeeded = succeeded;
        this.successorId = successorId;
        this.successorName = successorName;
        this.rivalId = rivalId;
        this.rivalName = rivalName;
        this.contestUntil = contestUntil;
        this.note = note;
    }

    public static SuccessionOutcome none(String note) {
        return new SuccessionOutcome(false, 0, null, 0, null, 0, note);
    }

    public static SuccessionOutcome success(long successorId, String successorName,
                                     long rivalId, String rivalName, long contestUntil) {
        return new SuccessionOutcome(true, successorId, successorName, rivalId, rivalName, contestUntil, null);
    }

    public boolean hasContest() {
        return contestUntil > 0;
    }
}
