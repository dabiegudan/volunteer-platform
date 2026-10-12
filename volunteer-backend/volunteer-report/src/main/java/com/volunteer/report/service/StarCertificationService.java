package com.volunteer.report.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.volunteer.common.exception.BusinessException;
import com.volunteer.report.entity.UserCredit;
import com.volunteer.report.mapper.UserCreditMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 星级志愿者认证服务
 * 实现星级志愿者的认证和等级管理
 */
@Service
@RequiredArgsConstructor
public class StarCertificationService {

    private static final Logger log = LoggerFactory.getLogger(StarCertificationService.class);

    private final UserCreditMapper userCreditMapper;

    /**
     * 星级认证等级规则
     * 1星：累计服务时长≥50小时
     * 2星：累计服务时长≥100小时，连续服务≥3个月
     * 3星：累计服务时长≥300小时，连续服务≥6个月，完成活动≥20个
     * 4星：累计服务时长≥500小时，连续服务≥12个月，完成活动≥50个，获得证书≥3个
     * 5星：累计服务时长≥1000小时，连续服务≥24个月，完成活动≥100个，获得证书≥5个
     */
    
    /**
     * 获取用户当前星级
     */
    public int getStarLevel(Long userId) {
        UserCredit credit = userCreditMapper.selectOne(
            new LambdaQueryWrapper<UserCredit>().eq(UserCredit::getUserId, userId)
        );
        if (credit == null) {
            return 0;
        }
        return credit.getStarLevel() != null ? credit.getStarLevel() : 0;
    }

    /**
     * 计算用户可达到的星级
     */
    public int calculateStarLevel(UserCredit credit) {
        int totalHours = credit.getTotalServiceHours() != null ? credit.getTotalServiceHours() : 0;
        int consecutiveMonths = credit.getConsecutiveVolunteerMonths() != null ? credit.getConsecutiveVolunteerMonths() : 0;
        int completedActivities = credit.getCompletedActivityCount() != null ? credit.getCompletedActivityCount() : 0;
        int certificates = credit.getCertificateCount() != null ? credit.getCertificateCount() : 0;

        if (totalHours >= 1000 && consecutiveMonths >= 24 && completedActivities >= 100 && certificates >= 5) {
            return 5;
        } else if (totalHours >= 500 && consecutiveMonths >= 12 && completedActivities >= 50 && certificates >= 3) {
            return 4;
        } else if (totalHours >= 300 && consecutiveMonths >= 6 && completedActivities >= 20) {
            return 3;
        } else if (totalHours >= 100 && consecutiveMonths >= 3) {
            return 2;
        } else if (totalHours >= 50) {
            return 1;
        }
        return 0;
    }

    /**
     * 更新用户星级
     */
    @Transactional
    public int updateStarLevel(Long userId) {
        log.info("更新用户星级, userId: {}", userId);
        
        UserCredit credit = userCreditMapper.selectOne(
            new LambdaQueryWrapper<UserCredit>().eq(UserCredit::getUserId, userId)
        );
        
        if (credit == null) {
            throw new BusinessException("用户信用记录不存在");
        }
        
        int oldLevel = credit.getStarLevel() != null ? credit.getStarLevel() : 0;
        int newLevel = calculateStarLevel(credit);
        
        if (newLevel > oldLevel) {
            credit.setStarLevel(newLevel);
            credit.setStarCertificationStatus("CERTIFIED");
            credit.setLastStarCertificationTime(LocalDateTime.now());
            
            // 设置下次认证时间（6个月后）
            credit.setNextStarCertificationTime(LocalDateTime.now().plusMonths(6));
            
            userCreditMapper.updateById(credit);
            log.info("用户星级提升, userId: {}, oldLevel: {}, newLevel: {}", userId, oldLevel, newLevel);
        }
        
        return newLevel;
    }

    /**
     * 申请星级认证
     */
    @Transactional
    public int applyStarCertification(Long userId) {
        log.info("用户申请星级认证, userId: {}", userId);
        
        UserCredit credit = userCreditMapper.selectOne(
            new LambdaQueryWrapper<UserCredit>().eq(UserCredit::getUserId, userId)
        );
        
        if (credit == null) {
            throw new BusinessException("用户信用记录不存在");
        }
        
        // 检查是否在冷却期内
        if (credit.getNextStarCertificationTime() != null && 
            LocalDateTime.now().isBefore(credit.getNextStarCertificationTime())) {
            throw new BusinessException("距离下次认证时间还有" + 
                java.time.Duration.between(LocalDateTime.now(), credit.getNextStarCertificationTime()).toDays() + "天");
        }
        
        return updateStarLevel(userId);
    }

    /**
     * 获取星级认证信息
     */
    public UserCredit getStarCertificationInfo(Long userId) {
        return userCreditMapper.selectOne(
            new LambdaQueryWrapper<UserCredit>().eq(UserCredit::getUserId, userId)
        );
    }

    /**
     * 获取星级所需条件
     */
    public java.util.Map<String, Object> getStarRequirements(int targetLevel) {
        java.util.Map<String, Object> requirements = new java.util.HashMap<>();
        
        switch (targetLevel) {
            case 1:
                requirements.put("totalHours", 50);
                requirements.put("description", "累计服务时长≥50小时");
                break;
            case 2:
                requirements.put("totalHours", 100);
                requirements.put("consecutiveMonths", 3);
                requirements.put("description", "累计服务时长≥100小时，连续服务≥3个月");
                break;
            case 3:
                requirements.put("totalHours", 300);
                requirements.put("consecutiveMonths", 6);
                requirements.put("completedActivities", 20);
                requirements.put("description", "累计服务时长≥300小时，连续服务≥6个月，完成活动≥20个");
                break;
            case 4:
                requirements.put("totalHours", 500);
                requirements.put("consecutiveMonths", 12);
                requirements.put("completedActivities", 50);
                requirements.put("certificates", 3);
                requirements.put("description", "累计服务时长≥500小时，连续服务≥12个月，完成活动≥50个，获得证书≥3个");
                break;
            case 5:
                requirements.put("totalHours", 1000);
                requirements.put("consecutiveMonths", 24);
                requirements.put("completedActivities", 100);
                requirements.put("certificates", 5);
                requirements.put("description", "累计服务时长≥1000小时，连续服务≥24个月，完成活动≥100个，获得证书≥5个");
                break;
            default:
                requirements.put("description", "未知等级");
        }
        
        return requirements;
    }
}