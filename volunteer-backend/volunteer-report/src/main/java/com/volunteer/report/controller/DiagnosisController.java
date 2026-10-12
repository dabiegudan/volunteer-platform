package com.volunteer.report.controller;

import com.volunteer.auth.annotation.RequiresAccess;
import com.volunteer.report.entity.ActivityHealth;
import com.volunteer.report.entity.DiagnosisReport;
import com.volunteer.report.entity.ImprovementSuggestion;
import com.volunteer.report.service.DiagnosisService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/diagnosis")
@RequiredArgsConstructor
public class DiagnosisController {
    
    private final DiagnosisService diagnosisService;
    
    @RequiresAccess("organizer")
    @PostMapping("/metric")
    public ResponseEntity<Map<String, Object>> recordMetric(
            @RequestParam Long activityId,
            @RequestParam String metricType,
            @RequestParam Double value) {
        diagnosisService.recordMetric(activityId, metricType, value);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "指标记录成功");
        return ResponseEntity.ok(result);
    }
    
    @RequiresAccess("organizer")
    @PostMapping("/metric/increment")
    public ResponseEntity<Map<String, Object>> incrementMetric(
            @RequestParam Long activityId,
            @RequestParam String metricType,
            @RequestParam(defaultValue = "1.0") Double increment) {
        diagnosisService.incrementMetric(activityId, metricType, increment);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "指标增量记录成功");
        return ResponseEntity.ok(result);
    }
    
    @RequiresAccess("organizer")
    @GetMapping("/metric")
    public ResponseEntity<Map<String, Double>> getMetrics(
            @RequestParam Long activityId,
            @RequestParam(required = false) String date) {
        LocalDate queryDate = date != null ? LocalDate.parse(date) : LocalDate.now();
        Map<String, Double> metrics = diagnosisService.getMetrics(activityId, queryDate);
        return ResponseEntity.ok(metrics);
    }
    
    @RequiresAccess("organizer")
    @GetMapping("/metric/history")
    public ResponseEntity<List<Map<String, Object>>> getMetricHistory(
            @RequestParam Long activityId,
            @RequestParam(required = false) String metricType,
            @RequestParam String startDate,
            @RequestParam String endDate) {
        List<Map<String, Object>> history = diagnosisService.getMetricHistory(
                activityId, metricType, LocalDate.parse(startDate), LocalDate.parse(endDate));
        return ResponseEntity.ok(history);
    }
    
    @RequiresAccess("organizer")
    @PostMapping("/diagnose")
    public ResponseEntity<ActivityHealth> diagnoseActivity(@RequestParam Long activityId) {
        ActivityHealth health = diagnosisService.diagnoseActivity(activityId);
        return ResponseEntity.ok(health);
    }
    
    @RequiresAccess("organizer")
    @GetMapping("/health")
    public ResponseEntity<ActivityHealth> getActivityHealth(@RequestParam Long activityId) {
        ActivityHealth health = diagnosisService.getActivityHealth(activityId);
        return ResponseEntity.ok(health);
    }
    
    @RequiresAccess("organizer")
    @GetMapping("/health/status")
    public ResponseEntity<List<ActivityHealth>> getActivitiesByStatus(@RequestParam String status) {
        List<ActivityHealth> activities = diagnosisService.getActivitiesByStatus(status);
        return ResponseEntity.ok(activities);
    }
    
    @RequiresAccess("organizer")
    @PostMapping("/report/generate")
    public ResponseEntity<DiagnosisReport> generateReport(
            @RequestParam Long activityId,
            @RequestParam(defaultValue = "WEEKLY") String reportType,
            @RequestParam(required = false) String periodStart,
            @RequestParam(required = false) String periodEnd) {
        LocalDate start = periodStart != null ? LocalDate.parse(periodStart) : null;
        LocalDate end = periodEnd != null ? LocalDate.parse(periodEnd) : null;
        DiagnosisReport report = diagnosisService.generateReport(activityId, reportType, start, end);
        return ResponseEntity.ok(report);
    }
    
    @RequiresAccess("organizer")
    @GetMapping("/report/latest")
    public ResponseEntity<DiagnosisReport> getLatestReport(@RequestParam Long activityId) {
        DiagnosisReport report = diagnosisService.getLatestReport(activityId);
        if (report == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(report);
    }
    
    @RequiresAccess("organizer")
    @GetMapping("/reports")
    public ResponseEntity<List<DiagnosisReport>> getReports(
            @RequestParam(required = false) Long activityId,
            @RequestParam(required = false) String reportType) {
        List<DiagnosisReport> reports = diagnosisService.getReports(activityId, reportType);
        return ResponseEntity.ok(reports);
    }
    
    @RequiresAccess("organizer")
    @GetMapping("/suggestions")
    public ResponseEntity<List<ImprovementSuggestion>> getSuggestions(
            @RequestParam(required = false) Long activityId,
            @RequestParam(required = false) String status) {
        List<ImprovementSuggestion> suggestions = diagnosisService.getSuggestions(activityId, status);
        return ResponseEntity.ok(suggestions);
    }
    
    @RequiresAccess("organizer")
    @PostMapping("/suggestion/status")
    public ResponseEntity<Map<String, Object>> updateSuggestionStatus(
            @RequestParam Long suggestionId,
            @RequestParam String status) {
        Map<String, Object> result = diagnosisService.updateSuggestionStatus(suggestionId, status);
        if ((Boolean) result.get("success")) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.badRequest().body(result);
        }
    }
    
    @RequiresAccess("organizer")
    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> getDashboardSummary() {
        Map<String, Object> summary = diagnosisService.getDashboardSummary();
        return ResponseEntity.ok(summary);
    }
}