package com.mtxgdn.plugin.gui;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * 插件制作工具主窗口（美化版）。
 * <p>
 * 采用「修仙主题」：深紫 + 金色 + 米色，搭配 Nimbus Look and Feel。
 * 三大选项卡：基础配置 / 触发器管理 / 预览与生成。
 */
public final class PluginMakerGUI {

    private final PluginConfig config = new PluginConfig();
    private final BasicConfigPanel basicPanel;
    private final TriggerPanel triggerPanel;

    private JFrame frame;
    private JTextArea logArea;
    private JFileChooser fileChooser;

    public PluginMakerGUI() {
        this.basicPanel = new BasicConfigPanel(config);
        this.triggerPanel = new TriggerPanel(config);
    }

    /** 显示 GUI 窗口（阻塞当前线程直到窗口关闭）。 */
    public void show() {
        SwingUtilities.invokeLater(() -> {
            Theme.installLookAndFeel();
            buildFrame();
        });
    }

    private void buildFrame() {
        frame = new JFrame("✨ PluginMaker —— 修仙服务端插件制作工具");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setMinimumSize(new Dimension(1000, 720));
        frame.setSize(1100, 780);
        frame.setLocationRelativeTo(null);

        // 顶部横幅（渐变背景）
        JPanel banner = buildBanner();

        // 选项卡
        JTabbedPane tabs = new JTabbedPane(JTabbedPane.TOP, JTabbedPane.SCROLL_TAB_LAYOUT);
        tabs.setFont(Theme.fontBold(13f));
        tabs.setBackground(Theme.BG_PRIMARY);
        tabs.setForeground(Theme.FG_TEXT);

        JPanel tab1 = new JPanel(new BorderLayout());
        tab1.setBackground(Theme.BG_PRIMARY);
        tab1.add(basicPanel, BorderLayout.CENTER);

        JPanel tab2 = new JPanel(new BorderLayout());
        tab2.setBackground(Theme.BG_PRIMARY);
        tab2.add(triggerPanel, BorderLayout.CENTER);

        JPanel tab3 = buildPreviewPanel();

        tabs.addTab("  📋 ① 基础配置  ", tab1);
        tabs.addTab("  ⚡ ② 触发器管理  ", tab2);
        tabs.addTab("  🚀 ③ 预览与生成  ", tab3);
        // 激活背景色
        for (int i = 0; i < tabs.getTabCount(); i++) {
            tabs.setBackgroundAt(i, Theme.BG_SECONDARY);
            tabs.setForegroundAt(i, Theme.FG_TEXT);
        }

        // 内容区
        JPanel content = new JPanel(new BorderLayout());
        content.add(tabs, BorderLayout.CENTER);

        // 组装
        frame.getContentPane().setLayout(new BorderLayout());
        frame.getContentPane().add(banner, BorderLayout.NORTH);
        frame.getContentPane().add(content, BorderLayout.CENTER);
        frame.getContentPane().add(buildStatusBar(), BorderLayout.SOUTH);

        // 让选项卡背景色生效
        JComponent c = (JComponent) tabs.getComponentAt(0);
        c.setBackground(Theme.BG_PRIMARY);
        c.setOpaque(true);

        frame.setVisible(true);

        fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "配置文件 (*.plugin.json)", "plugin.json", "json"));

        log("就绪。请在「基础配置」中填写插件信息，然后在「触发器管理」中配置事件触发器，最后点击「生成插件项目」。");
    }

    /** 构建顶部横幅（深色渐变背景 + 标题 + 工具栏）。 */
    private JPanel buildBanner() {
        JPanel banner = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g;
                GradientPaint gp = new GradientPaint(
                        0, 0, new Color(60, 40, 90),
                        getWidth(), getHeight(), new Color(110, 80, 150),
                        true);
                g2d.setPaint(gp);
                g2d.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        banner.setBorder(BorderFactory.createEmptyBorder(12, 20, 12, 20));

        // 标题区
        JPanel titlePanel = new JPanel(new BorderLayout(0, 2));
        titlePanel.setOpaque(false);
        JLabel title = Theme.titleLabel("✨ 修仙服务端 · 插件制作工具", 22f);
        title.setForeground(Theme.ACCENT_GOLD);
        JLabel subtitle = Theme.hintLabel("V1.4.1-alpha1  ·  让你用 GUI 轻松创建服务端插件项目");
        subtitle.setForeground(new Color(220, 208, 192));
        titlePanel.add(title, BorderLayout.CENTER);
        titlePanel.add(subtitle, BorderLayout.SOUTH);

        // 工具栏
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 5));
        toolbar.setOpaque(false);

        JButton saveBtn = buildNiceButton("💾 保存配置");
        saveBtn.addActionListener(e -> saveConfig());
        toolbar.add(saveBtn);

        JButton loadBtn = buildNiceButton("📂 加载配置");
        loadBtn.addActionListener(e -> loadConfig());
        toolbar.add(loadBtn);

        JButton refreshBtn = buildNiceButton("🔄 刷新预览");
        refreshBtn.addActionListener(e -> updatePreview());
        toolbar.add(refreshBtn);

        JButton genBtn = buildNicePrimaryButton("🚀 生成插件项目");
        genBtn.addActionListener(e -> generate());
        toolbar.add(genBtn);

        banner.add(titlePanel, BorderLayout.WEST);
        banner.add(toolbar, BorderLayout.EAST);
        return banner;
    }

    /** 构建预览与生成面板。 */
    private JPanel buildPreviewPanel() {
        JPanel panel = new JPanel(new BorderLayout(15, 15));
        panel.setBackground(Theme.BG_PRIMARY);
        panel.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));

        // 标题
        JLabel title = Theme.titleLabel("📝 配置摘要与生成预览", 16f);
        title.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        panel.add(title, BorderLayout.NORTH);

        // 双列布局：左摘要 + 右日志
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        split.setBackground(Theme.BG_PRIMARY);
        split.setResizeWeight(0.5);
        split.setDividerSize(4);
        split.setBorder(null);

        // 左：摘要卡片
        JPanel summary = new JPanel(new BorderLayout(10, 10));
        summary.setBackground(Theme.BG_SECONDARY);
        summary.setBorder(Theme.cardBorder("📊 配置摘要"));

        logArea = new JTextArea(20, 55);
        logArea.setEditable(false);
        logArea.setFont(Theme.fontMono(13f));
        logArea.setBackground(Theme.BG_PRIMARY);
        logArea.setForeground(Theme.FG_TEXT);
        logArea.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        logArea.setOpaque(true);
        JScrollPane sp = new JScrollPane(logArea);
        sp.setBackground(Theme.BG_SECONDARY);
        sp.getViewport().setBackground(Theme.BG_PRIMARY);
        sp.setBorder(BorderFactory.createEmptyBorder());
        summary.add(sp, BorderLayout.CENTER);
        split.add(summary);

        // 右：操作说明卡片
        JPanel help = new JPanel(new BorderLayout(10, 10));
        help.setBackground(Theme.BG_SECONDARY);
        help.setBorder(Theme.cardBorder("📖 使用说明"));

        String tips =
                "【第一步】基础配置\n" +
                "  · 填写插件名称、版本、作者、描述\n" +
                "  · 设置 Maven GroupId / ArtifactId / 主类名\n" +
                "  · 选择输出目录\n" +
                "  · 勾选需要生成的功能模块\n\n" +
                "【第二步】触发器管理（可选）\n" +
                "  · 点「新增」创建触发器\n" +
                "  · 选择事件类型、条件、动作\n" +
                "  · 可随时编辑、删除、重排序\n\n" +
                "【第三步】预览与生成\n" +
                "  · 检查下方摘要是否正确\n" +
                "  · 点「生成插件项目」输出到指定目录\n" +
                "  · 将项目编译后放入 ./plugins/ 目录\n\n" +
                "【可选】保存 / 加载配置\n" +
                "  · 将当前配置保存为 .plugin.json\n" +
                "  · 下次启动可直接加载，无需重填\n";
        JTextArea tipsArea = new JTextArea(tips, 25, 40);
        tipsArea.setEditable(false);
        tipsArea.setFont(Theme.fontMono(12.5f));
        tipsArea.setBackground(Theme.BG_SECONDARY);
        tipsArea.setForeground(Theme.FG_LABEL);
        tipsArea.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        tipsArea.setOpaque(true);
        help.add(tipsArea, BorderLayout.CENTER);

        split.add(help);
        panel.add(split, BorderLayout.CENTER);

        // 下方大按钮
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));
        bottom.setBackground(Theme.BG_PRIMARY);
        JButton genBtn = new JButton("🚀 生成插件项目");
        Theme.stylePrimaryButton(genBtn);
        genBtn.setBorder(BorderFactory.createEmptyBorder(14, 40, 14, 40));
        genBtn.setFont(Theme.fontBold(15f));
        genBtn.addActionListener(e -> generate());
        bottom.add(genBtn);
        panel.add(bottom, BorderLayout.SOUTH);

        updatePreview();
        return panel;
    }

    private void updatePreview() {
        basicPanel.applyToConfig();
        triggerPanel.applyToConfig();
        StringBuilder sb = new StringBuilder();
        sb.append("━━━━━━ 插件基本信息 ━━━━━━\n");
        sb.append("插件名称:   ").append(config.getPluginName()).append("\n");
        sb.append("版本号:     ").append(config.getVersion()).append("\n");
        sb.append("作者:       ").append(config.getAuthor()).append("\n");
        sb.append("描述:       ").append(config.getDescription()).append("\n");
        sb.append("Java 包名:  ").append(config.getPackageName()).append("\n");
        sb.append("主类:       ").append(config.getMainClass()).append("\n");
        sb.append("输出目录:   ").append(config.getOutputDir()).append("\n\n");
        sb.append("━━━━━━ 功能模块开关 ━━━━━━\n");
        sb.append("  [").append(flag(config.isIncludeCommand())).append("] 示例命令  /你好\n");
        sb.append("  [").append(flag(config.isIncludeItem())).append("] 示例物品\n");
        sb.append("  [").append(flag(config.isIncludeEvent())).append("] 事件系统\n");
        sb.append("  [").append(flag(config.isIncludeSecretRealm())).append("] 示例秘境\n\n");
        sb.append("━━━━━━ 事件触发器（").append(config.getTriggers().size()).append("） ━━━━━━\n");
        if (config.getTriggers().isEmpty()) {
            sb.append("  （暂无触发器）\n");
        } else {
            for (int i = 0; i < config.getTriggers().size(); i++) {
                TriggerConfig t = config.getTriggers().get(i);
                String typeName = t.getEventType() == com.mtxgdn.plugin.event.PluginEvent.Type.CUSTOM
                        ? "自定义[" + t.getCustomKey() + "]"
                        : t.getEventType().name();
                sb.append(String.format("  %d. %s  %s → %s (%s)%n",
                        i + 1,
                        t.isEnabled() ? "🟢" : "⚪",
                        typeName,
                        t.getAction().label,
                        t.getDescription()));
                if (!t.getCondition().isEmpty()) {
                    sb.append("        条件: ").append(t.getCondition()).append("\n");
                }
            }
        }
        sb.append("\n━━━━━━ 预计将生成以下文件 ━━━━━━\n");
        sb.append("  pom.xml\n");
        sb.append("  plugin.json\n");
        sb.append("  src/main/java/").append(config.getPackagePath()).append("/").append(config.getMainClass()).append(".java\n");
        if (config.isIncludeCommand()) {
            sb.append("  src/main/java/").append(config.getPackagePath()).append("/command/HelloCommand.java\n");
        }
        if (config.isIncludeItem()) {
            sb.append("  src/main/java/").append(config.getPackagePath()).append("/item/DemoItem.java\n");
        }
        if (config.isIncludeEvent() || !config.getTriggers().isEmpty()) {
            sb.append("  src/main/java/").append(config.getPackagePath()).append("/").append(config.getMainClass()).append("Triggers.java\n");
        }
        if (config.isIncludeSecretRealm()) {
            sb.append("  src/main/java/").append(config.getPackagePath()).append("/").append(config.getMainClass()).append("Realm.java\n");
        }
        sb.append("\n━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        logArea.setText(sb.toString());
        logArea.setCaretPosition(0);
    }

    // ==================== 工具栏操作 ====================

    private void saveConfig() {
        basicPanel.applyToConfig();
        triggerPanel.applyToConfig();
        fileChooser.setSelectedFile(new File(config.getPluginName() + ".plugin.json"));
        if (fileChooser.showSaveDialog(frame) == JFileChooser.APPROVE_OPTION) {
            try {
                File f = fileChooser.getSelectedFile();
                if (!f.getName().endsWith(".json")) f = new File(f.getAbsolutePath() + ".json");
                config.save(f);
                log("✅ 配置已保存到 " + f.getAbsolutePath());
                JOptionPane.showMessageDialog(frame, "配置已保存到 " + f.getName(), "完成", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException ex) {
                log("❌ 保存失败: " + ex.getMessage());
                JOptionPane.showMessageDialog(frame, "保存失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void loadConfig() {
        if (fileChooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            try {
                PluginConfig loaded = PluginConfig.load(fileChooser.getSelectedFile());
                copyConfig(loaded, config);
                basicPanel.loadFromConfig();
                triggerPanel.refreshTable();
                updatePreview();
                log("✅ 配置已从 " + fileChooser.getSelectedFile().getName() + " 加载");
                JOptionPane.showMessageDialog(frame, "配置已加载", "完成", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException ex) {
                log("❌ 加载失败: " + ex.getMessage());
                JOptionPane.showMessageDialog(frame, "加载失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void generate() {
        if (!basicPanel.applyToConfig()) return;
        triggerPanel.applyToConfig();
        try {
            log("🚀 开始生成插件项目...");
            log("  输出目录: " + new File(config.getOutputDir()).getAbsolutePath());
            List<String> files = new CodeGenerator(config).generateAll();
            log("✅ 生成完成！共 " + files.size() + " 个文件：");
            for (String f : files) log("   - " + f);
            log("");
            log("下一步:");
            log("  1) 进入目录: cd " + config.getOutputDir());
            log("  2) 将服务端 jar 安装到本地 Maven 仓库（详见 pom.xml）");
            log("  3) 执行: mvn package");
            log("  4) 将 target/" + config.getArtifactId() + "-" + config.getVersion() + ".jar 放入服务端 ./plugins/");
            log("  5) 重启服务端，插件将被自动加载 ✨");
            JOptionPane.showMessageDialog(frame,
                    "生成完成！共 " + files.size() + " 个文件。\n输出目录: " + new File(config.getOutputDir()).getAbsolutePath(),
                    "插件项目生成成功",
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) {
            log("❌ 生成失败: " + ex.getMessage());
            ex.printStackTrace();
            JOptionPane.showMessageDialog(frame, "生成失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ==================== 辅助 ====================

    private JPanel buildStatusBar() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(Theme.BG_SECONDARY);
        p.setForeground(Theme.FG_LABEL);
        p.setBorder(BorderFactory.createEmptyBorder(6, 16, 6, 16));
        JLabel status = new JLabel("PluginMaker · 事件系统 V1.4.1-alpha1  ·  设计：修仙主题");
        status.setFont(Theme.fontPlain(12f));
        status.setForeground(Theme.FG_HINT);
        p.add(status, BorderLayout.WEST);
        return p;
    }

    /** 创建普通按钮（紫色 + 悬停变金）。 */
    private JButton buildNiceButton(String text) {
        JButton b = new JButton(text);
        Theme.styleButton(b);
        return b;
    }

    /** 创建主按钮（金色大按钮，用于主要操作）。 */
    private JButton buildNicePrimaryButton(String text) {
        JButton b = new JButton(text);
        Theme.stylePrimaryButton(b);
        return b;
    }

    private void log(String msg) {
        if (logArea != null) {
            logArea.append(msg + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        }
        System.out.println("[PluginMaker] " + msg);
    }

    private static String flag(boolean b) { return b ? "✓" : " "; }

    private static void copyConfig(PluginConfig from, PluginConfig to) {
        to.setPluginName(from.getPluginName());
        to.setVersion(from.getVersion());
        to.setAuthor(from.getAuthor());
        to.setDescription(from.getDescription());
        to.setGroupId(from.getGroupId());
        to.setArtifactId(from.getArtifactId());
        to.setMainClass(from.getMainClass());
        to.setOutputDir(from.getOutputDir());
        to.setIncludeCommand(from.isIncludeCommand());
        to.setIncludeItem(from.isIncludeItem());
        to.setIncludeEvent(from.isIncludeEvent());
        to.setIncludeSecretRealm(from.isIncludeSecretRealm());
        to.getTriggers().clear();
        to.getTriggers().addAll(from.getTriggers());
    }
}
