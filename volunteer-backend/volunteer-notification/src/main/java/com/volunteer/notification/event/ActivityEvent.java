package com.volunteer.notification.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class ActivityEvent extends ApplicationEvent {
    
    private final Long userId;
    private final Long activityId;
    private final EventType eventType;
    private final String message;
    
    public enum EventType {
        REGISTRATION_SUCCESS,
        APPROVAL_SUCCESS,
        REGISTRATION_REJECTED,
        ATTENDANCE_REMINDER,
        ACTIVITY_CANCELLED
    }
    
    public ActivityEvent(Object source, Long userId, Long activityId, EventType eventType, String message) {
        super(source);
        this.userId = userId;
        this.activityId = activityId;
        this.eventType = eventType;
        this.message = message;
    }
}
