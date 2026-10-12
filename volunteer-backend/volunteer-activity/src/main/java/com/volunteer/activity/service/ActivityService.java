package com.volunteer.activity.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.volunteer.activity.dto.ActivityListDTO;
import com.volunteer.activity.dto.UserRegistrationDTO;
import com.volunteer.activity.entity.Activity;
import com.volunteer.activity.entity.ActivityRegistration;
import com.volunteer.activity.entity.Team;
import com.volunteer.activity.event.ActivityRegistrationEvent;
import com.volunteer.activity.mapper.ActivityMapper;
import com.volunteer.activity.mapper.ActivityRegistrationMapper;
import com.volunteer.activity.mapper.TeamMapper;
import com.volunteer.auth.entity.SysUser;
import com.volunteer.auth.mapper.SysUserMapper;
import com.volunteer.common.enums.ActivityScopeEnum;
import com.volunteer.common.enums.ActivityStatusEnum;
import com.volunteer.common.exception.BusinessException;
import com.volunteer.common.result.PageResult;
import com.volunteer.notification.event.ActivityEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ActivityService {

    private final ActivityMapper activityMapper;
    private final ActivityRegistrationMapper registrationMapper;
    private final ActivityCacheService activityCacheService;
    private final TeamMapper teamMapper;
    private final JdbcTemplate jdbcTemplate;
    private final SysUserMapper userMapper;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 分页查询活动列表（增强版）- 根据用户角色过滤
     */
    public Page<Activity> getActivityPage(Integer pageNum, Integer pageSize,
                                           String category, String keyword,
                                           String status, LocalDateTime startTime,
                                           LocalDateTime endTime, String sortBy, String sortOrder,
                                           String province, String city, String district, String street,
                                           String timeSlot, SysUser currentUser) {
        Page<Activity> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Activity> wrapper = new LambdaQueryWrapper<>();

        // 根据用户角色过滤活动
        applyRoleFilter(wrapper, currentUser);

        // 添加其他筛选条件
        if (category != null && !category.isEmpty()) {
            wrapper.eq(Activity::getCategory, category);
        }

        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w ->
                w.like(Activity::getTitle, keyword)
                 .or()
                 .like(Activity::getDescription, keyword)
                 .or()
                 .like(Activity::getLocation, keyword)
                 .or()
                 .like(Activity::getLeaderName, keyword)
                 .or()
                 .like(Activity::getLeaderOrgUnit, keyword)
            );
        }

        boolean canViewNonPublic = canViewNonPublicActivity(currentUser);
        if (status != null && !status.isEmpty()) {
            wrapper.eq(Activity::getStatus, status);
            if (!canViewNonPublic) {
                wrapper.in(Activity::getStatus, publicActivityStatuses());
            }
        } else if (!canViewNonPublic) {
            wrapper.in(Activity::getStatus, publicActivityStatuses());
        }

        if (province != null && !province.isEmpty()) {
            wrapper.eq(Activity::getProvince, province);
        }
        if (city != null && !city.isEmpty()) {
            wrapper.eq(Activity::getCity, city);
        }
        if (district != null && !district.isEmpty()) {
            wrapper.eq(Activity::getDistrict, district);
        }
        if (street != null && !street.isEmpty()) {
            wrapper.eq(Activity::getStreet, street);
        }

        if (timeSlot != null && !timeSlot.isEmpty()) {
            applyTimeSlotFilter(wrapper, timeSlot);
        }

        if (startTime != null) {
            wrapper.ge(Activity::getStartTime, startTime);
        }
        if (endTime != null) {
            wrapper.le(Activity::getStartTime, endTime);
        }

        if (sortBy != null && !sortBy.isEmpty()) {
            if ("asc".equalsIgnoreCase(sortOrder)) {
                wrapper.orderByAsc(
                    "startTime".equals(sortBy) ? Activity::getStartTime :
                    "title".equals(sortBy) ? Activity::getTitle :
                    "currentParticipants".equals(sortBy) ? Activity::getCurrentParticipants :
                    Activity::getCreateTime
                );
            } else {
                wrapper.orderByDesc(
                    "startTime".equals(sortBy) ? Activity::getStartTime :
                    "title".equals(sortBy) ? Activity::getTitle :
                    "currentParticipants".equals(sortBy) ? Activity::getCurrentParticipants :
                    Activity::getCreateTime
                );
            }
        } else {
            wrapper.orderByDesc(Activity::getCreateTime);
        }

        return activityMapper.selectPage(page, wrapper);
    }

    /**
     * 根据用户角色应用活动过滤
     */
    private void applyRoleFilter(LambdaQueryWrapper<Activity> wrapper, SysUser currentUser) {
        if (currentUser == null) {
            // 未登录用户：只能看到校级活动
            wrapper.eq(Activity::getActivityScope, ActivityScopeEnum.UNIVERSITY.getCode());
            return;
        }

        boolean isAdmin = checkIsAdmin(currentUser);
        boolean isOrganizer = checkIsOrganizer(currentUser);
        boolean isFullUser = checkIsFullUser(currentUser);

        if (isAdmin) {
            // 管理员：可以看到所有活动
            return;
        } else if (isOrganizer) {
            // 组织者：只能看到本院活动
            if (currentUser.getCollegeId() == null) {
                throw new BusinessException("请先联系管理员绑定学院");
            }
            wrapper.eq(Activity::getCollegeId, currentUser.getCollegeId())
                   .eq(Activity::getActivityScope, ActivityScopeEnum.COLLEGE.getCode());
        } else if (isFullUser) {
            // 完整用户：可以看到校级活动和本院活动
            wrapper.and(w -> w.eq(Activity::getActivityScope, ActivityScopeEnum.UNIVERSITY.getCode())
                    .or(o -> o.eq(Activity::getActivityScope, ActivityScopeEnum.COLLEGE.getCode())
                            .eq(Activity::getCollegeId, currentUser.getCollegeId())));
        } else {
            // 访客：只可以看到校级活动
            wrapper.eq(Activity::getActivityScope, ActivityScopeEnum.UNIVERSITY.getCode());
        }
    }

    private void applyTimeSlotFilter(LambdaQueryWrapper<Activity> wrapper, String timeSlot) {
        switch (timeSlot) {
            case "weekend":
                wrapper.and(w -> w.apply("DAYOFWEEK(start_time) = ?", 1).or().apply("DAYOFWEEK(start_time) = ?", 7));
                break;
            case "holiday":
                wrapper.apply("DATE_FORMAT(start_time, '%m-%d') IN ('01-01', '02-12', '02-13', '04-04', '04-05', '05-01', '05-02', '05-03', '06-01', '09-10', '10-01', '10-02', '10-03', '12-24', '12-25', '12-31')");
                break;
            case "after_school":
                wrapper.apply("HOUR(start_time) >= ?", 18);
                break;
            default:
                break;
        }
    }

    /** 兼容旧版本调用的重载方法 */
    public Page<Activity> getActivityPage(Integer pageNum, Integer pageSize, String category, String keyword) {
        String cacheKey = activityCacheService.buildCacheKey(pageNum, pageSize, category, keyword);
        PageResult<?> cachedResult = activityCacheService.getFromCache(cacheKey);
        if (cachedResult != null) {
            Page<Activity> cachedPage = new Page<>(cachedResult.getPageNum(), cachedResult.getPageSize());
            cachedPage.setTotal(cachedResult.getTotal());
            @SuppressWarnings("unchecked")
            List<Activity> records = (List<Activity>) cachedResult.getList();
            cachedPage.setRecords(records);
            return cachedPage;
        }

        Page<Activity> page = getActivityPage(pageNum, pageSize, category, keyword, null, null, null, null, null,
                null, null, null, null, null, null);
        activityCacheService.putToCache(cacheKey, PageResult.of(page));
        return page;
    }

    public Page<Activity> getActivityPage(Integer pageNum, Integer pageSize,
                                           String category, String keyword,
                                           String status, LocalDateTime startTime,
                                           LocalDateTime endTime, String sortBy, String sortOrder) {
        return getActivityPage(pageNum, pageSize, category, keyword, status, startTime, endTime, 
                              sortBy, sortOrder, null, null, null, null, null, null);
    }

    public Page<Activity> getActivityPage(Integer pageNum, Integer pageSize,
                                           String category, String keyword,
                                           String status, LocalDateTime startTime,
                                           LocalDateTime endTime, String sortBy, String sortOrder,
                                           String province, String city, String district, String street,
                                           String timeSlot) {
        return getActivityPage(pageNum, pageSize, category, keyword, status, startTime, endTime, 
                              sortBy, sortOrder, province, city, district, street, timeSlot, null);
    }

    public Activity getActivityById(Long id) {
        return activityMapper.selectById(id);
    }

    public Activity getActivityById(Long id, SysUser currentUser) {
        Activity activity = activityMapper.selectById(id);
        validateActivityExists(activity);

        if (canViewNonPublicActivity(currentUser)) {
            if (checkIsAdmin(currentUser) || checkIsAuditor(currentUser)) {
                return activity;
            }
            if (currentUser.getCollegeId() != null
                    && activity.getCollegeId() != null
                    && activity.getCollegeId().equals(currentUser.getCollegeId())) {
                return activity;
            }
            if (activity.getOrganizerId() != null && activity.getOrganizerId().equals(currentUser.getId())) {
                return activity;
            }
        }

        if (!publicActivityStatuses().contains(activity.getStatus())) {
            throw new BusinessException("活动不存在或暂不可见");
        }
        if (currentUser == null || !checkIsFullUser(currentUser)) {
            if (!ActivityScopeEnum.UNIVERSITY.getCode().equals(activity.getActivityScope())) {
                throw new BusinessException("活动不存在或暂不可见");
            }
            return activity;
        }
        boolean visibleToFullUser = ActivityScopeEnum.UNIVERSITY.getCode().equals(activity.getActivityScope())
                || (ActivityScopeEnum.COLLEGE.getCode().equals(activity.getActivityScope())
                && currentUser.getCollegeId() != null
                && currentUser.getCollegeId().equals(activity.getCollegeId()));
        if (!visibleToFullUser) {
            throw new BusinessException("活动不存在或暂不可见");
        }
        return activity;
    }

    /**
     * 创建活动 - 根据用户角色自动设置范围和学院
     */
    @Transactional
    public Activity createActivity(Activity activity, SysUser currentUser) {
        log.info("创建活动, userId: {}, title: {}", currentUser.getId(), activity.getTitle());
        
        boolean isOrganizer = checkIsOrganizer(currentUser);
        boolean isAdmin = checkIsAdmin(currentUser);
        
        if (isOrganizer) {
            if (currentUser.getCollegeId() == null) {
                throw new BusinessException("请先联系管理员绑定学院后再创建活动");
            }
            activity.setActivityScope(ActivityScopeEnum.COLLEGE.getCode());
            activity.setCollegeId(currentUser.getCollegeId());
        } else if (isAdmin) {
            if (activity.getActivityScope() == null) {
                activity.setActivityScope(ActivityScopeEnum.UNIVERSITY.getCode());
            }
            if (ActivityScopeEnum.COLLEGE.getCode().equals(activity.getActivityScope()) 
                    && activity.getCollegeId() == null) {
                throw new BusinessException("院级活动必须指定所属学院");
            }
            if (ActivityScopeEnum.UNIVERSITY.getCode().equals(activity.getActivityScope())) {
                activity.setCollegeId(null);
            }
        } else {
            throw new BusinessException("无权创建活动");
        }

        validateActivityCategory(activity);

        long count = activityMapper.selectCount(
            new LambdaQueryWrapper<Activity>()
                .eq(Activity::getOrganizerId, currentUser.getId())
                .eq(Activity::getTitle, activity.getTitle())
                .ne(Activity::getStatus, ActivityStatusEnum.CANCELLED.name())
        );
        if (count > 0) {
            throw new BusinessException("您已创建过同名活动");
        }
        
        if (activity.getMaxParticipants() != null && activity.getMaxParticipants() <= 0) {
            throw new BusinessException("活动人数上限必须大于0");
        }
        
        activity.setOrganizerId(currentUser.getId());
        activity.setStatus(ActivityStatusEnum.DRAFT.name());
        activity.setCurrentParticipants(0);
        activityMapper.insert(activity);
        activityCacheService.clearActivityCache();
        return activity;
    }

    /**
     * 创建活动（兼容旧接口）
     */
    @Transactional
    public Activity createActivity(Activity activity, Long userId) {
        return createActivity(activity, requireUser(userId));
    }

    @Transactional
    public void submitForAudit(Long id, SysUser currentUser) {
        log.info("提交活动审核, activityId: {}, userId: {}", id, currentUser.getId());
        Activity activity = activityMapper.selectById(id);
        validateActivityExists(activity);
        validateOwner(activity, currentUser.getId());
        // R-04:跨学院越权校验
        validateCollegeAccess(activity, currentUser, "提交");

        if (!ActivityStatusEnum.DRAFT.name().equals(activity.getStatus())) {
            throw new BusinessException("只有草稿状态的活动才能提交审核");
        }

        activity.setStatus(ActivityStatusEnum.AUDITING.name());
        activity.setSubmitTime(LocalDateTime.now());
        activityMapper.updateById(activity);
        activityCacheService.clearActivityCache(id);
        log.info("活动提交审核成功, activityId: {}", id);
    }

    @Transactional
    public void approveActivity(Long id, String comment) {
        log.info("审核通过活动, activityId: {}", id);
        Activity activity = activityMapper.selectById(id);
        validateActivityExists(activity);
        
        if (!ActivityStatusEnum.AUDITING.name().equals(activity.getStatus())) {
            throw new BusinessException("活动当前状态无法审核");
        }
        
        activity.setStatus(ActivityStatusEnum.APPROVED.name());
        activity.setAuditComment(comment);
        activity.setAuditTime(LocalDateTime.now());
        activityMapper.updateById(activity);
        activityCacheService.clearActivityCache(id);
        log.info("活动审核通过, activityId: {}", id);
    }

    @Transactional
    public void approveActivity(Long id, String comment, SysUser currentUser) {
        requireAuditPermission(id, currentUser);
        approveActivity(id, comment);
    }

    @Transactional
    public void rejectActivity(Long id, String reason) {
        log.info("审核拒绝活动, activityId: {}", id);
        Activity activity = activityMapper.selectById(id);
        validateActivityExists(activity);
        
        if (!ActivityStatusEnum.AUDITING.name().equals(activity.getStatus())) {
            throw new BusinessException("活动当前状态无法审核");
        }
        
        activity.setStatus(ActivityStatusEnum.REJECTED.name());
        activity.setAuditComment(reason);
        activity.setAuditTime(LocalDateTime.now());
        activityMapper.updateById(activity);
        activityCacheService.clearActivityCache(id);
        log.info("活动审核拒绝, activityId: {}", id);
    }

    @Transactional
    public void rejectActivity(Long id, String reason, SysUser currentUser) {
        requireAuditPermission(id, currentUser);
        rejectActivity(id, reason);
    }

    @Transactional
    public void publishActivity(Long id, SysUser currentUser) {
        log.info("发布活动, activityId: {}, userId: {}", id, currentUser.getId());
        Activity activity = activityMapper.selectById(id);
        validateActivityExists(activity);
        validateOwner(activity, currentUser.getId());
        // R-04:跨学院越权校验
        validateCollegeAccess(activity, currentUser, "发布");

        if (!ActivityStatusEnum.APPROVED.name().equals(activity.getStatus())) {
            throw new BusinessException("只有审核通过的活动才能发布");
        }

        activity.setStatus(ActivityStatusEnum.RECRUITING.name());
        activity.setPublishedTime(LocalDateTime.now());
        activityMapper.updateById(activity);
        activityCacheService.clearActivityCache(id);
        log.info("活动发布成功, activityId: {}", id);
    }

    @Transactional
    public Activity createUrgentActivity(Activity activity, Long userId) {
        return createUrgentActivity(activity, requireUser(userId));
    }

    @Transactional
    public Activity createUrgentActivity(Activity activity, SysUser currentUser) {
        log.info("Create urgent activity, userId: {}, title: {}", currentUser.getId(), activity.getTitle());

        boolean isOrganizer = checkIsOrganizer(currentUser);
        boolean isAdmin = checkIsAdmin(currentUser);

        if (isOrganizer) {
            if (currentUser.getCollegeId() == null) {
                throw new BusinessException("Organizer must bind a college before creating activities");
            }
            activity.setActivityScope(ActivityScopeEnum.COLLEGE.getCode());
            activity.setCollegeId(currentUser.getCollegeId());
        } else if (isAdmin) {
            if (activity.getActivityScope() == null) {
                activity.setActivityScope(ActivityScopeEnum.UNIVERSITY.getCode());
            }
            if (ActivityScopeEnum.COLLEGE.getCode().equals(activity.getActivityScope())
                    && activity.getCollegeId() == null) {
                throw new BusinessException("College activity must specify collegeId");
            }
            if (ActivityScopeEnum.UNIVERSITY.getCode().equals(activity.getActivityScope())) {
                activity.setCollegeId(null);
            }
        } else {
            throw new BusinessException("No permission to create activity");
        }

        activity.setOrganizerId(currentUser.getId());
        activity.setStatus(ActivityStatusEnum.RECRUITING.name());
        activity.setCurrentParticipants(0);
        activity.setIsUrgent(true);
        activity.setUrgentLevel("HIGH");
        activity.setPublishedTime(LocalDateTime.now());
        activityMapper.insert(activity);
        activityCacheService.clearActivityCache();
        log.info("Urgent activity created and published, activityId: {}", activity.getId());
        return activity;
    }
    @Transactional
    public void startActivity(Long id) {
        log.info("开始活动, activityId: {}", id);
        Activity activity = activityMapper.selectById(id);
        validateActivityExists(activity);
        
        if (!ActivityStatusEnum.RECRUITING.name().equals(activity.getStatus())
                && !ActivityStatusEnum.PUBLISHED.name().equals(activity.getStatus())) {
            throw new BusinessException("只有已发布的活动才能开始");
        }
        
        activity.setStatus(ActivityStatusEnum.ONGOING.name());
        activityMapper.updateById(activity);
        activityCacheService.clearActivityCache(id);
        log.info("活动开始, activityId: {}", id);
    }

    @Transactional
    public void completeActivity(Long id) {
        log.info("完成活动, activityId: {}", id);
        Activity activity = activityMapper.selectById(id);
        validateActivityExists(activity);
        
        if (!ActivityStatusEnum.ONGOING.name().equals(activity.getStatus())) {
            throw new BusinessException("只有进行中的活动才能完成");
        }
        
        activity.setStatus(ActivityStatusEnum.COMPLETED.name());
        activity.setEndTime(LocalDateTime.now());
        activityMapper.updateById(activity);
        activityCacheService.clearActivityCache(id);
        log.info("活动完成, activityId: {}", id);
    }

    @Transactional
    public void archiveActivity(Long id) {
        log.info("归档活动, activityId: {}", id);
        Activity activity = activityMapper.selectById(id);
        validateActivityExists(activity);
        
        if (!ActivityStatusEnum.COMPLETED.name().equals(activity.getStatus())) {
            throw new BusinessException("只有已完成的活动才能归档");
        }
        
        activity.setStatus(ActivityStatusEnum.ARCHIVED.name());
        activityMapper.updateById(activity);
        activityCacheService.clearActivityCache(id);
        log.info("活动归档, activityId: {}", id);
    }

    @Transactional
    public void cancelActivity(Long id, SysUser currentUser, String reason) {
        log.info("取消活动, activityId: {}, userId: {}", id, currentUser.getId());
        Activity activity = activityMapper.selectById(id);
        validateActivityExists(activity);
        validateOwner(activity, currentUser.getId());
        // R-04:跨学院越权校验
        validateCollegeAccess(activity, currentUser, "取消");

        String currentStatus = activity.getStatus();
        if (ActivityStatusEnum.CANCELLED.name().equals(currentStatus) ||
            ActivityStatusEnum.ARCHIVED.name().equals(currentStatus)) {
            throw new BusinessException("活动状态不允许取消");
        }

        activity.setStatus(ActivityStatusEnum.CANCELLED.name());
        activity.setCancelReason(reason);
        activity.setCancelTime(LocalDateTime.now());
        activityMapper.updateById(activity);
        activityCacheService.clearActivityCache(id);
        log.info("活动取消, activityId: {}", id);
    }

    @Transactional
    public void cancelActivity(Long id, Long userId, String reason) {
        cancelActivity(id, requireUser(userId), reason);
    }

    /**
     * 更新活动 - 验证学院权限
     */
    @Transactional
    public void updateActivity(Activity activity, SysUser currentUser) {
        Activity existingActivity = activityMapper.selectById(activity.getId());
        if (existingActivity == null) {
            throw new BusinessException("活动不存在");
        }
        
        boolean isAdmin = checkIsAdmin(currentUser);
        boolean isOrganizer = checkIsOrganizer(currentUser);
        
        if (!isAdmin) {
            if (!isOrganizer) {
                throw new BusinessException("无权修改活动");
            }
            if (!existingActivity.getCollegeId().equals(currentUser.getCollegeId())) {
                throw new BusinessException("无权修改其他学院的活动");
            }
            activity.setActivityScope(existingActivity.getActivityScope());
            activity.setCollegeId(existingActivity.getCollegeId());
        }

        validateActivityCategory(activity);
        
        activityMapper.updateById(activity);
        activityCacheService.clearActivityCache(activity.getId());
    }

    public Page<Activity> getAuditActivityPage(Integer pageNum, Integer pageSize, SysUser currentUser) {
        return getActivityPage(pageNum, pageSize, null, null, ActivityStatusEnum.AUDITING.name(),
                null, null, null, null, null, null, null, null, null, currentUser);
    }

    public Page<ActivityRegistration> getActivityRegistrations(Long activityId, Integer pageNum,
                                                               Integer pageSize, String status) {
        Page<ActivityRegistration> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<ActivityRegistration> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ActivityRegistration::getActivityId, activityId);
        if (status != null && !status.isEmpty()) {
            wrapper.eq(ActivityRegistration::getStatus, status);
        }
        wrapper.orderByDesc(ActivityRegistration::getCreateTime);
        return registrationMapper.selectPage(page, wrapper);
    }

    @Transactional
    public void auditActivity(Long id, String status, String comment) {
        String normalizedStatus = status == null ? "" : status.trim().toUpperCase();
        if (ActivityStatusEnum.APPROVED.name().equals(normalizedStatus)) {
            approveActivity(id, comment);
            return;
        }
        if (ActivityStatusEnum.REJECTED.name().equals(normalizedStatus)) {
            rejectActivity(id, comment);
            return;
        }
        throw new BusinessException("审核状态只能为 APPROVED 或 REJECTED");
    }

    @Transactional
    public void auditActivity(Long id, String status, String comment, SysUser currentUser) {
        requireAuditPermission(id, currentUser);
        auditActivity(id, status, comment);
    }

    private void validateActivityExists(Activity activity) {
        if (activity == null) {
            throw new BusinessException("活动不存在");
        }
    }

    private void validateOwner(Activity activity, Long userId) {
        if (!activity.getOrganizerId().equals(userId)) {
            throw new BusinessException("只能操作自己创建的活动");
        }
    }

    /**
     * 删除活动 - 验证学院权限
     */
    @Transactional
    public void deleteActivity(Long id, SysUser currentUser) {
        log.info("删除活动, activityId: {}, userId: {}", id, currentUser.getId());
        Activity activity = activityMapper.selectById(id);
        if (activity == null) {
            throw new BusinessException("活动不存在");
        }
        
        boolean isAdmin = checkIsAdmin(currentUser);
        boolean isOrganizer = checkIsOrganizer(currentUser);
        
        if (!isAdmin) {
            if (!isOrganizer) {
                throw new BusinessException("无权删除活动");
            }
            if (!activity.getCollegeId().equals(currentUser.getCollegeId())) {
                throw new BusinessException("无权删除其他学院的活动");
            }
        }

        long confirmedCount = registrationMapper.selectCount(
            new LambdaQueryWrapper<ActivityRegistration>()
                .eq(ActivityRegistration::getActivityId, id)
                .ne(ActivityRegistration::getStatus, "CANCELLED")
        );

        if (confirmedCount > 0) {
            log.warn("活动删除失败，已有{}人报名", confirmedCount);
            throw new BusinessException("该活动已有" + confirmedCount + "人报名，无法删除");
        }

        Long attendanceCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM attendance WHERE activity_id = ?", 
            Long.class, id
        );
        attendanceCount = attendanceCount != null ? attendanceCount : 0L;

        if (attendanceCount > 0) {
            log.warn("活动删除失败，已有{}条考勤记录", attendanceCount);
            throw new BusinessException("该活动已有" + attendanceCount + "条考勤记录，无法删除");
        }

        long teamCount = teamMapper.selectCount(
            new LambdaQueryWrapper<Team>()
                .eq(Team::getActivityId, id)
        );

        if (teamCount > 0) {
            log.warn("活动删除失败，已有{}支队伍", teamCount);
            throw new BusinessException("该活动已有" + teamCount + "支队伍，无法删除");
        }

        activityMapper.deleteById(id);
        activityCacheService.clearActivityCache(id);
        log.info("活动删除成功, activityId: {}", id);
    }

    /**
     * 删除活动（兼容旧接口）
     */
    @Transactional
    public void deleteActivity(Long id, Long userId) {
        deleteActivity(id, requireUser(userId));
    }

    public Page<ActivityListDTO> getActivityListWithCountdown(Integer pageNum, Integer pageSize,
                                                              String category, String keyword,
                                                              String status, LocalDateTime startTime,
                                                              LocalDateTime endTime, String sortBy, String sortOrder,
                                                              String province, String city, String district, String street,
                                                              String timeSlot) {
        return getActivityListWithCountdown(pageNum, pageSize, category, keyword, status, startTime, endTime,
                sortBy, sortOrder, province, city, district, street, timeSlot, null);
    }

    public Page<ActivityListDTO> getActivityListWithCountdown(Integer pageNum, Integer pageSize,
                                                              String category, String keyword,
                                                              String status, LocalDateTime startTime,
                                                              LocalDateTime endTime, String sortBy, String sortOrder,
                                                              String province, String city, String district, String street,
                                                              String timeSlot, SysUser currentUser) {
        Page<Activity> activityPage = getActivityPage(pageNum, pageSize, category, keyword, status, 
                                                      startTime, endTime, sortBy, sortOrder,
                                                      province, city, district, street, timeSlot, currentUser);
        
        Page<ActivityListDTO> dtoPage = new Page<>(pageNum, pageSize);
        dtoPage.setTotal(activityPage.getTotal());
        
        List<ActivityListDTO> dtoList = activityPage.getRecords().stream()
                .map(ActivityListDTO::fromEntity)
                .collect(Collectors.toList());
        
        dtoPage.setRecords(dtoList);
        return dtoPage;
    }

    @Transactional
    public ActivityRegistration registerActivity(Long activityId, Long userId) {
        log.info("用户报名活动, userId: {}, activityId: {}", userId, activityId);
        Activity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            throw new BusinessException("活动不存在");
        }

        if (!ActivityStatusEnum.RECRUITING.name().equals(activity.getStatus()) &&
            !ActivityStatusEnum.PUBLISHED.name().equals(activity.getStatus()) &&
            !ActivityStatusEnum.ONGOING.name().equals(activity.getStatus())) {
            throw new BusinessException("活动未在招募中");
        }

        if (activity.getRegistrationDeadline() != null
                && LocalDateTime.now().isAfter(activity.getRegistrationDeadline())) {
            throw new BusinessException("报名已截止");
        }

        ActivityRegistration existing = registrationMapper.selectOne(
                new LambdaQueryWrapper<ActivityRegistration>()
                        .eq(ActivityRegistration::getActivityId, activityId)
                        .eq(ActivityRegistration::getUserId, userId)
        );

        boolean needsApproval = requiresRegistrationApproval(activity);
        LocalDateTime now = LocalDateTime.now();

        if (existing != null) {
            if ("CANCELLED".equals(existing.getStatus())) {
                prepareRegistrationForSubmit(existing, needsApproval, now);
                if (!needsApproval) {
                    occupyActivitySeat(activityId);
                }
                registrationMapper.updateById(existing);
                publishRegistrationEvents(existing, activity,
                        needsApproval ? ActivityRegistrationEvent.EventType.PENDING : ActivityRegistrationEvent.EventType.APPROVED,
                        true);
                log.info("用户重新报名成功, userId: {}, activityId: {}", userId, activityId);
                return existing;
            }
            throw new BusinessException("您已报名该活动");
        }

        ActivityRegistration registration = new ActivityRegistration();
        registration.setActivityId(activityId);
        registration.setUserId(userId);
        prepareRegistrationForSubmit(registration, needsApproval, now);

        if (!needsApproval) {
            occupyActivitySeat(activityId);
        }
        registrationMapper.insert(registration);
        publishRegistrationEvents(registration, activity,
                needsApproval ? ActivityRegistrationEvent.EventType.PENDING : ActivityRegistrationEvent.EventType.APPROVED,
                true);

        log.info("用户报名成功, userId: {}, activityId: {}", userId, activityId);
        return registration;
    }

    private boolean requiresRegistrationApproval(Activity activity) {
        return Boolean.TRUE.equals(activity.getRequiresApproval());
    }

    private void prepareRegistrationForSubmit(ActivityRegistration registration, boolean needsApproval, LocalDateTime now) {
        registration.setStatus(needsApproval ? "PENDING" : "APPROVED");
        registration.setRegistrationTime(now);
        registration.setApprovalTime(needsApproval ? null : now);
        registration.setRejectReason(null);
        registration.setCancelTime(null);
        registration.setCancelReason(null);
        if (registration.getJoinMethod() == null) {
            registration.setJoinMethod("self_signup");
        }
    }

    private void occupyActivitySeat(Long activityId) {
        int updated = activityMapper.increaseParticipantsWithLock(activityId);
        if (updated == 0) {
            log.warn("活动人数更新失败，可能已超员, activityId: {}", activityId);
            throw new BusinessException("活动已满员，请刷新重试");
        }
    }

    private void publishRegistrationEvents(ActivityRegistration registration, Activity activity,
                                           ActivityRegistrationEvent.EventType eventType) {
        publishRegistrationEvents(registration, activity, eventType, false);
    }

    private void publishRegistrationEvents(ActivityRegistration registration, Activity activity,
                                           ActivityRegistrationEvent.EventType eventType,
                                           boolean applicationSubmitted) {
        eventPublisher.publishEvent(new ActivityRegistrationEvent(
                registration.getId(),
                activity.getId(),
                registration.getUserId(),
                activity.getTitle(),
                activity.getStartTime(),
                eventType,
                applicationSubmitted
        ));

        ActivityEvent.EventType notificationType = switch (eventType) {
            case APPROVED -> ActivityEvent.EventType.APPROVAL_SUCCESS;
            case REJECTED -> ActivityEvent.EventType.REGISTRATION_REJECTED;
            case CANCELLED -> ActivityEvent.EventType.ACTIVITY_CANCELLED;
            case PENDING -> ActivityEvent.EventType.REGISTRATION_SUCCESS;
        };
        eventPublisher.publishEvent(new ActivityEvent(
                this,
                registration.getUserId(),
                activity.getId(),
                notificationType,
                buildRegistrationMessage(activity, eventType)
        ));
    }

    private String buildRegistrationMessage(Activity activity, ActivityRegistrationEvent.EventType eventType) {
        String title = activity.getTitle() == null ? "activity" : activity.getTitle();
        return switch (eventType) {
            case APPROVED -> "Your registration for " + title + " has been approved.";
            case REJECTED -> "Your registration for " + title + " was rejected.";
            case CANCELLED -> "Your registration for " + title + " has been cancelled.";
            case PENDING -> "Your registration for " + title + " has been submitted and is pending review.";
        };
    }

    @Transactional
    public void approveRegistration(Long registrationId) {
        log.info("审核通过报名, registrationId: {}", registrationId);
        ActivityRegistration registration = registrationMapper.selectById(registrationId);
        if (registration == null) {
            throw new BusinessException("报名记录不存在");
        }

        Activity activity = activityMapper.selectById(registration.getActivityId());
        if (activity == null) {
            throw new BusinessException("活动不存在");
        }

        if (ActivityStatusEnum.ARCHIVED.name().equals(activity.getStatus())) {
            throw new BusinessException("活动已归档，无法修改报名状态");
        }

        if (!"PENDING".equals(registration.getStatus())) {
            throw new BusinessException("报名状态不允许审核");
        }

        int updated = activityMapper.increaseParticipantsWithLock(registration.getActivityId());
        if (updated == 0) {
            throw new BusinessException("活动已满员，无法通过报名");
        }

        registration.setStatus("APPROVED");
        registration.setApprovalTime(LocalDateTime.now());
        registrationMapper.updateById(registration);
        publishRegistrationEvents(registration, activity, ActivityRegistrationEvent.EventType.APPROVED);
        log.info("报名审核通过, registrationId: {}", registrationId);
    }

    @Transactional
    public void rejectRegistration(Long registrationId, String reason) {
        log.info("审核拒绝报名, registrationId: {}", registrationId);
        ActivityRegistration registration = registrationMapper.selectById(registrationId);
        if (registration == null) {
            throw new BusinessException("报名记录不存在");
        }

        Activity activity = activityMapper.selectById(registration.getActivityId());
        if (activity == null) {
            throw new BusinessException("活动不存在");
        }

        if (ActivityStatusEnum.ARCHIVED.name().equals(activity.getStatus())) {
            throw new BusinessException("活动已归档，无法修改报名状态");
        }

        if (!"PENDING".equals(registration.getStatus())) {
            throw new BusinessException("报名状态不允许审核");
        }

        registration.setStatus("REJECTED");
        registration.setRejectReason(reason);
        registration.setApprovalTime(LocalDateTime.now());
        registrationMapper.updateById(registration);
        publishRegistrationEvents(registration, activity, ActivityRegistrationEvent.EventType.REJECTED);
        
        log.info("报名审核拒绝, registrationId: {}", registrationId);
    }

    @Transactional
    public void updateRegistrationStatus(Long registrationId, String status) {
        log.info("更新报名状态, registrationId: {}, status: {}", registrationId, status);
        String normalizedStatus = status == null ? "" : status.trim().toUpperCase();
        if ("APPROVED".equals(normalizedStatus)) {
            approveRegistration(registrationId);
            return;
        }
        if ("REJECTED".equals(normalizedStatus)) {
            rejectRegistration(registrationId, "Rejected");
            return;
        }
        ActivityRegistration registration = registrationMapper.selectById(registrationId);
        if (registration == null) {
            throw new BusinessException("报名记录不存在");
        }

        Activity activity = activityMapper.selectById(registration.getActivityId());
        if (activity == null) {
            throw new BusinessException("活动不存在");
        }

        if (ActivityStatusEnum.ARCHIVED.name().equals(activity.getStatus())) {
            throw new BusinessException("活动已归档，无法修改报名状态");
        }

        if ("CANCELLED".equals(normalizedStatus)) {
            cancelRegistration(registration.getActivityId(), registration.getUserId());
            return;
        }
        if (!"WAITLIST".equals(normalizedStatus) && !"PENDING".equals(normalizedStatus)) {
            throw new BusinessException("不支持的报名状态");
        }

        registration.setStatus(normalizedStatus);
        registrationMapper.updateById(registration);
        log.info("报名状态更新成功, registrationId: {}", registrationId);
    }

    @Transactional
    public void cancelRegistration(Long activityId, Long userId) {
        log.info("用户取消报名, userId: {}, activityId: {}", userId, activityId);
        ActivityRegistration registration = registrationMapper.selectOne(
                new LambdaQueryWrapper<ActivityRegistration>()
                        .eq(ActivityRegistration::getActivityId, activityId)
                        .eq(ActivityRegistration::getUserId, userId)
        );

        if (registration == null) {
            throw new BusinessException("未找到报名记录");
        }

        Activity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            throw new BusinessException("活动不存在");
        }

        String oldStatus = registration.getStatus();
        if ("CANCELLED".equals(oldStatus)) {
            throw new BusinessException("报名已取消");
        }
        registration.setStatus("CANCELLED");
        registration.setCancelTime(LocalDateTime.now());
        registrationMapper.updateById(registration);

        int updated = "APPROVED".equals(oldStatus) ? activityMapper.decreaseParticipantsWithLock(activityId) : 1;
        if (updated == 0) {
            log.warn("减少活动人数失败, activityId: {}", activityId);
        }
        publishRegistrationEvents(registration, activity, ActivityRegistrationEvent.EventType.CANCELLED);
        log.info("用户取消报名成功, userId: {}, activityId: {}", userId, activityId);
    }

    private boolean checkIsAdmin(SysUser user) {
        return hasUserRole(user, "ADMIN");
    }

    private boolean checkIsOrganizer(SysUser user) {
        return hasUserRole(user, "ORGANIZER");
    }

    /**
     * 判断是否为学院管理员(R-04 实施)
     * <p>学院管理员只能操作本学院活动。</p>
     */
    private boolean checkIsCollegeAdmin(SysUser user) {
        return hasUserRole(user, "COLLEGE_ADMIN");
    }

    private boolean checkIsAuditor(SysUser user) {
        return hasUserRole(user, "AUDITOR");
    }

    private void requireAuditPermission(Long activityId, SysUser currentUser) {
        if (currentUser == null) {
            throw new BusinessException("请先登录");
        }
        Activity activity = activityMapper.selectById(activityId);
        validateActivityExists(activity);
        if (checkIsAdmin(currentUser) || checkIsAuditor(currentUser)) {
            return;
        }
        if (checkIsCollegeAdmin(currentUser)) {
            if (activity.getCollegeId() == null || currentUser.getCollegeId() == null
                    || !activity.getCollegeId().equals(currentUser.getCollegeId())) {
                throw new BusinessException("无权审核其他学院的活动");
            }
            return;
        }
        throw new BusinessException("无权审核活动");
    }

    /**
     * 跨学院越权校验(R-04 实施)
     * <p>对所有写操作生效:organizer 必须是创建者且本学院;collegeAdmin 必须与活动所属学院一致;
     * admin 不受学院限制。</p>
     */
    private void validateCollegeAccess(Activity activity, SysUser currentUser, String op) {
        if (currentUser == null) {
            throw new BusinessException("请先登录");
        }
        if (checkIsAdmin(currentUser)) {
            return;
        }
        if (currentUser.getCollegeId() == null) {
            throw new BusinessException("未绑定学院,无法" + op);
        }
        if (!checkIsOrganizer(currentUser)
                && !checkIsCollegeAdmin(currentUser)) {
            throw new BusinessException("无权" + op);
        }
        if (activity.getCollegeId() == null
                || !activity.getCollegeId().equals(currentUser.getCollegeId())) {
            log.warn("越权拦截: 用户{} (学院{}) 试图{}活动{} (学院{})",
                    currentUser.getId(), currentUser.getCollegeId(),
                    op, activity.getId(), activity.getCollegeId());
            throw new BusinessException("无权操作其他学院的活动");
        }
    }

    private boolean checkIsFullUser(SysUser user) {
        return user != null && user.getStudentId() != null;
    }

    private boolean canViewNonPublicActivity(SysUser user) {
        return checkIsAdmin(user) || checkIsOrganizer(user) || checkIsCollegeAdmin(user) || checkIsAuditor(user);
    }

    private void validateActivityCategory(Activity activity) {
        if (activity == null) {
            return;
        }
        String category = activity.getCategory();
        if (category == null || category.trim().isEmpty()) {
            activity.setCategory(null);
            return;
        }
        String normalizedCategory = category.trim();
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM activity_category WHERE category_code = ? AND COALESCE(status, 1) = 1",
                Integer.class,
                normalizedCategory
        );
        if (count == null || count == 0) {
            throw new BusinessException("活动分类不存在：" + normalizedCategory);
        }
        activity.setCategory(normalizedCategory);
    }

    private Set<String> publicActivityStatuses() {
        return Set.of(
                ActivityStatusEnum.RECRUITING.name(),
                ActivityStatusEnum.ONGOING.name(),
                ActivityStatusEnum.PUBLISHED.name()
        );
    }

    private String getUserRoleCode(SysUser user) {
        if (user == null) {
            return "VISITOR";
        }
        if (user.getRoleCode() != null && !user.getRoleCode().isEmpty()) {
            return user.getRoleCode();
        }
        List<String> roleCodes = getUserRoleCodes(user);
        if (!roleCodes.isEmpty()) {
            if (roleCodes.contains("ADMIN")) {
                return "ADMIN";
            }
            if (roleCodes.contains("COLLEGE_ADMIN")) {
                return "COLLEGE_ADMIN";
            }
            if (roleCodes.contains("ORGANIZER")) {
                return "ORGANIZER";
            }
            if (roleCodes.contains("AUDITOR")) {
                return "AUDITOR";
            }
            return roleCodes.get(0);
        }
        if (user.getStudentId() != null && !user.getStudentId().isEmpty()) {
            return "VOLUNTEER";
        }
        return "VISITOR";
    }

    private boolean hasUserRole(SysUser user, String roleCode) {
        if (user == null || roleCode == null) {
            return false;
        }
        if (roleCode.equals(user.getRoleCode())) {
            return true;
        }
        return getUserRoleCodes(user).contains(roleCode);
    }

    private List<String> getUserRoleCodes(SysUser user) {
        if (user == null || user.getId() == null) {
            return List.of();
        }
        try {
            return jdbcTemplate.queryForList(
                    "SELECT r.role_code FROM sys_user_role ur " +
                            "JOIN sys_role r ON ur.role_id = r.id " +
                            "WHERE ur.user_id = ? AND COALESCE(r.status, 1) = 1",
                    String.class,
                    user.getId()
            );
        } catch (Exception ex) {
            log.warn("查询用户角色失败, userId: {}", user.getId(), ex);
            return List.of();
        }
    }

    private SysUser requireUser(Long userId) {
        if (userId == null) {
            throw new BusinessException("Please login first");
        }
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("User does not exist");
        }
        return user;
    }

    public Page<UserRegistrationDTO> getUserRegistrations(Integer pageNum, Integer pageSize, String status, Long userId) {
        Page<UserRegistrationDTO> page = new Page<>(pageNum, pageSize);
        return registrationMapper.selectUserRegistrationsPage(page, userId, status);
    }

    public UserRegistrationDTO getUserRegistration(Long activityId, Long userId) {
        return registrationMapper.selectUserRegistration(activityId, userId);
    }
}
