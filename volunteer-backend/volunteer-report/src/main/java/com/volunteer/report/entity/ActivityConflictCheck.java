package com.volunteer.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("activity_conflict_check")
public class ActivityConflictCheck {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    @TableField("user_id")
    private Long userId;
    
    @TableField("activity_id")
    private Long activityId;
    
    @TableField("activity_start_time")
    private LocalDateTime activityStartTime;
    
    @TableField("activity_end_time")
    private LocalDateTime activityEndTime;
    
    @TableField("has_conflict")
    private Integer hasConflict;
    
    @TableField("conflict_course_ids")
    private String conflictCourseIds;
    
    @TableField("conflict_details")
    private String conflictDetails;
    
    @TableField("check_time")
    private LocalDateTime checkTime;
    
    @TableField("create_time")
    private LocalDateTime createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getActivityId() {
        return activityId;
    }

    public void setActivityId(Long activityId) {
        this.activityId = activityId;
    }

    public LocalDateTime getActivityStartTime() {
        return activityStartTime;
    }

    public void setActivityStartTime(LocalDateTime activityStartTime) {
        this.activityStartTime = activityStartTime;
    }

    public LocalDateTime getActivityEndTime() {
        return activityEndTime;
    }

    public void setActivityEndTime(LocalDateTime activityEndTime) {
        this.activityEndTime = activityEndTime;
    }

    public Integer getHasConflict() {
        return hasConflict;
    }

    public void setHasConflict(Integer hasConflict) {
        this.hasConflict = hasConflict;
    }

    public String getConflictCourseIds() {
        return conflictCourseIds;
    }

    public void setConflictCourseIds(String conflictCourseIds) {
        this.conflictCourseIds = conflictCourseIds;
    }

    public String getConflictDetails() {
        return conflictDetails;
    }

    public void setConflictDetails(String conflictDetails) {
        this.conflictDetails = conflictDetails;
    }

    public LocalDateTime getCheckTime() {
        return checkTime;
    }

    public void setCheckTime(LocalDateTime checkTime) {
        this.checkTime = checkTime;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}