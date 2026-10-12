package com.volunteer.notification.event;

import com.volunteer.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ActivityEventListener {
    
    private final NotificationService notificationService;
    
    @EventListener
    public void handleActivityEvent(ActivityEvent event) {
        log.info("Received activity event: {} for user: {}", event.getEventType(), event.getUserId());
        
        String title = switch (event.getEventType()) {
            case REGISTRATION_REJECTED -> "Registration rejected";
            case REGISTRATION_SUCCESS -> "报名成功";
            case APPROVAL_SUCCESS -> "审核通过";
            case ATTENDANCE_REMINDER -> "签到提醒";
            case ACTIVITY_CANCELLED -> "活动取消";
        };
        
        notificationService.sendMessage(
                event.getUserId(),
                event.getEventType().name(),
                title,
                event.getMessage(),
                event.getActivityId(),
                "ACTIVITY"
        );
    }
}
