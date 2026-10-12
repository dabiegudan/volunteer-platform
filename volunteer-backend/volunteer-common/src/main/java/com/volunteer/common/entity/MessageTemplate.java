package com.volunteer.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息模板实体
 */
@Data
@TableName("message_template")
public class MessageTemplate {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String templateCode;
    
    private String templateName;
    
    private String title;
    
    private String content;
    
    private String channel;
    
    private String status;
    
    private String description;
    
    private LocalDateTime createTime;
    
    private LocalDateTime updateTime;
}