package com.volunteer.report.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.volunteer.common.exception.BusinessException;
import com.volunteer.report.entity.UserTodo;
import com.volunteer.report.mapper.UserTodoMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserTodoService {
    
    private final UserTodoMapper userTodoMapper;
    private static final int MAX_TITLE_LENGTH = 200;
    private static final int TODO_PENDING = 0;
    private static final int TODO_COMPLETED = 1;
    
    public enum TodoCategory {
        ACTIVITY_REGISTRATION("报名审核", "#1890ff"),
        ATTENDANCE_CHECKIN("签到考勤", "#fa8c16"),
        ACTIVITY_EVALUATION("活动评价", "#52c41a"),
        CERTIFICATE_DOWNLOAD("证书下载", "#722ed1"),
        COMPENSATORY_CHECKIN("补签申请", "#eb2f96");
        
        private final String label;
        private final String color;
        
        TodoCategory(String label, String color) {
            this.label = label;
            this.color = color;
        }
        
        public String getLabel() {
            return label;
        }
        
        public String getColor() {
            return color;
        }
    }
    
    public List<UserTodo> getUserTodos(Long userId, String status) {
        LambdaQueryWrapper<UserTodo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserTodo::getUserId, userId);
        
        if ("pending".equals(status)) {
            wrapper.eq(UserTodo::getStatus, TODO_PENDING);
        } else if ("completed".equals(status)) {
            wrapper.eq(UserTodo::getStatus, TODO_COMPLETED);
        }
        
        wrapper.orderByDesc(UserTodo::getDeadline).orderByDesc(UserTodo::getCreateTime);
        return userTodoMapper.selectList(wrapper);
    }
    
    @Transactional
    public UserTodo createTodo(Long userId, String title) {
        String normalizedTitle = normalizeTitle(title);
        UserTodo todo = new UserTodo();
        todo.setUserId(userId);
        todo.setTitle(normalizedTitle);
        todo.setCompleted(false);
        todo.setStatus(TODO_PENDING);
        todo.setCreateTime(LocalDateTime.now());
        todo.setUpdateTime(LocalDateTime.now());
        userTodoMapper.insert(todo);
        return todo;
    }
    
    @Transactional
    public UserTodo createActivityAttendanceTodo(Long userId, String activityName, LocalDateTime activityTime, Long activityId) {
        String title = String.format("按时参加「%s」活动", activityName);
        
        if (existsByRelatedId(userId, activityId, "ACTIVITY_ATTENDANCE")) {
            log.info("待办事项已存在, userId: {}, activityId: {}", userId, activityId);
            return null;
        }
        
        UserTodo todo = new UserTodo();
        todo.setUserId(userId);
        todo.setTitle(title);
        todo.setCategory(TodoCategory.ATTENDANCE_CHECKIN.name());
        todo.setDeadline(activityTime);
        todo.setRelatedId(activityId);
        todo.setRelatedType("ACTIVITY_ATTENDANCE");
        todo.setCompleted(false);
        todo.setCreateTime(LocalDateTime.now());
        todo.setUpdateTime(LocalDateTime.now());
        userTodoMapper.insert(todo);
        
        log.info("创建活动签到待办, userId: {}, activityId: {}", userId, activityId);
        return todo;
    }
    
    @Transactional
    public UserTodo createActivityEvaluationTodo(Long userId, String activityName, LocalDateTime deadline, Long activityId) {
        String title = String.format("评价「%s」活动", activityName);
        
        if (existsByRelatedId(userId, activityId, "ACTIVITY_EVALUATION")) {
            log.info("评价待办已存在, userId: {}, activityId: {}", userId, activityId);
            return null;
        }
        
        UserTodo todo = new UserTodo();
        todo.setUserId(userId);
        todo.setTitle(title);
        todo.setCategory(TodoCategory.ACTIVITY_EVALUATION.name());
        todo.setDeadline(deadline);
        todo.setRelatedId(activityId);
        todo.setRelatedType("ACTIVITY_EVALUATION");
        todo.setCompleted(false);
        todo.setCreateTime(LocalDateTime.now());
        todo.setUpdateTime(LocalDateTime.now());
        userTodoMapper.insert(todo);
        
        log.info("创建活动评价待办, userId: {}, activityId: {}", userId, activityId);
        return todo;
    }
    
    @Transactional
    public UserTodo createCompensatoryCheckinTodo(Long userId, String activityName, Long activityId) {
        String title = String.format("申请补签「%s」活动", activityName);
        
        if (existsByRelatedId(userId, activityId, "COMPENSATORY_CHECKIN")) {
            log.info("补签待办已存在, userId: {}, activityId: {}", userId, activityId);
            return null;
        }
        
        UserTodo todo = new UserTodo();
        todo.setUserId(userId);
        todo.setTitle(title);
        todo.setCategory(TodoCategory.COMPENSATORY_CHECKIN.name());
        todo.setDeadline(LocalDateTime.now().plusDays(3));
        todo.setRelatedId(activityId);
        todo.setRelatedType("COMPENSATORY_CHECKIN");
        todo.setCompleted(false);
        todo.setCreateTime(LocalDateTime.now());
        todo.setUpdateTime(LocalDateTime.now());
        userTodoMapper.insert(todo);
        
        log.info("创建补签申请待办, userId: {}, activityId: {}", userId, activityId);
        return todo;
    }
    
    @Transactional
    public UserTodo createCertificateDownloadTodo(Long userId, String activityName, Long certificateId) {
        String title = String.format("下载「%s」活动证书", activityName);
        
        if (existsByRelatedId(userId, certificateId, "CERTIFICATE_DOWNLOAD")) {
            log.info("证书下载待办已存在, userId: {}, certificateId: {}", userId, certificateId);
            return null;
        }
        
        UserTodo todo = new UserTodo();
        todo.setUserId(userId);
        todo.setTitle(title);
        todo.setCategory(TodoCategory.CERTIFICATE_DOWNLOAD.name());
        todo.setDeadline(LocalDateTime.now().plusDays(7));
        todo.setRelatedId(certificateId);
        todo.setRelatedType("CERTIFICATE_DOWNLOAD");
        todo.setCompleted(false);
        todo.setCreateTime(LocalDateTime.now());
        todo.setUpdateTime(LocalDateTime.now());
        userTodoMapper.insert(todo);
        
        log.info("创建证书下载待办, userId: {}, certificateId: {}", userId, certificateId);
        return todo;
    }
    
    @Transactional
    public void markAsCompleted(Long userId, String relatedId, String relatedType) {
        markAsCompleted(userId, parseRelatedId(relatedId), relatedType);
    }

    @Transactional
    public void markAsCompleted(Long userId, Long relatedId, String relatedType) {
        LambdaQueryWrapper<UserTodo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserTodo::getUserId, userId)
               .eq(UserTodo::getRelatedId, relatedId)
               .eq(UserTodo::getRelatedType, relatedType)
               .eq(UserTodo::getStatus, TODO_PENDING);
        
        UserTodo todo = userTodoMapper.selectOne(wrapper);
        if (todo != null) {
            todo.setCompleted(true);
            todo.setStatus(TODO_COMPLETED);
            todo.setCompletedTime(LocalDateTime.now());
            todo.setUpdateTime(LocalDateTime.now());
            userTodoMapper.updateById(todo);
            log.info("标记待办完成, userId: {}, relatedId: {}, relatedType: {}", userId, relatedId, relatedType);
        }
    }
    
    private boolean existsByRelatedId(Long userId, Long relatedId, String relatedType) {
        LambdaQueryWrapper<UserTodo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserTodo::getUserId, userId)
               .eq(UserTodo::getRelatedId, relatedId)
               .eq(UserTodo::getRelatedType, relatedType)
               .eq(UserTodo::getStatus, TODO_PENDING);
        return userTodoMapper.selectCount(wrapper) > 0;
    }

    private Long parseRelatedId(String relatedId) {
        if (relatedId == null || relatedId.isBlank()) {
            throw new BusinessException("Related id is required");
        }
        try {
            return Long.valueOf(relatedId);
        } catch (NumberFormatException ex) {
            throw new BusinessException("Related id must be numeric");
        }
    }
    
    @Transactional
    public UserTodo updateTodo(Long userId, Long id, String title) {
        String normalizedTitle = normalizeTitle(title);
        UserTodo todo = userTodoMapper.selectById(id);
        if (todo == null) {
            throw new BusinessException("待办事项不存在");
        }
        if (!todo.getUserId().equals(userId)) {
            throw new BusinessException("无权修改此待办事项");
        }
        todo.setTitle(normalizedTitle);
        todo.setUpdateTime(LocalDateTime.now());
        userTodoMapper.updateById(todo);
        return todo;
    }
    
    @Transactional
    public void deleteTodo(Long userId, Long id) {
        UserTodo todo = userTodoMapper.selectById(id);
        if (todo == null) {
            throw new BusinessException("待办事项不存在");
        }
        if (!todo.getUserId().equals(userId)) {
            throw new BusinessException("无权删除此待办事项");
        }
        userTodoMapper.deleteById(id);
    }
    
    @Transactional
    public UserTodo toggleTodo(Long userId, Long id, Boolean completed) {
        UserTodo todo = userTodoMapper.selectById(id);
        if (todo == null) {
            throw new BusinessException("待办事项不存在");
        }
        if (!todo.getUserId().equals(userId)) {
            throw new BusinessException("无权修改此待办事项");
        }
        boolean nextCompleted = completed != null ? completed : !Boolean.TRUE.equals(todo.getCompleted());
        todo.setCompleted(nextCompleted);
        todo.setStatus(nextCompleted ? TODO_COMPLETED : TODO_PENDING);
        todo.setCompletedTime(nextCompleted ? LocalDateTime.now() : null);
        todo.setUpdateTime(LocalDateTime.now());
        userTodoMapper.updateById(todo);
        return todo;
    }
    
    @Transactional
    public void clearCompleted(Long userId) {
        LambdaQueryWrapper<UserTodo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserTodo::getUserId, userId)
               .eq(UserTodo::getStatus, TODO_COMPLETED);
        userTodoMapper.delete(wrapper);
    }

    private String normalizeTitle(String title) {
        if (title == null || title.trim().isEmpty()) {
            throw new BusinessException("Todo title is required");
        }
        String normalizedTitle = title.trim();
        if (normalizedTitle.length() > MAX_TITLE_LENGTH) {
            throw new BusinessException("Todo title is too long");
        }
        return normalizedTitle;
    }
}
