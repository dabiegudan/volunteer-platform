package com.volunteer.common.storage;

import com.volunteer.common.exception.BusinessException;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * {@link FileStorage} 的 MinIO 实现。
 *
 * <h2>与 MinioService 的分工</h2>
 * <ul>
 *   <li>{@link MinioService}（位于 volunteer-server）：面向<b>用户上传</b>，
 *       带文件大小/扩展名/Content-Type 白名单校验与 UUID 对象名校验；</li>
 *   <li>本类：面向<b>系统内部产物</b>（导出文件等），不做上传白名单校验
 *       （内容由服务端生成，非用户输入），对象名由调用方按业务规则构造。</li>
 * </ul>
 * <p>两者共用同一个 {@link MinioClient} 与桶配置。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MinioFileStorage implements FileStorage {

    private final MinioClient minioClient;
    private final MinioConfig minioConfig;

    /**
     * 启动时按配置决定是否自动建桶。
     *
     * <p>{@code minio.auto-create-bucket=true} 时自动创建，可消除首次部署
     * 必须手工登录控制台建桶的步骤；默认 false 保持原有行为。
     */
    @PostConstruct
    void init() {
        if (minioConfig.isAutoCreateBucket()) {
            try {
                ensureBucket();
            } catch (Exception e) {
                // 建桶失败不阻断启动：MinIO 可能稍后才就绪，首次上传时会再尝试
                log.warn("启动时自动创建存储桶失败，将在首次上传时重试。bucket={}, 原因={}",
                        minioConfig.getBucketName(), e.getMessage());
            }
        }
    }

    @Override
    public String upload(String storageKey, String objectName, byte[] content, String contentType) {
        if (content == null) {
            throw new BusinessException("上传内容不能为空");
        }
        return upload(storageKey, objectName, new ByteArrayInputStream(content), content.length, contentType);
    }

    @Override
    public String upload(String storageKey, String objectName, InputStream stream, long size, String contentType) {
        String fullObjectName = buildObjectName(storageKey, objectName);
        ensureBucket();
        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket())
                            .object(fullObjectName)
                            .stream(stream, size, -1)
                            .contentType(contentType != null ? contentType : "application/octet-stream")
                            .build()
            );
            log.info("对象上传成功, bucket={}, object={}", bucket(), fullObjectName);
            return fullObjectName;
        } catch (Exception e) {
            log.error("对象上传失败, bucket={}, object={}", bucket(), fullObjectName, e);
            throw new BusinessException("文件上传失败");
        }
    }

    @Override
    public byte[] download(String fullObjectName) {
        validateFullObjectName(fullObjectName);
        try (InputStream in = openStream(fullObjectName)) {
            return in.readAllBytes();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("对象下载失败, object={}", fullObjectName, e);
            throw new BusinessException("文件下载失败");
        }
    }

    @Override
    public InputStream openStream(String fullObjectName) {
        validateFullObjectName(fullObjectName);
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucket())
                            .object(fullObjectName)
                            .build()
            );
        } catch (ErrorResponseException e) {
            // 对象不存在属于可预期的业务情况，不打印完整堆栈
            log.warn("对象不存在, bucket={}, object={}", bucket(), fullObjectName);
            throw new BusinessException("文件不存在或已过期");
        } catch (Exception e) {
            log.error("读取对象失败, object={}", fullObjectName, e);
            throw new BusinessException("文件读取失败");
        }
    }

    @Override
    public void delete(String fullObjectName) {
        validateFullObjectName(fullObjectName);
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucket())
                            .object(fullObjectName)
                            .build()
            );
            log.info("对象删除成功, bucket={}, object={}", bucket(), fullObjectName);
        } catch (Exception e) {
            // 删除失败不应阻断业务（如清理任务），记录即可
            log.warn("对象删除失败, bucket={}, object={}, 原因={}", bucket(), fullObjectName, e.getMessage());
        }
    }

    @Override
    public boolean exists(String fullObjectName) {
        if (fullObjectName == null || fullObjectName.isBlank()) {
            return false;
        }
        try {
            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucket())
                            .object(fullObjectName)
                            .build()
            );
            return true;
        } catch (ErrorResponseException e) {
            return false;
        } catch (Exception e) {
            log.warn("检查对象是否存在失败, object={}, 原因={}", fullObjectName, e.getMessage());
            return false;
        }
    }

    private String bucket() {
        return minioConfig.getBucketName();
    }

    private void ensureBucket() {
        try {
            if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket()).build())) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket()).build());
                log.info("已创建存储桶: {}", bucket());
            }
        } catch (Exception e) {
            log.error("存储桶不可用, bucket={}", bucket(), e);
            throw new BusinessException("存储服务不可用");
        }
    }

    /**
     * 拼接存储前缀，实现按用途隔离对象。
     *
     * @param storageKey 前缀（upload / export）
     * @param objectName 原始对象名
     * @return 完整对象名，形如 {@code export/activity-stats-<uuid>.xlsx}
     */
    private String buildObjectName(String storageKey, String objectName) {
        if (objectName == null || objectName.isBlank()) {
            throw new BusinessException("对象名不能为空");
        }
        // 防目录穿越与非法字符
        if (objectName.contains("..") || objectName.startsWith("/") || objectName.contains("\\")) {
            throw new BusinessException("非法的对象名");
        }
        String prefix = (storageKey == null || storageKey.isBlank()) ? "" : storageKey.trim() + "/";
        return prefix + objectName;
    }

    /** 校验完整对象名（含前缀），仅做路径安全校验。 */
    private void validateFullObjectName(String fullObjectName) {
        if (fullObjectName == null || fullObjectName.isBlank()) {
            throw new BusinessException("对象名不能为空");
        }
        if (fullObjectName.contains("..") || fullObjectName.startsWith("/") || fullObjectName.contains("\\")) {
            throw new BusinessException("非法的对象名");
        }
    }
}
