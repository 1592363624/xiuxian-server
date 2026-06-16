package com.mtxgdn.plugin.gui;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.FontUIResource;
import javax.swing.table.JTableHeader;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 主题系统 —— 统一定义所有颜色、字体、边框、边距等视觉元素。
 * <p>
 * 采用「修仙主题」配色：深紫 + 金色 + 米色，营造典雅的仙侠氛围。
 */
public final class Theme {

    // ==================== 颜色定义 ====================

    /** 主背景色（深色） */
    public static final Color BG_PRIMARY = new Color(38, 26, 52);
    /** 次级背景色（面板） */
    public static final Color BG_SECONDARY = new Color(58, 42, 78);
    /** 输入框背景（米色） */
    public static final Color BG_INPUT = new Color(245, 240, 232);
    /** 表格交替行背景 */
    public static final Color BG_ROW_ALT = new Color(68, 50, 88);
    /** 表格选中行背景（金色高亮） */
    public static final Color BG_ROW_SELECTED = new Color(212, 175, 55);
    /** 悬停行背景 */
    public static final Color BG_ROW_HOVER = new Color(85, 68, 110);

    /** 主文字色（浅色） */
    public static final Color FG_TEXT = new Color(245, 240, 232);
    /** 次级文字色（标签） */
    public static final Color FG_LABEL = new Color(220, 208, 192);
    /** 标题文字色（金色） */
    public static final Color FG_ACCENT = new Color(232, 200, 80);
    /** 提示文字色（灰色） */
    public static final Color FG_HINT = new Color(170, 160, 180);

    /** 主色（紫） */
    public static final Color ACCENT_PURPLE = new Color(138, 98, 182);
    /** 次色（金） */
    public static final Color ACCENT_GOLD = new Color(212, 175, 55);
    /** 成功色（绿） */
    public static final Color ACCENT_SUCCESS = new Color(107, 191, 89);
    /** 警告色（红） */
    public static final Color ACCENT_DANGER = new Color(212, 89, 107);

    /** 边框颜色 */
    public static final Color BORDER_COLOR = new Color(107, 89, 142);
    /** 内部分隔线 */
    public static final Color BORDER_LIGHT = new Color(80, 60, 100);

    // ==================== 字体定义 ====================

    /** 标准字体 */
    public static Font fontPlain(float size) { return new Font(Font.SANS_SERIF, Font.PLAIN, Math.round(size)); }
    /** 粗体字体 */
    public static Font fontBold(float size) { return new Font(Font.SANS_SERIF, Font.BOLD, Math.round(size)); }
    /** 等宽字体 */
    public static Font fontMono(float size) { return new Font(Font.MONOSPACED, Font.PLAIN, Math.round(size)); }

    // ==================== 边框 ====================

    /** 空边距（通用内边距） */
    public static Border padding(int top, int left, int bottom, int right) {
        return BorderFactory.createEmptyBorder(top, left, bottom, right);
    }

    /** 卡片边框（圆角 + 颜色） */
    public static Border cardBorder(String title) {
        Border line = BorderFactory.createLineBorder(BORDER_COLOR, 1, true);
        Border inner = BorderFactory.createEmptyBorder(12, 16, 12, 16);
        Border titled = BorderFactory.createTitledBorder(line, title,
                javax.swing.border.TitledBorder.LEFT, javax.swing.border.TitledBorder.TOP,
                fontBold(12.5f), FG_ACCENT);
        return BorderFactory.createCompoundBorder(titled, inner);
    }

    /** 简单圆角边框 */
    public static Border roundedBorder() {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1, true),
                BorderFactory.createEmptyBorder(4, 8, 4, 8));
    }

    // ==================== 组件美化方法 ====================

    /** 应用样式到普通按钮（深色背景金色文字） */
    public static void styleButton(JButton b) {
        b.setBackground(ACCENT_PURPLE);
        b.setForeground(FG_TEXT);
        b.setFont(fontBold(12f));
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setOpaque(true);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
        // 悬停变色效果
        b.addMouseListener(new java.awt.event.MouseAdapter() {
            final Color orig = b.getBackground();
            @Override public void mouseEntered(java.awt.event.MouseEvent e) {
                b.setBackground(ACCENT_GOLD);
                b.setForeground(BG_PRIMARY);
            }
            @Override public void mouseExited(java.awt.event.MouseEvent e) {
                b.setBackground(orig);
                b.setForeground(FG_TEXT);
            }
        });
    }

    /** 应用样式到"主按钮"（金色大按钮，用于主要操作） */
    public static void stylePrimaryButton(JButton b) {
        b.setBackground(ACCENT_GOLD);
        b.setForeground(BG_PRIMARY);
        b.setFont(fontBold(13f));
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setOpaque(true);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
        b.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseEntered(java.awt.event.MouseEvent e) {
                b.setBackground(new Color(240, 210, 90));
            }
            @Override public void mouseExited(java.awt.event.MouseEvent e) {
                b.setBackground(ACCENT_GOLD);
            }
        });
    }

    /** 应用样式到"危险按钮"（红色，删除操作） */
    public static void styleDangerButton(JButton b) {
        b.setBackground(ACCENT_DANGER);
        b.setForeground(FG_TEXT);
        b.setFont(fontBold(12f));
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setOpaque(true);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
        b.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseEntered(java.awt.event.MouseEvent e) {
                b.setBackground(new Color(240, 110, 130));
            }
            @Override public void mouseExited(java.awt.event.MouseEvent e) {
                b.setBackground(ACCENT_DANGER);
            }
        });
    }

    /** 应用样式到文本输入框（浅色背景） */
    public static void styleInput(JTextComponent c) {
        c.setFont(fontPlain(13f));
        c.setBackground(BG_INPUT);
        c.setForeground(BG_PRIMARY);
        c.setCaretColor(ACCENT_PURPLE);
        c.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1, true),
                BorderFactory.createEmptyBorder(5, 8, 5, 8)));
        c.setOpaque(true);
    }

    /** 应用样式到下拉框 */
    public static void styleComboBox(JComboBox<?> c) {
        c.setFont(fontPlain(13f));
        c.setBackground(BG_INPUT);
        c.setForeground(BG_PRIMARY);
        c.setBorder(BorderFactory.createLineBorder(BORDER_COLOR, 1, true));
        c.setOpaque(true);
    }

    /** 应用样式到复选框 */
    public static void styleCheckBox(JCheckBox cb) {
        cb.setFont(fontPlain(13f));
        cb.setForeground(FG_TEXT);
        cb.setBackground(BG_SECONDARY);
        cb.setFocusPainted(false);
        cb.setOpaque(true);
    }

    /** 应用样式到标签 */
    public static JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setFont(fontPlain(13f));
        l.setForeground(FG_LABEL);
        return l;
    }

    /** 应用样式到标题标签 */
    public static JLabel titleLabel(String text, float size) {
        JLabel l = new JLabel(text);
        l.setFont(fontBold(size));
        l.setForeground(FG_ACCENT);
        return l;
    }

    /** 应用样式到提示标签（灰色小字） */
    public static JLabel hintLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(fontPlain(11f));
        l.setForeground(FG_HINT);
        return l;
    }

    /** 美化表格（交替行、自定义选中色、鼠标悬停） */
    public static void styleTable(JTable table) {
        table.setFont(fontPlain(12.5f));
        table.setBackground(BG_SECONDARY);
        table.setForeground(FG_TEXT);
        table.setSelectionBackground(BG_ROW_SELECTED);
        table.setSelectionForeground(BG_PRIMARY);
        table.setGridColor(BORDER_LIGHT);
        table.setShowGrid(true);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setFillsViewportHeight(true);

        // 表头样式
        JTableHeader h = table.getTableHeader();
        h.setFont(fontBold(12.5f));
        h.setBackground(ACCENT_PURPLE);
        h.setForeground(FG_TEXT);
        h.setOpaque(true);

        // 渲染器：交替背景色
        table.setDefaultRenderer(Object.class, new javax.swing.table.DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value,
                                                           boolean isSelected, boolean hasFocus,
                                                           int row, int column) {
                Component c = super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);
                if (!isSelected) {
                    c.setBackground(row % 2 == 0 ? BG_SECONDARY : BG_ROW_ALT);
                    c.setForeground(FG_TEXT);
                }
                if (c instanceof JComponent) {
                    ((JComponent) c).setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
                }
                return c;
            }
        });
        // 布尔值（复选框列）渲染器
        table.setDefaultRenderer(Boolean.class, new javax.swing.table.DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value,
                                                           boolean isSelected, boolean hasFocus,
                                                           int row, int column) {
                JCheckBox cb = new JCheckBox();
                cb.setSelected(Boolean.TRUE.equals(value));
                cb.setHorizontalAlignment(SwingConstants.CENTER);
                cb.setOpaque(true);
                if (isSelected) {
                    cb.setBackground(BG_ROW_SELECTED);
                } else {
                    cb.setBackground(row % 2 == 0 ? BG_SECONDARY : BG_ROW_ALT);
                }
                return cb;
            }
        });
        table.setRowHeight(30);
    }

    // ==================== 全局 Look and Feel ====================

    /** 初始化 Nimbus L&F + 全局颜色覆盖，在主窗口创建前调用。 */
    public static void installLookAndFeel() {
        try {
            // 尝试使用 Nimbus L&F（比系统默认更现代）
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    overrideNimbusDefaults();
                    return;
                }
            }
            // 没有 Nimbus，使用系统默认
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            // 回退到默认
            try {
                UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
            } catch (Exception ignored) {}
        }
    }

    private static void overrideNimbusDefaults() {
        // 使用 UIManager 覆盖 Nimbus 默认颜色
        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("nimbusBase", new ColorUIResource(BG_PRIMARY));
        defaults.put("nimbusBlueGrey", new ColorUIResource(BG_SECONDARY));
        defaults.put("nimbusLightBackground", new ColorUIResource(BG_SECONDARY));
        defaults.put("control", new ColorUIResource(ACCENT_PURPLE));
        defaults.put("textForeground", new ColorUIResource(FG_TEXT));
        defaults.put("textBackground", new ColorUIResource(BG_INPUT));
        defaults.put("menuText", new ColorUIResource(FG_TEXT));
        defaults.put("OptionPane.messageForeground", new ColorUIResource(FG_TEXT));
        defaults.put("nimbusSelectedText", new ColorUIResource(BG_PRIMARY));
        defaults.put("nimbusSelectionBackground", new ColorUIResource(ACCENT_GOLD));
        defaults.put("Table.background", new ColorUIResource(BG_SECONDARY));
        defaults.put("Table.gridColor", new ColorUIResource(BORDER_LIGHT));
        defaults.put("TableHeader.background", new ColorUIResource(ACCENT_PURPLE));
        defaults.put("TableHeader.textForeground", new ColorUIResource(FG_TEXT));
        defaults.put("Label.font", new FontUIResource(fontPlain(13f)));
        defaults.put("Button.font", new FontUIResource(fontBold(12.5f)));
        defaults.put("TextField.font", new FontUIResource(fontPlain(13f)));
        defaults.put("TextArea.font", new FontUIResource(fontPlain(13f)));
        defaults.put("ComboBox.font", new FontUIResource(fontPlain(13f)));
        defaults.put("Table.font", new FontUIResource(fontPlain(12.5f)));
        defaults.put("CheckBox.font", new FontUIResource(fontPlain(13f)));
        for (Map.Entry<String, Object> e : defaults.entrySet()) {
            UIManager.put(e.getKey(), e.getValue());
        }
    }

    private Theme() {}
}
