package com.volunteer.common.storage;

import io.minio.MinioClient;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO 客户端与配置。
 *
 * <h2>为什么放在 volunteer-common</h2>
 * <p>文件存储是跨模块能力：{@code volunteer-report} 的异步导出、{@code volunteer-server}
 * 的文件接口都需要使用。若把配置放在 {@code volunteer-server}，业务模块将被迫反向依赖
 * 启动模块。因此统一上移到公共模块，由 {@code com.volunteer.**} 的组件扫描自动装配。
 *
 * <h2>配置项</h2>
 * <ul>
 *   <li>{@code minio.endpoint} / {@code minio.accessKey} / {@code minio.secretKey}
 *       / {@code minio.bucketName} —— 连接参数，支持环境变量覆盖；</li>
 *   <li>{@code minio.auto-create-bucket} —— 是否在启动时自动创建桶。
 *       该字段此前只写在 yml 中、Java 类里没有对应属性，属于<b>失效配置</b>；
 *       现已补齐并真正生效，用于消除「首次部署必须手工建桶」的运维步骤。</li>
 * </ul>
 */
@Slf4j
@Data
@Configuration
@ConfigurationProperties(prefix = "minio")
public class MinioConfig {

    /** 服务地址，如 http://localhost:9090。 */
    private String endpoint;

    /** 访问密钥。 */
    private String accessKey;

    /** 秘密密钥。 */
    private String secretKey;

    /** 存储桶名称。 */
    private String bucketName;

    /** 是否在启动时自动创建桶（默认 false，保持向后兼容）。 */
    private boolean autoCreateBucket = false;

    /**
     * MinIO 客户端。
     *
     * <p>使用 {@link ConditionalOnMissingBean} 允许测试或特殊部署场景覆盖该 Bean。
     */
    @Bean
    @ConditionalOnMissingBean(MinioClient.class)
    public MinioClient minioClient() {
        log.info("Initializing MinIO client: endpoint={}, bucket={}, autoCreateBucket={}",
                endpoint, bucketName, autoCreateBucket);
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }
}
