package com.volunteer.attendance.event;

import lombok.Getter;

import java.math.BigDecimal;

@Getter
public class AttendanceEvent {

    public enum EventType {
        CHECKED_IN,
        DURATION_APPROVED
    }

    private final Long attendanceId;
    private final Long activityId;
    private final Long userId;
    private final BigDecimal durationHours;
    private final EventType eventType;

    public AttendanceEvent(Long attendanceId, Long activityId, Long userId,
                           BigDecimal durationHours, EventType eventType) {
        this.attendanceId = attendanceId;
        this.activityId = activityId;
        this.userId = userId;
        this.durationHours = durationHours;
        this.eventType = eventType;
    }
}
