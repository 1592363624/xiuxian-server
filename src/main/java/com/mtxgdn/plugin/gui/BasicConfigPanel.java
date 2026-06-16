package com.mtxgdn.plugin.gui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.io.File;

/**
 * 基础配置选项卡面板（美化版）。
 * <p>
 * 使用 Theme 主题系统：深紫 + 金色 + 米色，卡片式分组布局。
 */
final class BasicConfigPanel extends JPanel {

    private final PluginConfig config;

    // ====== 输入控件 ======
    private final JTextField pluginNameField = new JTextField(30);
    private final JTextField versionField = new JTextField(15);
    private final JTextField authorField = new JTextField(20);
    private final JTextArea descriptionArea = new JTextArea(4, 30);

    private final JTextField groupIdField = new JTextField(30);
    private final JTextField artifactIdField = new JTextField(30);
    private final JTextField mainClassField = new JTextField(25);

    private final JTextField outputDirField = new JTextField(40);

    private final JCheckBox includeCommandBox = new JCheckBox("注册示例命令  /你好  （问候 + 赠送灵石）");
    private final JCheckBox includeItemBox = new JCheckBox("注册示例物品  （演示物品注册与使用）");
    private final JCheckBox includeEventBox = new JCheckBox("注册事件触发器  （需配置下方触发器）");
    private final JCheckBox includeSecretRealmBox = new JCheckBox("注册示例秘境  （演示秘境系统）");

    BasicConfigPanel(PluginConfig config) {
        this.config = config;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        setBackground(Theme.BG_PRIMARY);

        // 所有子卡片
        add(buildInfoCard());
        add(Box.createVerticalStrut(14));
        add(buildPackageCard());
        add(Box.createVerticalStrut(14));
        add(buildOutputCard());
        add(Box.createVerticalStrut(14));
        add(buildFeatureCard());
        add(Box.createVerticalGlue());

        loadFromConfig();
    }

    /** 将 UI 输入应用到 config 对象，返回 false 表示验证失败。 */
    boolean applyToConfig() {
        String name = pluginNameField.getText().trim();
        String groupId = groupIdField.getText().trim();
        String artifactId = artifactIdField.getText().trim();
        String mainClass = mainClassField.getText().trim();
        String output = outputDirField.getText().trim();

        if (name.isEmpty()) return error("插件名称不能为空");
        if (groupId.isEmpty()) return error("GroupId 不能为空");
        if (artifactId.isEmpty()) return error("ArtifactId 不能为空");
        if (mainClass.isEmpty()) return error("主类名不能为空");
        if (output.isEmpty()) return error("输出目录不能为空");
        if (!isValidJavaIdentifier(mainClass)) return error("主类名 '" + mainClass + "' 不是合法的 Java 标识符");

        config.setPluginName(name);
        config.setVersion(versionField.getText().trim());
        config.setAuthor(authorField.getText().trim());
        config.setDescription(descriptionArea.getText().trim());
        config.setGroupId(groupId);
        config.setArtifactId(artifactId);
        config.setMainClass(mainClass);
        config.setOutputDir(output);
        config.setIncludeCommand(includeCommandBox.isSelected());
        config.setIncludeItem(includeItemBox.isSelected());
        config.setIncludeEvent(includeEventBox.isSelected());
        config.setIncludeSecretRealm(includeSecretRealmBox.isSelected());
        return true;
    }

    /** 从 config 对象中加载值到 UI 控件。 */
    void loadFromConfig() {
        pluginNameField.setText(config.getPluginName());
        versionField.setText(config.getVersion());
        authorField.setText(config.getAuthor());
        descriptionArea.setText(config.getDescription());
        groupIdField.setText(config.getGroupId());
        artifactIdField.setText(config.getArtifactId());
        mainClassField.setText(config.getMainClass());
        outputDirField.setText(config.getOutputDir());
        includeCommandBox.setSelected(config.isIncludeCommand());
        includeItemBox.setSelected(config.isIncludeItem());
        includeEventBox.setSelected(config.isIncludeEvent());
        includeSecretRealmBox.setSelected(config.isIncludeSecretRealm());
    }

    // ==================== 卡片构建 ====================

    private JPanel buildInfoCard() {
        JPanel card = buildCard("📋 插件基本信息");

        // 使用 GridBag 布局，左标签右输入
        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(Theme.BG_SECONDARY);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 4, 6, 10);
        c.anchor = GridBagConstraints.WEST;

        // 插件名称
        c.gridx = 0; c.gridy = 0; c.weightx = 0; c.fill = GridBagConstraints.NONE;
        form.add(Theme.label("插件名称"), c);
        c.gridx = 1; c.weightx = 1.0; c.fill = GridBagConstraints.HORIZONTAL;
        Theme.styleInput(pluginNameField);
        form.add(pluginNameField, c);

        // 版本 + 作者（一行两列）
        c.gridx = 0; c.gridy = 1; c.weightx = 0; c.fill = GridBagConstraints.NONE;
        form.add(Theme.label("版本号"), c);
        c.gridx = 1; c.weightx = 1.0; c.fill = GridBagConstraints.HORIZONTAL;
        Theme.styleInput(versionField);
        form.add(versionField, c);

        c.gridx = 0; c.gridy = 2; c.weightx = 0; c.fill = GridBagConstraints.NONE;
        form.add(Theme.label("作者"), c);
        c.gridx = 1; c.weightx = 1.0; c.fill = GridBagConstraints.HORIZONTAL;
        Theme.styleInput(authorField);
        form.add(authorField, c);

        // 描述（多行）
        c.gridx = 0; c.gridy = 3; c.weightx = 0; c.fill = GridBagConstraints.NONE;
        c.anchor = GridBagConstraints.NORTHEAST;
        form.add(Theme.label("描述"), c);
        c.gridx = 1; c.weightx = 1.0; c.fill = GridBagConstraints.BOTH;
        c.anchor = GridBagConstraints.WEST;
        Theme.styleInput(descriptionArea);
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        JScrollPane sp = new JScrollPane(descriptionArea);
        sp.setBackground(Theme.BG_SECONDARY);
        sp.setBorder(BorderFactory.createEmptyBorder());
        form.add(sp, c);

        card.add(form, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildPackageCard() {
        JPanel card = buildCard("📦 Java 包信息（Maven）");

        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(Theme.BG_SECONDARY);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 4, 6, 10);
        c.anchor = GridBagConstraints.WEST;

        c.gridx = 0; c.gridy = 0; c.weightx = 0; c.fill = GridBagConstraints.NONE;
        form.add(Theme.label("GroupId"), c);
        c.gridx = 1; c.weightx = 1.0; c.fill = GridBagConstraints.HORIZONTAL;
        Theme.styleInput(groupIdField);
        form.add(groupIdField, c);

        c.gridx = 0; c.gridy = 1; c.weightx = 0; c.fill = GridBagConstraints.NONE;
        form.add(Theme.label("ArtifactId"), c);
        c.gridx = 1; c.weightx = 1.0; c.fill = GridBagConstraints.HORIZONTAL;
        Theme.styleInput(artifactIdField);
        form.add(artifactIdField, c);

        c.gridx = 0; c.gridy = 2; c.weightx = 0; c.fill = GridBagConstraints.NONE;
        form.add(Theme.label("主类名"), c);
        c.gridx = 1; c.weightx = 1.0; c.fill = GridBagConstraints.HORIZONTAL;
        Theme.styleInput(mainClassField);
        form.add(mainClassField, c);

        // 提示
        c.gridx = 0; c.gridy = 3; c.gridwidth = 2;
        JLabel hint = Theme.hintLabel("💡 包名 = GroupId.ArtifactId（例如 com.example.my-plugin）");
        hint.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        form.add(hint, c);

        card.add(form, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildOutputCard() {
        JPanel card = buildCard("📂 输出目录");

        JPanel box = new JPanel(new BorderLayout(10, 5));
        box.setBackground(Theme.BG_SECONDARY);
        box.add(Theme.label("目标路径:"), BorderLayout.WEST);
        Theme.styleInput(outputDirField);
        box.add(outputDirField, BorderLayout.CENTER);

        JButton browse = new JButton("浏览...");
        Theme.styleButton(browse);
        browse.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            fc.setDialogTitle("选择输出目录");
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                outputDirField.setText(fc.getSelectedFile().getAbsolutePath());
            }
        });
        box.add(browse, BorderLayout.EAST);
        card.add(box, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildFeatureCard() {
        JPanel card = buildCard("🎛 功能模块开关（勾选后将生成对应代码）");

        JPanel box = new JPanel(new GridLayout(4, 1, 8, 8));
        box.setBackground(Theme.BG_SECONDARY);

        Theme.styleCheckBox(includeCommandBox);
        Theme.styleCheckBox(includeItemBox);
        Theme.styleCheckBox(includeEventBox);
        Theme.styleCheckBox(includeSecretRealmBox);

        box.add(includeCommandBox);
        box.add(includeItemBox);
        box.add(includeEventBox);
        box.add(includeSecretRealmBox);

        card.add(box, BorderLayout.CENTER);
        return card;
    }

    // ==================== 辅助方法 ====================

    private JPanel buildCard(String title) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(Theme.BG_SECONDARY);
        card.setBorder(Theme.cardBorder(title));
        return card;
    }

    private static boolean isValidJavaIdentifier(String s) {
        if (s == null || s.isEmpty()) return false;
        if (!Character.isJavaIdentifierStart(s.charAt(0))) return false;
        for (int i = 1; i < s.length(); i++) {
            if (!Character.isJavaIdentifierPart(s.charAt(i))) return false;
        }
        return true;
    }

    private boolean error(String msg) {
        JOptionPane.showMessageDialog(this, msg, "⚠ 输入有误", JOptionPane.WARNING_MESSAGE);
        return false;
    }
}
