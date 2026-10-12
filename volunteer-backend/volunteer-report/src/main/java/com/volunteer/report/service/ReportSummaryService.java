package com.volunteer.report.service;

import com.volunteer.attendance.entity.Attendance;
import com.volunteer.attendance.mapper.AttendanceMapper;
import com.volunteer.report.entity.UserDuration;
import com.volunteer.report.mapper.UserDurationMapper;
import dev.langchain4j.model.chat.ChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class ReportSummaryService {
    
    private final ChatModel chatLanguageModel;
    private final AttendanceMapper attendanceMapper;
    private final UserDurationMapper userDurationMapper;
    
    public Map<String, Object> generateUserReportSummary(Long userId) {
        Map<String, Object> reportData = collectReportData(userId);
        String aiSummary = generateAiSummary(reportData);
        
        Map<String, Object> result = new HashMap<>();
        result.put("reportData", reportData);
        result.put("aiSummary", aiSummary);
        
        return result;
    }
    
    private Map<String, Object> collectReportData(Long userId) {
        Map<String, Object> data = new HashMap<>();
        
        List<Attendance> attendances = attendanceMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Attendance>()
                        .eq(Attendance::getUserId, userId)
        );
        
        BigDecimal totalDuration = BigDecimal.ZERO;
        int completedActivities = 0;
        int totalActivities = attendances.size();
        
        for (Attendance attendance : attendances) {
            if (attendance.getDurationHours() != null) {
                totalDuration = totalDuration.add(attendance.getDurationHours());
                completedActivities++;
            }
        }
        
        List<UserDuration> userDurations = userDurationMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserDuration>()
                        .eq(UserDuration::getUserId, userId)
        );
        
        int approvedRecords = 0;
        for (UserDuration duration : userDurations) {
            if ("APPROVED".equals(duration.getStatus())) {
                approvedRecords++;
            }
        }
        
        data.put("userId", userId);
        data.put("totalDurationHours", totalDuration);
        data.put("totalActivities", totalActivities);
        data.put("completedActivities", completedActivities);
        data.put("approvedRecords", approvedRecords);
        data.put("generateTime", LocalDateTime.now());
        
        return data;
    }
    
    private String generateAiSummary(Map<String, Object> reportData) {
        try {
            String prompt = String.format(
                    "请根据以下志愿者数据生成一段200字以内的总结报告：\n\n" +
                    "用户ID：%s\n" +
                    "总服务时长：%.1f小时\n" +
                    "参与活动数：%d次\n" +
                    "完成活动数：%d次\n" +
                    "审核通过记录：%d条\n\n" +
                    "要求：\n" +
                    "1. 语言积极正面\n" +
                    "2. 突出志愿者的贡献\n" +
                    "3. 给出鼓励性评价",
                    reportData.get("userId"),
                    reportData.get("totalDurationHours"),
                    reportData.get("totalActivities"),
                    reportData.get("completedActivities"),
                    reportData.get("approvedRecords")
            );
            
            return chatLanguageModel.chat(prompt);
            
        } catch (Exception e) {
            log.warn("AI生成摘要失败，使用默认模板", e);
            return generateDefaultSummary(reportData);
        }
    }
    
    private String generateDefaultSummary(Map<String, Object> reportData) {
        return String.format(
                "该志愿者积极参与志愿服务，累计服务时长%.1f小时，参与活动%d次。" +
                "表现优秀，展现了良好的志愿服务精神。" +
                "感谢您的无私奉献！",
                reportData.get("totalDurationHours"),
                reportData.get("totalActivities")
        );
    }
}
