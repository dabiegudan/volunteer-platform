package com.volunteer.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ActivityStatusEnum {
    
    DRAFT("草稿"),
    AUDITING("审核中"),
    APPROVED("已通过"),
    REJECTED("已拒绝"),
    PUBLISHED("已发布"),
    RECRUITING("招募中"),
    ONGOING("进行中"),
    COMPLETED("已完成"),
    ARCHIVED("已归档"),
    CANCELLED("已取消");
    
    private final String desc;
}