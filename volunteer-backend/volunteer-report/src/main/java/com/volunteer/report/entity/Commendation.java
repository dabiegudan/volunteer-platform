package com.volunteer.report.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.volunteer.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * #5 表彰奖励管理 - 表彰记录实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("commendation")
public class Commendation extends BaseEntity {

    private Long userId;

    private String commendationNo;

    private String title;

    private String level;

    private String issuingUnit;

    private LocalDate issueDate;

    private String description;

    private String certificateUrl;

    private String status;

    private Long approvedBy;

    private LocalDateTime approvedAt;

    private String remark;
}