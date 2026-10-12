package com.volunteer.report.controller;

import com.volunteer.common.result.Result;
import com.volunteer.report.dto.ActivityTrendDTO;
import com.volunteer.report.dto.ServiceDurationDTO;
import com.volunteer.report.dto.VolunteerProfileDTO;
import com.volunteer.report.service.StatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 数据统计控制器
 * 为可视化图表提供数据接口
 */
@Tag(name = "数据统计")
@RestController
@RequestMapping("/api/statistics")
@RequiredArgsConstructor
@Slf4j
public class StatisticsController {

    private final StatisticsService statisticsService;

    @Operation(summary = "获取活动趋势数据")
    @GetMapping("/activity-trend")
    public Result<List<ActivityTrendDTO>> getActivityTrend(
            @Parameter(description = "开始日期") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @Parameter(description = "结束日期") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            @Parameter(description = "统计周期：DAY/WEEK/MONTH") @RequestParam(defaultValue = "DAY") String period) {
        return Result.success(statisticsService.getActivityTrend(startDate, endDate, period));
    }

    @Operation(summary = "获取服务时长统计")
    @GetMapping("/service-duration")
    public Result<List<ServiceDurationDTO>> getServiceDuration(
            @Parameter(description = "组织ID") @RequestParam(required = false) Long organizationId,
            @Parameter(description = "开始日期") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @Parameter(description = "结束日期") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        return Result.success(statisticsService.getServiceDuration(organizationId, startDate, endDate));
    }

    @Operation(summary = "获取活动分类统计")
    @GetMapping("/activity-category")
    public Result<Map<String, Object>> getActivityCategory(
            @Parameter(description = "组织ID") @RequestParam(required = false) Long organizationId) {
        return Result.success(statisticsService.getActivityCategory(organizationId));
    }

    @Operation(summary = "获取签到率分析")
    @GetMapping("/checkin-rate")
    public Result<Map<String, Object>> getCheckinRate(
            @Parameter(description = "活动ID") @RequestParam(required = false) Long activityId) {
        return Result.success(statisticsService.getCheckinRate(activityId));
    }

    @Operation(summary = "获取志愿者画像")
    @GetMapping("/volunteer-profile")
    public Result<VolunteerProfileDTO> getVolunteerProfile(
            @Parameter(description = "用户ID") @RequestAttribute("userId") Long userId) {
        return Result.success(statisticsService.getVolunteerProfile(userId));
    }
}
