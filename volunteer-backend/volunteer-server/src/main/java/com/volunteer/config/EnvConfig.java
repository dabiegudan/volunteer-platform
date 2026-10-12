package com.volunteer.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 环境变量读取与启动期校验。
 *
 * <p>本类演示并统一实现本项目推荐的两种环境变量读取方式，同时承担"配置自检"职责。
 *
 * <h2>方式一：{@code @Value} 注解（推荐用于固定、少量的配置项）</h2>
 * <ul>
 *   <li>语法：{@code @Value("${ENV_VAR:default}")}，冒号后为默认值</li>
 *   <li>支持松散绑定：环境变量 {@code LLM_API_KEY} 对应属性 {@code llm.api-key}</li>
 *   <li>优点：声明直观、类型转换自动完成、便于 IDE 检索</li>
 *   <li>缺点：无法在运行时动态获取任意键</li>
 * </ul>
 *
 * <h2>方式二：{@code Environment} 接口（推荐用于动态键、批量校验场景）</h2>
 * <ul>
 *   <li>通过 {@link Environment#getProperty(String)} 按需读取</li>
 *   <li>优点：可遍历、可做存在性判断，适合本类的批量配置自检</li>
 *   <li>缺点：键名以字符串书写，缺少编译期检查</li>
 * </ul>
 *
 * <h2>关键约定：本项目不加载 .env 文件</h2>
 * <p>Spring Boot 原生只从"操作系统环境变量 + 配置文件"读取配置。项目未引入
 * spring-dotenv / dotenv-java 等依赖，因此 {@code .env} 不会被应用直接读取：
 * <ul>
 *   <li>容器场景：由 docker-compose 的 {@code environment} 段注入为容器环境变量</li>
 *   <li>本机场景（推荐）：写入 {@code application-local.yml}，dev profile 通过
 *       {@code spring.config.import} 加载（该文件已被 .gitignore 忽略）</li>
 *   <li>本机场景（兼容旧流程）：由 scripts/load-env.ps1 或 load-env.sh 导入当前 shell 环境</li>
 * </ul>
 * <p>环境变量优先级始终高于 {@code application-local.yml}，两者同时存在时以环境变量为准。
 *
 * <h2>安全约定</h2>
 * <p>本类只判断"是否配置"与"长度是否达标"，<b>绝不打印密钥内容</b>，
 * 也不把敏感值写入日志或异常信息，避免密钥通过日志泄漏。
 *
 * @author Volunteer Platform Team
 */
@Slf4j
@Component
public class EnvConfig {

    /** JWT 密钥最小长度（与 JwtUtil 的强校验保持一致）。 */
    private static final int MIN_SECRET_LENGTH = 32;

    /** 明文占位符黑名单：出现这些值说明使用者忘记替换示例值。 */
    private static final List<String> PLACEHOLDERS = List.of(
            "change-me",
            "your-api-key-here",
            "your-secret-here",
            "replace-me",
            "changeme",
            // 历史版本曾把该值作为 JWT 默认密钥写入源码，现已移除；此处兜底拦截
            "VolunteerPlatformSecretKey2024",
            // 历史版本曾作为 AES 默认密钥
            "VolunteerService2024!SecureKey"
    );
    // ==========================================================================
    // 方式一：@Value 注解读取
    // ==========================================================================

    /** 激活的 Profile（dev / prod）。 */
    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    /** 数据库账号（@Value 直读，仅用于自检日志，不输出口令）。 */
    @Value("${spring.datasource.username:}")
    private String datasourceUsername;

    /** 数据库口令：只判断是否存在，绝不打印。 */
    @Value("${spring.datasource.password:}")
    private String datasourcePassword;

    /** JWT 密钥：只校验长度，绝不打印。 */
    @Value("${jwt.secret:}")
    private String jwtSecret;

    /** 数据加密密钥：只判断是否存在，绝不打印。 */
    @Value("${encrypt.secret-key:}")
    private String encryptSecretKey;

    /** MinIO 访问密钥：只判断是否存在，绝不打印。 */
    @Value("${minio.accessKey:}")
    private String minioAccessKey;

    /** MinIO 秘密密钥：只判断是否存在，绝不打印。 */
    @Value("${minio.secretKey:}")
    private String minioSecretKey;

    /** MinIO 服务地址（非敏感，可安全输出）。 */
    @Value("${minio.endpoint:}")
    private String minioEndpoint;

    /** MinIO 桶名（非敏感）。 */
    @Value("${minio.bucketName:}")
    private String minioBucket;

    /** 大模型密钥：只判断是否存在，绝不打印。 */
    @Value("${llm.api-key:}")
    private String llmApiKey;

    /** 大模型服务地址（非敏感）。 */
    @Value("${llm.base-url:}")
    private String llmBaseUrl;

    /** 大模型名称（非敏感）。 */
    @Value("${llm.model:}")
    private String llmModel;

    /** 数据库迁移开关（Flyway）。 */
    @Value("${spring.flyway.enabled:true}")
    private boolean flywayEnabled;

    /** Flyway 基线版本，用于启动自检时确认基线方案配置未被误改。 */
    @Value("${spring.flyway.baseline-version:1}")
    private String flywayBaselineVersion;

    // ==========================================================================
    // 方式二：Environment 接口读取
    // ==========================================================================

    private final Environment environment;

    public EnvConfig(Environment environment) {
        this.environment = environment;
    }

    /**
     * 读取原始环境变量（非配置属性），返回 {@code null} 表示未设置。
     *
     * <p>与 {@code getProperty("jwt.secret")} 的区别：本方法只查操作系统环境变量，
     * 不会回落到配置文件中的默认值，因此可以准确判断"使用方是否显式提供了该变量"。
     *
     * @param name 环境变量名（大写 + 下划线）
     * @return 变量值，未设置时为 {@code null}
     */
    public String getRawEnv(String name) {
        return environment.getProperty(name);
    }

    /** 判断原始环境变量是否存在且非空白。 */
    public boolean hasRawEnv(String name) {
        return StringUtils.hasText(getRawEnv(name));
    }

    /**
     * 按属性名读取配置（等价于 {@code @Value} 的能力，但可在运行时动态指定键）。
     *
     * @param propertyKey 属性名，如 {@code llm.timeout}
     * @param targetType  目标类型
     */
    public <T> T getProperty(String propertyKey, Class<T> targetType) {
        return environment.getProperty(propertyKey, targetType);
    }

    // ==========================================================================
    // 启动期配置自检
    // ==========================================================================

    /**
     * 应用启动时执行配置自检。
     *
     * <p>校验策略分三档：
     * <ol>
     *   <li><b>致命</b>：JWT 密钥缺失、过短或仍为占位符 —— 直接抛异常终止启动，
     *       避免带着无效密钥对外提供服务。</li>
     *   <li><b>告警</b>：LLM 密钥缺失或仍为占位符 —— 仅打印警告，
     *       因为 AI 不是主流程强依赖，其余功能可正常工作。</li>
     *   <li><b>提示</b>：生产环境下其他敏感项使用了默认值 —— 打印风险提示。</li>
     * </ol>
     */
    @PostConstruct
    public void validate() {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // ---- 1. JWT 密钥：致命校验 ----
        // 已彻底移除源码内置默认密钥，缺失即快速失败。
        // 注意 jwt.secret 不来自 application*.yml（那里刻意不写，见 application-dev.yml 注释），
        // 而是来自环境变量 JWT_SECRET（宽松绑定）或 dev 下 spring.config.import 的
        // application-local.yml，因此报错文案要把两个来源都提示出来。
        if (!StringUtils.hasText(jwtSecret)) {
            errors.add("JWT 签名密钥未配置：本机开发请在 src/main/resources/application-local.yml "
                    + "填写 jwt.secret（复制 application-local.yml.example 得到），"
                    + "或设置环境变量 JWT_SECRET（长度 ≥ 32 字符）");
        } else if (jwtSecret.length() < MIN_SECRET_LENGTH) {
            errors.add("JWT 签名密钥长度不足：当前 " + jwtSecret.length()
                    + " 字符，最少需要 " + MIN_SECRET_LENGTH + " 字符");
        } else if (isPlaceholder(jwtSecret)) {
            errors.add("JWT 签名密钥仍为示例占位值：请替换为随机生成的强密钥");
        }

        // ---- 2. 大模型密钥：告警级 ----
        if (!StringUtils.hasText(llmApiKey)) {
            warnings.add("LLM_API_KEY 未配置：/api/ai/** 全部接口将在调用时失败，"
                    + "其余功能不受影响");
        } else if (isPlaceholder(llmApiKey)) {
            warnings.add("LLM_API_KEY 仍为示例占位值：请替换为真实密钥");
        }

        // ---- 3. 其余敏感项：告警级 ----
        checkSecret(datasourcePassword, "数据库口令（SPRING_DATASOURCE_PASSWORD）", warnings);
        checkSecret(encryptSecretKey, "数据加密密钥（ENCRYPT_SECRET_KEY）", warnings);
        checkSecret(minioSecretKey, "MinIO 秘密密钥（MINIO_SECRET_KEY）", warnings);

        if (!StringUtils.hasText(minioAccessKey)) {
            warnings.add("MINIO_ACCESS_KEY 未配置：文件上传/下载功能不可用");
        }

        // ---- 4. 生产环境额外检查 ----
        if ("prod".equalsIgnoreCase(activeProfile)) {
            if ("root".equalsIgnoreCase(datasourceUsername)) {
                warnings.add("生产环境正在使用 root 账号连接数据库："
                        + "建议改为最小权限专用账号");
            }
            if (isPlaceholder(minioAccessKey)) {
                warnings.add("生产环境 MinIO 访问密钥仍为示例值，存在安全风险");
            }
        }

        // ---- 5. 输出结果（只输出结论，绝不输出密钥值）----
        log.info("========== 环境配置自检 ==========");
        log.info("激活环境(Profile)     : {}", activeProfile);
        log.info("数据库账号            : {}", maskAccount(datasourceUsername));
        log.info("数据库口令            : {}", hasText(datasourcePassword) ? "已配置" : "未配置");
        log.info("JWT 密钥长度          : {}", jwtSecret == null ? 0 : jwtSecret.length());
        log.info("Flyway 数据库迁移     : {}{}", flywayEnabled ? "开启" : "关闭",
                flywayEnabled ? "（基线版本 " + flywayBaselineVersion + "，已有库跳过基线前的脚本）" : "");
        log.info("MinIO 地址 / 桶       : {} / {}", minioEndpoint, minioBucket);
        log.info("MinIO 密钥            : {}", hasText(minioSecretKey) ? "已配置" : "未配置");
        log.info("LLM 模型              : {} @ {}", llmModel, llmBaseUrl);
        log.info("LLM 密钥              : {}", hasText(llmApiKey) ? "已配置" : "未配置");
        log.info("=================================");

        for (String warning : warnings) {
            log.warn("[配置警告] {}", warning);
        }

        if (!errors.isEmpty()) {
            for (String error : errors) {
                log.error("[配置错误] {}", error);
            }
            throw new IllegalStateException(
                    "环境变量配置校验未通过，应用终止启动。原因：" + String.join("；", errors));
        }
    }

    // ==========================================================================
    // 内部工具方法
    // ==========================================================================

    private void checkSecret(String value, String displayName, List<String> warnings) {
        if (!StringUtils.hasText(value)) {
            warnings.add(displayName + " 未配置");
        } else if (isPlaceholder(value)) {
            warnings.add(displayName + " 仍为示例占位值");
        }
    }

    /** 判断取值是否仍为示例占位值（前缀或完全匹配）。 */
    private boolean isPlaceholder(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String trimmed = value.trim();
        for (String placeholder : PLACEHOLDERS) {
            if (trimmed.equalsIgnoreCase(placeholder) || trimmed.startsWith(placeholder)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasText(String value) {
        return StringUtils.hasText(value);
    }

    /** 账号脱敏：保留首字符与长度信息，便于排查但不泄漏完整账号。 */
    private String maskAccount(String account) {
        if (!StringUtils.hasText(account)) {
            return "未配置";
        }
        if (account.length() <= 2) {
            return "**";
        }
        return account.charAt(0) + "***" + account.charAt(account.length() - 1)
                + "（长度 " + account.length() + "）";
    }
}
