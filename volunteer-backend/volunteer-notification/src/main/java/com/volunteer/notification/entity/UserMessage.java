package com.volunteer.notification.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.volunteer.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user_message")
public class UserMessage extends BaseEntity {
    
    private Long userId;
    
    private String messageType;
    
    private String title;
    
    private String content;
    
    private Long relatedId;
    
    private String relatedType;
    
    private Integer isRead;
    
    private LocalDateTime readTime;
}
