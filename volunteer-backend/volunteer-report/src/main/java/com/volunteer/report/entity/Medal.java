package com.volunteer.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("medal")
public class Medal {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String name;
    
    private String icon;
    
    private String description;
    
    private String category;
    
    private String rarity;
    
    private String requiredCondition;
    
    private Integer requiredValue;
    
    private String conditionType;
    
    private Integer rewardCredit;
    
    private Integer sortOrder;
    
    private Integer status;
    
    private LocalDateTime createTime;
}