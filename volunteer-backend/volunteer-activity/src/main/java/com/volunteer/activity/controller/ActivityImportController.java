package com.volunteer.activity.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.volunteer.activity.dto.ImportConfirmRequest;
import com.volunteer.activity.dto.ImportPreviewDTO;
import com.volunteer.activity.dto.ImportResultDTO;
import com.volunteer.activity.entity.ActivityImportLog;
import com.volunteer.activity.service.ActivityImportService;
import com.volunteer.auth.annotation.RequiresAccess;
import com.volunteer.auth.entity.SysUser;
import com.volunteer.auth.service.UserService;
import com.volunteer.common.result.PageResult;
import com.volunteer.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Tag(name = "活动参与者导入")
@RestController
@RequestMapping("/api/activity")
@RequiredArgsConstructor
@Slf4j
public class ActivityImportController {
    
    private final ActivityImportService importService;
    private final UserService userService;
    
    @Operation(summary = "下载导入模板")
    @GetMapping("/{activityId}/import/template")
    @RequiresAccess("organizer")
    public ResponseEntity<byte[]> downloadTemplate(@PathVariable Long activityId) {
        log.info("下载导入模板, activityId: {}", activityId);
        ByteArrayOutputStream outputStream = importService.downloadTemplate(activityId);
        
        String filename = "导入模板.xlsx";
        String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + encodedFilename + "\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(outputStream.toByteArray());
    }
    
    @Operation(summary = "预览导入数据")
    @PostMapping(value = "/{activityId}/import/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequiresAccess("organizer")
    public Result<ImportPreviewDTO> previewImport(
            @PathVariable Long activityId,
            @Parameter(description = "上传的Excel/CSV文件") @RequestParam("file") MultipartFile file,
            @RequestAttribute("userId") Long userId) {
        log.info("预览导入数据, activityId: {}, fileName: {}", activityId, file.getOriginalFilename());
        SysUser currentUser = userService.getUserById(userId);
        ImportPreviewDTO preview = importService.previewImport(activityId, file, currentUser);
        return Result.success(preview);
    }
    
    @Operation(summary = "确认导入")
    @PostMapping("/{activityId}/import/confirm")
    @RequiresAccess("organizer")
    public Result<ImportResultDTO> confirmImport(
            @PathVariable Long activityId,
            @Valid @RequestBody ImportConfirmRequest request,
            @RequestAttribute("userId") Long userId) {
        log.info("确认导入, activityId: {}, studentNos: {}", activityId, request.getStudentNos().size());
        SysUser currentUser = userService.getUserById(userId);
        ImportResultDTO result = importService.confirmImport(activityId, request.getStudentNos(), currentUser);
        return Result.success(result);
    }
    
    @Operation(summary = "获取导入日志列表")
    @GetMapping("/{activityId}/import/logs")
    @RequiresAccess("organizer")
    public Result<PageResult<ActivityImportLog>> getImportLogs(
            @PathVariable Long activityId,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") @Min(1) Integer pageNum,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") @Min(1) @Max(100) Integer pageSize) {
        log.info("获取导入日志列表, activityId: {}", activityId);
        Page<ActivityImportLog> page = importService.getImportLogs(activityId, pageNum, pageSize);
        return Result.success(PageResult.of(page));
    }
    
    @Operation(summary = "下载失败明细")
    @GetMapping("/import/log/{logId}/failures")
    @RequiresAccess("organizer")
    public ResponseEntity<byte[]> downloadFailures(@PathVariable Long logId) {
        log.info("下载失败明细, logId: {}", logId);
        ByteArrayOutputStream outputStream = importService.downloadFailures(logId);
        
        String filename = "导入失败明细.xlsx";
        String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + encodedFilename + "\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(outputStream.toByteArray());
    }
}
