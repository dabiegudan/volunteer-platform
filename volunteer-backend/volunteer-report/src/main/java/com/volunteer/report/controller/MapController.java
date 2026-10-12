package com.volunteer.report.controller;

import com.volunteer.common.result.Result;
import com.volunteer.report.service.MapService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * #7 GIS志愿地图与15分钟服务圈 - 控制器
 */
@Tag(name = "志愿地图")
@RestController
@RequestMapping("/api/map")
@RequiredArgsConstructor
@Slf4j
public class MapController {

    private final MapService mapService;

    @Operation(summary = "获取附近服务站点")
    @GetMapping("/stations")
    public Result<List<Map<String, Object>>> getNearbyStations(
            @Parameter(description = "纬度") @RequestParam BigDecimal lat,
            @Parameter(description = "经度") @RequestParam BigDecimal lng,
            @Parameter(description = "搜索半径(km)") @RequestParam(defaultValue = "5.0") Double radius) {
        return Result.success(mapService.getNearbyStations(lat, lng, radius));
    }

    @Operation(summary = "获取15分钟服务圈内活动(1.2km)")
    @GetMapping("/15min-circle")
    public Result<List<Map<String, Object>>> getFifteenMinCircle(
            @Parameter(description = "纬度") @RequestParam BigDecimal lat,
            @Parameter(description = "经度") @RequestParam BigDecimal lng) {
        return Result.success(mapService.getNearbyActivities(lat, lng));
    }

    @Operation(summary = "获取所有地图点位")
    @GetMapping("/points")
    public Result<List<Map<String, Object>>> getAllMapPoints(
            @Parameter(description = "纬度") @RequestParam BigDecimal lat,
            @Parameter(description = "经度") @RequestParam BigDecimal lng,
            @Parameter(description = "搜索半径(km)") @RequestParam(defaultValue = "10.0") Double radius) {
        return Result.success(mapService.getAllMapPoints(lat, lng, radius));
    }
}