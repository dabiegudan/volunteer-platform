package com.volunteer.activity.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.volunteer.common.entity.BaseEntity;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("activity")
public class Activity extends BaseEntity {
    
    @NotBlank(message = "活动标题不能为空")
    @Size(max = 255, message = "活动标题不能超过255字符")
    private String title;
    
    @Size(max = 5000, message = "活动描述不能超过5000字符")
    private String description;
    
    private String category;
    
    @Size(max = 255, message = "活动标签不能超过255字符")
    private String tags;
    
    @NotNull(message = "开始时间不能为空")
    private LocalDateTime startTime;
    
    @NotNull(message = "结束时间不能为空")
    private LocalDateTime endTime;
    
    private LocalDateTime registrationDeadline;
    
    @Size(max = 512, message = "活动地点不能超过512字符")
    private String location;
    
    private BigDecimal locationGpsLat;
    
    private BigDecimal locationGpsLng;
    
    @Size(max = 32, message = "省份不能超过32字符")
    private String province;
    
    @Size(max = 32, message = "城市不能超过32字符")
    private String city;
    
    @Size(max = 32, message = "区县不能超过32字符")
    private String district;
    
    @Size(max = 64, message = "街道不能超过64字符")
    private String street;
    
    private Boolean isUrgent;
    
    private String urgentLevel;
    
    @Min(value = 1, message = "最多参与人数不能小于1")
    @Max(value = 1000, message = "最多参与人数不能超过1000")
    private Integer maxParticipants;
    
    private Integer currentParticipants;
    
    @Min(value = 1, message = "最少参与人数不能小于1")
    private Integer minParticipants;
    
    private Long organizerId;
    
    private Long organizationId;
    
    private String activityScope;
    
    private Long collegeId;
    
    private String status;
    
    @Size(max = 512, message = "封面图片URL不能超过512字符")
    private String coverImage;
    
    private String attachments;
    
    @Min(value = 0, message = "学分奖励不能为负数")
    @Max(value = 100, message = "学分奖励不能超过100")
    private Integer creditReward;
    
    @DecimalMin(value = "0.5", message = "活动时长不能少于0.5小时")
    @DecimalMax(value = "24.0", message = "活动时长不能超过24小时")
    private BigDecimal durationHours;
    
    @Min(value = 10, message = "签到半径不能小于10米")
    @Max(value = 5000, message = "签到半径不能超过5000米")
    private Integer checkInRadius;
    
    private Boolean requiresApproval;
    
    private LocalDateTime publishedTime;
    
    private LocalDateTime submitTime;
    
    @Size(max = 500, message = "审核意见不能超过500字符")
    private String auditComment;
    
    private LocalDateTime auditTime;
    
    @Size(max = 500, message = "取消原因不能超过500字符")
    private String cancelReason;
    
    private LocalDateTime cancelTime;
    
    private Long createdBy;
    
    private Long updatedBy;

    // ===== #3 活动负责人字段规范化 - 新增字段 =====
    private String leaderName;

    private String leaderPhone;

    private String leaderOrgUnit;
}
