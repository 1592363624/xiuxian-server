package com.mtxgdn.common.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link ServiceContainer} 与 {@link ServiceRegistry} 门面的关系测试。
 *
 * <p>把"构造与接线"从静态字段搬进可实例化的容器，好处必须是<b>看得见</b>的，
 * 否则这次重构就只是换了个地方写同样的代码。这里钉住三点：</p>
 * <ol>
 *   <li>一个容器内，同一服务永远是同一个实例（有状态服务的前提）；</li>
 *   <li>换容器 = 换一整套实例（测试隔离的基础）；</li>
 *   <li>门面跟着容器走（否则老代码看到的是另一套状态）。</li>
 * </ol>
 */
class ServiceContainerTest {

    @AfterEach
    void restore() {
        ServiceRegistry.reset();
    }

    @Test
    void 同一个容器内服务实例唯一() {
        ServiceContainer c = new ServiceContainer();
        assertSame(c.getCombatService(), c.getCombatService(),
                "CombatService 持有 pendingChallenges，重复创建会让决斗挑战凭空消失");
        assertSame(c.getPlayerService(), c.getPlayerService());
        assertSame(c.getMapService(), c.getMapService(),
                "MapService 有多份实例缓存，重复创建会导致缓存与库不一致");
        c.close();
    }

    @Test
    void 换容器等于换一整套实例() {
        ServiceContainer a = new ServiceContainer();
        ServiceContainer b = new ServiceContainer();
        assertNotSame(a.getCombatService(), b.getCombatService(),
                "两个容器必须各自持有实例，否则测试之间无法隔离");
        assertNotSame(a.getPlayerService(), b.getPlayerService());
        a.close();
        b.close();
    }

    @Test
    void 门面始终指向当前容器() {
        ServiceContainer fresh = new ServiceContainer();
        ServiceRegistry.install(fresh);
        assertSame(fresh.getPlayerService(), ServiceRegistry.getPlayerService(),
                "老代码走静态门面，必须看到当前容器里的那一套，否则新旧路径各拿一份状态");
        assertSame(fresh, ServiceRegistry.container());
        fresh.close();
    }

    @Test
    void reset之后门面看到的是新实例() {
        Object before = ServiceRegistry.getCombatService();
        ServiceRegistry.reset();
        assertNotSame(before, ServiceRegistry.getCombatService(),
                "reset 的用途就是让每个测试类拿到干净实例");
    }

    @Test
    void 不允许把容器换成null() {
        assertThrows(IllegalArgumentException.class, () -> ServiceRegistry.install(null));
    }
}
