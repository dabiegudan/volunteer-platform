package com.volunteer.activity.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.volunteer.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("team")
public class Team extends BaseEntity {
    
    private String teamName;
    
    private Long activityId;
    
    private Long leaderId;
    
    private Integer maxMembers;
    
    private String status;
}
