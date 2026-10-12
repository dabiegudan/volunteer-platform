package com.volunteer.activity.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.volunteer.activity.dto.UserRegistrationDTO;
import com.volunteer.activity.service.ActivityService;
import com.volunteer.auth.annotation.RequiresAccess;
import com.volunteer.common.result.PageResult;
import com.volunteer.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "报名管理")
@RestController
@RequestMapping("/api/registration")
@RequiredArgsConstructor
public class RegistrationController {

    private final ActivityService activityService;

    @Operation(summary = "获取我的报名列表")
    @GetMapping("/my")
    public Result<PageResult<UserRegistrationDTO>> getMyRegistrations(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") @Min(1) Integer pageNum,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") @Min(1) @Max(100) Integer pageSize,
            @Parameter(description = "报名状态") @RequestParam(required = false) String status,
            @RequestAttribute("userId") Long userId) {
        Page<UserRegistrationDTO> page = activityService.getUserRegistrations(pageNum, pageSize, status, userId);
        return Result.success(PageResult.of(page));
    }

    @Operation(summary = "通过报名")
    @PostMapping("/{id}/approve")
    @RequiresAccess("organizer")
    public Result<Void> approveRegistration(@PathVariable Long id) {
        activityService.approveRegistration(id);
        return Result.success();
    }

    @Operation(summary = "拒绝报名")
    @PostMapping("/{id}/reject")
    @RequiresAccess("organizer")
    public Result<Void> rejectRegistration(@PathVariable Long id,
                                           @RequestParam(required = false) String reason,
                                           @RequestBody(required = false) Map<String, Object> body) {
        activityService.rejectRegistration(id, reason != null ? reason : readText(body, "reason", "审核拒绝"));
        return Result.success();
    }

    private String readText(Map<String, Object> body, String key, String fallback) {
        if (body == null || body.get(key) == null) {
            return fallback;
        }
        String value = String.valueOf(body.get(key));
        return value.isBlank() ? fallback : value;
    }
}
