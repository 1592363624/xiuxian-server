package com.mtxgdn.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 入站消息日志点必须走"结合会话状态"的脱敏，不能退回裸的关键词脱敏。
 *
 * <p><b>为什么需要源码级守卫</b>：这条泄露是靠语义发现的 ——
 * 注册第二步发来的是<b>纯口令</b>，{@code LogMasker.mask} 完全看不出它是凭据。
 * 修复后的写法是 {@code maskInbound(...)}：先查该用户是否正处于
 * "等待输入凭据"的会话状态，是则整条打码。</p>
 *
 * <p>但 {@code maskInbound} 是私有方法，行为级测试要构造完整的
 * WebSocket 会话、机器人状态机、绑定关系才能走到日志那一行 ——
 * 成本高且脆弱。而一旦有人"优化"成直接调 {@code LogMasker.mask(text)}，
 * 编译通过、功能正常、测试全绿，<b>泄露会静默复发</b>。</p>
 *
 * <p>所以这里退一步，直接检查源码：这些记录玩家原始消息的日志行，
 * 必须调用 {@code maskInbound} 而不是 {@code LogMasker.mask}。
 * 断言的是"接线没被改回去"，不是"脱敏算法正确"——后者由
 * {@link CredentialPromptLogLeakTest} 负责。</p>
 *
 * <p>所有检查都先剥掉注释，避免注释里提到旧写法造成假失败。</p>
 */
class InboundLogMaskingWiringTest {

    private static final Path ONEBOT = Paths.get(
            "src/main/java/com/mtxgdn/onebot/OneBotWebSocketServer.java");
    private static final Path QQ = Paths.get(
            "src/main/java/com/mtxgdn/qq/QqEventDispatcher.java");

    @Test
    @DisplayName("OneBot 私聊/群聊/原始上报三处日志都走 maskInbound")
    void oneBotUsesSessionAwareMasking() throws IOException {
        String src = stripComments(read(ONEBOT));

        // 这三行过去是裸的 LogMasker.mask(...)，正是泄露发生的地方
        List<String> logLines = new ArrayList<>();
        for (String line : src.split("\n")) {
            String t = line.trim();
            if (t.startsWith("log.info(\"[私聊:") || t.startsWith("log.info(\"[群聊:")) {
                logLines.add(t);
            }
            if (t.contains("botLog.logRecv(")) {
                logLines.add(t);
            }
        }

        assertTrue(logLines.size() >= 3,
                "应至少找到私聊、群聊、原始上报三处入站日志，实际 " + logLines.size() + " 处");

        for (String line : logLines) {
            assertTrue(line.contains("maskInbound"),
                    "入站日志必须走 maskInbound（它结合会话状态），"
                            + "直接调 LogMasker.mask 会让纯口令明文落盘：" + line);
        }
    }

    @Test
    @DisplayName("QQ 群聊/私聊两处日志都走 maskInbound")
    void qqUsesSessionAwareMasking() throws IOException {
        String src = stripComments(read(QQ));
        String[] lines = src.split("\n");

        // 日志语句可能跨行（log.info("[QQ群:" ... \n + maskInbound(...)），
        // 所以从 log.info 起始行往下最多看 3 行，拼成一条完整语句再判断。
        List<String> statements = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            String t = lines[i].trim();
            if (!t.contains("log.info(\"[QQ群:") && !t.contains("log.info(\"[QQ私聊:")) {
                continue;
            }
            StringBuilder stmt = new StringBuilder(t);
            // 括号未闭合就继续拼下一行
            int depth = count(t, '(') - count(t, ')');
            for (int j = i + 1; j < lines.length && depth > 0 && j <= i + 3; j++) {
                String next = lines[j].trim();
                stmt.append(' ').append(next);
                depth += count(next, '(') - count(next, ')');
            }
            statements.add(stmt.toString());
        }

        assertTrue(statements.size() >= 2,
                "应至少找到群聊、私聊两处入站日志，实际 " + statements.size() + " 处");

        for (String stmt : statements) {
            assertTrue(stmt.contains("maskInbound"),
                    "QQ 入站日志必须走 maskInbound：" + stmt);
        }
    }

    private static int count(String s, char ch) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == ch) {
                n++;
            }
        }
        return n;
    }

    @Test
    @DisplayName("两个适配器都实现了凭据输入状态判定，且覆盖改密与注销")
    void bothAdaptersKnowCredentialStates() throws IOException {
        for (Path p : new Path[]{ONEBOT, QQ}) {
            String src = stripComments(read(p));
            assertTrue(src.contains("isCredentialPromptState"),
                    p.getFileName() + " 应实现 isCredentialPromptState");
            for (String state : new String[]{
                    "WAITING_PASSWORD", "WAITING_OLD_PASSWORD",
                    "WAITING_NEW_PASSWORD", "WAITING_DELETE_CONFIRM"}) {
                assertTrue(src.contains(state),
                        p.getFileName() + " 的凭据状态判定漏了 " + state
                                + " —— 该状态下的输入同样要整条打码");
            }
        }
    }

    @Test
    @DisplayName("maskInbound 的兜底分支仍是关键词脱敏（不能让所有日志变星号）")
    void fallbackStillUsesKeywordMasking() throws IOException {
        for (Path p : new Path[]{ONEBOT, QQ}) {
            String src = stripComments(read(p));
            assertTrue(src.contains("LogMasker.mask("),
                    p.getFileName() + " 的兜底分支应调用 LogMasker.mask —— "
                            + "非凭据状态下的正常聊天必须可读，否则日志失去排查价值");
            assertTrue(src.contains("LogMasker.maskWhole("),
                    p.getFileName() + " 凭据状态下应调用 LogMasker.maskWhole");
        }
    }

    // ==================== 工具 ====================

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("读不到源码: " + path.toAbsolutePath(), e);
        }
    }

    /** 剥掉 // 与 /* *\/ 注释，避免注释里的旧写法造成假失败。 */
    private static String stripComments(String src) {
        StringBuilder out = new StringBuilder(src.length());
        boolean inLine = false;
        boolean inBlock = false;
        boolean inString = false;
        for (int i = 0; i < src.length(); i++) {
            char c = src.charAt(i);
            char next = i + 1 < src.length() ? src.charAt(i + 1) : '\0';
            if (inLine) {
                if (c == '\n') {
                    inLine = false;
                    out.append(c);
                }
                continue;
            }
            if (inBlock) {
                if (c == '*' && next == '/') {
                    inBlock = false;
                    i++;
                }
                continue;
            }
            if (!inString && c == '/' && next == '/') {
                inLine = true;
                i++;
                continue;
            }
            if (!inString && c == '/' && next == '*') {
                inBlock = true;
                i++;
                continue;
            }
            if (c == '"' && (i == 0 || src.charAt(i - 1) != '\\')) {
                inString = !inString;
            }
            out.append(c);
        }
        return out.toString();
    }
}
