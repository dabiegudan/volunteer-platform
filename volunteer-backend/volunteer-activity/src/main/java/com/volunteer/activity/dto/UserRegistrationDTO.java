package com.volunteer.activity.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 志愿者报名记录 DTO (包含活动及签到考勤状态)
 */
@Data
public class UserRegistrationDTO {
    private Long id;
    private Long activityId;
    private String activityTitle;
    private String activityCover;
    private LocalDateTime activityStartTime;
    private String activityLocation;
    private String status;
    private LocalDateTime createdAt;
    private BigDecimal hours;
    private LocalDateTime checkedInTime;
    private LocalDateTime checkedOutTime;
}
