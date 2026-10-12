package com.volunteer.activity.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.volunteer.activity.entity.Activity;
import com.volunteer.activity.entity.ActivityRegistration;
import com.volunteer.activity.mapper.ActivityMapper;
import com.volunteer.activity.mapper.ActivityRegistrationMapper;
import com.volunteer.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 活动截止提醒定时任务
 * 定时扫描即将截止的活动并发送提醒通知
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ActivityDeadlineTask {

    private final ActivityMapper activityMapper;
    private final ActivityRegistrationMapper registrationMapper;
    private final NotificationService notificationService;

    /**
     * 每小时执行一次，检查即将截止的活动。
     *
     * <p>ShedLock：多副本部署时仅一个实例执行，避免重复推送。
     * 最坏耗时预估 &lt; 10 分钟，故 {@code lockAtMostFor} 设为 15 分钟；
     * {@code lockAtLeastFor} 设为 5 分钟以抑制时钟抖动导致的重复触发。
     */
    @Scheduled(cron = "0 0 * * * ?")
    @SchedulerLock(name = "ActivityDeadlineTask_checkDeadlineAndNotify",
            lockAtMostFor = "PT15M", lockAtLeastFor = "PT5M")
    public void checkDeadlineAndNotify() {
        log.info("开始执行活动截止提醒任务");
        
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime deadline24HoursLater = now.plus(24, ChronoUnit.HOURS);
        
        LambdaQueryWrapper<Activity> wrapper = new LambdaQueryWrapper<>();
        wrapper.between(Activity::getRegistrationDeadline, now, deadline24HoursLater)
               .in(Activity::getStatus, "RECRUITING", "PUBLISHED");
        
        List<Activity> activities = activityMapper.selectList(wrapper);
        
        for (Activity activity : activities) {
            notifyParticipants(activity);
            log.info("已发送活动截止提醒, activityId: {}, title: {}", activity.getId(), activity.getTitle());
        }
        
        log.info("活动截止提醒任务执行完成，共处理{}个活动", activities.size());
    }

    /**
     * 通知已报名的参与者活动即将截止
     */
    private void notifyParticipants(Activity activity) {
        LambdaQueryWrapper<ActivityRegistration> regWrapper = new LambdaQueryWrapper<>();
        regWrapper.eq(ActivityRegistration::getActivityId, activity.getId())
                  .eq(ActivityRegistration::getStatus, "APPROVED");
        
        List<ActivityRegistration> registrations = registrationMapper.selectList(regWrapper);
        
        for (ActivityRegistration registration : registrations) {
            String title = "活动报名即将截止提醒";
            String content = String.format("您报名的「%s」活动报名即将于%s截止，请尽快完成相关准备工作。", 
                    activity.getTitle(), activity.getRegistrationDeadline());
            
            notificationService.sendMessage(registration.getUserId(), "ACTIVITY_DEADLINE", 
                    title, content, activity.getId(), "ACTIVITY");
        }
    }

    /**
     * 每日凌晨 1 点检查名额紧张的活动并发送提醒。
     *
     * <p>ShedLock：多副本部署时仅一个实例执行，避免重复推送。
     */
    @Scheduled(cron = "0 0 1 * * ?")
    @SchedulerLock(name = "ActivityDeadlineTask_checkLowQuotaAndNotify",
            lockAtMostFor = "PT15M", lockAtLeastFor = "PT5M")
    public void checkLowQuotaAndNotify() {
        log.info("开始执行活动名额紧张提醒任务");
        
        LambdaQueryWrapper<Activity> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Activity::getStatus, "RECRUITING", "PUBLISHED")
               .apply("current_participants >= max_participants * 0.9 AND max_participants > 0");
        
        List<Activity> activities = activityMapper.selectList(wrapper);
        
        for (Activity activity : activities) {
            notifyLowQuota(activity);
            log.info("已发送名额紧张提醒, activityId: {}, title: {}", activity.getId(), activity.getTitle());
        }
        
        log.info("名额紧张提醒任务执行完成，共处理{}个活动", activities.size());
    }

    /**
     * 通知参与者名额紧张
     */
    private void notifyLowQuota(Activity activity) {
        LambdaQueryWrapper<ActivityRegistration> regWrapper = new LambdaQueryWrapper<>();
        regWrapper.eq(ActivityRegistration::getActivityId, activity.getId())
                  .eq(ActivityRegistration::getStatus, "APPROVED");
        
        List<ActivityRegistration> registrations = registrationMapper.selectList(regWrapper);
        
        int remaining = activity.getMaxParticipants() - activity.getCurrentParticipants();
        
        for (ActivityRegistration registration : registrations) {
            String title = "活动名额紧张提醒";
            String content = String.format("您报名的「%s」活动仅剩%d个名额，建议尽快确认参与信息。", 
                    activity.getTitle(), remaining);
            
            notificationService.sendMessage(registration.getUserId(), "ACTIVITY_LOW_QUOTA", 
                    title, content, activity.getId(), "ACTIVITY");
        }
    }
}
