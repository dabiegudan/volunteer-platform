package com.volunteer.activity.entity;

import com.volunteer.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.baomidou.mybatisplus.annotation.TableName;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("activity_change_log")
public class ActivityChangeLog extends BaseEntity {
    
    private Long activityId;
    private String changeType;
    private String changeField;
    private String oldValue;
    private String newValue;
    private String changeContent;
    private Long operatorId;
    private String operatorType;
}
