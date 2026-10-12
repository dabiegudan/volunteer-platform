package com.volunteer.report.dto;

import java.io.Serializable;
import java.util.Map;

/**
 * 志愿者画像DTO（雷达图数据）
 */
public class VolunteerProfileDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String userName;
    private Map<String, Double> skillScores;
    private Integer totalActivities;
    private Double totalDuration;
    private Double creditScore;

    public VolunteerProfileDTO() {
    }

    public VolunteerProfileDTO(String userName, Map<String, Double> skillScores, Integer totalActivities, Double totalDuration, Double creditScore) {
        this.userName = userName;
        this.skillScores = skillScores;
        this.totalActivities = totalActivities;
        this.totalDuration = totalDuration;
        this.creditScore = creditScore;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public Map<String, Double> getSkillScores() {
        return skillScores;
    }

    public void setSkillScores(Map<String, Double> skillScores) {
        this.skillScores = skillScores;
    }

    public Integer getTotalActivities() {
        return totalActivities;
    }

    public void setTotalActivities(Integer totalActivities) {
        this.totalActivities = totalActivities;
    }

    public Double getTotalDuration() {
        return totalDuration;
    }

    public void setTotalDuration(Double totalDuration) {
        this.totalDuration = totalDuration;
    }

    public Double getCreditScore() {
        return creditScore;
    }

    public void setCreditScore(Double creditScore) {
        this.creditScore = creditScore;
    }
}
