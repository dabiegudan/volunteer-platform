package com.volunteer.report.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.volunteer.report.entity.*;
import com.volunteer.report.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiagnosisService {
    
    private final ActivityHealthMapper healthMapper;
    private final ActivityMetricMapper metricMapper;
    private final DiagnosisReportMapper reportMapper;
    private final ImprovementSuggestionMapper suggestionMapper;
    private final ObjectMapper objectMapper;
    
    @Transactional
    public void recordMetric(Long activityId, String metricType, Double value) {
        LocalDate today = LocalDate.now();
        ActivityMetric existing = metricMapper.findByActivityIdAndTypeAndDate(activityId, metricType, today);
        
        if (existing != null) {
            existing.setMetricValue(value);
            metricMapper.updateById(existing);
        } else {
            ActivityMetric metric = new ActivityMetric();
            metric.setActivityId(activityId);
            metric.setMetricType(metricType);
            metric.setMetricValue(value);
            metric.setMetricDate(today);
            metricMapper.insert(metric);
        }
    }
    
    @Transactional
    public void incrementMetric(Long activityId, String metricType, Double increment) {
        LocalDate today = LocalDate.now();
        ActivityMetric existing = metricMapper.findByActivityIdAndTypeAndDate(activityId, metricType, today);
        
        if (existing != null) {
            existing.setMetricValue(existing.getMetricValue() + increment);
            metricMapper.updateById(existing);
        } else {
            ActivityMetric metric = new ActivityMetric();
            metric.setActivityId(activityId);
            metric.setMetricType(metricType);
            metric.setMetricValue(increment);
            metric.setMetricDate(today);
            metricMapper.insert(metric);
        }
    }
    
    public Map<String, Double> getMetrics(Long activityId, LocalDate date) {
        List<ActivityMetric> metrics = metricMapper.findByActivityIdAndDateRange(activityId, date, date);
        return metrics.stream()
                .collect(Collectors.toMap(ActivityMetric::getMetricType, ActivityMetric::getMetricValue));
    }
    
    public List<Map<String, Object>> getMetricHistory(Long activityId, String metricType, 
                                                       LocalDate startDate, LocalDate endDate) {
        List<ActivityMetric> metrics = metricMapper.findByActivityIdAndDateRange(activityId, startDate, endDate);
        
        Map<LocalDate, Double> dateValueMap = new TreeMap<>();
        for (ActivityMetric metric : metrics) {
            if (metricType == null || metricType.equals(metric.getMetricType())) {
                dateValueMap.put(metric.getMetricDate(), metric.getMetricValue());
            }
        }
        
        return dateValueMap.entrySet().stream()
                .map(entry -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("date", entry.getKey().toString());
                    item.put("value", entry.getValue());
                    return item;
                })
                .collect(Collectors.toList());
    }
    
    @Transactional
    public ActivityHealth diagnoseActivity(Long activityId) {
        LocalDate today = LocalDate.now();
        Map<String, Double> metrics = getMetrics(activityId, today);
        
        double participationScore = calculateParticipationScore(metrics);
        double engagementScore = calculateEngagementScore(metrics);
        double qualityScore = calculateQualityScore(metrics);
        
        double overallScore = (participationScore * 0.35 + engagementScore * 0.35 + qualityScore * 0.30);
        
        String healthStatus = determineHealthStatus(overallScore);
        
        ActivityHealth existing = healthMapper.findByActivityIdAndDate(activityId, today);
        if (existing != null) {
            existing.setOverallScore(overallScore);
            existing.setParticipationScore(participationScore);
            existing.setEngagementScore(engagementScore);
            existing.setQualityScore(qualityScore);
            existing.setHealthStatus(healthStatus);
            healthMapper.updateById(existing);
            return existing;
        } else {
            ActivityHealth health = new ActivityHealth();
            health.setActivityId(activityId);
            health.setOverallScore(overallScore);
            health.setParticipationScore(participationScore);
            health.setEngagementScore(engagementScore);
            health.setQualityScore(qualityScore);
            health.setHealthStatus(healthStatus);
            health.setDiagnosisDate(today);
            healthMapper.insert(health);
            return health;
        }
    }
    
    private double calculateParticipationScore(Map<String, Double> metrics) {
        double applyCount = metrics.getOrDefault("APPLY_COUNT", 0.0);
        double viewCount = metrics.getOrDefault("VIEW_COUNT", 1.0);
        double participationRate = metrics.getOrDefault("PARTICIPATION_RATE", 0.0);
        
        double conversionRate = viewCount > 0 ? Math.min(100, (applyCount / viewCount) * 100) : 0;
        
        return Math.min(100, (conversionRate * 0.4 + participationRate * 0.6));
    }
    
    private double calculateEngagementScore(Map<String, Double> metrics) {
        double shareCount = metrics.getOrDefault("SHARE_COUNT", 0.0);
        double applyCount = metrics.getOrDefault("APPLY_COUNT", 1.0);
        
        double shareRate = applyCount > 0 ? Math.min(100, (shareCount / applyCount) * 100) : 0;
        double commentCount = metrics.getOrDefault("COMMENT_COUNT", 0.0);
        
        return Math.min(100, (shareRate * 0.6 + commentCount * 2));
    }
    
    private double calculateQualityScore(Map<String, Double> metrics) {
        double feedbackScore = metrics.getOrDefault("FEEDBACK_SCORE", 0.0);
        double complaintCount = metrics.getOrDefault("COMPLAINT_COUNT", 0.0);
        
        double baseScore = feedbackScore * 20;
        double penalty = complaintCount * 5;
        
        return Math.max(0, Math.min(100, baseScore - penalty));
    }
    
    private String determineHealthStatus(double score) {
        if (score >= 80) return "HEALTHY";
        if (score >= 60) return "CAUTION";
        return "DANGER";
    }
    
    public ActivityHealth getActivityHealth(Long activityId) {
        LocalDate today = LocalDate.now();
        ActivityHealth health = healthMapper.findByActivityIdAndDate(activityId, today);
        if (health == null) {
            return diagnoseActivity(activityId);
        }
        return health;
    }
    
    public List<ActivityHealth> getActivitiesByStatus(String status) {
        return healthMapper.findByHealthStatus(status);
    }
    
    public DiagnosisReport generateReport(Long activityId, String reportType,
                                         LocalDate periodStart, LocalDate periodEnd) {
        if (periodStart == null) {
            if ("WEEKLY".equals(reportType)) {
                periodStart = LocalDate.now().minusWeeks(1);
            } else if ("MONTHLY".equals(reportType)) {
                periodStart = LocalDate.now().minusMonths(1);
            } else {
                periodStart = LocalDate.now().minusMonths(1);
            }
        }
        if (periodEnd == null) {
            periodEnd = LocalDate.now();
        }

        List<ActivityMetric> metrics = metricMapper.findByActivityIdAndDateRange(activityId, periodStart, periodEnd);
        
        Map<String, List<Double>> metricValues = new HashMap<>();
        for (ActivityMetric metric : metrics) {
            metricValues.computeIfAbsent(metric.getMetricType(), k -> new ArrayList<>()).add(metric.getMetricValue());
        }
        
        Map<String, Object> reportData = new HashMap<>();
        reportData.put("activityId", activityId);
        reportData.put("reportType", reportType);
        reportData.put("periodStart", periodStart.toString());
        reportData.put("periodEnd", periodEnd.toString());
        reportData.put("generatedAt", LocalDateTime.now().toString());
        
        Map<String, Object> summary = new HashMap<>();
        for (Map.Entry<String, List<Double>> entry : metricValues.entrySet()) {
            List<Double> values = entry.getValue();
            summary.put(entry.getKey() + "_avg", values.stream().mapToDouble(Double::doubleValue).average().orElse(0));
            summary.put(entry.getKey() + "_max", values.stream().mapToDouble(Double::doubleValue).max().orElse(0));
            summary.put(entry.getKey() + "_min", values.stream().mapToDouble(Double::doubleValue).min().orElse(0));
            summary.put(entry.getKey() + "_total", values.stream().mapToDouble(Double::doubleValue).sum());
        }
        reportData.put("summary", summary);
        
        List<ImprovementSuggestion> suggestions = generateSuggestions(activityId, summary);
        reportData.put("suggestions", suggestions.stream().map(s -> {
            Map<String, Object> item = new HashMap<>();
            item.put("title", s.getTitle());
            item.put("severity", s.getSeverity());
            item.put("description", s.getDescription());
            item.put("suggestedAction", s.getSuggestedAction());
            return item;
        }).collect(Collectors.toList()));
        
        String title = String.format("%s活动诊断报告 (%s ~ %s)", 
                getActivityName(activityId), periodStart.toString(), periodEnd.toString());
        
        DiagnosisReport report = new DiagnosisReport();
        report.setActivityId(activityId);
        report.setReportTitle(title);
        try {
            report.setReportContent(objectMapper.writeValueAsString(reportData));
        } catch (Exception e) {
            log.error("Failed to serialize report data", e);
            report.setReportContent(reportData.toString());
        }
        report.setReportType(reportType);
        report.setPeriodStart(periodStart);
        report.setPeriodEnd(periodEnd);
        report.setGeneratedAt(LocalDateTime.now());
        reportMapper.insert(report);
        
        return report;
    }
    
    private String getActivityName(Long activityId) {
        Map<Long, String> activityNames = new HashMap<>();
        activityNames.put(1L, "环保志愿者活动");
        activityNames.put(2L, "垃圾分类宣传");
        activityNames.put(3L, "社区关爱老人");
        activityNames.put(4L, "社区清洁");
        activityNames.put(5L, "文化节志愿者");
        activityNames.put(6L, "艺术展览协助");
        activityNames.put(7L, "支教活动");
        activityNames.put(8L, "课后辅导");
        activityNames.put(9L, "运动会志愿者");
        activityNames.put(10L, "体育赛事协助");
        activityNames.put(11L, "义诊志愿者");
        activityNames.put(12L, "健康宣传");
        return activityNames.getOrDefault(activityId, "活动" + activityId);
    }
    
    @Transactional
    public List<ImprovementSuggestion> generateSuggestions(Long activityId, Map<String, Object> summary) {
        List<ImprovementSuggestion> suggestions = new ArrayList<>();
        
        double avgParticipationRate = getDoubleValue(summary, "PARTICIPATION_RATE_avg", 0.0);
        double avgApplyCount = getDoubleValue(summary, "APPLY_COUNT_avg", 0.0);
        double avgFeedbackScore = getDoubleValue(summary, "FEEDBACK_SCORE_avg", 0.0);
        double avgShareCount = getDoubleValue(summary, "SHARE_COUNT_avg", 0.0);
        double avgViewCount = getDoubleValue(summary, "VIEW_COUNT_avg", 0.0);
        
        if (avgParticipationRate < 50) {
            suggestions.add(createSuggestion(activityId, "PARTICIPATION", "HIGH",
                    "参与率偏低",
                    "活动参与率低于50%，可能影响活动效果",
                    "建议优化活动宣传渠道，增加宣传频次，优化活动时间安排"));
        }
        
        if (avgApplyCount < 20) {
            suggestions.add(createSuggestion(activityId, "ATTRACTIVENESS", "MEDIUM",
                    "报名人数较少",
                    "活动报名人数低于预期，可能吸引力不足",
                    "建议丰富活动内容，增加活动亮点，提供更多激励措施"));
        }
        
        if (avgFeedbackScore < 3.5) {
            suggestions.add(createSuggestion(activityId, "QUALITY", "HIGH",
                    "反馈评分偏低",
                    "志愿者反馈评分低于3.5分，需要关注服务质量",
                    "建议收集详细反馈意见，针对性改进服务流程和内容"));
        }
        
        if (avgShareCount < 5) {
            suggestions.add(createSuggestion(activityId, "ENGAGEMENT", "LOW",
                    "分享次数较少",
                    "活动分享率较低，传播效果有限",
                    "建议设计分享奖励机制，提升活动传播度"));
        }
        
        if (avgViewCount < 50) {
            suggestions.add(createSuggestion(activityId, "VISIBILITY", "MEDIUM",
                    "曝光量不足",
                    "活动浏览量较低，可能宣传不够",
                    "建议增加宣传渠道，优化活动标题和描述"));
        }
        
        for (ImprovementSuggestion suggestion : suggestions) {
            suggestionMapper.insert(suggestion);
        }
        
        return suggestions;
    }
    
    private ImprovementSuggestion createSuggestion(Long activityId, String type, String severity,
                                                   String title, String description, String action) {
        ImprovementSuggestion suggestion = new ImprovementSuggestion();
        suggestion.setActivityId(activityId);
        suggestion.setSuggestionType(type);
        suggestion.setSeverity(severity);
        suggestion.setTitle(title);
        suggestion.setDescription(description);
        suggestion.setSuggestedAction(action);
        suggestion.setPriority("HIGH".equals(severity) ? 1 : "MEDIUM".equals(severity) ? 2 : 3);
        suggestion.setStatus("PENDING");
        return suggestion;
    }
    
    public List<ImprovementSuggestion> getSuggestions(Long activityId, String status) {
        if (activityId != null && status != null) {
            return suggestionMapper.findByActivityIdAndStatus(activityId, status);
        } else if (activityId != null) {
            return suggestionMapper.findByActivityId(activityId);
        } else if (status != null) {
            return suggestionMapper.findByStatus(status);
        }
        return suggestionMapper.selectList(null);
    }
    
    @Transactional
    public Map<String, Object> updateSuggestionStatus(Long suggestionId, String status) {
        ImprovementSuggestion suggestion = suggestionMapper.selectById(suggestionId);
        if (suggestion == null) {
            return Map.of("success", false, "message", "建议不存在");
        }
        
        suggestion.setStatus(status);
        suggestionMapper.updateById(suggestion);
        
        return Map.of("success", true, "message", "状态更新成功");
    }
    
    public DiagnosisReport getLatestReport(Long activityId) {
        return reportMapper.findLatestByActivityId(activityId);
    }
    
    public List<DiagnosisReport> getReports(Long activityId, String reportType) {
        if (activityId != null && reportType != null) {
            return reportMapper.findByActivityId(activityId).stream()
                    .filter(r -> reportType.equals(r.getReportType()))
                    .collect(Collectors.toList());
        } else if (activityId != null) {
            return reportMapper.findByActivityId(activityId);
        } else if (reportType != null) {
            return reportMapper.findByReportType(reportType);
        }
        return reportMapper.selectList(null);
    }
    
    public Map<String, Object> getDashboardSummary() {
        List<ActivityHealth> allHealth = healthMapper.findByDate(LocalDate.now());
        
        long healthyCount = allHealth.stream().filter(h -> "HEALTHY".equals(h.getHealthStatus())).count();
        long cautionCount = allHealth.stream().filter(h -> "CAUTION".equals(h.getHealthStatus())).count();
        long dangerCount = allHealth.stream().filter(h -> "DANGER".equals(h.getHealthStatus())).count();
        
        double avgScore = allHealth.stream()
                .mapToDouble(ActivityHealth::getOverallScore)
                .average()
                .orElse(0);
        
        List<ImprovementSuggestion> pendingSuggestions = suggestionMapper.findByStatus("PENDING");
        long highPriorityCount = pendingSuggestions.stream()
                .filter(s -> "HIGH".equals(s.getSeverity()))
                .count();
        
        Map<String, Object> summary = new HashMap<>();
        summary.put("totalActivities", allHealth.size());
        summary.put("healthyCount", healthyCount);
        summary.put("cautionCount", cautionCount);
        summary.put("dangerCount", dangerCount);
        summary.put("averageScore", Math.round(avgScore * 100.0) / 100.0);
        summary.put("pendingSuggestions", pendingSuggestions.size());
        summary.put("highPrioritySuggestions", highPriorityCount);
        
        return summary;
    }
    
    private double getDoubleValue(Map<String, Object> map, String key, double defaultValue) {
        Object value = map.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Double) {
            return (Double) value;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}