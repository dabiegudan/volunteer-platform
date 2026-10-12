package com.volunteer.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("user_credit")
public class UserCredit {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long userId;
    
    private Integer totalCredit;
    
    private Integer availableCredit;
    
    private Integer frozenCredit;
    
    private Integer growthValue;
    
    private String level;
    
    private Integer starLevel;
    
    private Integer totalServiceHours;
    
    private Integer consecutiveVolunteerMonths;
    
    private Integer completedActivityCount;
    
    private Integer certificateCount;
    
    private String starCertificationStatus;
    
    private LocalDateTime lastStarCertificationTime;
    
    private LocalDateTime nextStarCertificationTime;
    
    private LocalDateTime createTime;
    
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

    public Integer getTotalCredit() {
        return totalCredit;
    }

    public void setTotalCredit(Integer totalCredit) {
        this.totalCredit = totalCredit;
    }

    public Integer getAvailableCredit() {
        return availableCredit;
    }

    public void setAvailableCredit(Integer availableCredit) {
        this.availableCredit = availableCredit;
    }

    public Integer getFrozenCredit() {
        return frozenCredit;
    }

    public void setFrozenCredit(Integer frozenCredit) {
        this.frozenCredit = frozenCredit;
    }

    public Integer getGrowthValue() {
        return growthValue;
    }

    public void setGrowthValue(Integer growthValue) {
        this.growthValue = growthValue;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public Integer getStarLevel() {
        return starLevel;
    }

    public void setStarLevel(Integer starLevel) {
        this.starLevel = starLevel;
    }

    public Integer getTotalServiceHours() {
        return totalServiceHours;
    }

    public void setTotalServiceHours(Integer totalServiceHours) {
        this.totalServiceHours = totalServiceHours;
    }

    public Integer getConsecutiveVolunteerMonths() {
        return consecutiveVolunteerMonths;
    }

    public void setConsecutiveVolunteerMonths(Integer consecutiveVolunteerMonths) {
        this.consecutiveVolunteerMonths = consecutiveVolunteerMonths;
    }

    public Integer getCompletedActivityCount() {
        return completedActivityCount;
    }

    public void setCompletedActivityCount(Integer completedActivityCount) {
        this.completedActivityCount = completedActivityCount;
    }

    public Integer getCertificateCount() {
        return certificateCount;
    }

    public void setCertificateCount(Integer certificateCount) {
        this.certificateCount = certificateCount;
    }

    public String getStarCertificationStatus() {
        return starCertificationStatus;
    }

    public void setStarCertificationStatus(String starCertificationStatus) {
        this.starCertificationStatus = starCertificationStatus;
    }

    public LocalDateTime getLastStarCertificationTime() {
        return lastStarCertificationTime;
    }

    public void setLastStarCertificationTime(LocalDateTime lastStarCertificationTime) {
        this.lastStarCertificationTime = lastStarCertificationTime;
    }

    public LocalDateTime getNextStarCertificationTime() {
        return nextStarCertificationTime;
    }

    public void setNextStarCertificationTime(LocalDateTime nextStarCertificationTime) {
        this.nextStarCertificationTime = nextStarCertificationTime;
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