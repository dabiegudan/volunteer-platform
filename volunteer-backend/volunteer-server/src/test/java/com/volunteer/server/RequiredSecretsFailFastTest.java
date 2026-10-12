package com.volunteer.server;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.support.GenericApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 必填配置项的「快速失败」验证。
 *
 * <h2>为什么需要</h2>
 * <p>阶段一移除了两个危险的内置默认值：
 * <ul>
 *   <li>{@code jwt.secret} —— 原先源码内硬编码默认密钥，等于向所有人公开签发凭证的能力；</li>
 *   <li>{@code encrypt.secret-key} —— 原先 {@code EncryptUtil} 中硬编码 AES 密钥，
 *       任何人拿到仓库即可解密身份证号等敏感字段。</li>
 * </ul>
 * <p>移除后必须确认：<b>未提供这些配置时应用确实启动失败</b>，
 * 而不是静默退化到某个不安全的值。本测试即锁定该行为，
 * 防止后续有人「为了方便」又把默认值加回去。
 *
 * <h2>实现方式</h2>
 * <p>使用 {@link ApplicationContextRunner} 驱动真实上下文，并直接调用
 * {@code setSecretKey} 验证注入路径，避免依赖 Spring 测试上下文缓存。
 */
class RequiredSecretsFailFastTest {

    /** 与 application.yml 中密钥无默认值的语义保持一致。 */
    private ApplicationContextRunner baseRunner(String dbName) {
        return new ApplicationContextRunner()
                .withPropertyValues(
                        "spring.datasource.url=jdbc:h2:mem:" + dbName + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
                        "spring.datasource.driver-class-name=org.h2.Driver",
                        "spring.datasource.username=sa",
                        "spring.datasource.password=",
                        "llm.api-key=test-key"
                );
    }

    @Test
    @DisplayName("缺失 JWT_SECRET 时上下文启动失败（不再有源码内置默认值）")
    void failsWhenJwtSecretMissing() {
        // JwtUtil 的注入表达式是 ${jwt.secret:}（无默认值），application.yml
        // 已不再声明 jwt.secret：本 runner 不加载 application-dev.yml，
        // 因此也不会导入 application-local.yml，解析结果为空 → 启动失败
        baseRunner("failfast_jwt_missing")
                .withUserConfiguration(com.volunteer.auth.util.JwtUtil.class)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).isNotNull();
                    assertThat(rootCause(context.getStartupFailure()).getMessage())
                            .as("失败原因应指向 jwt secret 配置")
                            .containsIgnoringCase("jwt");
                });
    }

    @Test
    @DisplayName("JWT 签名密钥长度不足 32 字符时上下文启动失败")
    void failsWhenJwtSecretTooShort() {
        baseRunner("failfast_jwt_short")
                .withPropertyValues("jwt.secret=too-short")
                .withUserConfiguration(com.volunteer.auth.util.JwtUtil.class)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(rootCause(context.getStartupFailure()).getMessage())
                            .containsIgnoringCase("32");
                });
    }

    @Test
    @DisplayName("缺失 ENCRYPT_SECRET_KEY 时 EncryptUtil 初始化失败（不再有硬编码密钥兜底）")
    void failsWhenEncryptSecretMissing() {
        baseRunner("failfast_encrypt_missing")
                .withPropertyValues("encrypt.secret-key=")
                .withUserConfiguration(com.volunteer.common.util.EncryptUtil.class)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(rootCause(context.getStartupFailure()).getMessage())
                            .as("失败原因应指向 encrypt.secret-key 配置")
                            .contains("encrypt.secret-key");
                });
    }

    @Test
    @DisplayName("提供合法密钥时 EncryptUtil 可正常加解密（密钥来自 Spring 配置，而非硬编码）")
    void encryptUtilWorksWithSpringProvidedKey() {
        // 34 字符，满足 >= 16 的长度要求
        String key = "spring-provided-aes-key-34-char!!!";

        try (GenericApplicationContext context = new GenericApplicationContext()) {
            TestPropertyValues.of("encrypt.secret-key=" + key).applyTo(context);

            String resolved = context.getEnvironment().getProperty("encrypt.secret-key");
            assertThat(resolved)
                    .as("Spring 应能解析出 encrypt.secret-key")
                    .isNotNull();
            // TestPropertyValues 会在 "=" 后保留一个空格，此处去除以模拟真实 YAML 取值
            String normalized = resolved.trim();

            com.volunteer.common.util.EncryptUtil util =
                    new com.volunteer.common.util.EncryptUtil();
            util.setSecretKey(normalized);

            String plain = "110101199001011234";
            String cipher = com.volunteer.common.util.EncryptUtil.encrypt(plain);
            assertThat(cipher)
                    .as("密文不应等于明文")
                    .isNotEqualTo(plain);
            assertThat(com.volunteer.common.util.EncryptUtil.decrypt(cipher))
                    .as("使用同一密钥应能正确解密")
                    .isEqualTo(plain);
        }
    }

    @Test
    @DisplayName("EncryptUtil 在密钥未配置或过短时立即失败，而不是使用兜底密钥")
    void encryptUtilFailsWithoutKey() {
        com.volunteer.common.util.EncryptUtil util =
                new com.volunteer.common.util.EncryptUtil();

        // 空密钥：注入阶段就必须快速失败
        assertThat(catchThrowable(() -> util.setSecretKey("")))
                .as("空密钥必须被拒绝")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("encrypt.secret-key");

        // 密钥过短同样必须被拒绝
        assertThat(catchThrowable(() -> util.setSecretKey("short")))
                .as("过短密钥必须被拒绝")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("长度不足");

        // 合法密钥应被接受（证明校验不是无条件拒绝）
        assertThat(catchThrowable(() -> util.setSecretKey("a-valid-key-of-at-least-16-chars")))
                .as("合法密钥不应被拒绝")
                .isNull();
    }

    private Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static Throwable catchThrowable(ThrowingRunnable runnable) {
        try {
            runnable.run();
            return null;
        } catch (Throwable t) {
            return t;
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Throwable;
    }
}

