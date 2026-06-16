package com.mtxgdn.plugin.gui;

import com.mtxgdn.plugin.event.PluginEvent;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

/**
 * 触发器管理面板（美化版）。
 * <p>
 * 表格列出所有触发器，支持新增 / 编辑 / 删除 / 上移 / 下移。
 * 表格采用交替行背景色，金色选中高亮。
 */
final class TriggerPanel extends JPanel {

    private final PluginConfig config;
    private final JTable table;
    private final DefaultTableModel tableModel;
    private final JLabel statusLabel;

    private static final String[] COLUMNS = {"启用", "事件类型", "触发条件", "动作", "描述"};

    TriggerPanel(PluginConfig config) {
        this.config = config;
        setLayout(new BorderLayout(15, 10));
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        setBackground(Theme.BG_PRIMARY);

        // ====== 表格模型 ======
        tableModel = new DefaultTableModel(COLUMNS, 0) {
            @Override
            public Class<?> getColumnClass(int column) {
                if (column == 0) return Boolean.class;
                return String.class;
            }
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 0;  // 只有"启用"列可直接切换
            }
        };

        table = new JTable(tableModel);
        Theme.styleTable(table);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // 列宽
        table.getColumnModel().getColumn(0).setPreferredWidth(55);
        table.getColumnModel().getColumn(1).setPreferredWidth(130);
        table.getColumnModel().getColumn(2).setPreferredWidth(180);
        table.getColumnModel().getColumn(3).setPreferredWidth(110);
        table.getColumnModel().getColumn(4).setPreferredWidth(280);

        // 双击编辑
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) editSelected();
            }
        });

        // 状态标签
        statusLabel = new JLabel("  共 0 条触发器");
        statusLabel.setFont(Theme.fontBold(13f));
        statusLabel.setForeground(Theme.ACCENT_GOLD);

        // 组装
        add(buildTopBar(), BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(buildButtonBar(), BorderLayout.SOUTH);

        refreshTable();
    }

    /** 将表格中的修改（启用状态）同步到 config。 */
    void applyToConfig() {
        if (table.isEditing()) table.getCellEditor().stopCellEditing();
        for (int i = 0; i < tableModel.getRowCount() && i < config.getTriggers().size(); i++) {
            config.getTriggers().get(i).setEnabled((Boolean) tableModel.getValueAt(i, 0));
        }
    }

    /** 重新加载表格内容（从 config）。 */
    void refreshTable() {
        int select = table.getSelectedRow();
        tableModel.setRowCount(0);
        for (TriggerConfig t : config.getTriggers()) {
            String typeText = t.getEventType() == PluginEvent.Type.CUSTOM
                    ? "自定义[" + t.getCustomKey() + "]"
                    : t.getEventType().name();
            Object[] row = {
                    t.isEnabled(),
                    typeText,
                    t.getCondition(),
                    t.getAction().label,
                    t.getDescription()
            };
            tableModel.addRow(row);
        }
        if (select >= 0 && select < tableModel.getRowCount()) {
            table.setRowSelectionInterval(select, select);
        }
        updateStatus();
    }

    // ==================== 构建 UI 组件 ====================

    private JPanel buildTopBar() {
        JPanel bar = new JPanel(new BorderLayout(10, 5));
        bar.setBackground(Theme.BG_PRIMARY);
        JLabel title = Theme.titleLabel("⚡ 事件触发器配置", 15f);
        title.setForeground(Theme.ACCENT_GOLD);
        title.setBorder(BorderFactory.createEmptyBorder(0, 4, 6, 0));
        bar.add(title, BorderLayout.WEST);

        // 右侧小提示
        JLabel hint = Theme.hintLabel("  双击任意行可编辑，勾选左侧复选框快速启用/禁用");
        hint.setHorizontalAlignment(SwingConstants.RIGHT);
        bar.add(hint, BorderLayout.CENTER);

        return bar;
    }

    private JPanel buildButtonBar() {
        JPanel bar = new JPanel(new BorderLayout(10, 10));
        bar.setBackground(Theme.BG_PRIMARY);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 5));
        buttons.setBackground(Theme.BG_PRIMARY);

        JButton addBtn = new JButton("➕ 新增触发器");
        Theme.styleButton(addBtn);
        addBtn.setFont(Theme.fontBold(12.5f));
        addBtn.addActionListener(e -> addNew());

        JButton editBtn = new JButton("✏️ 编辑");
        Theme.styleButton(editBtn);
        editBtn.addActionListener(e -> editSelected());

        JButton deleteBtn = new JButton("🗑 删除");
        Theme.styleDangerButton(deleteBtn);
        deleteBtn.addActionListener(e -> deleteSelected());

        JButton upBtn = new JButton("⬆ 上移");
        Theme.styleButton(upBtn);
        upBtn.addActionListener(e -> moveUp());

        JButton downBtn = new JButton("⬇ 下移");
        Theme.styleButton(downBtn);
        downBtn.addActionListener(e -> moveDown());

        buttons.add(addBtn);
        buttons.add(editBtn);
        buttons.add(deleteBtn);
        buttons.add(Box.createHorizontalStrut(20));
        buttons.add(upBtn);
        buttons.add(downBtn);

        bar.add(buttons, BorderLayout.WEST);
        bar.add(statusLabel, BorderLayout.EAST);
        return bar;
    }

    private void updateStatus() {
        int active = 0;
        for (TriggerConfig t : config.getTriggers()) if (t.isEnabled()) active++;
        statusLabel.setText("  共 " + config.getTriggers().size() + " 条触发器  ·  已启用 " + active + " 条");
    }

    // ==================== 操作 ====================

    private void addNew() {
        TriggerConfig newTrigger = new TriggerConfig();
        newTrigger.setDescription("新建触发器 #" + (config.getTriggers().size() + 1));
        if (showEditor(newTrigger, "✨ 新增触发器")) {
            config.getTriggers().add(newTrigger);
            refreshTable();
            int idx = config.getTriggers().size() - 1;
            table.setRowSelectionInterval(idx, idx);
        }
    }

    private void editSelected() {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "请先选择一行触发器", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        TriggerConfig t = config.getTriggers().get(row);
        if (showEditor(t, "✏ 编辑触发器 · " + t.getDescription())) {
            refreshTable();
            table.setRowSelectionInterval(row, row);
        }
    }

    private void deleteSelected() {
        int row = table.getSelectedRow();
        if (row < 0) return;
        TriggerConfig t = config.getTriggers().get(row);
        int choice = JOptionPane.showConfirmDialog(this,
                "确定要删除触发器：\n  " + t.getDescription(),
                "⚠ 删除确认",
                JOptionPane.YES_NO_OPTION);
        if (choice == JOptionPane.YES_OPTION) {
            config.getTriggers().remove(row);
            refreshTable();
        }
    }

    private void moveUp() {
        int row = table.getSelectedRow();
        if (row <= 0) return;
        java.util.List<TriggerConfig> list = config.getTriggers();
        TriggerConfig tmp = list.get(row - 1);
        list.set(row - 1, list.get(row));
        list.set(row, tmp);
        refreshTable();
        table.setRowSelectionInterval(row - 1, row - 1);
    }

    private void moveDown() {
        int row = table.getSelectedRow();
        java.util.List<TriggerConfig> list = config.getTriggers();
        if (row < 0 || row >= list.size() - 1) return;
        TriggerConfig tmp = list.get(row + 1);
        list.set(row + 1, list.get(row));
        list.set(row, tmp);
        refreshTable();
        table.setRowSelectionInterval(row + 1, row + 1);
    }

    // ==================== 编辑器对话框 ====================

    private boolean showEditor(TriggerConfig trigger, String title) {
        JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this), title, true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.getContentPane().setBackground(Theme.BG_PRIMARY);

        // ====== 主容器：卡片式 ======
        JPanel outer = new JPanel(new BorderLayout(15, 15));
        outer.setBorder(BorderFactory.createEmptyBorder(18, 20, 18, 20));
        outer.setBackground(Theme.BG_PRIMARY);

        // ====== 上：表单卡片 ======
        JPanel formCard = new JPanel(new BorderLayout());
        formCard.setBackground(Theme.BG_SECONDARY);
        formCard.setBorder(Theme.cardBorder("📝 触发器配置"));

        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(Theme.BG_SECONDARY);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 4, 6, 12);
        c.anchor = GridBagConstraints.WEST;

        // 描述
        JTextField descField = new JTextField(trigger.getDescription(), 40);
        Theme.styleInput(descField);
        c.gridx = 0; c.gridy = 0; c.weightx = 0; c.fill = GridBagConstraints.NONE;
        form.add(Theme.label("描述"), c);
        c.gridx = 1; c.weightx = 1.0; c.fill = GridBagConstraints.HORIZONTAL;
        form.add(descField, c);

        // 事件类型
        JComboBox<PluginEvent.Type> typeCombo = new JComboBox<>(PluginEvent.Type.values());
        typeCombo.setSelectedItem(trigger.getEventType());
        Theme.styleComboBox(typeCombo);
        c.gridx = 0; c.gridy = 1; c.weightx = 0; c.fill = GridBagConstraints.NONE;
        form.add(Theme.label("事件类型"), c);
        c.gridx = 1; c.weightx = 1.0; c.fill = GridBagConstraints.HORIZONTAL;
        form.add(typeCombo, c);

        // 自定义 key（根据事件类型激活）
        JTextField customKeyField = new JTextField(trigger.getCustomKey(), 30);
        Theme.styleInput(customKeyField);
        customKeyField.setEnabled(trigger.getEventType() == PluginEvent.Type.CUSTOM);
        c.gridx = 0; c.gridy = 2; c.weightx = 0; c.fill = GridBagConstraints.NONE;
        form.add(Theme.label("自定义 key"), c);
        c.gridx = 1; c.weightx = 1.0; c.fill = GridBagConstraints.HORIZONTAL;
        form.add(customKeyField, c);

        // 条件
        JTextField condField = new JTextField(trigger.getCondition(), 30);
        Theme.styleInput(condField);
        condField.setToolTipText("例如: command=/你好  或  playerId=123   多条件用逗号分隔");
        c.gridx = 0; c.gridy = 3; c.weightx = 0; c.fill = GridBagConstraints.NONE;
        form.add(Theme.label("触发条件"), c);
        c.gridx = 1; c.weightx = 1.0; c.fill = GridBagConstraints.HORIZONTAL;
        form.add(condField, c);

        // 动作
        JComboBox<TriggerConfig.Action> actionCombo = new JComboBox<>(TriggerConfig.Action.values());
        actionCombo.setSelectedItem(trigger.getAction());
        Theme.styleComboBox(actionCombo);
        c.gridx = 0; c.gridy = 4; c.weightx = 0; c.fill = GridBagConstraints.NONE;
        form.add(Theme.label("响应动作"), c);
        c.gridx = 1; c.weightx = 1.0; c.fill = GridBagConstraints.HORIZONTAL;
        form.add(actionCombo, c);

        // 动作参数
        JTextField actionParamField = new JTextField(trigger.getActionParam(), 30);
        Theme.styleInput(actionParamField);
        actionParamField.setToolTipText("消息内容 / 灵石数量 / 物品 key");
        c.gridx = 0; c.gridy = 5; c.weightx = 0; c.fill = GridBagConstraints.NONE;
        form.add(Theme.label("动作参数"), c);
        c.gridx = 1; c.weightx = 1.0; c.fill = GridBagConstraints.HORIZONTAL;
        form.add(actionParamField, c);

        // Java 代码
        JTextArea javaArea = new JTextArea(trigger.getJavaCode(), 7, 40);
        javaArea.setEnabled(trigger.getAction() == TriggerConfig.Action.RUN_JAVA);
        javaArea.setFont(Theme.fontMono(12.5f));
        javaArea.setBackground(Theme.BG_PRIMARY);
        javaArea.setForeground(Theme.FG_TEXT);
        javaArea.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER_COLOR, 1, true),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)));

        c.gridx = 0; c.gridy = 6; c.weightx = 0; c.fill = GridBagConstraints.NONE;
        c.anchor = GridBagConstraints.NORTHWEST;
        form.add(Theme.label("Java 代码"), c);
        c.gridx = 1; c.weightx = 1.0; c.fill = GridBagConstraints.BOTH;
        c.anchor = GridBagConstraints.WEST;
        JScrollPane codeScroll = new JScrollPane(javaArea);
        codeScroll.setBorder(BorderFactory.createEmptyBorder());
        codeScroll.setBackground(Theme.BG_SECONDARY);
        form.add(codeScroll, c);

        // 启用状态
        JCheckBox enabledBox = new JCheckBox("触发器已启用（在事件发生时执行）", trigger.isEnabled());
        Theme.styleCheckBox(enabledBox);
        c.gridx = 0; c.gridy = 7; c.gridwidth = 2; c.weightx = 1.0;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(10, 4, 0, 12);
        form.add(enabledBox, c);

        formCard.add(form, BorderLayout.CENTER);

        // ====== 下：按钮 ======
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 10));
        buttons.setBackground(Theme.BG_PRIMARY);

        final boolean[] result = {false};

        JButton ok = new JButton("💾 保存");
        Theme.stylePrimaryButton(ok);
        ok.addActionListener(e -> {
            String desc = descField.getText().trim();
            if (desc.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "描述不能为空", "⚠ 提示", JOptionPane.WARNING_MESSAGE);
                return;
            }
            trigger.setDescription(desc);
            trigger.setEventType((PluginEvent.Type) typeCombo.getSelectedItem());
            trigger.setCustomKey(customKeyField.getText().trim());
            trigger.setCondition(condField.getText().trim());
            trigger.setAction((TriggerConfig.Action) actionCombo.getSelectedItem());
            trigger.setActionParam(actionParamField.getText().trim());
            trigger.setJavaCode(javaArea.getText());
            trigger.setEnabled(enabledBox.isSelected());
            result[0] = true;
            dialog.dispose();
        });

        JButton cancel = new JButton("取消");
        Theme.styleButton(cancel);
        cancel.addActionListener(e -> dialog.dispose());

        buttons.add(ok);
        buttons.add(cancel);

        // ====== 事件监听：根据类型激活输入 ======
        typeCombo.addActionListener(e -> {
            PluginEvent.Type t = (PluginEvent.Type) typeCombo.getSelectedItem();
            customKeyField.setEnabled(t == PluginEvent.Type.CUSTOM);
            if (t == PluginEvent.Type.CUSTOM && customKeyField.getText().trim().isEmpty()) {
                customKeyField.setText("my_trigger");
            }
        });

        actionCombo.addActionListener(e -> {
            TriggerConfig.Action a = (TriggerConfig.Action) actionCombo.getSelectedItem();
            javaArea.setEnabled(a == TriggerConfig.Action.RUN_JAVA);
            if (a == TriggerConfig.Action.RUN_JAVA && javaArea.getText().trim().isEmpty()) {
                javaArea.setText("// 在此编写自定义代码\n// event 与 context 均可用\ncontext.getLogger().info(\"自定义触发器被触发\");\n// 也可以调用 context.getPlayerService()、getItemService() 等");
            }
        });

        // ====== 组装 ======
        outer.add(formCard, BorderLayout.CENTER);
        outer.add(buttons, BorderLayout.SOUTH);

        dialog.setContentPane(outer);
        dialog.setSize(new Dimension(720, 560));
        dialog.setMinimumSize(new Dimension(620, 460));
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
        return result[0];
    }
}
