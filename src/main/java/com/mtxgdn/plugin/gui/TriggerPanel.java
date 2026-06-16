package com.mtxgdn.plugin.gui;

import com.mtxgdn.plugin.event.PluginEvent;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

/**
 * 触发器管理面板 —— 简洁现代化版。
 * <p>
 * 上方是操作按钮栏 + 表格（带交替行底色），双击任意行打开编辑器对话框。
 * 编辑器对话框内部表单放入 JScrollPane，小屏幕也不会被裁切。
 */
final class TriggerPanel extends JPanel {

    private final PluginConfig config;
    private final JTable table;
    private final DefaultTableModel tableModel;
    private final JLabel statusLabel;

    private static final String[] COLUMNS = {"启用", "事件类型", "触发条件", "动作", "描述"};

    TriggerPanel(PluginConfig config) {
        this.config = config;
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
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
                return column == 0;
            }
        };

        table = new JTable(tableModel);
        Theme.styleTable(table);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        table.getColumnModel().getColumn(0).setPreferredWidth(55);
        table.getColumnModel().getColumn(1).setPreferredWidth(140);
        table.getColumnModel().getColumn(2).setPreferredWidth(200);
        table.getColumnModel().getColumn(3).setPreferredWidth(120);
        table.getColumnModel().getColumn(4).setPreferredWidth(280);

        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) editSelected();
            }
        });

        statusLabel = new JLabel("共 0 条触发器");
        statusLabel.setFont(Theme.fontRegular(12.5f));
        statusLabel.setForeground(Theme.FG_MUTED);

        JScrollPane tableScroll = new JScrollPane(table,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        tableScroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER_COLOR, 1, true));
        tableScroll.getViewport().setBackground(Theme.BG_INPUT);

        add(buildTopBar(), BorderLayout.NORTH);
        add(tableScroll, BorderLayout.CENTER);
        add(buildButtonBar(), BorderLayout.SOUTH);

        refreshTable();
    }

    /** 将表格中的修改同步到 config。 */
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
                    ? "自定义 [" + t.getCustomKey() + "]"
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

    // ==================== UI 组件 ====================

    private JPanel buildTopBar() {
        JPanel bar = new JPanel(new BorderLayout(10, 4));
        bar.setBackground(Theme.BG_PRIMARY);
        JLabel title = Theme.titleLabel("⚡ 事件触发器", 14f);
        title.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
        bar.add(title, BorderLayout.WEST);

        JLabel hint = Theme.hintLabel("双击任意行可编辑，勾选左侧复选框快速启用 / 禁用");
        hint.setHorizontalAlignment(SwingConstants.RIGHT);
        hint.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
        bar.add(hint, BorderLayout.CENTER);
        return bar;
    }

    private JPanel buildButtonBar() {
        JPanel bar = new JPanel(new BorderLayout(10, 8));
        bar.setBackground(Theme.BG_PRIMARY);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttons.setBackground(Theme.BG_PRIMARY);

        JButton addBtn    = new JButton("➕ 新增触发器");
        JButton editBtn   = new JButton("✏️ 编辑");
        JButton deleteBtn = new JButton("🗑 删除");
        JButton upBtn     = new JButton("⬆ 上移");
        JButton downBtn   = new JButton("⬇ 下移");

        Theme.stylePrimaryButton(addBtn);
        Theme.styleButton(editBtn);
        Theme.styleDangerButton(deleteBtn);
        Theme.styleButton(upBtn);
        Theme.styleButton(downBtn);

        addBtn.addActionListener(e -> addNew());
        editBtn.addActionListener(e -> editSelected());
        deleteBtn.addActionListener(e -> deleteSelected());
        upBtn.addActionListener(e -> moveUp());
        downBtn.addActionListener(e -> moveDown());

        buttons.add(addBtn);
        buttons.add(editBtn);
        buttons.add(deleteBtn);
        buttons.add(Box.createHorizontalStrut(12));
        buttons.add(upBtn);
        buttons.add(downBtn);

        bar.add(buttons, BorderLayout.WEST);
        bar.add(statusLabel, BorderLayout.EAST);
        return bar;
    }

    private void updateStatus() {
        int active = 0;
        for (TriggerConfig t : config.getTriggers()) if (t.isEnabled()) active++;
        statusLabel.setText("共 " + config.getTriggers().size() + " 条触发器  ·  已启用 " + active + " 条");
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

        // ====== 表单卡片 ======
        JPanel formCard = new JPanel(new BorderLayout());
        formCard.setBackground(Theme.BG_SECONDARY);
        formCard.setBorder(Theme.cardBorder("📝 触发器配置"));

        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(Theme.BG_SECONDARY);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;

        // 描述
        JTextField descField = new JTextField(trigger.getDescription(), 40);
        Theme.styleInput(descField);
        addFormRow(form, gbc, 0, "描述", descField, null);

        // 事件类型
        JComboBox<PluginEvent.Type> typeCombo = new JComboBox<>(PluginEvent.Type.values());
        typeCombo.setSelectedItem(trigger.getEventType());
        Theme.styleComboBox(typeCombo);
        addFormRow(form, gbc, 1, "事件类型", typeCombo, null);

        // 自定义 key
        JTextField customKeyField = new JTextField(trigger.getCustomKey(), 30);
        Theme.styleInput(customKeyField);
        customKeyField.setEnabled(trigger.getEventType() == PluginEvent.Type.CUSTOM);
        addFormRow(form, gbc, 2, "自定义 key", customKeyField, "仅当事件类型 = CUSTOM 时生效");

        // 条件
        JTextField condField = new JTextField(trigger.getCondition(), 30);
        Theme.styleInput(condField);
        addFormRow(form, gbc, 3, "触发条件", condField,
                "例如：command=/你好  或  playerId=123   多条件用逗号分隔");

        // 动作
        JComboBox<TriggerConfig.Action> actionCombo = new JComboBox<>(TriggerConfig.Action.values());
        actionCombo.setSelectedItem(trigger.getAction());
        Theme.styleComboBox(actionCombo);
        addFormRow(form, gbc, 4, "响应动作", actionCombo, null);

        // 动作参数
        JTextField actionParamField = new JTextField(trigger.getActionParam(), 30);
        Theme.styleInput(actionParamField);
        addFormRow(form, gbc, 5, "动作参数", actionParamField,
                "消息内容 / 灵石数量 / 物品 key（根据动作类型选择）");

        // Java 代码
        JTextArea javaArea = new JTextArea(trigger.getJavaCode(), 7, 40);
        javaArea.setEnabled(trigger.getAction() == TriggerConfig.Action.RUN_JAVA);
        javaArea.setFont(Theme.fontMono(12f));
        javaArea.setBackground(Theme.BG_INPUT);
        javaArea.setForeground(Theme.FG_TEXT);
        javaArea.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER_COLOR, 1, true),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)));
        javaArea.setLineWrap(true);
        javaArea.setWrapStyleWord(true);

        gbc.gridx = 0; gbc.gridy = 6; gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE;
        gbc.insets = new Insets(8, 4, 4, 8);
        form.add(Theme.label("Java 代码"), gbc);

        gbc.gridx = 1; gbc.weightx = 1.0; gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(8, 0, 8, 8);
        JScrollPane codeScroll = new JScrollPane(javaArea);
        codeScroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER_COLOR, 1, true));
        codeScroll.getViewport().setBackground(Theme.BG_INPUT);
        form.add(codeScroll, gbc);

        // 启用状态
        JCheckBox enabledBox = new JCheckBox("触发器已启用（事件发生时执行）", trigger.isEnabled());
        Theme.styleCheckBox(enabledBox);
        gbc.gridx = 0; gbc.gridy = 7; gbc.gridwidth = 2; gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(8, 4, 0, 8);
        form.add(enabledBox, gbc);

        formCard.add(form, BorderLayout.CENTER);

        // ====== 把卡片放入 JScrollPane 避免小屏裁切 ======
        JScrollPane formScroll = new JScrollPane(formCard,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        formScroll.setBorder(BorderFactory.createEmptyBorder());
        formScroll.getVerticalScrollBar().setUnitIncrement(16);
        formScroll.getViewport().setBackground(Theme.BG_SECONDARY);

        // ====== 底部按钮 ======
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

        // ====== 事件联动 ======
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
                javaArea.setText("// 在此编写自定义代码\n// event 与 context 均可使用\ncontext.getLogger().info(\"自定义触发器被触发\");");
            }
        });

        // ====== 组装 ======
        JPanel outer = new JPanel(new BorderLayout(0, 0));
        outer.setBackground(Theme.BG_PRIMARY);
        outer.setBorder(BorderFactory.createEmptyBorder(14, 14, 8, 14));
        outer.add(formScroll, BorderLayout.CENTER);
        outer.add(buttons, BorderLayout.SOUTH);

        dialog.setContentPane(outer);
        dialog.setSize(new Dimension(760, 580));
        dialog.setMinimumSize(new Dimension(620, 460));
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
        return result[0];
    }

    /** 往 form 里加一行输入。 */
    private void addFormRow(JPanel form, GridBagConstraints gbc, int row,
                            String labelText, JComponent input, String hint) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.insets = new Insets(6, 4, 4, 10);
        gbc.anchor = GridBagConstraints.WEST;

        JLabel label = Theme.label(labelText);
        label.setPreferredSize(new Dimension(110, label.getPreferredSize().height));
        form.add(label, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(6, 0, 4, 8);
        form.add(input, gbc);

        if (hint != null && !hint.isEmpty()) {
            gbc.gridx = 1;
            gbc.gridy = row + 1;
            gbc.weightx = 1.0;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.insets = new Insets(0, 0, 8, 8);
            JLabel hintLabel = Theme.hintLabel(hint);
            hintLabel.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));
            form.add(hintLabel, gbc);
        }
    }
}
