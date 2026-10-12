package com.volunteer.activity.service;

import com.volunteer.activity.entity.Activity;
import com.volunteer.activity.entity.ActivityChangeLog;
import com.volunteer.activity.mapper.ActivityChangeLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityChangeLogService {
    
    private final ActivityChangeLogMapper activityChangeLogMapper;
    
    @Transactional
    public void logActivityChange(Long activityId, Activity oldActivity, Activity newActivity, Long operatorId, String operatorType) {
        if (oldActivity == null) {
            ActivityChangeLog logEntry = new ActivityChangeLog();
            logEntry.setActivityId(activityId);
            logEntry.setChangeType("CREATE");
            logEntry.setOperatorId(operatorId);
            logEntry.setOperatorType(operatorType);
            logEntry.setChangeContent("活动创建");
            activityChangeLogMapper.insert(logEntry);
            return;
        }
        
        Class<?> clazz = Activity.class;
        Field[] fields = clazz.getDeclaredFields();
        
        for (Field field : fields) {
            field.setAccessible(true);
            try {
                Object oldValue = field.get(oldActivity);
                Object newValue = field.get(newActivity);
                
                if (oldValue == null && newValue == null) {
                    continue;
                }
                
                if ((oldValue == null || newValue == null) && oldValue != newValue) {
                    createChangeLog(activityId, field.getName(),
                            oldValue != null ? oldValue.toString() : null,
                            newValue != null ? newValue.toString() : null,
                            operatorId, operatorType);
                } else if (oldValue != null && newValue != null && !oldValue.equals(newValue)) {
                    createChangeLog(activityId, field.getName(),
                            oldValue.toString(),
                            newValue.toString(),
                            operatorId, operatorType);
                }
            } catch (Exception e) {
                log.warn("Failed to log field change for field: {}", field.getName(), e);
            }
        }
    }
    
    private void createChangeLog(Long activityId, String changeField, String oldValue, String newValue,
                                 Long operatorId, String operatorType) {
        ActivityChangeLog logEntry = new ActivityChangeLog();
        logEntry.setActivityId(activityId);
        logEntry.setChangeType("UPDATE");
        logEntry.setChangeField(changeField);
        logEntry.setOldValue(oldValue);
        logEntry.setNewValue(newValue);
        logEntry.setOperatorId(operatorId);
        logEntry.setOperatorType(operatorType);
        activityChangeLogMapper.insert(logEntry);
    }
    
    public void logStatusChange(Long activityId, String oldStatus, String newStatus, 
                                Long operatorId, String operatorType) {
        ActivityChangeLog logEntry = new ActivityChangeLog();
        logEntry.setActivityId(activityId);
        logEntry.setChangeType("STATUS_CHANGE");
        logEntry.setChangeField("status");
        logEntry.setOldValue(oldStatus);
        logEntry.setNewValue(newStatus);
        logEntry.setOperatorId(operatorId);
        logEntry.setOperatorType(operatorType);
        activityChangeLogMapper.insert(logEntry);
    }
    
    public List<ActivityChangeLog> getActivityChangeLogs(Long activityId) {
        return activityChangeLogMapper.selectByActivityId(activityId);
    }
}
