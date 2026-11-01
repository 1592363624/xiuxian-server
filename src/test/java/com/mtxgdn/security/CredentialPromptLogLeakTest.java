package com.mtxgdn.security;

import com.mtxgdn.util.LogMasker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 账号流程「第二步输入凭据」的日志泄露。
 *
 * <p><b>这不是回归测试，是对一个真实漏洞的复现与封堵验证。</b></p>
 *
 * <h3>漏洞形态</h3>
 * <p>注册/改密/注销都是<b>两步</b>的：</p>
 * <pre>
 *   玩家:   /注册 张三
 *   机器人: 角色名【张三】可用！请在私聊中发送你的密码（不少于6位）
 *   玩家:   mySecret123          ← 这一条是纯密码
 * </pre>
 *
 * <p>第二步那条消息里<b>没有任何关键词</b>。而 {@code LogMasker.mask} 的整套
 * 识别逻辑都建立在关键词之上（看到 {@code /注册}、{@code password=} 才动手），
 * 对一条纯口令完全无能为力 —— 它甚至不知道这段文字是敏感的。</p>
 *
 * <p>结果：明文口令原样进了 {@code log/} 与服务端日志。任何能读日志的人
 * （运维、日志收集系统、误发的日志包）都能直接拿去登录。</p>
 *
 * <h3>与已有防护的关系</h3>
 * <p>{@code LogMasker} 本身没坏，它挡住了 {@code /注册 密码123} 这种
 * "一行写完"的形态。漏掉的是"分开两步写"的形态。所以修复不是改识别规则
 * （规则再强也认不出 {@code mySecret123} 是密码），而是让调用方在
 * <b>已知该用户正处于等待输入凭据的状态</b>时直接整条打码。</p>
 */
class CredentialPromptLogLeakTest {

    /** 一个足够像真实口令的串，且不含任何会被关键词规则命中的词。 */
    private static final String SECRET = "Zx9vQ2mLp7";

    // ==================== 漏洞本体：关键词脱敏对纯口令无效 ====================

    @Test
    @DisplayName("复现：纯口令消息用关键词脱敏仍然明文（这就是泄露点）")
    void keywordMaskingCannotCatchBareCredential() {
        // 这条断言记录的是"为什么必须引入 maskWhole"：
        // mask() 对一条纯口令返回原样，它是设计如此 —— 它无从判断。
        assertEquals(SECRET, LogMasker.mask(SECRET),
                "mask() 认不出纯口令，这是预期行为，所以调用方必须结合会话状态处理");
    }

    @Test
    @DisplayName("复现：口令看起来像普通单词时同样漏掉")
    void ordinaryLookingPasswordAlsoLeaks() {
        assertEquals("mypassword", LogMasker.mask("mypassword"));
        assertEquals("123456789", LogMasker.mask("123456789"));
    }

    // ==================== 修复手段：整条打码 ====================

    @Test
    @DisplayName("maskWhole 把整条内容打掉，任何形态的口令都留不下")
    void maskWholeRemovesEverything() {
        for (String input : new String[]{SECRET, "mypassword", "123456789",
                "含中文的密码abc", "p@ss w0rd!"}) {
            String masked = LogMasker.maskWhole(input);
            assertEquals("***", masked, "应整条打码，输入: " + input);
            assertFalse(masked.contains(input), "不能残留原文: " + masked);
        }
    }

    @Test
    @DisplayName("maskWhole 对 null / 空串原样返回，不制造虚假日志")
    void maskWholeHandlesNullAndEmpty() {
        assertNull(LogMasker.maskWhole(null));
        assertEquals("", LogMasker.maskWhole(""));
    }

    // ==================== 组合：会话状态决定用哪个 ====================

    @Test
    @DisplayName("等待输入凭据的状态下，纯口令被完全遮蔽")
    void credentialStateHidesSecret() {
        // 模拟调用方的分支：session.state 属于凭据输入状态 → maskWhole
        String logged = LogMasker.maskWhole(SECRET);
        assertFalse(logged.contains(SECRET), "日志里不能出现明文口令");
    }

    @Test
    @DisplayName("非凭据状态下，正常聊天不受影响（脱敏不能把日志变成一片星号）")
    void normalChatStillReadable() {
        String chat = "今天天气不错，谁在清河村";
        assertEquals(chat, LogMasker.mask(chat),
                "普通聊天应原样保留，否则日志失去排查价值");
    }

    @Test
    @DisplayName("一行写完的注册指令仍然只遮蔽口令、保留指令名")
    void singleLineRegisterStillMasked() {
        String masked = LogMasker.mask("/注册 张三 " + SECRET);
        assertFalse(masked.contains(SECRET), "一行写法同样不能泄露，实际: " + masked);
        assertTrue(masked.contains("/注册"), "指令名应保留便于排查，实际: " + masked);
    }
}
