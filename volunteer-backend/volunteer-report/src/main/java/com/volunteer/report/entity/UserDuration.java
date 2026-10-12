package com.volunteer.report.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.volunteer.common.entity.BaseEntity;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("user_duration")
public class UserDuration extends BaseEntity {
    
    private Long userId;
    
    private Long activityId;
    
    private Long attendanceId;
    
    private BigDecimal originalDuration;
    
    private BigDecimal adjustedDuration;
    
    private String adjustmentReason;
    
    private String status;
    
    private Long approverId;
    
    private LocalDateTime approvalTime;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getActivityId() {
        return activityId;
    }

    public void setActivityId(Long activityId) {
        this.activityId = activityId;
    }

    public Long getAttendanceId() {
        return attendanceId;
    }

    public void setAttendanceId(Long attendanceId) {
        this.attendanceId = attendanceId;
    }

    public BigDecimal getOriginalDuration() {
        return originalDuration;
    }

    public void setOriginalDuration(BigDecimal originalDuration) {
        this.originalDuration = originalDuration;
    }

    public BigDecimal getAdjustedDuration() {
        return adjustedDuration;
    }

    public void setAdjustedDuration(BigDecimal adjustedDuration) {
        this.adjustedDuration = adjustedDuration;
    }

    public String getAdjustmentReason() {
        return adjustmentReason;
    }

    public void setAdjustmentReason(String adjustmentReason) {
        this.adjustmentReason = adjustmentReason;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getApproverId() {
        return approverId;
    }

    public void setApproverId(Long approverId) {
        this.approverId = approverId;
    }

    public LocalDateTime getApprovalTime() {
        return approvalTime;
    }

    public void setApprovalTime(LocalDateTime approvalTime) {
        this.approvalTime = approvalTime;
    }
}
