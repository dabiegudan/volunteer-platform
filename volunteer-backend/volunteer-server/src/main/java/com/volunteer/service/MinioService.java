package com.volunteer.service;

import com.volunteer.common.exception.BusinessException;
import com.volunteer.common.storage.FileStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 用户上传文件服务。
 *
 * <h2>职责</h2>
 * <p>本类只做<b>用户输入侧</b>的校验（大小、扩展名、Content-Type、对象名格式），
 * 实际的对象读写全部委托给 {@link FileStorage}（{@code com.volunteer.common.storage} 中的
 * {@code MinioFileStorage}）。
 *
 * <p>这样划分的原因：用户上传必须严格白名单校验（防上传可执行文件），
 * 而系统内部产物（如导出文件）由服务端生成、无需扩展名白名单。
 * 两类场景共用同一个对象存储客户端与桶，但不共用校验规则。
 *
 * <p>对象统一存放在 {@link FileStorage#PREFIX_USER_UPLOAD} 前缀下，与导出产物隔离。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MinioService {

    private final FileStorage fileStorage;

    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".jpg", ".jpeg", ".png", ".gif", ".webp",
            ".pdf", ".txt", ".csv", ".doc", ".docx", ".xls", ".xlsx"
    );

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/gif",
            "image/webp",
            "application/pdf",
            "text/plain",
            "text/csv",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    );

    /**
     * 仅允许「UUID + 可选扩展名」的对象名，避免用户构造任意路径。
     */
    private static final Pattern OBJECT_NAME_PATTERN = Pattern.compile(
            "^(?:[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"
                    + "(?:\\.[A-Za-z0-9]{1,10})?)$"
    );

    /** 校验并上传用户文件，返回含前缀的完整对象名。 */
    public String uploadFile(MultipartFile file) {
        String extension = validateUploadFile(file);
        String objectName = UUID.randomUUID().toString() + extension;
        try (InputStream inputStream = file.getInputStream()) {
            return fileStorage.upload(
                    FileStorage.PREFIX_USER_UPLOAD,
                    objectName,
                    inputStream,
                    file.getSize(),
                    file.getContentType());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("文件上传失败, object={}", objectName, e);
            throw new BusinessException("文件上传失败");
        }
    }

    /** 下载用户上传的文件。 */
    public InputStream downloadFile(String objectName) {
        validateUserUploadObjectName(objectName);
        return fileStorage.openStream(FileStorage.PREFIX_USER_UPLOAD + "/" + objectName);
    }

    /** 删除用户上传的文件。 */
    public void deleteFile(String objectName) {
        validateUserUploadObjectName(objectName);
        fileStorage.delete(FileStorage.PREFIX_USER_UPLOAD + "/" + objectName);
    }

    /**
     * 获取文件访问地址。
     *
     * <p>用户上传的文件通过后端下载接口代理，而不是直接暴露对象存储预签名 URL ——
     * 避免把内网 MinIO 地址暴露给浏览器，也便于统一做权限校验。
     */
    public String getFileUrl(String objectName) {
        validateUserUploadObjectName(objectName);
        return "/api/file/download?objectName=" + objectName;
    }

    private String validateUploadFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("File is empty");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException("File size exceeds 10MB");
        }

        String extension = extractExtension(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException("Unsupported file type");
        }

        String contentType = file.getContentType();
        if (contentType != null && !contentType.isBlank()) {
            String normalizedContentType = contentType.toLowerCase(Locale.ROOT);
            if (!"application/octet-stream".equals(normalizedContentType)
                    && !ALLOWED_CONTENT_TYPES.contains(normalizedContentType)) {
                throw new BusinessException("Unsupported content type");
            }
        }
        return extension;
    }

    private String extractExtension(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new BusinessException("File name is required");
        }
        String normalized = originalFilename.replace("\\", "/");
        String fileName = normalized.substring(normalized.lastIndexOf('/') + 1);
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            throw new BusinessException("File extension is required");
        }
        return fileName.substring(dotIndex).toLowerCase(Locale.ROOT);
    }

    private void validateUserUploadObjectName(String objectName) {
        if (objectName == null || objectName.isBlank() || !OBJECT_NAME_PATTERN.matcher(objectName).matches()) {
            throw new BusinessException("Invalid file object name");
        }
        int dotIndex = objectName.lastIndexOf('.');
        if (dotIndex >= 0) {
            String extension = objectName.substring(dotIndex).toLowerCase(Locale.ROOT);
            if (!ALLOWED_EXTENSIONS.contains(extension)) {
                throw new BusinessException("Unsupported file type");
            }
        }
    }
}
