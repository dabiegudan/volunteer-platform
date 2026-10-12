package com.volunteer.report.controller;

import com.volunteer.auth.annotation.RequiresAccess;
import com.volunteer.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@Tag(name = "全局统计")
@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {

    private final JdbcTemplate jdbcTemplate;

    @Operation(summary = "获取全局统计")
    @GetMapping("/global")
    @RequiresAccess("admin")
    public Result<Map<String, Object>> getGlobalStats() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalUsers", queryCount("SELECT COUNT(*) FROM sys_user WHERE deleted = 0"));
        data.put("totalActivities", queryCount("SELECT COUNT(*) FROM activity WHERE deleted = 0"));
        data.put("ongoingActivities", queryCount("SELECT COUNT(*) FROM activity WHERE status = 'ONGOING' AND deleted = 0"));
        data.put("totalRegistrations", queryCount("SELECT COUNT(*) FROM activity_registration WHERE status = 'APPROVED'"));
        data.put("totalDuration", queryDecimal("SELECT COALESCE(SUM(COALESCE(adjusted_duration, original_duration, 0)), 0) FROM user_duration"));
        data.put("activeViolations", queryCount("SELECT COUNT(*) FROM violation_record WHERE status = 'ACTIVE'"));
        return Result.success(data);
    }

    private Long queryCount(String sql) {
        Long result = jdbcTemplate.queryForObject(sql, Long.class);
        return result == null ? 0L : result;
    }

    private Double queryDecimal(String sql) {
        Double result = jdbcTemplate.queryForObject(sql, Double.class);
        return result == null ? 0.0 : result;
    }
}
