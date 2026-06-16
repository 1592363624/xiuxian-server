package com.mtxgdn.plugin.gui;

import javax.swing.*;
import java.awt.*;
import java.io.File;

/**
 * 基础配置选项卡面板 —— 简洁现代化版。
 * <p>
 * 分 3 个卡片：插件信息 / 包信息 / 输出目录 & 功能开关。
 */
final class BasicConfigPanel extends JPanel {

    private final PluginConfig config;

    // ====== 输入控件 ======
    private final JTextField pluginNameField = new JTextField(28);
    private final JTextField versionField    = new JTextField(12);
    private final JTextField authorField     = new JTextField(18);
    private final JTextArea  descriptionArea = new JTextArea(3, 28);

    private final JTextField artifactIdField = new JTextField(22);
    private final JTextField groupIdField    = new JTextField(22);
    private final JTextField mainClassField  = new JTextField(22);

    private final JTextField outputDirField  = new JTextField(28);

    // 功能开关（对应 PluginConfig 的四个 include 字段）
    private final JCheckBox includeCommandBox     = new JCheckBox("包含示例命令（/你好）");
    private final JCheckBox includeItemBox        = new JCheckBox("包含示例物品（可装备的灵器）");
    private final JCheckBox includeEventBox       = new JCheckBox("包含事件监听器（监听消息、登录）");
    private final JCheckBox includeSecretRealmBox = new JCheckBox("包含示例秘境（可进入的副本）");

    BasicConfigPanel(PluginConfig config) {
        this.config = config;

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(Theme.BG_PRIMARY);
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        // 样式
        Theme.styleInput(pluginNameField);
        Theme.styleInput(versionField);
        Theme.styleInput(authorField);
        Theme.styleInput(descriptionArea);
        Theme.styleInput(artifactIdField);
        Theme.styleInput(groupIdField);
        Theme.styleInput(mainClassField);
        Theme.styleInput(outputDirField);
        Theme.styleCheckBox(includeCommandBox);
        Theme.styleCheckBox(includeItemBox);
        Theme.styleCheckBox(includeEventBox);
        Theme.styleCheckBox(includeSecretRealmBox);

        // 组装
        add(buildPluginInfoCard());
        add(Box.createVerticalStrut(12));
        add(buildPackageInfoCard());
        add(Box.createVerticalStrut(12));
        add(buildOutputCard());
        add(Box.createVerticalStrut(12));
        add(buildFeatureSwitchesCard());
        add(Box.createVerticalGlue());

        refreshFromConfig();
    }

    /** 把界面输入同步到 config。 */
    void applyToConfig() {
        config.setPluginName(pluginNameField.getText().trim());
        config.setVersion(versionField.getText().trim());
        config.setAuthor(authorField.getText().trim());
        config.setDescription(descriptionArea.getText().trim());
        config.setArtifactId(artifactIdField.getText().trim());
        config.setGroupId(groupIdField.getText().trim());
        config.setMainClass(mainClassField.getText().trim());
        config.setOutputDir(outputDirField.getText().trim());
        config.setIncludeCommand(includeCommandBox.isSelected());
        config.setIncludeItem(includeItemBox.isSelected());
        config.setIncludeEvent(includeEventBox.isSelected());
        config.setIncludeSecretRealm(includeSecretRealmBox.isSelected());
    }

    /** 从 config 重新填充界面。 */
    void refreshFromConfig() {
        pluginNameField.setText(config.getPluginName());
        versionField.setText(config.getVersion());
        authorField.setText(config.getAuthor());
        descriptionArea.setText(config.getDescription());
        artifactIdField.setText(config.getArtifactId());
        groupIdField.setText(config.getGroupId());
        mainClassField.setText(config.getMainClass());
        outputDirField.setText(config.getOutputDir());
        includeCommandBox.setSelected(config.isIncludeCommand());
        includeItemBox.setSelected(config.isIncludeItem());
        includeEventBox.setSelected(config.isIncludeEvent());
        includeSecretRealmBox.setSelected(config.isIncludeSecretRealm());
    }

    // ==================== 卡片 ====================

    private JPanel makeCard(String title) {
        JPanel card = new JPanel(new BorderLayout(0, 10));
        card.setBackground(Theme.BG_SECONDARY);
        card.setBorder(Theme.cardBorder(title));
        return card;
    }

    private JPanel makeFormPanel() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(Theme.BG_SECONDARY);
        return form;
    }

    private void addFieldRow(JPanel form, int row, String labelText, JComponent input, String hint) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(4, 4, 4, 12);
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;

        JLabel label = Theme.label(labelText);
        label.setPreferredSize(new Dimension(100, label.getPreferredSize().height));
        form.add(label, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(4, 0, 4, 12);
        form.add(input, gbc);

        if (hint != null && !hint.isEmpty()) {
            gbc.gridx = 1;
            gbc.gridy = row + 1;
            gbc.insets = new Insets(0, 0, 8, 12);
            JLabel hintLabel = Theme.hintLabel(hint);
            hintLabel.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));
            form.add(hintLabel, gbc);
        }
    }

    private JPanel buildPluginInfoCard() {
        JPanel card = makeCard("📦 插件基本信息");
        JPanel form = makeFormPanel();
        addFieldRow(form, 0, "插件名",  pluginNameField, "中英文皆可");
        addFieldRow(form, 2, "版本",    versionField,    "例如 1.0.0");
        addFieldRow(form, 4, "作者",    authorField,     "可选");
        addFieldRow(form, 6, "描述",    descriptionArea, "简短描述这段插件的用途");
        card.add(form, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildPackageInfoCard() {
        JPanel card = makeCard("🏷 包名 & 主类信息");
        JPanel form = makeFormPanel();
        addFieldRow(form, 0, "GroupId",    groupIdField,    "Maven groupId，例如 com.example");
        addFieldRow(form, 2, "ArtifactId", artifactIdField, "Maven artifactId，例如 my-plugin（小写字母 + 连字符）");
        addFieldRow(form, 4, "主类名",     mainClassField,   "插件入口类名（如 MyPlugin）");
        card.add(form, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildOutputCard() {
        JPanel card = makeCard("📂 输出目录");
        JPanel form = makeFormPanel();

        JPanel browseRow = new JPanel(new BorderLayout(10, 0));
        browseRow.setBackground(Theme.BG_SECONDARY);
        browseRow.add(outputDirField, BorderLayout.CENTER);

        JButton browseBtn = new JButton("浏览...");
        Theme.styleButton(browseBtn);
        browseBtn.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                outputDirField.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });
        browseRow.add(browseBtn, BorderLayout.EAST);

        addFieldRow(form, 0, "输出目录", browseRow, "插件项目将生成到此目录下");
        card.add(form, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildFeatureSwitchesCard() {
        JPanel card = makeCard("⚙ 功能开关（勾选后将生成对应示例代码）");
        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(Theme.BG_SECONDARY);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.weightx = 1.0;
        gbc.insets = new Insets(4, 6, 4, 6);

        gbc.gridy = 0; form.add(includeCommandBox,     gbc);
        gbc.gridy = 1; form.add(includeItemBox,        gbc);
        gbc.gridy = 2; form.add(includeEventBox,       gbc);
        gbc.gridy = 3; form.add(includeSecretRealmBox, gbc);

        card.add(form, BorderLayout.CENTER);
        return card;
    }
}
