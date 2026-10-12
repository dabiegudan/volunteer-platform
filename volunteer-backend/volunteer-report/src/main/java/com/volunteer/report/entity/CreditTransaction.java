package com.volunteer.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("credit_transaction")
public class CreditTransaction {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long userId;
    
    private Integer amount;
    
    private String type;
    
    private String source;
    
    private Long sourceId;
    
    private String description;
    
    private Integer balanceBefore;
    
    private Integer balanceAfter;
    
    private LocalDateTime createTime;
}