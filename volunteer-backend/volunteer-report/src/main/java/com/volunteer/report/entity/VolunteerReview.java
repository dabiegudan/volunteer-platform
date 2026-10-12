package com.volunteer.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 志愿者评价实体
 */
@Data
@TableName("volunteer_review")
public class VolunteerReview {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long reviewedUserId;
    
    private Long reviewerUserId;
    
    private Long activityId;
    
    private Integer rating;
    
    private String content;
    
    private String tags;
    
    private String status;
    
    private LocalDateTime createTime;
}