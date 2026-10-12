package com.volunteer.report.entity;

import lombok.Data;

@Data
public class OrganizationStatistic {
    
    private Long organizationId;
    private String organizationName;
    private Integer volunteerCount;
    private Integer activityCount;
    private java.math.BigDecimal totalDuration;
    private java.time.LocalDateTime updateTime;
}
