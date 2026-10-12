package com.volunteer.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 认证证书实体
 */
@Data
@TableName("certification")
public class Certification {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long userId;
    
    private String name;
    
    private String code;
    
    private String type;
    
    private String issuer;
    
    private String description;
    
    private LocalDateTime issueDate;
    
    private LocalDateTime expireDate;
    
    private String status;
    
    private LocalDateTime createTime;
}