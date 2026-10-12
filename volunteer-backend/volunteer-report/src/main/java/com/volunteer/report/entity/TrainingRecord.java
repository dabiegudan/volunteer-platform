package com.volunteer.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户培训记录实体
 */
@Data
@TableName("training_record")
public class TrainingRecord {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long userId;
    
    private Long courseId;
    
    private String status;
    
    private Integer progress;
    
    private LocalDateTime startTime;
    
    private LocalDateTime completionTime;
    
    private LocalDateTime createTime;
    
    private LocalDateTime updateTime;
}