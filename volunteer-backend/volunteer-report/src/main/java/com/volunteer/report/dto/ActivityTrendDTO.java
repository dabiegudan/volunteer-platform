package com.volunteer.report.dto;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * 活动趋势数据DTO
 */
public class ActivityTrendDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private LocalDate date;
    private Integer count;
    private Integer participantCount;

    public ActivityTrendDTO() {
    }

    public ActivityTrendDTO(LocalDate date, Integer count, Integer participantCount) {
        this.date = date;
        this.count = count;
        this.participantCount = participantCount;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public Integer getCount() {
        return count;
    }

    public void setCount(Integer count) {
        this.count = count;
    }

    public Integer getParticipantCount() {
        return participantCount;
    }

    public void setParticipantCount(Integer participantCount) {
        this.participantCount = participantCount;
    }
}
