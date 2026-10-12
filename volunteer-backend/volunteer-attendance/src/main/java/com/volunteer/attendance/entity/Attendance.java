package com.volunteer.attendance.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.volunteer.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("attendance")
public class Attendance extends BaseEntity {
    
    private Long activityId;
    
    private Long userId;

    private Long registrationId;
    
    private LocalDateTime checkInTime;
    
    private LocalDateTime checkOutTime;
    
    private String checkInLocation;
    
    private String checkOutLocation;
    
    @TableField("check_in_gps_lat")
    private BigDecimal checkInGpsLat;
    
    @TableField("check_in_gps_lng")
    private BigDecimal checkInGpsLng;
    
    @TableField("check_out_gps_lat")
    private BigDecimal checkOutGpsLat;
    
    @TableField("check_out_gps_lng")
    private BigDecimal checkOutGpsLng;
    
    private String checkInMode;
    
    private String checkOutMode;
    
    private String status;
    
    private BigDecimal durationHours;
    
    private Integer isLate;
    
    private Integer isEarly;
    
    private Integer isSupplementary;

    private String supplementaryStatus;
    
    private String supplementaryReason;
    
    private String supplementaryProof;
    
    private LocalDateTime approvalTime;
    
    private String rejectReason;
}
