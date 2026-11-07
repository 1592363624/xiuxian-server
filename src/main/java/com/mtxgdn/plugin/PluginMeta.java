package com.mtxgdn.plugin;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 在插件主类上标注元数据，无需额外 plugin.json 文件也可识别。
 * <p>
 * 用法示例:
 * <pre>
 * {@literal @}PluginMeta(name = "我的插件", version = "1.0.0", author = "张三")
 * public class MyPlugin implements Plugin {
 *     // ...
 * }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface PluginMeta {
    String name();
    String version() default "1.0.0";
    String author() default "匿名";
    String description() default "";

    /**
     * 硬依赖：这里列出的插件不存在时，本插件<b>不会被加载</b>。
     *
     * <p>选"不加载"而不是"加载了但功能半残"，是因为半残状态极难排查：
     * 插件会安静地少掉一部分内容注册，玩家看到的是"某个功能没有"，
     * 而不是"某个插件没起来"——前者根本无从下手。</p>
     *
     * <p>加载顺序由依赖关系决定，与本字段的书写顺序无关。</p>
     */
    String[] depends() default {};

    /**
     * 软依赖：这里列出的插件不存在时，本插件<b>照常加载</b>，由插件自己降级。
     *
     * <p>典型用法是配合 {@code PluginContext.getService(...)} 做能力探测：</p>
     * <pre>
     *   SectApi api = context.getService(SectApi.class);
     *   if (api != null) { // 有宗门插件，启用联动
     *   }
     * </pre>
     *
     * <p>软依赖仍然参与排序：插件存在时，保证它先初始化。</p>
     */
    String[] softDepends() default {};
}
