package com.volunteer.report.controller;

import com.volunteer.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * #14 可视化运营大屏 - 控制器
 */
@Tag(name = "运营大屏")
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@Slf4j
public class DashboardController {

    private final JdbcTemplate jdbcTemplate;

    @Operation(summary = "获取运营大屏数据")
    @GetMapping
    public Result<Map<String, Object>> getDashboardData() {
        Map<String, Object> data = new LinkedHashMap<>();

        // 核心指标
        data.put("totalUsers", queryCount("SELECT COUNT(*) FROM sys_user WHERE deleted = 0"));
        data.put("todayNewUsers", queryCount("SELECT COUNT(*) FROM sys_user WHERE create_time >= CURDATE() AND deleted = 0"));
        data.put("totalActivities", queryCount("SELECT COUNT(*) FROM activity WHERE deleted = 0"));
        data.put("ongoingActivities", queryCount("SELECT COUNT(*) FROM activity WHERE status = 'ONGOING' AND deleted = 0"));
        data.put("totalDuration", querySum("SELECT COALESCE(SUM(adjusted_duration), 0) FROM user_duration"));
        data.put("totalRegistrations", queryCount("SELECT COUNT(*) FROM activity_registration WHERE status = 'APPROVED'"));

        // 活动类型分布
        List<Map<String, Object>> categoryDistribution = jdbcTemplate.queryForList(
                "SELECT category AS name, COUNT(*) AS value FROM activity WHERE deleted = 0 GROUP BY category");
        data.put("categoryDistribution", categoryDistribution);

        // 活动状态分布
        List<Map<String, Object>> statusDistribution = jdbcTemplate.queryForList(
                "SELECT status AS name, COUNT(*) AS value FROM activity WHERE deleted = 0 GROUP BY status");
        data.put("statusDistribution", statusDistribution);

        // 近7天新增用户趋势
        List<Map<String, Object>> userTrend = jdbcTemplate.queryForList(
                "SELECT DATE(create_time) AS date, COUNT(*) AS count FROM sys_user " +
                "WHERE create_time >= DATE_SUB(CURDATE(), INTERVAL 7 DAY) AND deleted = 0 " +
                "GROUP BY DATE(create_time) ORDER BY date");
        data.put("userTrend", userTrend);

        // 近7天活动趋势
        List<Map<String, Object>> activityTrend = jdbcTemplate.queryForList(
                "SELECT DATE(create_time) AS date, COUNT(*) AS count FROM activity " +
                "WHERE create_time >= DATE_SUB(CURDATE(), INTERVAL 7 DAY) AND deleted = 0 " +
                "GROUP BY DATE(create_time) ORDER BY date");
        data.put("activityTrend", activityTrend);

        // 学院参与统计
        List<Map<String, Object>> collegeStats = jdbcTemplate.queryForList(
                "SELECT college_name AS name, COUNT(*) AS value FROM sys_user " +
                "WHERE college_name IS NOT NULL AND deleted = 0 GROUP BY college_name ORDER BY value DESC");
        data.put("collegeStats", collegeStats);

        // 月度时长统计
        List<Map<String, Object>> monthlyDuration = jdbcTemplate.queryForList(
                "SELECT DATE_FORMAT(create_time, '%Y-%m') AS month, COALESCE(SUM(adjusted_duration), 0) AS total " +
                "FROM user_duration WHERE create_time >= DATE_SUB(CURDATE(), INTERVAL 12 MONTH) " +
                "GROUP BY DATE_FORMAT(create_time, '%Y-%m') ORDER BY month");
        data.put("monthlyDuration", monthlyDuration);

        return Result.success(data);
    }

    private Long queryCount(String sql) {
        Long result = jdbcTemplate.queryForObject(sql, Long.class);
        return result != null ? result : 0L;
    }

    private Double querySum(String sql) {
        Double result = jdbcTemplate.queryForObject(sql, Double.class);
        return result != null ? result : 0.0;
    }
}