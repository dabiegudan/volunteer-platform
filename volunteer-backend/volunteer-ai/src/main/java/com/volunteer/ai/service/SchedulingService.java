package com.volunteer.ai.service;

import dev.langchain4j.model.chat.ChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class SchedulingService {
    
    private final ChatModel chatModel;
    
    public Map<String, Object> generateSchedule(List<Map<String, Object>> volunteers, 
                                                Map<String, Object> activityInfo) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            StringBuilder volunteersInfo = new StringBuilder();
            for (Map<String, Object> volunteer : volunteers) {
                volunteersInfo.append(String.format(
                        "志愿者ID: %s, 姓名: %s, 技能: %s, 可用时间: %s, 服务经验: %s年\n",
                        volunteer.get("id"),
                        volunteer.get("name"),
                        volunteer.get("skills"),
                        volunteer.get("availability"),
                        volunteer.get("experience") != null ? volunteer.get("experience") : 0
                ));
            }
            
            String prompt = String.format("""
                    你是一个志愿者活动排班助手。请根据以下信息为活动生成优化的排班表：
                    
                    活动信息：
                    - 活动名称：%s
                    - 活动时间：%s
                    - 活动地点：%s
                    - 所需志愿者人数：%s
                    - 所需技能：%s
                    - 活动描述：%s
                    
                    志愿者列表：
                    %s
                    
                    请为该活动生成详细的排班方案，包括：
                    1. 各时间段的人员安排
                    2. 每个志愿者的具体职责分配
                    3. 技能匹配说明
                    4. 排班合理性说明
                    
                    要求：
                    - 最大化志愿者技能与岗位需求的匹配度
                    - 均衡分配工作时长
                    - 考虑志愿者的时间可用性
                    - 使用清晰的表格或列表形式呈现
                    """, 
                    activityInfo.get("activityName"),
                    activityInfo.get("activityTime"),
                    activityInfo.get("location"),
                    activityInfo.get("requiredVolunteers"),
                    activityInfo.get("requiredSkills"),
                    activityInfo.get("description") != null ? activityInfo.get("description") : "无",
                    volunteersInfo.toString());
            
            String schedule = chatModel.chat(prompt);
            
            result.put("success", true);
            result.put("schedule", schedule);
            result.put("activityName", activityInfo.get("activityName"));
            result.put("volunteerCount", volunteers.size());
            
            log.debug("排班方案生成完成，活动: {}, 志愿者数: {}", activityInfo.get("activityName"), volunteers.size());
            
        } catch (Exception e) {
            log.error("排班方案生成失败", e);
            result.put("success", false);
            result.put("schedule", "很抱歉，暂时无法生成排班方案，请稍后再试。");
            result.put("error", e.getMessage());
        }
        
        return result;
    }
    
    public Map<String, Object> matchVolunteersToActivity(List<Map<String, Object>> volunteers, 
                                                         Map<String, Object> activityInfo) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            List<Map<String, Object>> matchedVolunteers = new ArrayList<>();
            
            for (Map<String, Object> volunteer : volunteers) {
                Map<String, Object> matchResult = new HashMap<>();
                matchResult.putAll(volunteer);
                
                int matchScore = calculateMatchScore(volunteer, activityInfo);
                matchResult.put("matchScore", matchScore);
                matchResult.put("matchReasons", getMatchReasons(volunteer, activityInfo));
                
                matchedVolunteers.add(matchResult);
            }
            
            matchedVolunteers.sort((a, b) -> {
                Integer scoreA = (Integer) a.get("matchScore");
                Integer scoreB = (Integer) b.get("matchScore");
                return scoreB.compareTo(scoreA);
            });
            
            int requiredCount = activityInfo.get("requiredVolunteers") != null ? 
                    ((Number) activityInfo.get("requiredVolunteers")).intValue() : 5;
            
            List<Map<String, Object>> bestMatches = matchedVolunteers.subList(0, Math.min(requiredCount, matchedVolunteers.size()));
            
            result.put("success", true);
            result.put("matchedVolunteers", bestMatches);
            result.put("allVolunteers", matchedVolunteers);
            result.put("matchedCount", bestMatches.size());
            
            log.debug("志愿者匹配完成，活动: {}, 匹配人数: {}", activityInfo.get("activityName"), bestMatches.size());
            
        } catch (Exception e) {
            log.error("志愿者匹配失败", e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        
        return result;
    }
    
    public Map<String, Object> generateMultiDaySchedule(List<Map<String, Object>> volunteers,
                                                        Map<String, Object> activityInfo,
                                                        int days) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            StringBuilder volunteersInfo = new StringBuilder();
            for (Map<String, Object> volunteer : volunteers) {
                volunteersInfo.append(String.format(
                        "志愿者ID: %s, 姓名: %s, 技能: %s, 可用日期: %s, 每日可用时长: %s小时\n",
                        volunteer.get("id"),
                        volunteer.get("name"),
                        volunteer.get("skills"),
                        volunteer.get("availableDates"),
                        volunteer.get("dailyHours") != null ? volunteer.get("dailyHours") : 4
                ));
            }
            
            String prompt = String.format("""
                    你是一个多日活动排班助手。请根据以下信息为活动生成多日排班方案：
                    
                    活动信息：
                    - 活动名称：%s
                    - 活动开始日期：%s
                    - 活动持续天数：%d天
                    - 活动地点：%s
                    - 每日所需志愿者人数：%s
                    - 所需技能：%s
                    
                    志愿者列表：
                    %s
                    
                    请生成详细的多日排班方案，包括：
                    1. 每天的人员安排表
                    2. 每个志愿者的总工作时长统计
                    3. 技能分布说明
                    4. 排班均衡性说明
                    
                    要求：
                    - 均衡分配每位志愿者的工作天数和时长
                    - 确保每天都有足够的技能覆盖
                    - 考虑志愿者的日期可用性
                    """, 
                    activityInfo.get("activityName"),
                    activityInfo.get("startDate"),
                    days,
                    activityInfo.get("location"),
                    activityInfo.get("dailyRequired"),
                    activityInfo.get("requiredSkills"),
                    volunteersInfo.toString());
            
            String schedule = chatModel.chat(prompt);
            
            result.put("success", true);
            result.put("schedule", schedule);
            result.put("activityName", activityInfo.get("activityName"));
            result.put("days", days);
            result.put("volunteerCount", volunteers.size());
            
            log.debug("多日排班方案生成完成，活动: {}, 天数: {}", activityInfo.get("activityName"), days);
            
        } catch (Exception e) {
            log.error("多日排班方案生成失败", e);
            result.put("success", false);
            result.put("schedule", "很抱歉，暂时无法生成多日排班方案，请稍后再试。");
            result.put("error", e.getMessage());
        }
        
        return result;
    }
    
    public Map<String, Object> analyzeScheduleConflicts(List<Map<String, Object>> volunteers,
                                                       Map<String, Object> activityInfo) {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> conflicts = new ArrayList<>();
        
        for (Map<String, Object> volunteer : volunteers) {
            String availability = (String) volunteer.get("availability");
            String activityTime = (String) activityInfo.get("activityTime");
            
            if (availability != null && activityTime != null && 
                !isTimeAvailable(availability, activityTime)) {
                Map<String, Object> conflict = new HashMap<>();
                conflict.put("volunteerId", volunteer.get("id"));
                conflict.put("volunteerName", volunteer.get("name"));
                conflict.put("conflictType", "时间冲突");
                conflict.put("volunteerAvailability", availability);
                conflict.put("activityTime", activityTime);
                conflicts.add(conflict);
            }
        }
        
        result.put("conflicts", conflicts);
        result.put("hasConflicts", !conflicts.isEmpty());
        result.put("conflictCount", conflicts.size());
        
        return result;
    }
    
    private int calculateMatchScore(Map<String, Object> volunteer, Map<String, Object> activity) {
        int score = 0;
        
        String volunteerSkills = (String) volunteer.get("skills");
        String requiredSkills = (String) activity.get("requiredSkills");
        
        if (volunteerSkills != null && requiredSkills != null) {
            String[] skillList = requiredSkills.split(",");
            for (String skill : skillList) {
                if (volunteerSkills.contains(skill.trim())) {
                    score += 20;
                }
            }
        }
        
        Object experience = volunteer.get("experience");
        if (experience != null) {
            try {
                double exp = Double.parseDouble(experience.toString());
                score += Math.min((int) (exp * 10), 30);
            } catch (Exception e) {
                // ignore
            }
        }
        
        String availability = (String) volunteer.get("availability");
        String activityTime = (String) activity.get("activityTime");
        if (availability != null && activityTime != null && isTimeAvailable(availability, activityTime)) {
            score += 30;
        }
        
        return Math.min(score, 100);
    }
    
    private List<String> getMatchReasons(Map<String, Object> volunteer, Map<String, Object> activity) {
        List<String> reasons = new ArrayList<>();
        
        String volunteerSkills = (String) volunteer.get("skills");
        String requiredSkills = (String) activity.get("requiredSkills");
        
        if (volunteerSkills != null && requiredSkills != null) {
            String[] skillList = requiredSkills.split(",");
            for (String skill : skillList) {
                if (volunteerSkills.contains(skill.trim())) {
                    reasons.add("具备技能：" + skill.trim());
                }
            }
        }
        
        Object experience = volunteer.get("experience");
        if (experience != null) {
            reasons.add("志愿服务经验：" + experience + "年");
        }
        
        String availability = (String) volunteer.get("availability");
        String activityTime = (String) activity.get("activityTime");
        if (availability != null && activityTime != null && isTimeAvailable(availability, activityTime)) {
            reasons.add("时间可用");
        }
        
        return reasons;
    }
    
    private boolean isTimeAvailable(String availability, String activityTime) {
        return availability != null && (availability.contains("全天") || 
                availability.contains(activityTime.substring(11, 16)));
    }
}