package com.volunteer.activity.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 活动模板实体
 * 用于快速创建相似活动
 */
@Data
@TableName("activity_template")
public class ActivityTemplate {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    @TableField("template_name")
    private String name;
    
    @TableField("default_description")
    private String description;
    
    private String category;
    
    private String tags;
    
    @TableField("default_location")
    private String location;
    
    private BigDecimal locationGpsLat;
    
    private BigDecimal locationGpsLng;
    
    private String province;
    
    private String city;
    
    private String district;
    
    private String street;
    
    @TableField("default_max_participants")
    private Integer maxParticipants;
    
    private Integer minParticipants;
    
    @TableField("default_credit_reward")
    private Integer creditReward;
    
    @TableField("default_duration_hours")
    private BigDecimal durationHours;
    
    @TableField("default_check_in_radius")
    private Integer checkInRadius;
    
    @TableField("default_requires_approval")
    private Boolean requiresApproval;
    
    private Long organizationId;
    
    @TableField("owner_id")
    private Long createdBy;
    
    private String status;
    
    private LocalDateTime createTime;
    
    private LocalDateTime updateTime;
}
