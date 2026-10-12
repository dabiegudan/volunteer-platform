package com.volunteer.server.config;

import com.volunteer.common.exception.BusinessException;
import com.volunteer.common.storage.FileStorage;
import com.volunteer.service.MinioService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link MinioService} 的用户输入侧校验规则锁定。
 *
 * <p>本类是「用户上传」的唯一入口，承担防上传可执行文件、防路径穿越的职责。
 * 这些规则一旦被放宽就是真实的安全回归，因此逐条固定：扩展名白名单、
 * Content-Type 白名单、10MB 上限、对象名必须是 UUID（可选扩展名）。</p>
 */
class MinioServiceTest {

    private final FileStorage fileStorage = mock(FileStorage.class);
    private final MinioService service = new MinioService(fileStorage);

    private MockMultipartFile file(String name, String contentType, byte[] content) {
        return new MockMultipartFile("file", name, contentType, content);
    }

    @Test
    @DisplayName("合法图片：按 UUID 命名并落到用户上传前缀下")
    void uploadsAllowedImageUnderUserUploadPrefix() {
        when(fileStorage.upload(anyString(), anyString(), any(InputStream.class), anyLong(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0) + "/" + invocation.getArgument(1));

        String objectName = service.uploadFile(
                file("avatar.png", "image/png", "x".getBytes(StandardCharsets.UTF_8)));

        assertThat(objectName).startsWith(FileStorage.PREFIX_USER_UPLOAD + "/");
        assertThat(objectName).endsWith(".png");
        // UUID + 扩展名 的格式约束
        assertThat(objectName.substring(FileStorage.PREFIX_USER_UPLOAD.length() + 1))
                .matches("[0-9a-fA-F-]{36}\\.png");
    }

    @Test
    @DisplayName("空文件被拒绝")
    void rejectsEmptyFile() {
        assertThatThrownBy(() -> service.uploadFile(
                file("a.png", "image/png", new byte[0])))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("超过 10MB 被拒绝")
    void rejectsOversizedFile() {
        byte[] big = new byte[(int) (10L * 1024 * 1024) + 1];
        assertThatThrownBy(() -> service.uploadFile(file("big.png", "image/png", big)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("size");
    }

    @Test
    @DisplayName("扩展名白名单外的文件被拒绝（防上传脚本/可执行文件）")
    void rejectsDisallowedExtension() {
        assertThatThrownBy(() -> service.uploadFile(
                file("evil.jsp", "text/plain", "x".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("type");
    }

    @Test
    @DisplayName("文件名带路径时只取最后一段（防路径穿越）")
    void stripsDirectoryFromFileName() {
        when(fileStorage.upload(anyString(), anyString(), any(InputStream.class), anyLong(), any()))
                .thenReturn("ok");

        String objectName = service.uploadFile(file(
                "../../etc/passwd.pdf", "application/pdf", "x".getBytes(StandardCharsets.UTF_8)));

        assertThat(objectName).isEqualTo("ok");
        verify(fileStorage).upload(eq(FileStorage.PREFIX_USER_UPLOAD), anyString(), any(), anyLong(), any());
    }

    @Test
    @DisplayName("无扩展名被拒绝")
    void rejectsMissingExtension() {
        assertThatThrownBy(() -> service.uploadFile(
                file("noext", "image/png", "x".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("无文件名被拒绝")
    void rejectsMissingFileName() {
        assertThatThrownBy(() -> service.uploadFile(
                file(null, "image/png", "x".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("Content-Type 白名单外的类型被拒绝")
    void rejectsDisallowedContentType() {
        assertThatThrownBy(() -> service.uploadFile(
                file("a.png", "application/x-sh", "x".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("content type");
    }

    @Test
    @DisplayName("application/octet-stream 作为兼容类型被接受")
    void acceptsOctetStreamContentType() {
        when(fileStorage.upload(anyString(), anyString(), any(InputStream.class), anyLong(), any()))
                .thenReturn("ok");

        assertThat(service.uploadFile(file(
                "a.pdf", "application/octet-stream", "x".getBytes(StandardCharsets.UTF_8))))
                .isEqualTo("ok");
    }

    @Test
    @DisplayName("Content-Type 为空时跳过类型校验")
    void skipsContentTypeCheckWhenAbsent() {
        when(fileStorage.upload(anyString(), anyString(), any(InputStream.class), anyLong(), any()))
                .thenReturn("ok");

        assertThat(service.uploadFile(file(
                "a.txt", null, "x".getBytes(StandardCharsets.UTF_8))))
                .isEqualTo("ok");
    }

    @Test
    @DisplayName("存储层抛业务异常时原样上抛（保留具体原因）")
    void propagatesBusinessExceptionFromStorage() {
        when(fileStorage.upload(anyString(), anyString(), any(InputStream.class), anyLong(), any()))
                .thenThrow(new BusinessException("桶不存在"));

        assertThatThrownBy(() -> service.uploadFile(
                file("a.png", "image/png", "x".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("桶不存在");
    }

    @Test
    @DisplayName("存储层抛其他异常时包装为「文件上传失败」")
    void wrapsUnexpectedStorageFailure() {
        when(fileStorage.upload(anyString(), anyString(), any(InputStream.class), anyLong(), any()))
                .thenThrow(new IllegalStateException("boom"));

        assertThatThrownBy(() -> service.uploadFile(
                file("a.png", "image/png", "x".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("文件上传失败");
    }

    @Test
    @DisplayName("合法对象名可下载/删除/取地址")
    void allowsDownloadDeleteAndUrlForValidObjectName() {
        String objectName = "3f2504e0-4f89-11d3-9a0c-0305e82c3301.png";
        when(fileStorage.openStream(anyString())).thenReturn(new ByteArrayInputStream(new byte[0]));

        assertThat(service.getFileUrl(objectName)).isEqualTo("/api/file/download?objectName=" + objectName);
        assertThat(service.downloadFile(objectName)).isNotNull();
        service.deleteFile(objectName);

        verify(fileStorage).delete(FileStorage.PREFIX_USER_UPLOAD + "/" + objectName);
    }

    @Test
    @DisplayName("对象名不是 UUID 时拒绝（防任意路径）")
    void rejectsNonUuidObjectName() {
        assertThatThrownBy(() -> service.getFileUrl("../../etc/passwd"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("object name");
    }

    @Test
    @DisplayName("空对象名被拒绝")
    void rejectsBlankObjectName() {
        assertThatThrownBy(() -> service.getFileUrl("  "))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("UUID 对象名带了非白名单扩展名时拒绝")
    void rejectsUuidObjectNameWithDisallowedExtension() {
        assertThatThrownBy(() -> service.getFileUrl(
                "3f2504e0-4f89-11d3-9a0c-0305e82c3301.jsp"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("type");
    }
}
