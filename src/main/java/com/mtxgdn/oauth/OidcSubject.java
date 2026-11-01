package com.mtxgdn.oauth;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * OIDC 的 {@code sub}（Subject）值。
 *
 * <h3>为什么不能直接用 user_id 或 username</h3>
 * <ul>
 *   <li><b>user_id</b> 是自增主键。数据库重建、换服迁移后同一个 id 会指向另一个人，
 *       而客户端那边还存着旧映射 —— 结果是<b>账号串号</b>：A 的客户端身份登进了 B。</li>
 *   <li><b>username</b> 一旦将来支持改名就会变，等于换了个新账号；
 *       而且它是可预测的 —— 攻击者能抢先用别人的名字完成一次授权，
 *       把对方的 sub 抢注到自己客户端账号下。</li>
 * </ul>
 *
 * <h3>本方案</h3>
 * <p>{@code sub = "uid_" + 标准化用户名 + "_" + HMAC(用户名)}。三重考虑：</p>
 * <ul>
 *   <li><b>前缀带用户名</b>：出问题时能在日志里一眼看出是谁，不必查表；</li>
 *   <li><b>后缀是 HMAC</b>：即使有人猜到用户名，也算不出对应 sub，
 *       所以无法抢注 —— 而它又只由用户名决定，所以是稳定的；</li>
 *   <li><b>用用户名而非 id 做输入</b>：id 会变（迁移），用户名在"不支持改名"的
 *       现有约束下稳定，且 HMAC 本身就是防猜测的那一层。</li>
 * </ul>
 *
 * <p>HMAC 的密钥来自 OIDC 私钥的 SHA-256 派生 —— 不额外引入需要保管的秘密，
 * 又保证了换服务端密钥时整个 sub 空间一起变（这是一致的，客户端会重新绑定）。</p>
 *
 * <h3>改名的情况</h3>
 * <p>本实现不接受改名（系统中没有改名入口）。如果将来加了这个功能，
 * 必须在改名时同时通知客户端，否则对方的 sub 会变、变成新账号 ——
 * 这正是本类注释要留下警告的原因。</p>
 */
public final class OidcSubject {

    private static final String PREFIX = "uid_";

    private OidcSubject() {
    }

    /**
     * 计算某用户的 sub。
     *
     * @param username 用户名（大小写敏感 —— 登录本身就是大小写敏感的）
     * @return 形如 {@code uid_张三_a1b2c3d4e5f6g7h8} 的字符串
     */
    public static String of(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        return PREFIX + username + "_" + hmac(username);
    }

    /**
     * 校验一个 sub 是否确实由本服务为指定用户名签发。
     *
     * <p>用 {@link MessageDigest#isEqual} 做常量时间比较 —— sub 会出现在
     * 客户端配置和日志里，普通字符串比较存在时序侧信道。虽然此处的风险很低，
     * 但成本几乎为零，没有理由不做好。</p>
     */
    public static boolean verify(String sub, String username) {
        if (sub == null || username == null) {
            return false;
        }
        String expected = of(username);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                sub.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * HMAC-SHA256(用户名)，取前 16 个 base64url 字符。
     *
     * <p>16 字符 ≈ 96 位，碰撞概率可忽略；再长只会让 sub 在客户端界面上更难显示。</p>
     */
    private static String hmac(String username) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(derivedKey(), "HmacSHA256"));
            byte[] digest = mac.doFinal(username.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest).substring(0, 16);
        } catch (Exception e) {
            throw new IllegalStateException("[OIDC] 计算 sub 失败: " + e.getMessage(), e);
        }
    }

    /**
     * 从 RSA 私钥派生 HMAC 密钥。
     *
     * <p>不直接把私钥当 HMAC 密钥用 —— 那会形成"同一密钥跨算法复用"，
     * 是密码学上的忌讳。这里先做一次 SHA-256 域分离。</p>
     */
    private static byte[] derivedKey() throws Exception {
        byte[] raw = OidcKeyManager.privateKey().getEncoded();
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        // 域分隔标签，避免生成的密钥与其它用途的派生值撞车
        sha.update("xiuxian-oidc-sub-v1".getBytes(StandardCharsets.UTF_8));
        return sha.digest(raw);
    }
}
