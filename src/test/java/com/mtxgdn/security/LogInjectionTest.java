package com.mtxgdn.security;

import com.mtxgdn.util.TextSanitizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 日志注入（Log Injection / CRLF Injection）回归测试。
 *
 * <p><b>攻击形态</b>：玩家在聊天、角色名、宗门名等自由文本里带上换行符
 * {@code \n} / {@code \r}。这些文本会被原样写进日志文件
 * （{@code OneBotWebSocketServer} 的 {@code [群聊:...] 昵称: 内容}、
 * {@code MinecraftServerProcess} 的 {@code [MC 聊天] 名字: 内容} 等）。</p>
 *
 * <p>{@code GameLogger} 是<b>逐行</b>追加 {@code [时间] [级别] [线程] 前缀} 的，
 * 所以嵌入的换行会额外产生一整行<b>没有前缀</b>的文本 —— 与真正的日志行
 * 在视觉上几乎无法区分。攻击者因此可以：</p>
 * <ul>
 *   <li>伪造系统告警，误导运维（{@code "正常聊天\n[ERROR] 数据库连接已失效"}）；</li>
 *   <li>伪造其他玩家的行为，栽赃（{@code "\n[群聊:123] [QQ:999] 管理员: 已封禁玩家X"}）；</li>
 *   <li>污染基于日志行的审计与统计（每次换行都算一条新记录）。</li>
 * </ul>
 *
 * <p>修复方式是在净化阶段把控制字符替换为可见占位符，而不是直接丢弃 ——
 * 丢弃会让两条不同的输入净化为同一结果，反而掩盖异常。</p>
 */
class LogInjectionTest {

    /** 攻击者能构造出的典型载荷：先给一段正常内容，再伪造一行告警。 */
    private static final String FORGED_ALERT =
            "今天天气不错\n[ERROR] [main] 数据库连接已失效，请立即检查";

    @Test
    @DisplayName("聊天内容中的换行符必须被消除（防止伪造日志行）")
    void chatContentNewlineNeutralized() {
        String sanitized = TextSanitizer.sanitizeChatContent(FORGED_ALERT);

        assertFalse(sanitized.contains("\n"), "净化后不得残留 \\n，实际: " + sanitized);
        assertFalse(sanitized.contains("\r"), "净化后不得残留 \\r，实际: " + sanitized);
        assertEquals(1, sanitized.split("\n", -1).length - 1 + 1,
                "净化结果必须只有一行（按 \\n 切分只有 1 段），实际: " + sanitized);
    }

    @Test
    @DisplayName("聊天内容中的回车符同样必须被消除（防止 \\r 覆盖行首）")
    void chatContentCarriageReturnNeutralized() {
        String sanitized = TextSanitizer.sanitizeChatContent("正常内容\r[ERROR] 伪造的告警");
        assertFalse(sanitized.contains("\r"), "净化后不得残留 \\r，实际: " + sanitized);
    }

    @Test
    @DisplayName("混合 CRLF 与制表符也必须被消除")
    void chatContentCrLfAndTabNeutralized() {
        String sanitized = TextSanitizer.sanitizeChatContent("第一行\r\n第二行\t缩进");
        assertFalse(sanitized.contains("\r"));
        assertFalse(sanitized.contains("\n"));
        assertFalse(sanitized.contains("\t"));
    }

    @Test
    @DisplayName("角色名中的换行符必须被消除（角色名同样进日志）")
    void playerNameNewlineNeutralized() {
        String sanitized = TextSanitizer.sanitizePlayerName("坏名\n[ERROR] 伪造");
        assertFalse(sanitized.contains("\n"), "角色名不得含 \\n，实际: " + sanitized);
        assertFalse(sanitized.contains("\r"));
    }

    @Test
    @DisplayName("净化后仍应保留可见文本，不能整条丢成空串")
    void textPreservedNotDropped() {
        String sanitized = TextSanitizer.sanitizeChatContent(FORGED_ALERT);

        assertFalse(sanitized.isBlank(), "换行前后的正常文字必须保留，不能整条丢空");
        assertTrue(sanitized.contains("今天天气不错"), "第一段内容应保留");
        assertTrue(sanitized.contains("数据库连接已失效"), "第二段内容应保留（只是不能再独占一行）");
    }

    @Test
    @DisplayName("正常多字节内容不应被误伤")
    void normalContentUntouched() {
        assertEquals("你好，世界！", TextSanitizer.sanitizeChatContent("你好，世界！"));
        assertEquals("hello world", TextSanitizer.sanitizeChatContent("hello world"));
    }

    @Test
    @DisplayName("净化幂等：重复净化不应继续变化")
    void idempotent() {
        String once = TextSanitizer.sanitizeChatContent(FORGED_ALERT);
        String twice = TextSanitizer.sanitizeChatContent(once);
        assertEquals(once, twice, "净化必须幂等，否则二次处理会继续改写内容");
    }
}
