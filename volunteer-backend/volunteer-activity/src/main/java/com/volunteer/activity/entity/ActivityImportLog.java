package com.volunteer.activity.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.volunteer.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("activity_import_log")
public class ActivityImportLog extends BaseEntity {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long activityId;
    
    private Long operatorId;
    
    private String fileName;
    
    private Integer totalCount;
    
    private Integer successCount;
    
    private Integer skipCount;
    
    private Integer failCount;
    
    private String failDetails;
    
    private LocalDateTime importTime;
}