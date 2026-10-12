package com.volunteer.attendance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.volunteer.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("violation_record")
public class ViolationRecord extends BaseEntity {
    
    private Long userId;
    
    private Long activityId;
    
    private String violationType;
    
    private String violationReason;
    
    private String restrictionType;
    
    private LocalDateTime restrictionEndTime;
    
    private String status;
}
