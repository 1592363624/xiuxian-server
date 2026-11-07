package com.mtxgdn.plugin;

import java.util.Collections;
import java.util.List;

/**
 * 插件元数据，由 plugin.json 或类上的 @PluginMeta 注解解析而来。
 */
public final class PluginInfo {
    private final String name;
    private final String version;
    private final String author;
    private final String description;
    private final String mainClass;
    private final List<String> depends;
    private final List<String> softDepends;

    /** 兼容旧调用方：不带依赖声明等价于"什么都需要、什么都不会先加载"。 */
    public PluginInfo(String name, String version, String author, String description, String mainClass) {
        this(name, version, author, description, mainClass,
                Collections.emptyList(), Collections.emptyList());
    }

    public PluginInfo(String name, String version, String author, String description, String mainClass,
                      List<String> depends, List<String> softDepends) {
        this.name = name;
        this.version = version;
        this.author = author;
        this.description = description;
        this.mainClass = mainClass;
        this.depends = depends == null ? Collections.emptyList() : List.copyOf(depends);
        this.softDepends = softDepends == null ? Collections.emptyList() : List.copyOf(softDepends);
    }

    public String getName() { return name; }
    public String getVersion() { return version; }
    public String getAuthor() { return author; }
    public String getDescription() { return description; }
    public String getMainClass() { return mainClass; }

    /** 硬依赖插件名（缺失则不加载）。 */
    public List<String> getDepends() { return depends; }

    /** 软依赖插件名（缺失则降级，但存在时保证先初始化）。 */
    public List<String> getSoftDepends() { return softDepends; }

    @Override
    public String toString() {
        String base = name + " v" + version + " (作者: " + author + ")";
        if (depends.isEmpty() && softDepends.isEmpty()) return base;
        return base + " [依赖: " + depends + ", 软依赖: " + softDepends + "]";
    }
}
