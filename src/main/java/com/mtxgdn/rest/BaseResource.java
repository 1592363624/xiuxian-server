package com.mtxgdn.rest;

import com.mtxgdn.common.service.ServiceLookup;
import com.mtxgdn.common.service.ServiceRegistry;

/**
 * REST 资源的公共基类：为子类提供<b>服务查询面</b>。
 *
 * <p><b>为什么需要基类</b>：资源由 Jersey 通过无参构造反射实例化
 * （{@code new ResourceConfig().packages("com.mtxgdn.rest")}），没法声明构造参数；
 * 测试里也大量 {@code new AdminXxxResource()} 直接调方法。所以在"资源创建"这个
 * 框架边界取一次当前容器，子类用 {@code services.getXxxService()} 即可，
 * 不再散落 {@code ServiceRegistry.getXxxService()}。</p>
 *
 * <p>容器在资源创建时一定已经就绪（{@code Main} 先建容器再起 Jersey），
 * 因此这里拿到的是启动流程装好的那一套；测试里则是 {@code TestBase} 每个测试类
 * 换出来的新容器。</p>
 */
public abstract class BaseResource {

    /** 本资源使用的服务查询面（创建时绑定当前容器）。 */
    protected final ServiceLookup services = ServiceRegistry.container();
}
