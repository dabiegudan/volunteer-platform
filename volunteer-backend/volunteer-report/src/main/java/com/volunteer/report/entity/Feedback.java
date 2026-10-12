package com.volunteer.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户反馈实体
 */
@Data
@TableName("feedback")
public class Feedback {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long userId;
    
    private String type;
    
    private String title;
    
    private String content;
    
    private String images;
    
    private String status;
    
    private String reply;
    
    private LocalDateTime replyTime;
    
    private Long replyBy;
    
    private LocalDateTime createTime;
    
    private LocalDateTime updateTime;
}