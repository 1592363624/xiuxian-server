package com.mtxgdn.util;

public class TextSanitizer {

    /**
     * 控制字符的可见占位符。
     *
     * <p><b>为什么替换而不是删除</b>：删除会让 {@code "a\nb"} 与 {@code "ab"}
     * 净化为同一个结果 —— 两条语义完全不同的输入变得无法区分，反而掩盖了
     * 异常输入。替换成 {@code ␤}（U+2424，换行符号）后，日志里能一眼看出
     * "这里原本有个换行"，同时在视觉上仍是一行。</p>
     */
    private static final char LINE_BREAK_PLACEHOLDER = '\u2424';

    /** 制表符的可见占位符（U+2192，右箭头）。 */
    private static final char TAB_PLACEHOLDER = '\u2192';

    public static String sanitizeContent(String input) {
        if (input == null) return "";

        StringBuilder sb = new StringBuilder(input.length());

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            switch (c) {
                case '<': sb.append("&lt;"); break;
                case '>': sb.append("&gt;"); break;
                case '"': sb.append("&quot;"); break;
                case '\'': sb.append("&#39;"); break;
                case '&': sb.append("&amp;"); break;
                // 日志注入防护：换行/回车会让一条消息在日志里裂成多行，
                // 且新行不带 GameLogger 的时间/级别/线程前缀，与真实日志行
                // 无法区分 —— 攻击者可借此伪造告警或栽赃他人。
                case '\n':
                case '\r': sb.append(LINE_BREAK_PLACEHOLDER); break;
                case '\t': sb.append(TAB_PLACEHOLDER); break;
                // 其余 C0 控制字符与 DEL：同样不应进入日志或前端
                default:
                    if (c < 0x20 || c == 0x7F) {
                        sb.append(' ');
                    } else {
                        sb.append(c);
                    }
            }
        }

        return sb.toString();
    }

    public static String sanitizePlayerName(String name) {
        if (name == null || name.trim().isEmpty()) return "";
        String sanitized = name.trim();
        sanitized = sanitized.replaceAll("[<>\"'&\\n\\r\\t]", "");
        if (sanitized.length() > 16) {
            sanitized = sanitized.substring(0, 16);
        }
        if (sanitized.isEmpty()) return "";
        return sanitized;
    }

    public static String sanitizeChatContent(String content) {
        if (content == null) return "";
        // 先截断再转义：反过来会把 "&amp;" 这类实体从中间截断成 "&am"，
        // 既留下一个无效实体，又让结果长度不可预测。
        String bounded = content.length() > 500 ? content.substring(0, 500) : content;
        return sanitizeContent(bounded);
    }
}

