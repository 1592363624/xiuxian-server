package com.mtxgdn.oauth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.interfaces.RSAPublicKey;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OIDC 签名密钥的持久化。
 *
 * <p>这组测试守的是本实现最容易踩的坑：<b>密钥必须跨重启稳定</b>。
 * 如果每次启动都生成新密钥对，客户端缓存的 JWKS 会用旧公钥去验新 token，
 * 表现为"登录时好时坏"，而且日志里看不出任何异常。</p>
 */
class OidcKeyManagerTest {

    @Test
    @DisplayName("首次启动生成密钥，并落盘")
    void generatesAndPersists(@TempDir Path dir) {
        OidcKeyManager.resetForTest();
        OidcKeyManager.init(dir);

        assertTrue(OidcKeyManager.isReady());
        assertNotNull(OidcKeyManager.keyId());
        assertTrue(Files.exists(dir.resolve("oidc-key.pem")), "密钥必须落盘");
    }

    @Test
    @DisplayName("重启后复用同一密钥，kid 不变")
    void survivesRestart(@TempDir Path dir) {
        OidcKeyManager.resetForTest();
        OidcKeyManager.init(dir);
        String firstKid = OidcKeyManager.keyId();
        RSAPublicKey firstPub = OidcKeyManager.publicKey();

        // 模拟重启：清内存状态，保留文件
        OidcKeyManager.resetForTest();
        assertFalse(OidcKeyManager.isReady());

        OidcKeyManager.init(dir);

        assertEquals(firstKid, OidcKeyManager.keyId(),
                "重启后 kid 必须一致 —— 变了说明密钥被重新生成，"
                        + "客户端缓存的 JWKS 会失效");
        assertEquals(firstPub.getModulus(), OidcKeyManager.publicKey().getModulus(),
                "公钥模数必须一致");
    }

    @Test
    @DisplayName("JWK 只含公开参数，绝不能泄露私钥分量")
    void jwkExposesOnlyPublicParts(@TempDir Path dir) {
        OidcKeyManager.resetForTest();
        OidcKeyManager.init(dir);

        String jwk = OidcKeyManager.publicJwkJson();

        assertTrue(jwk.contains("\"n\""), "应含模数 n");
        assertTrue(jwk.contains("\"e\""), "应含指数 e");
        assertTrue(jwk.contains("\"RS256\""));

        // 这几个是私钥参数。它们出现在 JWKS 里意味着任何人可伪造 id_token
        assertFalse(jwk.contains("\"d\""), "JWK 绝不能含私钥指数 d");
        assertFalse(jwk.contains("\"p\""), "JWK 绝不能含质数 p");
        assertFalse(jwk.contains("\"q\""), "JWK 绝不能含质数 q");
        assertFalse(jwk.contains("\"dp\""), "JWK 绝不能含 dp");
        assertFalse(jwk.contains("\"dq\""), "JWK 绝不能含 dq");
        assertFalse(jwk.contains("\"qi\""), "JWK 绝不能含 qi");
    }

    @Test
    @DisplayName("重复 init 是幂等的，不会换密钥")
    void initIsIdempotent(@TempDir Path dir) {
        OidcKeyManager.resetForTest();
        OidcKeyManager.init(dir);
        String kid = OidcKeyManager.keyId();

        OidcKeyManager.init(dir);
        OidcKeyManager.init(dir);

        assertEquals(kid, OidcKeyManager.keyId());
    }

    @Test
    @DisplayName("密钥文件损坏时抛异常，不静默重新生成")
    void corruptFileFailsLoudly(@TempDir Path dir) throws Exception {
        OidcKeyManager.resetForTest();
        Files.writeString(dir.resolve("oidc-key.pem"), "这不是 PEM");

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> OidcKeyManager.init(dir));

        // 关键：必须报错而不是"重新生成一个算了"。静默换密钥会让所有
        // 依赖 OIDC 的集成突然全挂，且现场没有任何提示
        assertTrue(e.getMessage().contains("无法解析") || e.getMessage().contains("失败"),
                "错误信息应说明是密钥文件的问题: " + e.getMessage());
    }

    @Test
    @DisplayName("未初始化时取密钥抛异常，而不是返回 null")
    void uninitializedThrows() {
        OidcKeyManager.resetForTest();
        assertFalse(OidcKeyManager.isReady());
        assertThrows(IllegalStateException.class, OidcKeyManager::privateKey);
        assertThrows(IllegalStateException.class, OidcKeyManager::publicKey);
    }
}
