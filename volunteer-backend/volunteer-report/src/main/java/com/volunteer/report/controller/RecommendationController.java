package com.volunteer.report.controller;

import com.volunteer.auth.annotation.RequiresAccess;
import com.volunteer.report.service.UserPreferenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/recommendation")
@RequiredArgsConstructor
public class RecommendationController {

    private final UserPreferenceService userPreferenceService;

    @RequiresAccess(requireStudentId = true)
    @GetMapping("/profile")
    public ResponseEntity<Map<String, Object>> getUserProfile(@RequestAttribute("userId") Long userId) {
        Map<String, Object> profile = userPreferenceService.getUserProfileInfo(userId);
        return ResponseEntity.ok(profile);
    }

    @RequiresAccess(requireStudentId = true)
    @PostMapping("/profile")
    public ResponseEntity<Map<String, Object>> updateUserProfile(
            @RequestAttribute("userId") Long userId,
            @RequestBody Map<String, Object> updates) {
        userPreferenceService.updateUserProfile(userId, updates);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "用户画像更新成功");
        return ResponseEntity.ok(result);
    }

    @RequiresAccess(requireStudentId = true)
    @GetMapping("/preferences")
    public ResponseEntity<List<Map<String, Object>>> getUserPreferences(@RequestAttribute("userId") Long userId) {
        List<Map<String, Object>> preferences = userPreferenceService.getUserPreferences(userId)
                .stream()
                .map(p -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("activityType", p.getActivityType());
                    item.put("category", p.getCategory());
                    item.put("priority", p.getPriority());
                    return item;
                })
                .toList();
        return ResponseEntity.ok(preferences);
    }

    @RequiresAccess(requireStudentId = true)
    @PostMapping("/preferences/add")
    public ResponseEntity<Map<String, Object>> addPreference(
            @RequestAttribute("userId") Long userId,
            @RequestParam String activityType,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "3") Integer priority) {
        userPreferenceService.addActivityPreference(userId, activityType, category, priority);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "偏好添加成功");
        return ResponseEntity.ok(result);
    }

    @RequiresAccess(requireStudentId = true)
    @PostMapping("/preferences/remove")
    public ResponseEntity<Map<String, Object>> removePreference(
            @RequestAttribute("userId") Long userId,
            @RequestParam String activityType) {
        userPreferenceService.removeActivityPreference(userId, activityType);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "偏好移除成功");
        return ResponseEntity.ok(result);
    }

    @RequiresAccess(requireStudentId = true)
    @GetMapping("/list")
    public ResponseEntity<List<Map<String, Object>>> getRecommendations(
            @RequestAttribute("userId") Long userId,
            @RequestParam(defaultValue = "10") Integer limit) {
        List<Map<String, Object>> recommendations = userPreferenceService.getRecommendations(userId, limit);
        return ResponseEntity.ok(recommendations);
    }

    @RequiresAccess(requireStudentId = true)
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refreshRecommendations(@RequestAttribute("userId") Long userId) {
        userPreferenceService.generateRecommendations(userId);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "推荐已刷新");
        return ResponseEntity.ok(result);
    }

    @RequiresAccess(requireStudentId = true)
    @PostMapping("/action")
    public ResponseEntity<Map<String, Object>> logAction(
            @RequestAttribute("userId") Long userId,
            @RequestParam Long activityId,
            @RequestParam String action,
            @RequestParam(required = false) String sessionId) {
        userPreferenceService.logRecommendationAction(userId, activityId, action, sessionId);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "行为记录成功");
        return ResponseEntity.ok(result);
    }

    @RequiresAccess(requireStudentId = true)
    @PostMapping("/activity/view")
    public ResponseEntity<Map<String, Object>> recordView(@RequestParam Long activityId) {
        userPreferenceService.recordActivityView(activityId);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "浏览记录成功");
        return ResponseEntity.ok(result);
    }

    @RequiresAccess(requireStudentId = true)
    @PostMapping("/activity/apply")
    public ResponseEntity<Map<String, Object>> recordApply(@RequestParam Long activityId) {
        userPreferenceService.recordActivityApply(activityId);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "报名记录成功");
        return ResponseEntity.ok(result);
    }

    @RequiresAccess(requireStudentId = true)
    @PostMapping("/activity/share")
    public ResponseEntity<Map<String, Object>> recordShare(@RequestParam Long activityId) {
        userPreferenceService.recordActivityShare(activityId);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "分享记录成功");
        return ResponseEntity.ok(result);
    }

    @RequiresAccess(requireStudentId = true)
    @GetMapping("/hot")
    public ResponseEntity<List<Map<String, Object>>> getHotActivities(
            @RequestParam(defaultValue = "10") Integer limit) {
        List<Map<String, Object>> hotActivities = userPreferenceService.getHotActivities(limit);
        return ResponseEntity.ok(hotActivities);
    }

    @RequiresAccess(requireStudentId = true)
    @PostMapping("/score/availability")
    public ResponseEntity<Map<String, Object>> updateAvailabilityScore(
            @RequestAttribute("userId") Long userId,
            @RequestParam Integer increment) {
        userPreferenceService.updateAvailabilityScore(userId, increment);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "活跃度评分已更新");
        return ResponseEntity.ok(result);
    }

    @RequiresAccess(requireStudentId = true)
    @PostMapping("/score/reliability")
    public ResponseEntity<Map<String, Object>> updateReliabilityScore(
            @RequestAttribute("userId") Long userId,
            @RequestParam Integer increment) {
        userPreferenceService.updateReliabilityScore(userId, increment);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "可靠性评分已更新");
        return ResponseEntity.ok(result);
    }
}
