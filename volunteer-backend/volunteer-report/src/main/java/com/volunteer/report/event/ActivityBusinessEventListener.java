package com.volunteer.report.event;

import com.volunteer.activity.event.ActivityRegistrationEvent;
import com.volunteer.report.service.CreditService;
import com.volunteer.report.service.UserPreferenceService;
import com.volunteer.report.service.UserTodoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ActivityBusinessEventListener {

    private final UserTodoService userTodoService;
    private final CreditService creditService;
    private final UserPreferenceService userPreferenceService;

    @EventListener
    public void handleActivityRegistrationEvent(ActivityRegistrationEvent event) {
        try {
            if (event.isApplicationSubmitted()) {
                handleApplied(event);
            }
            switch (event.getEventType()) {
                case PENDING -> {
                }
                case APPROVED -> handleApproved(event);
                case REJECTED, CANCELLED -> handleClosed(event);
            }
        } catch (Exception ex) {
            log.warn("Failed to handle activity registration event. type: {}, registrationId: {}, error: {}",
                    event.getEventType(), event.getRegistrationId(), ex.getMessage());
        }
    }

    private void handleApplied(ActivityRegistrationEvent event) {
        creditService.updateTaskProgressByType(event.getUserId(), "APPLY", 1);
        userPreferenceService.recordActivityApply(event.getActivityId());
    }

    private void handleApproved(ActivityRegistrationEvent event) {
        userTodoService.createActivityAttendanceTodo(
                event.getUserId(),
                event.getActivityTitle(),
                event.getActivityStartTime(),
                event.getActivityId()
        );
    }

    private void handleClosed(ActivityRegistrationEvent event) {
        userTodoService.markAsCompleted(
                event.getUserId(),
                event.getActivityId(),
                "ACTIVITY_ATTENDANCE"
        );
    }
}
