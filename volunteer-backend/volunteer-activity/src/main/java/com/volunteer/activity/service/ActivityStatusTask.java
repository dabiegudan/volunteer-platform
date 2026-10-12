package com.volunteer.activity.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.volunteer.activity.entity.Activity;
import com.volunteer.activity.mapper.ActivityMapper;
import com.volunteer.common.enums.ActivityStatusEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 活动状态自动管理定时任务
 * 每日凌晨自动校验并更新活动状态
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActivityStatusTask {

    private final ActivityMapper activityMapper;
    private final ActivityCacheService activityCacheService;

    /**
     * 每日凌晨 0 点执行活动状态自动更新。
     *
     * <p>ShedLock：多副本部署时仅一个实例执行，避免重复归档。
     * 该方法带 {@code @Transactional}，最坏耗时预估 &lt; 20 分钟，
     * 故 {@code lockAtMostFor} 设为 30 分钟。
     *
     * <p>注意：锁在事务<b>外层</b>生效（ShedLock 的切面优先级高于事务切面），
     * 因此锁覆盖完整的「加锁 → 事务提交 → 释放锁」过程。
     */
    @Scheduled(cron = "0 0 0 * * ?")
    @SchedulerLock(name = "ActivityStatusTask_updateActivityStatusDaily",
            lockAtMostFor = "PT30M", lockAtLeastFor = "PT5M")
    @Transactional
    public void updateActivityStatusDaily() {
        log.info("开始执行活动状态自动更新任务");
        
        LocalDateTime now = LocalDateTime.now();
        
        // Registration deadline is enforced in registerActivity; no extra state is written.
        updateStatusForRegistrationDeadline(now);
        
        // 更新已发布且到开始时间的活动状态为ONGOING
        updateStatusForStart(now);
        
        // 更新进行中且已到结束时间的活动状态为COMPLETED
        updateStatusForEnd(now);
        
        // 更新已完成超过30天的活动状态为ARCHIVED
        updateStatusForArchive(now);
        
        activityCacheService.clearActivityCache();
        log.info("活动状态自动更新任务执行完成");
    }

    /**
     * 更新报名截止的活动状态
     */
    private void updateStatusForRegistrationDeadline(LocalDateTime now) {
        long count = activityMapper.selectCount(new LambdaQueryWrapper<Activity>()
                .in(Activity::getStatus, ActivityStatusEnum.RECRUITING.name(), ActivityStatusEnum.PUBLISHED.name())
                .isNotNull(Activity::getRegistrationDeadline)
                .le(Activity::getRegistrationDeadline, now));
        if (count > 0) {
            log.info("Activity registration deadline passed, waiting for start. count={}", count);
        }
    }

    /**
     * 更新到开始时间的活动状态
     */
    private void updateStatusForStart(LocalDateTime now) {
        List<Activity> activities = activityMapper.selectList(new LambdaQueryWrapper<Activity>()
                .in(Activity::getStatus, ActivityStatusEnum.RECRUITING.name(), ActivityStatusEnum.PUBLISHED.name())
                .le(Activity::getStartTime, now)
                .gt(Activity::getEndTime, now));
        
        for (Activity activity : activities) {
            activity.setStatus(ActivityStatusEnum.ONGOING.name());
            activityMapper.updateById(activity);
            log.info("活动开始，状态更新为ONGOING: activityId={}, title={}", 
                    activity.getId(), activity.getTitle());
        }
    }

    /**
     * 更新已结束的活动状态
     */
    private void updateStatusForEnd(LocalDateTime now) {
        List<Activity> activities = activityMapper.selectList(new LambdaQueryWrapper<Activity>()
                .eq(Activity::getStatus, ActivityStatusEnum.ONGOING.name())
                .le(Activity::getEndTime, now));
        
        for (Activity activity : activities) {
            activity.setStatus(ActivityStatusEnum.COMPLETED.name());
            activityMapper.updateById(activity);
            log.info("活动结束，状态更新为COMPLETED: activityId={}, title={}", 
                    activity.getId(), activity.getTitle());
        }
    }

    /**
     * 更新已完成超过30天的活动状态为归档
     */
    private void updateStatusForArchive(LocalDateTime now) {
        LocalDateTime thirtyDaysAgo = now.minusDays(30);
        List<Activity> activities = activityMapper.selectList(new LambdaQueryWrapper<Activity>()
                .eq(Activity::getStatus, ActivityStatusEnum.COMPLETED.name())
                .le(Activity::getEndTime, thirtyDaysAgo));
        
        for (Activity activity : activities) {
            activity.setStatus(ActivityStatusEnum.ARCHIVED.name());
            activityMapper.updateById(activity);
            log.info("活动归档，状态更新为ARCHIVED: activityId={}, title={}", 
                    activity.getId(), activity.getTitle());
        }
    }

    /**
     * 手动触发状态更新（用于测试或特殊情况）
     */
    @Transactional
    public void triggerStatusUpdate() {
        updateActivityStatusDaily();
    }
}
