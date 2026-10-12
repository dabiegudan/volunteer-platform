package com.volunteer.report.event;

import com.volunteer.activity.entity.Activity;
import com.volunteer.activity.mapper.ActivityMapper;
import com.volunteer.attendance.event.AttendanceEvent;
import com.volunteer.report.service.CreditService;
import com.volunteer.report.service.UserPreferenceService;
import com.volunteer.report.service.UserTodoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class AttendanceBusinessEventListener {

    private final ActivityMapper activityMapper;
    private final UserTodoService userTodoService;
    private final CreditService creditService;
    private final UserPreferenceService userPreferenceService;

    @EventListener
    public void handleAttendanceEvent(AttendanceEvent event) {
        try {
            switch (event.getEventType()) {
                case CHECKED_IN -> handleCheckedIn(event);
                case DURATION_APPROVED -> handleDurationApproved(event);
            }
        } catch (Exception ex) {
            log.warn("Failed to handle attendance event. type: {}, attendanceId: {}, error: {}",
                    event.getEventType(), event.getAttendanceId(), ex.getMessage());
        }
    }

    private void handleCheckedIn(AttendanceEvent event) {
        userTodoService.markAsCompleted(
                event.getUserId(),
                event.getActivityId(),
                "ACTIVITY_ATTENDANCE"
        );
        creditService.updateTaskProgressByType(event.getUserId(), "CHECKIN", 1);
    }

    private void handleDurationApproved(AttendanceEvent event) {
        Activity activity = activityMapper.selectById(event.getActivityId());
        String activityTitle = activity != null && activity.getTitle() != null ? activity.getTitle() : "activity";

        if (activity != null && activity.getCreditReward() != null && activity.getCreditReward() > 0) {
            creditService.addCredit(
                    event.getUserId(),
                    activity.getCreditReward(),
                    "ACTIVITY",
                    event.getActivityId(),
                    "Activity reward: " + activityTitle
            );
        } else {
            creditService.checkMedalUnlock(event.getUserId());
        }

        userTodoService.createActivityEvaluationTodo(
                event.getUserId(),
                activityTitle,
                LocalDateTime.now().plusDays(7),
                event.getActivityId()
        );
        userPreferenceService.updateReliabilityScore(event.getUserId(), 2);
    }
}
