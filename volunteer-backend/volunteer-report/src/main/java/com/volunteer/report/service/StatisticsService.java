package com.volunteer.report.service;

import com.volunteer.report.dto.ActivityTrendDTO;
import com.volunteer.report.dto.ServiceDurationDTO;
import com.volunteer.report.dto.VolunteerProfileDTO;
import com.volunteer.report.entity.ActivityMetric;
import com.volunteer.report.entity.UserDuration;
import com.volunteer.report.mapper.ActivityMetricMapper;
import com.volunteer.report.mapper.UserDurationMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 数据统计服务
 * 为可视化图表提供统计数据
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatisticsService {

    private final ActivityMetricMapper activityMetricMapper;
    private final UserDurationMapper userDurationMapper;
    private final JdbcTemplate jdbcTemplate;

    /**
     * 获取活动趋势数据（折线图）
     * 基于 ActivityMetric 的 metricDate 和 metricValue 聚合
     */
    public List<ActivityTrendDTO> getActivityTrend(LocalDate startDate, LocalDate endDate, String period) {
        if (startDate == null) startDate = LocalDate.now().minusMonths(1);
        if (endDate == null) endDate = LocalDate.now();

        LambdaQueryWrapper<ActivityMetric> wrapper = new LambdaQueryWrapper<>();
        wrapper.between(ActivityMetric::getMetricDate, startDate, endDate)
               .orderByAsc(ActivityMetric::getMetricDate);

        List<ActivityMetric> metrics = activityMetricMapper.selectList(wrapper);

        // 按日期聚合
        Map<LocalDate, List<ActivityMetric>> grouped = metrics.stream()
                .filter(m -> m.getMetricDate() != null)
                .collect(Collectors.groupingBy(ActivityMetric::getMetricDate));

        // 填充空缺日期
        List<ActivityTrendDTO> result = new ArrayList<>();
        LocalDate current = startDate;
        long totalDays = ChronoUnit.DAYS.between(startDate, endDate);
        long step = Math.max(1, totalDays / 30); // 最多30个数据点

        while (!current.isAfter(endDate)) {
            List<ActivityMetric> dayMetrics = grouped.getOrDefault(current, Collections.emptyList());
            ActivityTrendDTO dto = new ActivityTrendDTO();
            dto.setDate(current);
            dto.setCount(dayMetrics.size());
            dto.setParticipantCount(dayMetrics.stream()
                    .mapToInt(m -> m.getMetricValue() != null ? m.getMetricValue().intValue() : 0).sum());
            result.add(dto);

            current = current.plusDays(step);
        }

        return result;
    }

    /**
     * 获取服务时长统计（柱状图）
     * 基于 UserDuration 按活动ID聚合
     */
    public List<ServiceDurationDTO> getServiceDuration(Long organizationId, LocalDate startDate, LocalDate endDate) {
        if (startDate == null) startDate = LocalDate.now().minusMonths(1);
        if (endDate == null) endDate = LocalDate.now();

        LambdaQueryWrapper<UserDuration> wrapper = new LambdaQueryWrapper<>();
        // 注意：UserDuration 没有 activityDate 字段，通过 createTime 筛选
        wrapper.between(UserDuration::getCreateTime, startDate.atStartOfDay(), endDate.plusDays(1).atStartOfDay());

        List<UserDuration> durations = userDurationMapper.selectList(wrapper);

        // 按活动ID聚合
        Map<Long, List<UserDuration>> grouped = durations.stream()
                .filter(d -> d.getActivityId() != null)
                .collect(Collectors.groupingBy(UserDuration::getActivityId));

        return grouped.entrySet().stream()
                .map(entry -> {
                    ServiceDurationDTO dto = new ServiceDurationDTO();
                    dto.setOrganizationName("活动#" + entry.getKey());
                    dto.setTotalDuration(entry.getValue().stream()
                            .mapToDouble(d -> {
                                if (d.getAdjustedDuration() != null) {
                                    return d.getAdjustedDuration().doubleValue();
                                }
                                if (d.getOriginalDuration() != null) {
                                    return d.getOriginalDuration().doubleValue();
                                }
                                return 0.0;
                            })
                            .sum());
                    dto.setVolunteerCount((long) entry.getValue().size());
                    return dto;
                })
                .sorted((a, b) -> Double.compare(b.getTotalDuration(), a.getTotalDuration()))
                .limit(10)
                .collect(Collectors.toList());
    }

    /**
     * 获取活动分类统计（饼图）
     */
    public Map<String, Object> getActivityCategory(Long organizationId) {
        LambdaQueryWrapper<ActivityMetric> wrapper = new LambdaQueryWrapper<>();
        List<ActivityMetric> metrics = activityMetricMapper.selectList(wrapper);

        Map<String, Long> categoryCount = metrics.stream()
                .filter(m -> m.getMetricType() != null)
                .collect(Collectors.groupingBy(
                        ActivityMetric::getMetricType,
                        Collectors.counting()
                ));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("categories", new ArrayList<>(categoryCount.keySet()));
        result.put("values", new ArrayList<>(categoryCount.values()));
        result.put("total", metrics.size());

        return result;
    }

    /**
     * 获取签到率分析（仪表盘数据）
     */
    public Map<String, Object> getCheckinRate(Long activityId) {
        long registeredCount = queryRegisteredCount(activityId);
        long checkinCount = queryRegisteredCheckinCount(activityId);
        double rate = registeredCount > 0 ? roundOneDecimal(checkinCount * 100.0 / registeredCount) : 0.0;

        LambdaQueryWrapper<ActivityMetric> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(ActivityMetric::getMetricType, Arrays.asList("CHECKIN_RATE", "CHECK_IN_RATE", "CHECKIN"));
        if (activityId != null) {
            wrapper.eq(ActivityMetric::getActivityId, activityId);
        }
        wrapper.orderByDesc(ActivityMetric::getMetricDate);
        List<ActivityMetric> metrics = activityMetricMapper.selectList(wrapper);

        if (registeredCount <= 0 && checkinCount <= 0 && !metrics.isEmpty()) {
            rate = metrics.stream()
                    .map(ActivityMetric::getMetricValue)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .map(this::normalizePercent)
                    .orElse(0.0);
        }
        rate = clampPercent(rate);

        Map<String, Object> result = new LinkedHashMap<>();
        if (registeredCount > 0 || checkinCount > 0 || !metrics.isEmpty()) {
            result.put("activityName", activityId == null ? "All activities" : "活动#" + activityId);
            result.put("rate", rate);
            result.put("participants", registeredCount);
            result.put("checkins", checkinCount);
        } else {
            result.put("rate", 0);
            result.put("participants", 0);
            result.put("checkins", 0);
        }

        return result;
    }

    private long queryRegisteredCount(Long activityId) {
        String sql = "SELECT COUNT(*) FROM activity_registration " +
                "WHERE status IN ('APPROVED', 'CHECKED_IN', 'COMPLETED')";
        if (activityId != null) {
            sql += " AND activity_id = ?";
            return queryLong(sql, activityId);
        }
        return queryLong(sql);
    }

    private long queryRegisteredCheckinCount(Long activityId) {
        String sql = "SELECT COUNT(DISTINCT ar.id) FROM activity_registration ar " +
                "LEFT JOIN attendance a ON a.activity_id = ar.activity_id " +
                "AND a.user_id = ar.user_id AND a.check_in_time IS NOT NULL " +
                "WHERE ar.status IN ('APPROVED', 'CHECKED_IN', 'COMPLETED') " +
                "AND (ar.status IN ('CHECKED_IN', 'COMPLETED') OR a.id IS NOT NULL)";
        if (activityId != null) {
            sql += " AND ar.activity_id = ?";
            return queryLong(sql, activityId);
        }
        return queryLong(sql);
    }

    private long queryLong(String sql, Object... args) {
        Long value = args == null || args.length == 0
                ? jdbcTemplate.queryForObject(sql, Long.class)
                : jdbcTemplate.queryForObject(sql, Long.class, args);
        return value != null ? value : 0L;
    }

    private double normalizePercent(double value) {
        double percent = value <= 1.0 ? value * 100.0 : value;
        return clampPercent(roundOneDecimal(percent));
    }

    private double clampPercent(double value) {
        return Math.max(0.0, Math.min(100.0, value));
    }

    private double roundOneDecimal(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    /**
     * 获取志愿者画像数据（雷达图）
     */
    public VolunteerProfileDTO getVolunteerProfile(Long userId) {
        LambdaQueryWrapper<UserDuration> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserDuration::getUserId, userId);

        List<UserDuration> durations = userDurationMapper.selectList(wrapper);

        VolunteerProfileDTO profile = new VolunteerProfileDTO();
        profile.setUserName("志愿者#" + userId);

        profile.setTotalActivities(durations.size());
        profile.setTotalDuration(durations.stream()
                .mapToDouble(d -> {
                    if (d.getAdjustedDuration() != null) {
                        return d.getAdjustedDuration().doubleValue();
                    }
                    if (d.getOriginalDuration() != null) {
                        return d.getOriginalDuration().doubleValue();
                    }
                    return 0.0;
                })
                .sum());
        // Deterministic profile scores derived from actual service records.
        double totalDuration = profile.getTotalDuration();
        int totalActivities = profile.getTotalActivities();
        double participationScore = capScore(50.0 + totalActivities * 5.0);
        double durationScore = capScore(50.0 + totalDuration * 1.5);
        double reliabilityScore = capScore(60.0 + Math.min(totalActivities, 8) * 4.0);
        double growthScore = capScore((participationScore + durationScore) / 2.0);

        Map<String, Double> skillScores = new LinkedHashMap<>();
        skillScores.put("\u53c2\u4e0e\u6d3b\u8dc3\u5ea6", participationScore);
        skillScores.put("\u670d\u52a1\u65f6\u957f", durationScore);
        skillScores.put("\u5c65\u7ea6\u7a33\u5b9a\u6027", reliabilityScore);
        skillScores.put("\u6210\u957f\u8868\u73b0", growthScore);
        profile.setSkillScores(skillScores);
        profile.setCreditScore(capScore((participationScore + durationScore + reliabilityScore) / 3.0));
        return profile;
    }

    private double capScore(double score) {
        return Math.max(0.0, Math.min(100.0, Math.round(score * 10.0) / 10.0));
    }

}
