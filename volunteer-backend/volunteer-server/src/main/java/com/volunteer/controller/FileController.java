package com.volunteer.controller;

import com.volunteer.common.result.Result;
import com.volunteer.auth.annotation.RequiresAccess;
import com.volunteer.service.MinioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Tag(name = "文件管理", description = "文件上传、下载、删除接口")
@RestController
@RequestMapping("/api/file")
@RequiredArgsConstructor
@Slf4j
public class FileController {
    
    private final MinioService minioService;
    
    @Operation(summary = "上传文件")
    @PostMapping("/upload")
    public Result<String> uploadFile(@Parameter(description = "文件") @RequestParam("file") MultipartFile file) {
        String objectName = minioService.uploadFile(file);
        String url = minioService.getFileUrl(objectName);
        return Result.success(url);
    }
    
    @Operation(summary = "获取文件URL")
    @GetMapping("/url")
    public Result<String> getFileUrl(@Parameter(description = "文件标识") @RequestParam("objectName") String objectName) {
        String url = minioService.getFileUrl(objectName);
        return Result.success(url);
    }
    
    @Operation(summary = "下载文件")
    @GetMapping("/download")
    public ResponseEntity<byte[]> downloadFile(@Parameter(description = "文件标识") @RequestParam("objectName") String objectName) throws Exception {
        try (InputStream inputStream = minioService.downloadFile(objectName)) {
            byte[] bytes = inputStream.readAllBytes();
            String filename = URLEncoder.encode(objectName, StandardCharsets.UTF_8);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(bytes);
        }
    }
    
    @Operation(summary = "删除文件")
    @DeleteMapping("/delete")
    @RequiresAccess("admin")
    public Result<Void> deleteFile(@Parameter(description = "文件标识") @RequestParam("objectName") String objectName) {
        minioService.deleteFile(objectName);
        return Result.success();
    }
}
