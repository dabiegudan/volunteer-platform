package com.volunteer.report.controller;

import com.volunteer.auth.annotation.RequiresAccess;
import com.volunteer.report.entity.Skill;
import com.volunteer.report.service.SkillService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/skill")
@RequiredArgsConstructor
public class SkillController {
    
    private final SkillService skillService;
    
    @GetMapping("/categories")
    public ResponseEntity<List<Map<String, Object>>> getSkillsWithCategories() {
        List<Map<String, Object>> result = skillService.getSkillsWithCategories();
        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/search")
    public ResponseEntity<List<Skill>> searchSkills(@RequestParam(required = false) String keyword) {
        List<Skill> skills = skillService.searchSkills(keyword);
        return ResponseEntity.ok(skills);
    }
    
    @GetMapping("/{skillId}")
    public ResponseEntity<Skill> getSkillById(@PathVariable Long skillId) {
        Skill skill = skillService.getSkillById(skillId);
        if (skill == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(skill);
    }
    
    @GetMapping("/user")
    public ResponseEntity<List<Map<String, Object>>> getUserSkills(@RequestAttribute("userId") Long userId) {
        List<Map<String, Object>> skills = skillService.getUserSkills(userId);
        return ResponseEntity.ok(skills);
    }
    
    @PostMapping("/apply")
    public ResponseEntity<Map<String, Object>> applyForSkill(
            @RequestAttribute("userId") Long userId,
            @RequestParam Long skillId) {
        Map<String, Object> result = skillService.applyForSkill(userId, skillId);
        if ((Boolean) result.get("success")) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.badRequest().body(result);
        }
    }
    
    @PostMapping("/approve")
    @RequiresAccess("admin")
    public ResponseEntity<Map<String, Object>> approveSkill(
            @RequestParam Long userSkillId,
            @RequestAttribute("userId") Long approverId,
            @RequestParam(required = false) String certificateNo) {
        Map<String, Object> result = skillService.approveSkill(userSkillId, approverId, certificateNo);
        if ((Boolean) result.get("success")) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.badRequest().body(result);
        }
    }
    
    @PostMapping("/reject")
    @RequiresAccess("admin")
    public ResponseEntity<Map<String, Object>> rejectSkill(
            @RequestParam Long userSkillId,
            @RequestAttribute("userId") Long approverId,
            @RequestParam String reason) {
        Map<String, Object> result = skillService.rejectSkill(userSkillId, approverId, reason);
        if ((Boolean) result.get("success")) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.badRequest().body(result);
        }
    }
    
    @PostMapping("/certification/apply")
    public ResponseEntity<Map<String, Object>> applyForCertification(
            @RequestAttribute("userId") Long userId,
            @RequestParam Long skillId,
            @RequestParam String targetLevel,
            @RequestBody(required = false) List<String> proofFiles,
            @RequestParam(required = false) String description) {
        Map<String, Object> result = skillService.applyForCertification(userId, skillId, targetLevel, proofFiles, description);
        if ((Boolean) result.get("success")) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.badRequest().body(result);
        }
    }
    
    @PostMapping("/certification/review")
    @RequiresAccess("admin")
    public ResponseEntity<Map<String, Object>> reviewCertification(
            @RequestParam Long applicationId,
            @RequestAttribute("userId") Long reviewerId,
            @RequestParam String status,
            @RequestParam(required = false) String comments) {
        Map<String, Object> result = skillService.reviewCertification(applicationId, reviewerId, status, comments);
        if ((Boolean) result.get("success")) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.badRequest().body(result);
        }
    }
    
    @GetMapping("/certification/applications")
    @RequiresAccess("admin")
    public ResponseEntity<List<Map<String, Object>>> getApplications(
            @RequestParam(required = false) String status) {
        List<Map<String, Object>> applications = skillService.getApplications(status);
        return ResponseEntity.ok(applications);
    }
    
    @GetMapping("/certification/user")
    public ResponseEntity<List<Map<String, Object>>> getUserApplications(@RequestAttribute("userId") Long userId) {
        List<Map<String, Object>> applications = skillService.getUserApplications(userId);
        return ResponseEntity.ok(applications);
    }
    
    @PostMapping("/matching/add")
    @RequiresAccess("organizer")
    public ResponseEntity<Map<String, Object>> addSkillMatching(
            @RequestParam Long activityId,
            @RequestParam Long skillId,
            @RequestParam(required = false) String requiredLevel,
            @RequestParam(defaultValue = "1") Integer importance) {
        skillService.addSkillMatching(activityId, skillId, requiredLevel, importance);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "技能匹配添加成功");
        return ResponseEntity.ok(result);
    }
    
    @PostMapping("/matching/remove")
    @RequiresAccess("organizer")
    public ResponseEntity<Map<String, Object>> removeSkillMatching(
            @RequestParam Long activityId,
            @RequestParam Long skillId) {
        skillService.removeSkillMatching(activityId, skillId);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "技能匹配移除成功");
        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/matching/{activityId}")
    public ResponseEntity<List<Map<String, Object>>> getSkillMatchings(@PathVariable Long activityId) {
        List<Map<String, Object>> matchings = skillService.getSkillMatchings(activityId);
        return ResponseEntity.ok(matchings);
    }
    
    @GetMapping("/match-percentage")
    public ResponseEntity<Map<String, Object>> getSkillMatchPercentage(
            @RequestAttribute("userId") Long userId,
            @RequestParam Long activityId) {
        Map<String, Object> result = skillService.getSkillMatchPercentage(userId, activityId);
        return ResponseEntity.ok(result);
    }
}
