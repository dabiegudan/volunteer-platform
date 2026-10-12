package com.volunteer.activity.dto;

import com.volunteer.activity.entity.Activity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 活动列表DTO - 包含倒计时信息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActivityListDTO {
    
    private Long id;
    private String title;
    private String description;
    private String category;
    private String tags;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime registrationDeadline;
    private String location;
    private Integer maxParticipants;
    private Integer currentParticipants;
    private String status;
    private Integer creditReward;
    private Boolean isUrgent;
    private String urgentLevel;
    
    private String countdownType;
    private Long countdownSeconds;
    private String countdownDisplay;
    private Boolean isUrgentDeadline;
    private Boolean isLowQuota;
    private Integer quotaPercentage;
    
    public static ActivityListDTO fromEntity(Activity activity) {
        ActivityListDTO dto = new ActivityListDTO();
        dto.setId(activity.getId());
        dto.setTitle(activity.getTitle());
        dto.setDescription(activity.getDescription());
        dto.setCategory(activity.getCategory());
        dto.setTags(activity.getTags());
        dto.setStartTime(activity.getStartTime());
        dto.setEndTime(activity.getEndTime());
        dto.setRegistrationDeadline(activity.getRegistrationDeadline());
        dto.setLocation(activity.getLocation());
        dto.setMaxParticipants(activity.getMaxParticipants());
        dto.setCurrentParticipants(activity.getCurrentParticipants());
        dto.setStatus(activity.getStatus());
        dto.setCreditReward(activity.getCreditReward());
        dto.setIsUrgent(activity.getIsUrgent());
        dto.setUrgentLevel(activity.getUrgentLevel());
        
        calculateCountdown(dto);
        calculateQuotaStatus(dto);
        
        return dto;
    }
    
    private static void calculateCountdown(ActivityListDTO dto) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime deadline = dto.getRegistrationDeadline();
        
        if (deadline == null) {
            dto.setCountdownType("NONE");
            dto.setCountdownSeconds(null);
            dto.setCountdownDisplay("");
            dto.setIsUrgentDeadline(false);
            return;
        }
        
        Duration duration = Duration.between(now, deadline);
        long seconds = duration.getSeconds();
        
        if (seconds <= 0) {
            dto.setCountdownType("EXPIRED");
            dto.setCountdownSeconds(0L);
            dto.setCountdownDisplay("报名已截止");
            dto.setIsUrgentDeadline(false);
        } else if (seconds <= 86400) {
            dto.setCountdownType("URGENT");
            dto.setCountdownSeconds(seconds);
            dto.setCountdownDisplay(formatCountdown(seconds));
            dto.setIsUrgentDeadline(true);
        } else {
            dto.setCountdownType("NORMAL");
            dto.setCountdownSeconds(seconds);
            dto.setCountdownDisplay(formatCountdown(seconds));
            dto.setIsUrgentDeadline(false);
        }
    }
    
    private static void calculateQuotaStatus(ActivityListDTO dto) {
        if (dto.getMaxParticipants() == null || dto.getMaxParticipants() == 0) {
            dto.setQuotaPercentage(0);
            dto.setIsLowQuota(false);
            return;
        }
        
        int percentage = (dto.getCurrentParticipants() * 100) / dto.getMaxParticipants();
        dto.setQuotaPercentage(percentage);
        dto.setIsLowQuota(percentage >= 90);
    }
    
    private static String formatCountdown(long seconds) {
        if (seconds <= 0) {
            return "已截止";
        }
        
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        
        if (days > 0) {
            return String.format("%d天%d小时", days, hours);
        } else if (hours > 0) {
            return String.format("%d小时%d分钟", hours, minutes);
        } else if (minutes > 0) {
            return String.format("%d分钟%d秒", minutes, secs);
        } else {
            return String.format("%d秒", secs);
        }
    }
}