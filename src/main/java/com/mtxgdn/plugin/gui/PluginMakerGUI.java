package com.mtxgdn.plugin.gui;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 插件制作工具主窗口 —— 现代化简洁版。
 * <p>
 * 结构：顶部标题栏 / 中部选项卡（基础配置 / 触发器 / 预览）+ 右侧日志 / 底部操作栏。
 * 每个选项卡内部都包含可滚动区域，避免内容超出屏幕被裁切。
 */
public class PluginMakerGUI extends JFrame {

    private final PluginConfig config = new PluginConfig();
    private BasicConfigPanel basicPanel;
    private TriggerPanel triggerPanel;
    private JTextArea logArea;

    public static void launch() {
        // 必须在任何 I/O 操作和 Swing 初始化前设置，强制 UTF-8 编码，避免中文乱码
        System.setProperty("file.encoding", "UTF-8");
        System.setProperty("sun.jnu.encoding", "UTF-8");
        SwingUtilities.invokeLater(() -> {
            Theme.installLookAndFeel();
            PluginMakerGUI gui = new PluginMakerGUI();
            gui.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            // 确保顶层容器更新所有子组件字体（有些UI代理
            SwingUtilities.updateComponentTreeUI(gui);
            gui.setVisible(true);
        });
    }

    private PluginMakerGUI() {
        setTitle("🛠 插件制作工具 · Plugin Maker");
        setMinimumSize(new Dimension(880, 600));
        setPreferredSize(new Dimension(1100, 760));
        setSize(1100, 760);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setBackground(Theme.BG_PRIMARY);
        root.setBorder(BorderFactory.createEmptyBorder(12, 14, 14, 14));

        root.add(buildHeader(),   BorderLayout.NORTH);
        root.add(buildBody(),     BorderLayout.CENTER);
        root.add(buildFooter(),   BorderLayout.SOUTH);

        add(root);
        log("欢迎使用 Plugin Maker");
        log("请在【基础配置】中填写信息，在【事件触发器】中配置触发器，然后点击【生成插件】。");
    }

    // ================ 顶部 ================

    private JComponent buildHeader() {
        JPanel p = new JPanel(new BorderLayout(16, 4));
        p.setBackground(Theme.BG_PRIMARY);
        p.setBorder(BorderFactory.createEmptyBorder(0, 4, 8, 4));

        JLabel title = Theme.titleLabel("🛠 插件制作工具", 20f);
        title.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0));

        JLabel subtitle = Theme.hintLabel("基于模板快速生成 Server 插件项目");
        subtitle.setBorder(BorderFactory.createEmptyBorder(4, 2, 0, 0));

        JPanel left = new JPanel();
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.setBackground(Theme.BG_PRIMARY);
        left.add(title);
        left.add(subtitle);

        JLabel right = Theme.hintLabel("Plugin Maker · v1.4.1-alpha1");
        right.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 4));

        p.add(left, BorderLayout.WEST);
        p.add(right, BorderLayout.EAST);
        return p;
    }

    // ================ 中部：选项卡 + 日志 ================

    private JComponent buildBody() {
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        split.setBorder(BorderFactory.createEmptyBorder());
        split.setBackground(Theme.BG_PRIMARY);
        split.setLeftComponent(buildTabbedPane());
        split.setRightComponent(buildLogPanel());
        split.setResizeWeight(0.72);
        split.setDividerSize(3);
        return split;
    }

    private JComponent buildTabbedPane() {
        JTabbedPane tabs = new JTabbedPane(JTabbedPane.TOP);
        Theme.styleTabbedPane(tabs);
        tabs.setFont(Theme.fontBold(13f));

        basicPanel = new BasicConfigPanel(config);
        triggerPanel = new TriggerPanel(config);

        JScrollPane basicScroll   = wrapInScrollPane(basicPanel);
        JScrollPane triggerScroll = wrapInScrollPane(triggerPanel);

        tabs.addTab("  📋 基础配置  ", basicScroll);
        tabs.addTab("  ⚡ 事件触发器  ", triggerScroll);
        tabs.addTab("  ✅ 生成 & 预览  ", buildPreviewTab());

        return tabs;
    }

    private JScrollPane wrapInScrollPane(JPanel panel) {
        JScrollPane sp = new JScrollPane(panel,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.getVerticalScrollBar().setUnitIncrement(16);
        sp.getHorizontalScrollBar().setUnitIncrement(16);
        sp.setBackground(Theme.BG_PRIMARY);
        sp.getViewport().setBackground(Theme.BG_PRIMARY);
        return sp;
    }

    private JComponent buildPreviewTab() {
        JPanel p = new JPanel(new BorderLayout(12, 12));
        p.setBackground(Theme.BG_PRIMARY);
        p.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));

        // 使用说明
        JPanel hintCard = new JPanel(new BorderLayout(8, 8));
        hintCard.setBackground(Theme.BG_SECONDARY);
        hintCard.setBorder(Theme.cardBorder("📖 使用说明"));
        JTextArea hint = new JTextArea(
                "1. 在【基础配置】中填写插件名、版本、GroupId、ArtifactId、主类名、输出目录，以及是否包含示例命令/物品/事件/秘境。\n" +
                "2. 在【事件触发器】中添加触发器（事件类型、触发条件、响应动作、Java 代码）。\n" +
                "3. 完成后点击底部【💾 保存配置】把当前配置存为 JSON，下次可通过【📂 加载配置】恢复。\n" +
                "4. 点击【🚀 生成插件】即可在输出目录下生成完整的 Maven 项目。\n" +
                "5. 在生成目录中执行  mvn clean package  即可得到 JAR 包，放入服务端 ./plugins 目录即可加载。"
        );
        hint.setLineWrap(true);
        hint.setWrapStyleWord(true);
        hint.setEditable(false);
        hint.setFont(Theme.fontRegular(12.5f));
        hint.setBackground(Theme.BG_SECONDARY);
        hint.setForeground(Theme.FG_TEXT);
        hintCard.add(hint, BorderLayout.CENTER);
        p.add(hintCard, BorderLayout.NORTH);

        // 当前配置预览
        JPanel previewCard = new JPanel(new BorderLayout(8, 8));
        previewCard.setBackground(Theme.BG_SECONDARY);
        previewCard.setBorder(Theme.cardBorder("🧾 当前配置预览"));
        JTextArea preview = new JTextArea(configPreview(), 14, 40);
        preview.setFont(Theme.fontMono(12f));
        preview.setEditable(false);
        preview.setBackground(Theme.BG_SECONDARY);
        preview.setForeground(Theme.FG_TEXT);
        JScrollPane previewScroll = new JScrollPane(preview);
        previewScroll.setBorder(BorderFactory.createEmptyBorder());
        previewScroll.getViewport().setBackground(Theme.BG_SECONDARY);
        previewCard.add(previewScroll, BorderLayout.CENTER);

        JButton refreshBtn = new JButton("🔄 重新读取配置");
        Theme.styleButton(refreshBtn);
        refreshBtn.addActionListener(e -> {
            if (basicPanel != null) basicPanel.applyToConfig();
            preview.setText(configPreview());
        });
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        btnRow.setBackground(Theme.BG_SECONDARY);
        btnRow.add(refreshBtn);
        previewCard.add(btnRow, BorderLayout.SOUTH);

        p.add(previewCard, BorderLayout.CENTER);
        return p;
    }

    private String configPreview() {
        StringBuilder sb = new StringBuilder();
        sb.append("插件名:       ").append(config.getPluginName()).append('\n');
        sb.append("版本:         ").append(config.getVersion()).append('\n');
        sb.append("作者:         ").append(config.getAuthor()).append('\n');
        sb.append("描述:         ").append(config.getDescription()).append('\n');
        sb.append("GroupId:      ").append(config.getGroupId()).append('\n');
        sb.append("ArtifactId:   ").append(config.getArtifactId()).append('\n');
        sb.append("包名:         ").append(config.getPackageName()).append('\n');
        sb.append("主类:         ").append(config.getMainClass()).append('\n');
        sb.append("输出目录:     ").append(config.getOutputDir()).append('\n');
        sb.append("包含命令:     ").append(config.isIncludeCommand()).append('\n');
        sb.append("包含物品:     ").append(config.isIncludeItem()).append('\n');
        sb.append("包含事件:     ").append(config.isIncludeEvent()).append('\n');
        sb.append("包含秘境:     ").append(config.isIncludeSecretRealm()).append('\n');
        sb.append("触发器数量:   ").append(config.getTriggers().size()).append('\n');
        int i = 0;
        for (TriggerConfig t : config.getTriggers()) {
            sb.append("  [").append(++i).append("] ")
                    .append(t.isEnabled() ? "✔" : "○").append(' ')
                    .append(t.getEventType()).append(" → ").append(t.getAction().label)
                    .append(" (").append(t.getDescription()).append(")\n");
        }
        return sb.toString();
    }

    // ================ 日志 ================

    private JComponent buildLogPanel() {
        JPanel logPanel = new JPanel(new BorderLayout(8, 8));
        logPanel.setBackground(Theme.BG_PRIMARY);

        JLabel title = Theme.titleLabel("📜 运行日志", 13f);
        title.setBorder(BorderFactory.createEmptyBorder(0, 2, 4, 0));

        logArea = new JTextArea("", 22, 28);
        logArea.setFont(Theme.fontMono(11.5f));
        logArea.setForeground(new Color(200, 210, 220));
        logArea.setBackground(Theme.BG_CODE);
        logArea.setEditable(false);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        logArea.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

        JScrollPane logScroll = new JScrollPane(logArea,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        logScroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER_COLOR, 1, true));
        logScroll.getViewport().setBackground(Theme.BG_CODE);

        logPanel.add(title, BorderLayout.NORTH);
        logPanel.add(logScroll, BorderLayout.CENTER);
        return logPanel;
    }

    // ================ 底部操作栏 ================

    private JComponent buildFooter() {
        JPanel bar = new JPanel(new BorderLayout(12, 0));
        bar.setBackground(Theme.BG_PRIMARY);
        bar.setBorder(BorderFactory.createEmptyBorder(10, 4, 0, 4));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.setBackground(Theme.BG_PRIMARY);

        JButton saveBtn = new JButton("💾 保存配置");
        Theme.styleButton(saveBtn);
        saveBtn.addActionListener(e -> saveConfig());

        JButton loadBtn = new JButton("📂 加载配置");
        Theme.styleButton(loadBtn);
        loadBtn.addActionListener(e -> loadConfig());

        left.add(saveBtn);
        left.add(loadBtn);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setBackground(Theme.BG_PRIMARY);

        JButton genBtn = new JButton("🚀 生成插件项目");
        Theme.stylePrimaryButton(genBtn);
        genBtn.addActionListener(e -> generatePlugin());

        right.add(genBtn);

        bar.add(left, BorderLayout.WEST);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    // ================ 操作 ================

    private void saveConfig() {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("plugin-config.json"));
        int r = chooser.showSaveDialog(this);
        if (r != JFileChooser.APPROVE_OPTION) return;
        try {
            if (basicPanel != null) basicPanel.applyToConfig();
            if (triggerPanel != null) triggerPanel.applyToConfig();
            config.save(chooser.getSelectedFile());
            log("✔ 配置已保存至 " + chooser.getSelectedFile().getPath());
        } catch (IOException e) {
            log("✖ 保存失败：" + e.getMessage());
            JOptionPane.showMessageDialog(this, "保存失败：" + e.getMessage(),
                    "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadConfig() {
        JFileChooser chooser = new JFileChooser();
        int r = chooser.showOpenDialog(this);
        if (r != JFileChooser.APPROVE_OPTION) return;
        try {
            PluginConfig loaded = PluginConfig.load(chooser.getSelectedFile());
            config.setPluginName(loaded.getPluginName());
            config.setVersion(loaded.getVersion());
            config.setAuthor(loaded.getAuthor());
            config.setDescription(loaded.getDescription());
            config.setArtifactId(loaded.getArtifactId());
            config.setGroupId(loaded.getGroupId());
            config.setMainClass(loaded.getMainClass());
            config.setOutputDir(loaded.getOutputDir());
            config.setIncludeCommand(loaded.isIncludeCommand());
            config.setIncludeItem(loaded.isIncludeItem());
            config.setIncludeEvent(loaded.isIncludeEvent());
            config.setIncludeSecretRealm(loaded.isIncludeSecretRealm());
            config.getTriggers().clear();
            config.getTriggers().addAll(loaded.getTriggers());

            if (basicPanel != null) basicPanel.refreshFromConfig();
            if (triggerPanel != null) triggerPanel.refreshTable();
            log("✔ 已加载配置 " + chooser.getSelectedFile().getPath());
        } catch (IOException e) {
            log("✖ 加载失败：" + e.getMessage());
            JOptionPane.showMessageDialog(this, "加载失败：" + e.getMessage(),
                    "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void generatePlugin() {
        if (basicPanel != null) basicPanel.applyToConfig();
        if (triggerPanel != null) triggerPanel.applyToConfig();

        List<String> errors = validateConfig(config);
        if (!errors.isEmpty()) {
            String msg = "请先修正以下问题：\n\n  · " + String.join("\n  · ", errors);
            JOptionPane.showMessageDialog(this, msg, "⚠ 配置不完整", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            log("▶ 开始生成插件项目（" + config.getPluginName() + " v" + config.getVersion() + "）...");
            CodeGenerator gen = new CodeGenerator(config);
            List<String> files = gen.generateAll();
            log("✔ 生成完成，共 " + files.size() + " 个文件：");
            for (String f : files) log("   - " + f);
            log("  提示：在输出目录中执行  mvn clean package  可编译为 JAR 包");
            log("  将 JAR 放入服务端 ./plugins 目录即可被加载。");

            JOptionPane.showMessageDialog(this,
                    "✔ 项目已生成（共 " + files.size() + " 个文件）\n输出目录：" + config.getOutputDir(),
                    "生成完成", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            log("✖ 生成失败：" + e.getMessage());
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                    "生成失败：" + e.getMessage(),
                    "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static List<String> validateConfig(PluginConfig c) {
        List<String> errors = new ArrayList<>();
        if (c.getPluginName() == null || c.getPluginName().trim().isEmpty()) errors.add("插件名不能为空");
        if (c.getVersion() == null || c.getVersion().trim().isEmpty()) errors.add("版本号不能为空");
        if (c.getGroupId() == null || c.getGroupId().trim().isEmpty()) errors.add("GroupId 不能为空");
        if (c.getArtifactId() == null || c.getArtifactId().trim().isEmpty()) errors.add("ArtifactId 不能为空");
        if (c.getMainClass() == null || c.getMainClass().trim().isEmpty()) errors.add("主类名不能为空");
        if (c.getOutputDir() == null || c.getOutputDir().trim().isEmpty()) errors.add("输出目录不能为空");
        return errors;
    }

    private void log(String msg) {
        if (logArea == null) return;
        SwingUtilities.invokeLater(() -> {
            logArea.append(msg + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }
}
