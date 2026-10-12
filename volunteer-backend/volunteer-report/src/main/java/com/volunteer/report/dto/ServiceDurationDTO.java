package com.volunteer.report.dto;

import java.io.Serializable;

/**
 * 服务时长统计DTO
 */
public class ServiceDurationDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String organizationName;
    private Double totalDuration;
    private Long volunteerCount;

    public ServiceDurationDTO() {
    }

    public ServiceDurationDTO(String organizationName, Double totalDuration, Long volunteerCount) {
        this.organizationName = organizationName;
        this.totalDuration = totalDuration;
        this.volunteerCount = volunteerCount;
    }

    public String getOrganizationName() {
        return organizationName;
    }

    public void setOrganizationName(String organizationName) {
        this.organizationName = organizationName;
    }

    public Double getTotalDuration() {
        return totalDuration;
    }

    public void setTotalDuration(Double totalDuration) {
        this.totalDuration = totalDuration;
    }

    public Long getVolunteerCount() {
        return volunteerCount;
    }

    public void setVolunteerCount(Long volunteerCount) {
        this.volunteerCount = volunteerCount;
    }
}
