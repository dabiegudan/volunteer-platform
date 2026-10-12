package com.volunteer.common.util;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 敏感字段加解密工具（AES-256-GCM）。
 *
 * <h2>密钥来源（重要）</h2>
 * <p>密钥由 Spring 从配置项 {@code encrypt.secret-key} 注入，因此以下所有方式都能生效：
 * <ul>
 *   <li>环境变量 {@code ENCRYPT_SECRET_KEY}（容器由 docker-compose 注入 / 本机由 .env 注入）</li>
 *   <li>{@code application-local.yml}（仅 dev profile 通过 {@code spring.config.import} 加载，
 *       文件已被 .gitignore 忽略，适合本机开发；优先级高于 application.yml、低于环境变量）</li>
 *   <li>{@code application.yml} 中的 {@code encrypt.secret-key}</li>
 *   <li>命令行参数 {@code --encrypt.secret-key=...}</li>
 * </ul>
 *
 * <p><b>历史实现的问题：</b>旧版本在 {@code static} 块中直接用
 * {@code System.getenv("ENCRYPT_SECRET_KEY")} 读取密钥，并以硬编码字符串兜底。这导致：
 * <ol>
 *   <li>硬编码密钥随源码入库，任何人拿到仓库即可解密身份证号等敏感字段；</li>
 *   <li>完全不读 Spring 配置，{@code encrypt.secret-key} 与 {@code ENCRYPT_SECRET_KEY}
 *       两处配置对加密逻辑无效，易出现「加密用 A 密钥、解密用 B 密钥」的静默故障。</li>
 * </ol>
 *
 * <h2>更换密钥的影响</h2>
 * <p>更换 {@code encrypt.secret-key} 会导致历史已加密字段无法解密。
 * 必须配套数据迁移：先用旧密钥批量解密，再用新密钥批量加密。
 *
 * <h2>静态方法说明</h2>
 * <p>{@link com.volunteer.common.interceptor.EncryptInterceptor} 等 MyBatis 插件在非 Spring
 * 管理上下文中调用本类，故保留静态方法签名；密钥仍由 Spring 通过 {@link #setSecretKey(String)}
 * 统一注入，保证单一来源。
 */
@Component
public class EncryptUtil {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    /** 密钥最小长度（AES-128 起）。 */
    private static final int MIN_KEY_LENGTH = 16;

    /**
     * 密文版本前缀。
     *
     * <p>引入前缀是为了让「已加密」与「历史明文」可区分，从而支持幂等加密与平滑迁移：
     * <ul>
     *   <li>写入前先判断是否已带前缀，避免重复加密（重复加密会让数据彻底无法还原）；</li>
     *   <li>读取时若没有前缀，说明是历史明文数据，原样返回而不是抛异常，
     *       否则上线加密功能的那一刻所有存量行都会读失败。</li>
     * </ul>
     */
    public static final String ENCRYPTED_PREFIX = "enc:v1:";

    /** 由 Spring 注入的密钥；未注入时保持 null，调用加解密会快速失败。 */
    private static volatile String secretKey;

    /**
     * 由 Spring 注入 {@code encrypt.secret-key}。
     *
     * <p>使用实例方法注入而非 {@code static} 字段注入（Spring 不支持 static 字段注入），
     * 注入结果写入静态变量供静态方法使用。
     *
     * @param key 配置项 encrypt.secret-key 的值
     * @throws IllegalStateException 密钥为空或长度不足 16 个字符
     */
    @Value("${encrypt.secret-key:}")
    public void setSecretKey(String key) {
        if (key == null || key.isEmpty()) {
            throw new IllegalStateException(
                    "encrypt.secret-key 未配置：请设置环境变量 ENCRYPT_SECRET_KEY（至少 16 个字符）");
        }
        if (key.length() < MIN_KEY_LENGTH) {
            throw new IllegalStateException(
                    "encrypt.secret-key 长度不足：当前 " + key.length()
                            + " 个字符，至少需要 " + MIN_KEY_LENGTH + " 个字符");
        }
        secretKey = key;
    }

    @PostConstruct
    void init() {
        // 触发一次长度自检；密钥内容不写入任何日志
        if (secretKey == null) {
            throw new IllegalStateException("encrypt.secret-key 注入失败");
        }
    }

    private static String requireKey() {
        String key = secretKey;
        if (key == null || key.isEmpty()) {
            throw new IllegalStateException(
                    "加密密钥尚未初始化：EncryptUtil 必须由 Spring 容器管理，"
                            + "请确认已配置 encrypt.secret-key");
        }
        return key;
    }

    /**
     * 从配置的密钥字符串派生合规的 AES-256 密钥（32 字节）。
     *
     * <p><b>为什么必须派生：</b>JDK 的 AES 实现只接受 16 / 24 / 32 字节的密钥长度。
     * 历史实现直接把 {@code SECRET_KEY.getBytes()} 交给 {@link SecretKeySpec}，
     * 因此只要配置的密钥不是恰好这三个长度（例如原有的 29 字符默认密钥），
     * 加解密都会在运行期抛 {@code InvalidKeyException: Invalid AES key length}——
     * 这是一个此前被忽略的真实缺陷。
     *
     * <p>改用 SHA-256 摘要派生后，任意长度（≥16 字符）的配置值都能得到固定 32 字节的
     * AES-256 密钥，且同一配置值始终派生出同一密钥，保证历史数据可解密。
     *
     * @return 32 字节密钥材料
     */
    private static byte[] deriveKey() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(requireKey().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("加密密钥派生失败", e);
        }
    }

    /**
     * 幂等加密：已加密（带 {@link #ENCRYPTED_PREFIX}）的值原样返回。
     *
     * <p>写侧必须使用本方法而不是 {@link #encrypt(String)}，否则「更新一个已加密实体」
     * 会把密文当明文再加密一次，数据永久不可还原。</p>
     */
    public static String encryptIfNeeded(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return plainText;
        }
        if (isEncrypted(plainText)) {
            return plainText;
        }
        return ENCRYPTED_PREFIX + encrypt(plainText);
    }

    /**
     * 宽容解密：非密文（历史明文数据）原样返回，解密失败也回退为原值。
     *
     * <p>用于读侧：加密功能上线时库里必然存在未加密的存量行，此时必须「能读」而不是抛异常。
     * 解密失败通常是密钥轮换后的旧数据，回退原值至少能让接口可用，便于运维排查。</p>
     */
    public static String decryptIfNeeded(String value) {
        if (value == null || value.isEmpty() || !isEncrypted(value)) {
            return value;
        }
        try {
            return decrypt(value.substring(ENCRYPTED_PREFIX.length()));
        } catch (RuntimeException e) {
            return value;
        }
    }

    /** 判断值是否为带版本前缀的密文。 */
    public static boolean isEncrypted(String value) {
        return value != null && value.startsWith(ENCRYPTED_PREFIX);
    }

    /**
     * 加密明文。
     *
     * @param plainText 明文，null 或空串原样返回
     * @return Base64(IV + 密文)，密钥错误时抛出异常
     */
    public static String encrypt(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return plainText;
        }

        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            SecureRandom random = new SecureRandom();
            random.nextBytes(iv);

            SecretKeySpec keySpec = new SecretKeySpec(deriveKey(), ALGORITHM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec);

            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            // 不把密钥或明文带入异常信息
            throw new IllegalStateException("加密失败", e);
        }
    }

    /**
     * 解密密文。
     *
     * @param encryptedText Base64(IV + 密文)，null 或空串原样返回
     * @return 明文
     */
    public static String decrypt(String encryptedText) {
        if (encryptedText == null || encryptedText.isEmpty()) {
            return encryptedText;
        }

        try {
            byte[] combined = Base64.getDecoder().decode(encryptedText);

            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] encrypted = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(combined, GCM_IV_LENGTH, encrypted, 0, encrypted.length);

            SecretKeySpec keySpec = new SecretKeySpec(deriveKey(), ALGORITHM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);

            byte[] decrypted = cipher.doFinal(encrypted);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("解密失败：密钥不匹配或数据已损坏", e);
        }
    }
}
