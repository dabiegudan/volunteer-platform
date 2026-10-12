package com.volunteer.report.service;

import com.volunteer.report.entity.*;
import com.volunteer.report.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SkillService {
    
    private final SkillCategoryMapper categoryMapper;
    private final SkillMapper skillMapper;
    private final UserSkillMapper userSkillMapper;
    private final CertificationApplicationMapper applicationMapper;
    private final SkillMatchingMapper matchingMapper;
    
    public List<SkillCategory> getSkillCategories() {
        return categoryMapper.findActiveCategories();
    }
    
    public List<Map<String, Object>> getSkillsWithCategories() {
        List<SkillCategory> categories = categoryMapper.findActiveCategories();
        List<Skill> skills = skillMapper.findActiveSkills();
        
        Map<Long, List<Skill>> skillsByCategory = skills.stream()
                .collect(Collectors.groupingBy(Skill::getCategoryId));
        
        List<Map<String, Object>> result = new ArrayList<>();
        for (SkillCategory category : categories) {
            Map<String, Object> item = new HashMap<>();
            item.put("categoryId", category.getId());
            item.put("categoryName", category.getName());
            item.put("categoryIcon", category.getIcon());
            item.put("categoryDescription", category.getDescription());
            
            List<Map<String, Object>> skillList = skillsByCategory.getOrDefault(category.getId(), Collections.emptyList())
                    .stream()
                    .map(s -> {
                        Map<String, Object> skillMap = new HashMap<>();
                        skillMap.put("id", s.getId());
                        skillMap.put("name", s.getName());
                        skillMap.put("description", s.getDescription());
                        skillMap.put("icon", s.getIcon());
                        skillMap.put("level", s.getLevel());
                        skillMap.put("requiredHours", s.getRequiredHours());
                        skillMap.put("requiredActivities", s.getRequiredActivities());
                        return skillMap;
                    })
                    .collect(Collectors.toList());
            
            item.put("skills", skillList);
            result.add(item);
        }
        
        return result;
    }
    
    public List<Skill> searchSkills(String keyword) {
        if (keyword == null || keyword.isEmpty()) {
            return skillMapper.findActiveSkills();
        }
        return skillMapper.searchByKeyword(keyword);
    }
    
    public Skill getSkillById(Long skillId) {
        return skillMapper.selectById(skillId);
    }
    
    public List<Map<String, Object>> getUserSkills(Long userId) {
        List<UserSkill> userSkills = userSkillMapper.findByUserId(userId);
        Map<Long, Skill> skillMap = skillMapper.findActiveSkills().stream()
                .collect(Collectors.toMap(Skill::getId, s -> s));
        
        List<Map<String, Object>> result = new ArrayList<>();
        for (UserSkill userSkill : userSkills) {
            Skill skill = skillMap.get(userSkill.getSkillId());
            if (skill != null) {
                Map<String, Object> item = new HashMap<>();
                item.put("id", userSkill.getId());
                item.put("skillId", skill.getId());
                item.put("skillName", skill.getName());
                item.put("skillIcon", skill.getIcon());
                item.put("skillDescription", skill.getDescription());
                item.put("level", userSkill.getLevel());
                item.put("proficiency", userSkill.getProficiency());
                item.put("verifiedHours", userSkill.getVerifiedHours());
                item.put("verifiedActivities", userSkill.getVerifiedActivities());
                item.put("status", userSkill.getStatus());
                item.put("certificateNo", userSkill.getCertificateNo());
                item.put("certificateUrl", userSkill.getCertificateUrl());
                item.put("applyTime", userSkill.getApplyTime());
                item.put("approveTime", userSkill.getApproveTime());
                item.put("rejectReason", userSkill.getRejectReason());
                result.add(item);
            }
        }
        
        return result;
    }
    
    @Transactional
    public Map<String, Object> applyForSkill(Long userId, Long skillId) {
        UserSkill existing = userSkillMapper.findByUserIdAndSkillId(userId, skillId);
        if (existing != null) {
            if ("PENDING".equals(existing.getStatus())) {
                return Map.of("success", false, "message", "已有待审核的申请");
            } else if ("APPROVED".equals(existing.getStatus())) {
                return Map.of("success", false, "message", "该技能已认证");
            }
        }
        
        Skill skill = skillMapper.selectById(skillId);
        if (skill == null) {
            return Map.of("success", false, "message", "技能不存在");
        }
        
        UserSkill userSkill = new UserSkill();
        userSkill.setUserId(userId);
        userSkill.setSkillId(skillId);
        userSkill.setLevel("初级");
        userSkill.setProficiency(0);
        userSkill.setVerifiedHours(0);
        userSkill.setVerifiedActivities(0);
        userSkill.setStatus("PENDING");
        userSkill.setApplyTime(LocalDateTime.now());
        userSkillMapper.insert(userSkill);
        
        return Map.of("success", true, "message", "申请提交成功，等待审核");
    }
    
    @Transactional
    public Map<String, Object> approveSkill(Long userSkillId, Long approverId, String certificateNo) {
        UserSkill userSkill = userSkillMapper.selectById(userSkillId);
        if (userSkill == null) {
            return Map.of("success", false, "message", "记录不存在");
        }
        
        if (!"PENDING".equals(userSkill.getStatus())) {
            return Map.of("success", false, "message", "状态不允许操作");
        }
        
        userSkill.setStatus("APPROVED");
        userSkill.setApproverId(approverId);
        userSkill.setApproveTime(LocalDateTime.now());
        userSkill.setCertificateNo(certificateNo != null ? certificateNo : generateCertificateNo());
        userSkill.setProficiency(20);
        
        userSkillMapper.updateById(userSkill);
        
        return Map.of("success", true, "message", "审核通过");
    }
    
    @Transactional
    public Map<String, Object> rejectSkill(Long userSkillId, Long approverId, String reason) {
        UserSkill userSkill = userSkillMapper.selectById(userSkillId);
        if (userSkill == null) {
            return Map.of("success", false, "message", "记录不存在");
        }
        
        if (!"PENDING".equals(userSkill.getStatus())) {
            return Map.of("success", false, "message", "状态不允许操作");
        }
        
        userSkill.setStatus("REJECTED");
        userSkill.setApproverId(approverId);
        userSkill.setApproveTime(LocalDateTime.now());
        userSkill.setRejectReason(reason);
        
        userSkillMapper.updateById(userSkill);
        
        return Map.of("success", true, "message", "已拒绝");
    }
    
    private String generateCertificateNo() {
        return "CERT" + System.currentTimeMillis() + String.format("%03d", new Random().nextInt(1000));
    }
    
    @Transactional
    public Map<String, Object> applyForCertification(Long userId, Long skillId, String targetLevel, 
                                                     List<String> proofFiles, String description) {
        List<CertificationApplication> existing = applicationMapper.findByUserIdAndSkillId(userId, skillId);
        for (CertificationApplication app : existing) {
            if ("PENDING".equals(app.getStatus())) {
                return Map.of("success", false, "message", "已有待审核的申请");
            }
        }
        
        Skill skill = skillMapper.selectById(skillId);
        if (skill == null) {
            return Map.of("success", false, "message", "技能不存在");
        }
        
        CertificationApplication application = new CertificationApplication();
        application.setUserId(userId);
        application.setSkillId(skillId);
        application.setTargetLevel(targetLevel);
        application.setProofFiles(proofFiles != null ? String.join(",", proofFiles) : null);
        application.setDescription(description);
        application.setStatus("PENDING");
        application.setApplyTime(LocalDateTime.now());
        applicationMapper.insert(application);
        
        return Map.of("success", true, "message", "认证申请提交成功，等待审核");
    }
    
    @Transactional
    public Map<String, Object> reviewCertification(Long applicationId, Long reviewerId, 
                                                   String status, String comments) {
        CertificationApplication application = applicationMapper.selectById(applicationId);
        if (application == null) {
            return Map.of("success", false, "message", "申请不存在");
        }
        
        if (!"PENDING".equals(application.getStatus())) {
            return Map.of("success", false, "message", "状态不允许操作");
        }
        
        if (!Set.of("APPROVED", "REJECTED").contains(status)) {
            return Map.of("success", false, "message", "审核状态不合法");
        }

        application.setStatus(status);
        application.setReviewerId(reviewerId);
        application.setReviewTime(LocalDateTime.now());
        application.setReviewComments(comments);
        
        if ("APPROVED".equals(status)) {
            application.setCertificateNo(generateCertificateNo());
            
            UserSkill userSkill = userSkillMapper.findByUserIdAndSkillId(
                    application.getUserId(), application.getSkillId());
            
            if (userSkill == null) {
                userSkill = new UserSkill();
                userSkill.setUserId(application.getUserId());
                userSkill.setSkillId(application.getSkillId());
                userSkill.setApplyTime(application.getApplyTime());
            }

            userSkill.setLevel(application.getTargetLevel());
            userSkill.setStatus("APPROVED");
            userSkill.setCertificateNo(application.getCertificateNo());
            userSkill.setApproverId(reviewerId);
            userSkill.setApproveTime(LocalDateTime.now());
            
            int proficiency = getProficiencyByLevel(application.getTargetLevel());
            userSkill.setProficiency(proficiency);
            if (userSkill.getId() == null) {
                userSkillMapper.insert(userSkill);
            } else {
                userSkillMapper.updateById(userSkill);
            }
        }
        
        applicationMapper.updateById(application);
        
        String message = "APPROVED".equals(status) ? "审核通过" : "已拒绝";
        return Map.of("success", true, "message", message);
    }
    
    private int getProficiencyByLevel(String level) {
        return switch (level) {
            case "初级" -> 20;
            case "中级" -> 50;
            case "高级" -> 80;
            case "专家" -> 100;
            default -> 20;
        };
    }
    
    public List<Map<String, Object>> getApplications(String status) {
        List<CertificationApplication> applications = status != null ? 
                applicationMapper.findByStatus(status) : applicationMapper.findPendingApplications();
        
        Map<Long, Skill> skillMap = skillMapper.findActiveSkills().stream()
                .collect(Collectors.toMap(Skill::getId, s -> s));
        
        return applications.stream().map(app -> {
            Map<String, Object> item = new HashMap<>();
            item.put("id", app.getId());
            item.put("userId", app.getUserId());
            item.put("skillId", app.getSkillId());
            item.put("skillName", skillMap.get(app.getSkillId()) != null ? skillMap.get(app.getSkillId()).getName() : "");
            item.put("targetLevel", app.getTargetLevel());
            item.put("description", app.getDescription());
            item.put("status", app.getStatus());
            item.put("applyTime", app.getApplyTime());
            item.put("reviewTime", app.getReviewTime());
            item.put("reviewComments", app.getReviewComments());
            item.put("certificateNo", app.getCertificateNo());
            return item;
        }).collect(Collectors.toList());
    }
    
    public List<Map<String, Object>> getUserApplications(Long userId) {
        List<CertificationApplication> applications = applicationMapper.findByUserId(userId);
        
        Map<Long, Skill> skillMap = skillMapper.findActiveSkills().stream()
                .collect(Collectors.toMap(Skill::getId, s -> s));
        
        return applications.stream().map(app -> {
            Map<String, Object> item = new HashMap<>();
            item.put("id", app.getId());
            item.put("skillId", app.getSkillId());
            item.put("skillName", skillMap.get(app.getSkillId()) != null ? skillMap.get(app.getSkillId()).getName() : "");
            item.put("skillIcon", skillMap.get(app.getSkillId()) != null ? skillMap.get(app.getSkillId()).getIcon() : "");
            item.put("targetLevel", app.getTargetLevel());
            item.put("description", app.getDescription());
            item.put("status", app.getStatus());
            item.put("applyTime", app.getApplyTime());
            item.put("reviewComments", app.getReviewComments());
            item.put("certificateNo", app.getCertificateNo());
            return item;
        }).collect(Collectors.toList());
    }
    
    @Transactional
    public void addSkillMatching(Long activityId, Long skillId, String requiredLevel, Integer importance) {
        SkillMatching existing = matchingMapper.findByActivityIdAndSkillId(activityId, skillId);
        if (existing != null) {
            existing.setRequiredLevel(requiredLevel);
            existing.setImportance(importance);
            matchingMapper.updateById(existing);
        } else {
            SkillMatching matching = new SkillMatching();
            matching.setActivityId(activityId);
            matching.setSkillId(skillId);
            matching.setRequiredLevel(requiredLevel);
            matching.setImportance(importance);
            matchingMapper.insert(matching);
        }
    }
    
    @Transactional
    public void removeSkillMatching(Long activityId, Long skillId) {
        SkillMatching matching = matchingMapper.findByActivityIdAndSkillId(activityId, skillId);
        if (matching != null) {
            matchingMapper.deleteById(matching.getId());
        }
    }
    
    public List<Map<String, Object>> getSkillMatchings(Long activityId) {
        List<SkillMatching> matchings = matchingMapper.findByActivityId(activityId);
        
        Map<Long, Skill> skillMap = skillMapper.findActiveSkills().stream()
                .collect(Collectors.toMap(Skill::getId, s -> s));
        
        return matchings.stream().map(m -> {
            Map<String, Object> item = new HashMap<>();
            item.put("id", m.getId());
            item.put("skillId", m.getSkillId());
            item.put("skillName", skillMap.get(m.getSkillId()) != null ? skillMap.get(m.getSkillId()).getName() : "");
            item.put("skillIcon", skillMap.get(m.getSkillId()) != null ? skillMap.get(m.getSkillId()).getIcon() : "");
            item.put("requiredLevel", m.getRequiredLevel());
            item.put("importance", m.getImportance());
            return item;
        }).collect(Collectors.toList());
    }
    
    public Map<String, Object> getSkillMatchPercentage(Long userId, Long activityId) {
        List<SkillMatching> requiredSkills = matchingMapper.findByActivityId(activityId);
        if (requiredSkills.isEmpty()) {
            return Map.of("matchPercentage", 100, "matchedCount", 0, "totalRequired", 0, "missingSkills", List.of());
        }
        
        List<UserSkill> userSkills = userSkillMapper.findApprovedSkillsByUserId(userId);
        Set<Long> userSkillIds = userSkills.stream()
                .map(UserSkill::getSkillId)
                .collect(Collectors.toSet());
        
        int matchedCount = 0;
        List<Map<String, Object>> missingSkills = new ArrayList<>();
        
        for (SkillMatching matching : requiredSkills) {
            if (userSkillIds.contains(matching.getSkillId())) {
                matchedCount++;
            } else {
                Skill skill = skillMapper.selectById(matching.getSkillId());
                if (skill != null) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("skillId", skill.getId());
                    item.put("skillName", skill.getName());
                    item.put("skillIcon", skill.getIcon());
                    item.put("requiredLevel", matching.getRequiredLevel());
                    missingSkills.add(item);
                }
            }
        }
        
        int matchPercentage = (matchedCount * 100) / requiredSkills.size();
        
        return Map.of(
                "matchPercentage", matchPercentage,
                "matchedCount", matchedCount,
                "totalRequired", requiredSkills.size(),
                "missingSkills", missingSkills
        );
    }
}
