package com.mtxgdn.oauth;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Set;

/**
 * OIDC 的 RSA 签名密钥。
 *
 * <h3>为什么单独一套密钥</h3>
 * <p>{@code JwtUtil} 用 HS256 <b>对称</b>密钥签业务 token —— 密钥同时用于签发和验证，
 * 所以它绝不能给出去。而 OIDC 的 {@code id_token} 必须让<b>第三方客户端</b>验证签名，
 * 这只能用非对称的 RS256：私钥留在服务端签，公钥通过 JWKS 公开。</p>
 *
 * <p>两套密钥互不相干。谁要是把 HS256 那个拿去当 OIDC 密钥，等于把签发权
 * 分给了所有能验签的人。</p>
 *
 * <h3>密钥必须持久化</h3>
 * <p>这是本类存在的核心理由。如果每次启动都生成新密钥对：</p>
 * <ul>
 *   <li>客户端缓存的 {@code id_token} 验签全部失败；</li>
 *   <li>更糟的是客户端可能缓存了 JWKS，用旧公钥去验新 token，
 *       表现为"登录时好时坏"，排查起来极其痛苦。</li>
 * </ul>
 * <p>所以密钥落在 {@code config/oidc-key.pem}，第一次启动生成，之后一直复用。</p>
 *
 * <h3>失败时的行为</h3>
 * <p>读不到或解析不了已有密钥文件时<b>直接抛异常</b>，不静默重新生成 ——
 * 静默换密钥会让所有正在依赖 OIDC 的集成突然全挂，而且没有任何提示。
 * 宁可启动失败让人看见。</p>
 */
public final class OidcKeyManager {

    /** 密钥文件名（放在 config 目录下，与配置文件同级） */
    private static final String KEY_FILE = "oidc-key.pem";

    /** RSA 位数。2048 是 OIDC 实践下限，4096 会明显拖慢每次签发 */
    private static final int KEY_SIZE = 2048;

    /** JSON Web Key 里的密钥 ID：用公钥摘要生成，密钥换了 kid 自然就变 */
    private static volatile String keyId;

    private static volatile RSAPublicKey publicKey;
    private static volatile RSAPrivateKey privateKey;

    private OidcKeyManager() {
    }

    /**
     * 加载或生成密钥对。
     *
     * <p>幂等：多次调用只做一次实际加载。</p>
     *
     * @param configDir {@code config} 目录
     * @throws IllegalStateException 已有密钥文件但无法解析（不回退到重新生成）
     */
    public static synchronized void init(Path configDir) {
        if (privateKey != null) {
            return;
        }
        Path keyFile = configDir.resolve(KEY_FILE);
        try {
            Files.createDirectories(configDir);
            if (Files.exists(keyFile)) {
                load(keyFile);
                System.out.println("[OIDC] ✓ 已加载签名密钥（kid=" + keyId + "）");
                return;
            }
            generateAndSave(keyFile);
            System.out.println("[OIDC] ✓ 已生成签名密钥并保存到 " + keyFile.toAbsolutePath()
                    + "（kid=" + keyId + "）");
        } catch (IOException e) {
            throw new IllegalStateException("[OIDC] 读写签名密钥失败: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "[OIDC] 签名密钥无法解析（" + keyFile.toAbsolutePath() + "）。"
                            + "请检查文件是否损坏；确认无误后可删除该文件让程序重新生成，"
                            + "但客户端需要重新获取 JWKS。原因: " + e.getMessage(), e);
        }
    }

    /** 是否已初始化（未初始化时 OIDC 端点应返回 503 而不是崩溃） */
    public static boolean isReady() {
        return privateKey != null && publicKey != null;
    }

    public static RSAPrivateKey privateKey() {
        RSAPrivateKey key = privateKey;
        if (key == null) {
            throw new IllegalStateException("[OIDC] 签名密钥未初始化");
        }
        return key;
    }

    public static RSAPublicKey publicKey() {
        RSAPublicKey key = publicKey;
        if (key == null) {
            throw new IllegalStateException("[OIDC] 公钥未初始化");
        }
        return key;
    }

    public static String keyId() {
        return keyId;
    }

    /**
     * 导出公钥的 JWK（JSON Web Key）。
     *
     * <p>只含公开参数 {@code n} 与 {@code e}，<b>绝不含</b> {@code d}/{@code p}/{@code q}
     * 这些私钥分量 —— 这个对象会被匿名访问的 {@code /jwks.json} 直接吐出去。</p>
     */
    public static String publicJwkJson() {
        RSAPublicKey pub = publicKey();
        // BigInteger.toByteArray() 可能带前导 0x00（符号位），JWK 要求无符号大端
        String n = base64Url(stripLeadingZero(pub.getModulus().toByteArray()));
        String e = base64Url(stripLeadingZero(pub.getPublicExponent().toByteArray()));
        return "{\"kty\":\"RSA\",\"use\":\"sig\",\"alg\":\"RS256\",\"kid\":\"" + keyId()
                + "\",\"n\":\"" + n + "\",\"e\":\"" + e + "\"}";
    }

    // ==================== 内部 ====================

    private static void generateAndSave(Path keyFile) throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(KEY_SIZE);
        KeyPair pair = gen.generateKeyPair();
        applyKeys((RSAPublicKey) pair.getPublic(), (RSAPrivateKey) pair.getPrivate());

        String pem = "# OIDC id_token 签名密钥 —— 泄露等同于任何人可以伪造登录身份\n"
                + "# 只是本机生成的一次性文件，请勿提交到版本库\n"
                + "-----BEGIN PRIVATE KEY-----\n"
                + wrap(Base64.getEncoder().encodeToString(privateKey().getEncoded()))
                + "-----END PRIVATE KEY-----\n"
                + "-----BEGIN PUBLIC KEY-----\n"
                + wrap(Base64.getEncoder().encodeToString(publicKey().getEncoded()))
                + "-----END PUBLIC KEY-----\n";
        Files.write(keyFile, pem.getBytes(StandardCharsets.UTF_8));
        restrictPermissions(keyFile);
    }

    private static void load(Path keyFile) throws Exception {
        String pem = Files.readString(keyFile, StandardCharsets.UTF_8);
        byte[] priv = decodePem(pem, "PRIVATE KEY");
        byte[] pub = decodePem(pem, "PUBLIC KEY");
        if (priv == null) {
            throw new IllegalStateException("缺少私钥段");
        }

        KeyFactory factory = KeyFactory.getInstance("RSA");
        RSAPrivateKey privKey = (RSAPrivateKey) factory.generatePrivate(new PKCS8EncodedKeySpec(priv));
        // 公钥段缺失时从私钥推导 —— RSA 私钥里含 n 与 e，能还原出公钥
        RSAPublicKey pubKey = pub != null
                ? (RSAPublicKey) factory.generatePublic(new X509EncodedKeySpec(pub))
                : derivePublic(privKey);
        applyKeys(pubKey, privKey);
    }

    private static void applyKeys(RSAPublicKey pub, RSAPrivateKey priv) throws NoSuchAlgorithmException {
        publicKey = pub;
        privateKey = priv;
        keyId = sha256Base64Url(pub.getEncoded(), 16);
    }

    /**
     * 从私钥推导公钥。
     *
     * <p>老版本文件可能只存了私钥段；能从私钥还原就不该因为缺公钥段而拒绝启动。</p>
     */
    private static RSAPublicKey derivePublic(RSAPrivateKey priv) {
        try {
            java.security.interfaces.RSAPrivateCrtKey crt =
                    (java.security.interfaces.RSAPrivateCrtKey) priv;
            return (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new java.security.spec.RSAPublicKeySpec(
                            priv.getModulus(), crt.getPublicExponent()));
        } catch (Exception e) {
            throw new IllegalStateException("[OIDC] 密钥文件缺少公钥段且无法从私钥推导", e);
        }
    }

    private static byte[] decodePem(String pem, String label) {
        String begin = "-----BEGIN " + label + "-----";
        String end = "-----END " + label + "-----";
        int s = pem.indexOf(begin);
        if (s < 0) {
            return null;
        }
        int e = pem.indexOf(end, s);
        if (e < 0) {
            return null;
        }
        String body = pem.substring(s + begin.length(), e).replaceAll("\\s", "");
        return Base64.getDecoder().decode(body);
    }

    private static String wrap(String base64) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < base64.length(); i += 64) {
            sb.append(base64, i, Math.min(i + 64, base64.length())).append('\n');
        }
        return sb.toString();
    }

    private static byte[] stripLeadingZero(byte[] bytes) {
        if (bytes.length > 1 && bytes[0] == 0) {
            byte[] out = new byte[bytes.length - 1];
            System.arraycopy(bytes, 1, out, 0, out.length);
            return out;
        }
        return bytes;
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256Base64Url(byte[] input, int chars) throws NoSuchAlgorithmException {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(input);
        String full = base64Url(digest);
        return full.substring(0, Math.min(chars, full.length()));
    }

    /**
     * 尽力收紧文件权限。
     *
     * <p>私钥文件被同机其他用户读到，等于可以伪造任意玩家的登录身份。
     * Windows 上 setPosixFilePermissions 会抛 UnsupportedOperationException，
     * 忽略即可 —— 那边靠的是用户目录本身的 ACL。</p>
     */
    private static void restrictPermissions(Path file) {
        try {
            Files.setPosixFilePermissions(file, Set.of(
                    PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException | IOException ignored) {
            // 非 POSIX 文件系统，跳过
        }
    }

    /**
     * 清空内存状态（不删文件）。
     *
     * <p>仅供测试模拟"重启"用。生产代码没有理由调用它 —— 清掉之后拿不到私钥，
     * 直到下次 {@link #init(java.nio.file.Path)} 才能再签发 id_token。</p>
     */
    public static void resetForTest() {
        privateKey = null;
        publicKey = null;
        keyId = null;
    }
}
