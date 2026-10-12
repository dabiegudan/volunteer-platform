package com.volunteer.attendance.service;

import com.volunteer.activity.entity.Activity;
import com.volunteer.activity.mapper.ActivityMapper;
import com.volunteer.attendance.entity.Attendance;
import com.volunteer.attendance.mapper.AttendanceMapper;
import com.volunteer.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import dev.langchain4j.model.chat.ChatModel;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class SupplementaryAnalysisService {
    
    private final ChatModel chatLanguageModel;
    private final ActivityMapper activityMapper;
    private final AttendanceMapper attendanceMapper;
    
    public Map<String, Object> analyzeSupplementaryReason(Long activityId, Long userId, String reason) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            Activity activity = activityMapper.selectById(activityId);
            if (activity == null) {
                throw new BusinessException("活动不存在");
            }
            
            double reasonScore = analyzeReasonQuality(reason);
            double timeFactor = calculateTimeFactor(activity);
            double historyFactor = calculateHistoryFactor(userId);
            
            double totalScore = (reasonScore * 0.5) + (timeFactor * 0.3) + (historyFactor * 0.2);
            String recommendation = totalScore >= 70 ? "APPROVE" : (totalScore >= 40 ? "REVIEW" : "REJECT");
            
            String aiAnalysis = generateAiAnalysis(activity, reason, totalScore, recommendation);
            
            result.put("reasonScore", reasonScore);
            result.put("timeFactor", timeFactor);
            result.put("historyFactor", historyFactor);
            result.put("totalScore", totalScore);
            result.put("recommendation", recommendation);
            result.put("analysis", aiAnalysis);
            result.put("isValid", true);
            
        } catch (Exception e) {
            log.warn("AI补签分析失败，使用简单规则", e);
            result.put("isValid", false);
            result.put("error", e.getMessage());
            result.put("recommendation", "REVIEW");
        }
        
        return result;
    }
    
    private double analyzeReasonQuality(String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            return 0;
        }
        
        try {
            String prompt = String.format(
                    "请分析以下志愿者补签理由的合理性，给出0-100的分数。\n" +
                    "补签理由：%s\n\n" +
                    "评分标准：\n" +
                    "1. 理由是否具体详细（30分）\n" +
                    "2. 理由是否合理可信（40分）\n" +
                    "3. 理由是否充分（30分）\n\n" +
                    "请只返回一个数字分数，不要其他文字。",
                    reason
            );
            
            String response = chatLanguageModel.chat(prompt);
            return parseScore(response);
            
        } catch (Exception e) {
            log.warn("AI分析失败，使用简单规则", e);
            int length = reason.trim().length();
            if (length < 10) return 20;
            if (length < 30) return 40;
            if (length < 60) return 60;
            return 70;
        }
    }
    
    private double calculateTimeFactor(Activity activity) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime activityEnd = activity.getEndTime();
        
        if (activityEnd == null) {
            return 50;
        }
        
        Duration duration = Duration.between(activityEnd, now);
        long days = duration.toDays();
        
        if (days <= 1) return 100;
        if (days <= 3) return 80;
        if (days <= 7) return 60;
        if (days <= 14) return 40;
        if (days <= 30) return 20;
        return 0;
    }
    
    private double calculateHistoryFactor(Long userId) {
        int totalAttendances = attendanceMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Attendance>()
                        .eq(Attendance::getUserId, userId)
        ).intValue();
        
        int supplementaryCount = attendanceMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Attendance>()
                        .eq(Attendance::getUserId, userId)
                        .eq(Attendance::getIsSupplementary, 1)
        ).intValue();
        
        if (totalAttendances == 0) {
            return 80;
        }
        
        double supplementaryRate = (double) supplementaryCount / totalAttendances;
        
        if (supplementaryRate <= 0.1) return 100;
        if (supplementaryRate <= 0.2) return 80;
        if (supplementaryRate <= 0.3) return 60;
        if (supplementaryRate <= 0.5) return 40;
        return 20;
    }
    
    private String generateAiAnalysis(Activity activity, String reason, double score, String recommendation) {
        try {
            String prompt = String.format(
                    "请生成一段简短的补签申请分析（100字以内）。\n" +
                    "活动：%s\n" +
                    "补签理由：%s\n" +
                    "综合评分：%.1f\n" +
                    "建议：%s",
                    activity.getTitle(), reason, score, 
                    "APPROVE".equals(recommendation) ? "通过" : ("REJECT".equals(recommendation) ? "拒绝" : "需人工审核")
            );
            
            return chatLanguageModel.chat(prompt);
            
        } catch (Exception e) {
            log.warn("AI生成分析失败", e);
            return String.format("综合评分：%.1f，建议：%s", score, 
                    "APPROVE".equals(recommendation) ? "通过" : ("REJECT".equals(recommendation) ? "拒绝" : "需人工审核"));
        }
    }
    
    private double parseScore(String response) {
        try {
            String cleaned = response.trim().replaceAll("[^0-9.]", "");
            double score = Double.parseDouble(cleaned);
            return Math.max(0, Math.min(100, score));
        } catch (Exception e) {
            return 50;
        }
    }
}
