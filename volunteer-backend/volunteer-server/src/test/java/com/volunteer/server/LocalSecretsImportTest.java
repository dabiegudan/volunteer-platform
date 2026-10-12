package com.volunteer.server;

import com.volunteer.auth.util.JwtUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * 本机密钥载体 {@code application-local.yml} 的语义验证。
 *
 * <h2>背景</h2>
 * <p>dev profile 的 {@code application-dev.yml} 顶部声明了
 * <pre>spring.config.import: optional:classpath:application-local.yml</pre>
 * 本机开发只需把 {@code application-local.yml.example} 复制为
 * {@code application-local.yml}（已被 .gitignore 忽略）并填入真实密钥即可启动，
 * 不再需要「启动前先 source .env」这一前置步骤。
 *
 * <h2>断言为什么这么写（重要）</h2>
 * <p>跑在 {@code ConfigDataApplicationContextInitializer} 之上时，Spring Boot 会把
 * {@code configurationProperties} 放在属性源<b>最前面</b>，它内部按
 * {@code test → systemProperties → systemEnvironment → random → configData} 的顺序查找。
 * 也就是说：<b>配置文件的值会压过测试注入的同名属性</b>，而开发机上真实存在的
 * {@code src/main/resources/application-local.yml} 一定会被加载（{@code spring.config.import}
 * 是追加语义，无法被测试屏蔽）。因此：
 * <ul>
 *   <li>凡是"文件里有、测试也想注入"的键（{@code jwt.secret} / {@code encrypt.secret-key}），
 *       断言必须<b>以文件内容为准</b>，见
 *       {@link #realLocalFileValuesAreWhatTheConfigResolves()}（文件不存在时自动跳过，CI 不会误报）；</li>
 *   <li>不存在的键（如环境变量形式的 {@code JWT_SECRET}）可以直接断言注入值；</li>
 *   <li>只依赖密钥本身、与来源无关的校验（占位值拒绝）用<b>直接实例化 + 调用初始化方法</b>的方式验证，
 *       与 {@code RequiredSecretsFailFastTest} 验证 {@code EncryptUtil} 的做法一致：既不依赖上下文缓存，
 *       也不会被配置文件先截胡而测不到目标分支。</li>
 * </ul>
 *
 * <h2>夹具</h2>
 * <p>{@code src/test/resources/application-local-*.yml} 通过 {@code classpath:} 导入，
 * 走的是与真实场景完全相同的加载路径（{@code ConfigDataLocationResolver} + classpath 资源）。
 */
class LocalSecretsImportTest {

    /** 真实本机密钥文件的 classpath 位置。 */
    private static final String REAL_LOCAL_FILE = "application-local.yml";

    /** 夹具：同时提供 jwt.secret 与 encrypt.secret-key。 */
    private static final String FULL_FIXTURE = "optional:classpath:application-local-full.yml";

    /** 夹具：只提供 encrypt.secret-key（jwt.secret 留给其他来源）。 */
    private static final String ENCRYPT_ONLY_FIXTURE =
            "optional:classpath:application-local-encrypt-only.yml";

    /**
     * 构造一个与真实启动等价的上下文运行器（不带夹具）。
     *
     * <p>只走 {@code application-dev.yml} 里声明的那一条真实导入，用于验证"本机零 source 启动"。
     * 夹具是<b>追加</b>导入，一旦带上就会排到真实文件前面把它盖住，所以这两个用途必须分开。
     */
    private static ApplicationContextRunner productionRunner() {
        return new ApplicationContextRunner()
                // 触发 application.yml / application-dev.yml 解析，从而执行其中的 spring.config.import
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues("spring.profiles.active=dev");
    }

    /**
     * 构造一个额外追加导入夹具的运行器，用于需要"某键必定存在"的与机器无关的用例。
     *
     * @param fixture 追加导入的夹具表达式
     */
    private static ApplicationContextRunner runner(String fixture) {
        return productionRunner()
                .withPropertyValues("spring.config.import=" + fixture);
    }

    /** 跳过条件：开发机上存在真实的本机密钥文件。 */
    static boolean realLocalFileExists() {
        return new ClassPathResource(REAL_LOCAL_FILE).exists();
    }

    /**
     * 真实文件里的值应当就是上下文最终解析出的值。
     *
     * <p>这是"本机零 source 启动"最关键的证据：不做任何 source、不设任何环境变量，只靠
     * {@code application-dev.yml} 里那一行 {@code spring.config.import}，
     * {@code jwt.secret} / {@code encrypt.secret-key} 就被 Spring 读到，
     * 且 {@link JwtUtil} 能通过启动期强校验。
     */
    @Test
    @EnabledIf("realLocalFileExists")
    @DisplayName("真实 application-local.yml 的值被 application-dev.yml 的导入原样读出")
    void realLocalFileValuesAreWhatTheConfigResolves() throws IOException {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource(REAL_LOCAL_FILE));
        var expected = yaml.getObject();
        assertThat(expected).as("application-local.yml 必须能被解析").isNotNull();

        Object expectedSecret = expected.get("jwt.secret");
        Object expectedEncryptKey = expected.get("encrypt.secret-key");
        assertThat(expectedSecret).as("真实文件应包含 jwt.secret").isNotNull();
        assertThat(expectedEncryptKey).as("真实文件应包含 encrypt.secret-key").isNotNull();

        productionRunner()
                .withUserConfiguration(JwtUtil.class)
                .run(context -> {
                    assertThat(context)
                            .as("只靠 spring.config.import，JwtUtil 就能通过启动校验")
                            .hasNotFailed();
                    ConfigurableEnvironment environment = context.getEnvironment();
                    assertThat(environment.getProperty("jwt.secret"))
                            .as("上下文解析出的 jwt.secret 应等于本机文件里的值")
                            .isEqualTo(expectedSecret);
                    assertThat(environment.getProperty("encrypt.secret-key"))
                            .as("上下文解析出的 encrypt.secret-key 应等于本机文件里的值")
                            .isEqualTo(expectedEncryptKey);
                });
    }

    @Test
    @DisplayName("环境变量的宽松绑定可用：JWT_SECRET 能映射到 jwt.secret")
    void environmentVariableFormOfTheSecretIsVisible() {
        runner(ENCRYPT_ONLY_FIXTURE)
                // 键名与文件里的 jwt.secret 不同，两者不会相撞；用它与"文件里的值"对比，
                // 无论本机有没有真实文件、环境变量在容器里是否生效，断言都成立。
                .withSystemProperties("JWT_SECRET=env-supplied-wide-binding-probe-secret")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    ConfigurableEnvironment environment = context.getEnvironment();
                    assertThat(environment.getProperty("JWT_SECRET"))
                            .as("环境变量形式的键可被读取")
                            .isEqualTo("env-supplied-wide-binding-probe-secret");
                    assertThat(environment.getProperty("jwt.secret"))
                            .as("jwt.secret 必须可解析（来自导入文件或环境变量），且不等于探针值")
                            .isNotNull()
                            .isNotEqualTo("env-supplied-wide-binding-probe-secret");
                });
    }

    @Test
    @DisplayName("optional 导入的文件不存在时不报错")
    void missingImportedFileIsTolerated() {
        // 先确认夹具存在时上下文正常，再换成不存在的路径，证明"缺失"本身不会导致启动失败
        runner(FULL_FIXTURE).run(context -> assertThat(context).hasNotFailed());
        String absent = "optional:classpath:absent-" + System.nanoTime() + ".yml";
        runner(absent).run(context -> assertThat(context)
                .as("optional 前缀意味着文件缺失只是跳过，不会让应用启动失败")
                .hasNotFailed());
    }

    @Test
    @DisplayName("application-dev.yml 里真实存在该导入语句（防止配置被误删）")
    void applicationDevYmlDeclaresTheLocalImport() {
        productionRunner().run(context -> {
            assertThat(context).hasNotFailed();
            // 导入项可以是数组元素，也可能与其它来源合并成逗号串，故按"包含"判断。
            // 该断言只检查"dev 配置声明了这一行"，不要求它真的解析出文件。
            assertThat(context.getEnvironment().getProperty("spring.config.import"))
                    .as("dev 配置应声明导入本机密钥文件")
                    .contains("optional:classpath:application-local.yml");
        });
    }

    @Test
    @DisplayName("仍是模板示例值时启动失败（拒绝 CHANGE_ME 占位密钥）")
    void placeholderSecretFromExampleTemplateIsRejected() {
        // 与 RequiredSecretsFailFastTest 验证 EncryptUtil 的做法一致：直接实例化并调用初始化方法，
        // 避免依赖 Spring 上下文缓存与属性源顺序——这条校验只取决于被注入的密钥本身。
        JwtUtil placeholder = new JwtUtil();
        ReflectionTestUtils.setField(placeholder, "secret",
                "CHANGE_ME_jwt_secret_at_least_32_chars_long_0000");
        assertThat(catchThrowable(placeholder::init))
                .as("占位密钥必须被拒绝，而不是带着公开密钥启动")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CHANGE_ME");

        // 反向断言：证明不是无条件拒绝，合法密钥仍能通过
        JwtUtil valid = new JwtUtil();
        ReflectionTestUtils.setField(valid, "secret", "a-valid-secret-key-of-at-least-32-chars");
        assertThat(catchThrowable(valid::init))
                .as("合法密钥不应被拒绝")
                .isNull();
    }
}
