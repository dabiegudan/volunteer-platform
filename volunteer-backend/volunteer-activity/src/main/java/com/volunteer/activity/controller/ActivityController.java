package com.volunteer.activity.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.volunteer.activity.dto.ActivityListDTO;
import com.volunteer.activity.dto.UserRegistrationDTO;
import com.volunteer.activity.entity.Activity;
import com.volunteer.activity.entity.ActivityRegistration;
import com.volunteer.activity.service.ActivityService;
import com.volunteer.auth.annotation.RequiresAccess;
import com.volunteer.auth.entity.SysUser;
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
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@Tag(name = "活动管理")
@RestController
@RequestMapping("/api/activity")
@RequiredArgsConstructor
@Slf4j
public class ActivityController {

    private final ActivityService activityService;

    @Operation(summary = "分页查询活动列表（增强版）")
    @GetMapping("/page")
    public Result<PageResult<Activity>> getActivityPage(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") @Min(1) Integer pageNum,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") @Min(1) @Max(100) Integer pageSize,
            @Parameter(description = "活动分类") @RequestParam(required = false) String category,
            @Parameter(description = "搜索关键词") @RequestParam(required = false) String keyword,
            @Parameter(description = "活动状态") @RequestParam(required = false) String status,
            @Parameter(description = "开始时间") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @Parameter(description = "结束时间") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @Parameter(description = "排序字段") @RequestParam(required = false) String sortBy,
            @Parameter(description = "排序方式：asc/desc") @RequestParam(required = false) String sortOrder,
            @RequestAttribute(value = "currentUser", required = false) SysUser currentUser) {
        Page<Activity> page = activityService.getActivityPage(pageNum, pageSize, category, keyword,
                status, startTime, endTime, sortBy, sortOrder, null, null, null, null, null, currentUser);
        return Result.success(PageResult.of(page));
    }

    @Operation(summary = "获取待审核活动列表")
    @GetMapping("/audit/page")
    public Result<PageResult<Activity>> getAuditActivityPage(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") @Min(1) Integer pageNum,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") @Min(1) @Max(100) Integer pageSize,
            @RequestAttribute("currentUser") SysUser currentUser) {
        Page<Activity> page = activityService.getAuditActivityPage(pageNum, pageSize, currentUser);
        return Result.success(PageResult.of(page));
    }

    @Operation(summary = "获取活动详情")
    @GetMapping("/{id}")
    public Result<Activity> getActivityById(@PathVariable Long id,
                                            @RequestAttribute(value = "currentUser", required = false) SysUser currentUser) {
        return Result.success(activityService.getActivityById(id, currentUser));
    }

    @Operation(summary = "创建活动")
    @PostMapping
    @RequiresAccess(value = "organizer", requireStudentId = true)
    public Result<Activity> createActivity(@Valid @RequestBody Activity activity,
                                           @RequestAttribute("currentUser") SysUser currentUser) {
        return Result.success(activityService.createActivity(activity, currentUser));
    }

    @Operation(summary = "更新活动")
    @PutMapping("/{id}")
    @RequiresAccess(value = "organizer", requireStudentId = true)
    public Result<Void> updateActivity(@PathVariable Long id,
                                       @Valid @RequestBody Activity activity,
                                       @RequestAttribute("currentUser") SysUser currentUser) {
        activity.setId(id);
        activityService.updateActivity(activity, currentUser);
        return Result.success();
    }

    @Operation(summary = "发布活动")
    @PostMapping("/publish/{id}")
    @RequiresAccess(value = "organizer", requireStudentId = true)
    public Result<Void> publishActivity(@PathVariable Long id, @RequestAttribute("currentUser") SysUser currentUser) {
        activityService.publishActivity(id, currentUser);
        return Result.success();
    }

    @Operation(summary = "活动审核通过")
    @PostMapping("/{id}/approve")
    public Result<Void> approveActivity(@PathVariable Long id,
                                        @RequestBody(required = false) Map<String, Object> body,
                                        @RequestAttribute("currentUser") SysUser currentUser) {
        activityService.approveActivity(id, readText(body, "comment", "审核通过"), currentUser);
        return Result.success();
    }

    @Operation(summary = "活动审核拒绝")
    @PostMapping("/{id}/reject")
    public Result<Void> rejectActivity(@PathVariable Long id,
                                       @RequestBody(required = false) Map<String, Object> body,
                                       @RequestAttribute("currentUser") SysUser currentUser) {
        activityService.rejectActivity(id, readText(body, "reason", "审核拒绝"), currentUser);
        return Result.success();
    }

    @Operation(summary = "审核活动")
    @PostMapping("/{id}/audit")
    public Result<Void> auditActivity(@PathVariable Long id,
                                      @RequestParam(required = false) String status,
                                      @RequestParam(required = false) String comment,
                                      @RequestBody(required = false) Map<String, Object> body,
                                      @RequestAttribute("currentUser") SysUser currentUser) {
        String auditStatus = status != null ? status : readText(body, "status", null);
        String auditComment = comment != null ? comment : readText(body, "comment", readText(body, "reason", null));
        activityService.auditActivity(id, auditStatus, auditComment, currentUser);
        return Result.success();
    }

    @Operation(summary = "删除活动")
    @DeleteMapping("/{id}")
    @RequiresAccess(value = "organizer", requireStudentId = true)
    public Result<Void> deleteActivity(@PathVariable Long id, @RequestAttribute("currentUser") SysUser currentUser) {
        activityService.deleteActivity(id, currentUser);
        return Result.success();
    }

    @Operation(summary = "报名活动")
    @PostMapping("/{id}/register")
    @RequiresAccess(requireStudentId = true)
    public Result<ActivityRegistration> registerActivity(@PathVariable Long id, @RequestAttribute("userId") Long userId) {
        return Result.success(activityService.registerActivity(id, userId));
    }

    @Operation(summary = "获取活动报名列表")
    @GetMapping("/{id}/registrations")
    public Result<PageResult<ActivityRegistration>> getActivityRegistrations(
            @PathVariable Long id,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") @Min(1) Integer pageNum,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") @Min(1) @Max(100) Integer pageSize,
            @Parameter(description = "报名状态") @RequestParam(required = false) String status) {
        Page<ActivityRegistration> page = activityService.getActivityRegistrations(id, pageNum, pageSize, status);
        return Result.success(PageResult.of(page));
    }

    @Operation(summary = "取消报名")
    @DeleteMapping("/{id}/register")
    public Result<Void> cancelRegistration(@PathVariable Long id, @RequestAttribute("userId") Long userId) {
        activityService.cancelRegistration(id, userId);
        return Result.success();
    }

    @Operation(summary = "分页查询活动列表（带倒计时）")
    @GetMapping("/list")
    public Result<PageResult<ActivityListDTO>> getActivityListWithCountdown(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") @Min(1) Integer pageNum,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") @Min(1) @Max(100) Integer pageSize,
            @Parameter(description = "活动分类") @RequestParam(required = false) String category,
            @Parameter(description = "搜索关键词") @RequestParam(required = false) String keyword,
            @Parameter(description = "活动状态") @RequestParam(required = false) String status,
            @Parameter(description = "开始时间") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @Parameter(description = "结束时间") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @Parameter(description = "排序字段") @RequestParam(required = false) String sortBy,
            @Parameter(description = "排序方式：asc/desc") @RequestParam(required = false) String sortOrder,
            @Parameter(description = "省份") @RequestParam(required = false) String province,
            @Parameter(description = "城市") @RequestParam(required = false) String city,
            @Parameter(description = "区县") @RequestParam(required = false) String district,
            @Parameter(description = "街道") @RequestParam(required = false) String street,
            @Parameter(description = "时段筛选：weekend/holiday/after_school") @RequestParam(required = false) String timeSlot,
            @RequestAttribute(value = "currentUser", required = false) SysUser currentUser) {
        Page<ActivityListDTO> page = activityService.getActivityListWithCountdown(pageNum, pageSize, category, keyword,
                status, startTime, endTime, sortBy, sortOrder, province, city, district, street, timeSlot, currentUser);
        return Result.success(PageResult.of(page));
    }

    @Operation(summary = "获取当前用户的活动报名记录列表")
    @GetMapping("/registrations")
    public Result<PageResult<UserRegistrationDTO>> getUserRegistrations(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") @Min(1) Integer pageNum,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") @Min(1) @Max(100) Integer pageSize,
            @Parameter(description = "报名状态") @RequestParam(required = false) String status,
            @RequestAttribute("userId") Long userId) {
        Page<UserRegistrationDTO> page = activityService.getUserRegistrations(pageNum, pageSize, status, userId);
        return Result.success(PageResult.of(page));
    }

    @Operation(summary = "获取当前用户特定活动的报名状态")
    @GetMapping("/{id}/registration")
    public Result<UserRegistrationDTO> getUserRegistration(
            @PathVariable Long id,
            @RequestAttribute("userId") Long userId) {
        return Result.success(activityService.getUserRegistration(id, userId));
    }

    private String readText(Map<String, Object> body, String key, String fallback) {
        if (body == null || body.get(key) == null) {
            return fallback;
        }
        String value = String.valueOf(body.get(key));
        return value.isBlank() ? fallback : value;
    }
}
