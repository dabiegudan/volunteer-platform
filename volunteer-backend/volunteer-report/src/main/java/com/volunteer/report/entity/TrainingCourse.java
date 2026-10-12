package com.volunteer.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 培训课程实体
 */
@Data
@TableName("training_course")
public class TrainingCourse {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String title;
    
    private String description;
    
    private String category;
    
    private String level;
    
    private Integer durationMinutes;
    
    private BigDecimal creditReward;
    
    private String coverImage;
    
    private String videoUrl;
    
    private String materials;
    
    private String status;
    
    private Integer viewCount;
    
    private Integer completionCount;
    
    private Long createdBy;
    
    private LocalDateTime createTime;
    
    private LocalDateTime updateTime;
}