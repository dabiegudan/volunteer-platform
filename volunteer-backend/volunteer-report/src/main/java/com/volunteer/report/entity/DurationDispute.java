package com.volunteer.report.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.volunteer.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * #4 记录异议/修正工作流 - 时长异议实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("duration_dispute")
public class DurationDispute extends BaseEntity {

    private Long userId;

    private Long attendanceId;

    private Long activityId;

    private String disputeNo;

    private BigDecimal originalDuration;

    private BigDecimal requestedDuration;

    private String reason;

    private String evidence;

    private String status;

    private Long reviewerId;

    private String reviewComment;

    private LocalDateTime reviewTime;

    private BigDecimal approvedDuration;

    private String auditTrail;
}