package com.volunteer.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("user_profile")
public class UserProfile {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    @TableField("user_id")
    private Long userId;
    
    private String interests;
    
    @TableField("preferred_areas")
    private String preferredAreas;
    
    @TableField("preferred_time_slots")
    private String preferredTimeSlots;
    
    @TableField("activity_history")
    private String activityHistory;
    
    @TableField("skill_tags")
    private String skillTags;
    
    @TableField("personality_type")
    private String personalityType;
    
    @TableField("availability_score")
    private Integer availabilityScore;
    
    @TableField("reliability_score")
    private Integer reliabilityScore;
    
    @TableField("create_time")
    private LocalDateTime createTime;
    
    @TableField("update_time")
    private LocalDateTime updateTime;

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

    public String getInterests() {
        return interests;
    }

    public void setInterests(String interests) {
        this.interests = interests;
    }

    public String getPreferredAreas() {
        return preferredAreas;
    }

    public void setPreferredAreas(String preferredAreas) {
        this.preferredAreas = preferredAreas;
    }

    public String getPreferredTimeSlots() {
        return preferredTimeSlots;
    }

    public void setPreferredTimeSlots(String preferredTimeSlots) {
        this.preferredTimeSlots = preferredTimeSlots;
    }

    public String getActivityHistory() {
        return activityHistory;
    }

    public void setActivityHistory(String activityHistory) {
        this.activityHistory = activityHistory;
    }

    public String getSkillTags() {
        return skillTags;
    }

    public void setSkillTags(String skillTags) {
        this.skillTags = skillTags;
    }

    public String getPersonalityType() {
        return personalityType;
    }

    public void setPersonalityType(String personalityType) {
        this.personalityType = personalityType;
    }

    public Integer getAvailabilityScore() {
        return availabilityScore;
    }

    public void setAvailabilityScore(Integer availabilityScore) {
        this.availabilityScore = availabilityScore;
    }

    public Integer getReliabilityScore() {
        return reliabilityScore;
    }

    public void setReliabilityScore(Integer reliabilityScore) {
        this.reliabilityScore = reliabilityScore;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}