package com.volunteer.activity.event;

import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class ActivityRegistrationEvent {

    public enum EventType {
        PENDING,
        APPROVED,
        REJECTED,
        CANCELLED
    }

    private final Long registrationId;
    private final Long activityId;
    private final Long userId;
    private final String activityTitle;
    private final LocalDateTime activityStartTime;
    private final EventType eventType;
    private final boolean applicationSubmitted;

    public ActivityRegistrationEvent(Long registrationId, Long activityId, Long userId,
                                     String activityTitle, LocalDateTime activityStartTime,
                                     EventType eventType) {
        this(registrationId, activityId, userId, activityTitle, activityStartTime, eventType, false);
    }

    public ActivityRegistrationEvent(Long registrationId, Long activityId, Long userId,
                                     String activityTitle, LocalDateTime activityStartTime,
                                     EventType eventType, boolean applicationSubmitted) {
        this.registrationId = registrationId;
        this.activityId = activityId;
        this.userId = userId;
        this.activityTitle = activityTitle;
        this.activityStartTime = activityStartTime;
        this.eventType = eventType;
        this.applicationSubmitted = applicationSubmitted;
    }
}
