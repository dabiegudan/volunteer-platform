package com.volunteer.report.controller;

import com.volunteer.auth.annotation.RequiresAccess;
import com.volunteer.common.result.PageResult;
import com.volunteer.common.result.Result;
import com.volunteer.report.entity.ExternalPlatformDuration;
import com.volunteer.report.service.ExternalDurationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Tag(name = "外部平台时长")
@RestController
@RequestMapping("/api/external-duration")
@RequiredArgsConstructor
public class ExternalDurationController {

    private final ExternalDurationService externalDurationService;

    @Operation(summary = "获取支持的外部平台")
    @GetMapping("/platforms")
    public Result<List<String>> getSupportedPlatforms() {
        return Result.success(externalDurationService.getSupportedPlatforms());
    }

    @Operation(summary = "导入外部平台时长")
    @PostMapping("/import")
    public Result<ExternalPlatformDuration> importDuration(
            @RequestBody Map<String, Object> body,
            @RequestAttribute("userId") Long userId) {
        return Result.success(externalDurationService.importDuration(
                userId,
                stringValue(body.get("platformCode")),
                stringValue(body.get("platformName")),
                decimalValue(body.get("durationHours")),
                stringValue(body.get("activityName")),
                stringValue(body.get("activityDate")),
                stringValue(body.get("certificateNumber"))));
    }

    @Operation(summary = "批量导入外部平台时长")
    @PostMapping("/import/batch")
    public Result<Integer> batchImportDuration(
            @RequestBody List<ExternalPlatformDuration> durations,
            @RequestAttribute("userId") Long userId) {
        return Result.success(externalDurationService.batchImportDuration(userId, durations));
    }

    @Operation(summary = "查询我的外部时长导入记录")
    @GetMapping("/records")
    public Result<PageResult<ExternalPlatformDuration>> getMyRecords(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String status,
            @RequestAttribute("userId") Long userId) {
        return Result.success(PageResult.of(externalDurationService.getImportRecords(userId, pageNum, pageSize, status)));
    }

    @Operation(summary = "管理员查询外部时长导入记录")
    @GetMapping("/admin/records")
    @RequiresAccess("admin")
    public Result<PageResult<ExternalPlatformDuration>> getRecordsAdmin(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String status) {
        return Result.success(PageResult.of(externalDurationService.getImportRecords(userId, pageNum, pageSize, status)));
    }

    @Operation(summary = "审核外部时长")
    @PutMapping("/{durationId}/audit")
    @RequiresAccess("admin")
    public Result<Void> auditDuration(
            @PathVariable Long durationId,
            @RequestBody Map<String, Object> body,
            @RequestAttribute("userId") Long auditorId) {
        externalDurationService.auditDuration(
                durationId,
                booleanValue(body.get("approved")),
                stringValue(body.get("rejectReason")),
                auditorId);
        return Result.success();
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private BigDecimal decimalValue(Object value) {
        return new BigDecimal(value.toString());
    }

    private boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(value.toString());
    }
}
