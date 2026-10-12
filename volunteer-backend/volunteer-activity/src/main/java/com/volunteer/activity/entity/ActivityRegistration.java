package com.volunteer.activity.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.volunteer.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("activity_registration")
public class ActivityRegistration extends BaseEntity {
    
    private Long activityId;
    
    private Long userId;
    
    private Long teamId;
    
    private String status;
    
    private LocalDateTime registrationTime;
    
    private LocalDateTime approvalTime;
    
    private Long approvalUserId;
    
    private LocalDateTime cancelTime;
    
    private String cancelReason;
    
    private BigDecimal creditEarned;
    
    private String remark;
    
    private String rejectReason;
    
    private String joinMethod;
    
    private Long operatorId;
}
