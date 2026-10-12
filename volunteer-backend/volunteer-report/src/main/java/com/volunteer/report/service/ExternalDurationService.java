package com.volunteer.report.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.volunteer.common.exception.BusinessException;
import com.volunteer.report.entity.ExternalPlatformDuration;
import com.volunteer.report.entity.UserCredit;
import com.volunteer.report.mapper.ExternalPlatformDurationMapper;
import com.volunteer.report.mapper.UserCreditMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 跨平台时长对接服务
 * 处理从外部平台导入志愿服务时长
 */
@Service
@RequiredArgsConstructor
public class ExternalDurationService {

    private static final Logger log = LoggerFactory.getLogger(ExternalDurationService.class);

    private final ExternalPlatformDurationMapper durationMapper;
    private final UserCreditMapper userCreditMapper;
    private final TimeBankService timeBankService;
    private final StarCertificationService starCertificationService;

    private static final String[] SUPPORTED_PLATFORMS = {"CHINA_VOLUNTEER", "LOCAL_VOLUNTEER", "UNIVERSITY_VOLUNTEER"};

    /**
     * 导入外部平台时长
     */
    @Transactional
    public ExternalPlatformDuration importDuration(Long userId, String platformCode, String platformName,
                                                  BigDecimal durationHours, String activityName,
                                                  String activityDate, String certificateNumber) {
        validateImportPayload(platformCode, durationHours);
        log.info("导入外部平台时长, userId: {}, platform: {}, duration: {}", userId, platformName, durationHours);
        
        // 验证平台是否支持
        if (!isPlatformSupported(platformCode)) {
            throw new BusinessException("不支持的外部平台: " + platformCode);
        }
        
        // 创建导入记录
        ExternalPlatformDuration duration = new ExternalPlatformDuration();
        duration.setUserId(userId);
        duration.setPlatformCode(platformCode);
        duration.setPlatformName(platformName);
        duration.setDurationHours(durationHours);
        duration.setActivityName(activityName);
        duration.setActivityDate(activityDate);
        duration.setCertificateNumber(certificateNumber);
        duration.setStatus("PENDING");
        duration.setImportTime(LocalDateTime.now());
        
        durationMapper.insert(duration);
        log.info("外部平台时长导入成功, id: {}", duration.getId());
        return duration;
    }

    /**
     * 批量导入外部平台时长
     */
    @Transactional
    public int batchImportDuration(Long userId, List<ExternalPlatformDuration> durations) {
        int count = 0;
        for (ExternalPlatformDuration duration : durations) {
            try {
                validateImportPayload(duration.getPlatformCode(), duration.getDurationHours());
                duration.setUserId(userId);
                duration.setStatus("PENDING");
                duration.setImportTime(LocalDateTime.now());
                durationMapper.insert(duration);
                count++;
            } catch (Exception e) {
                log.warn("导入外部平台时长失败, activity: {}", duration.getActivityName(), e);
            }
        }
        return count;
    }

    /**
     * 审核导入的时长
     */
    @Transactional
    public void auditDuration(Long durationId, boolean approved, String rejectReason, Long auditorId) {
        log.info("审核外部平台时长, durationId: {}, approved: {}", durationId, approved);
        
        ExternalPlatformDuration duration = durationMapper.selectById(durationId);
        if (duration == null) {
            throw new BusinessException("导入记录不存在");
        }
        
        if (!"PENDING".equals(duration.getStatus())) {
            throw new BusinessException("记录状态不允许审核");
        }
        
        if (approved) {
            validatePositiveDuration(duration.getDurationHours());
            // 审核通过，更新用户时长和积分
            updateUserDuration(duration.getUserId(), duration.getDurationHours());
            
            duration.setStatus("APPROVED");
            log.info("外部平台时长审核通过, userId: {}, duration: {}", duration.getUserId(), duration.getDurationHours());
        } else {
            duration.setStatus("REJECTED");
            duration.setRejectReason(rejectReason);
            log.info("外部平台时长审核拒绝, durationId: {}", durationId);
        }
        
        duration.setAuditTime(LocalDateTime.now());
        duration.setAuditorId(auditorId);
        durationMapper.updateById(duration);
    }

    /**
     * 更新用户时长
     */
    private void updateUserDuration(Long userId, BigDecimal durationHours) {
        UserCredit credit = userCreditMapper.selectOne(
            new LambdaQueryWrapper<UserCredit>().eq(UserCredit::getUserId, userId)
        );
        
        if (credit == null) {
            // 创建新的信用记录
            credit = new UserCredit();
            credit.setUserId(userId);
            credit.setTotalCredit(0);
            credit.setAvailableCredit(0);
            credit.setFrozenCredit(0);
            credit.setGrowthValue(0);
            credit.setLevel("LV1");
            credit.setStarLevel(0);
            credit.setTotalServiceHours(0);
            credit.setConsecutiveVolunteerMonths(0);
            credit.setCompletedActivityCount(0);
            credit.setCertificateCount(0);
            credit.setCreateTime(LocalDateTime.now());
        }
        
        // 更新总服务时长
        int hours = durationHours.intValue();
        credit.setTotalServiceHours((credit.getTotalServiceHours() != null ? credit.getTotalServiceHours() : 0) + hours);
        
        // 更新成长值（每小时10成长值）
        credit.setGrowthValue((credit.getGrowthValue() != null ? credit.getGrowthValue() : 0) + hours * 10);
        
        // 更新信用积分（每小时1积分）
        credit.setTotalCredit((credit.getTotalCredit() != null ? credit.getTotalCredit() : 0) + hours);
        credit.setAvailableCredit((credit.getAvailableCredit() != null ? credit.getAvailableCredit() : 0) + hours);
        
        credit.setUpdateTime(LocalDateTime.now());
        
        if (credit.getId() == null) {
            userCreditMapper.insert(credit);
        } else {
            userCreditMapper.updateById(credit);
        }
        
        // 同步到时间银行（1小时=1TBC）
        timeBankService.deposit(userId, durationHours, null, "EXTERNAL_IMPORT");
        
        // 更新星级
        starCertificationService.updateStarLevel(userId);
    }

    /**
     * 分页查询导入记录
     */
    public Page<ExternalPlatformDuration> getImportRecords(Long userId, Integer pageNum, Integer pageSize, String status) {
        Page<ExternalPlatformDuration> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<ExternalPlatformDuration> wrapper = new LambdaQueryWrapper<>();
        
        if (userId != null) {
            wrapper.eq(ExternalPlatformDuration::getUserId, userId);
        }
        
        if (status != null && !status.isEmpty()) {
            wrapper.eq(ExternalPlatformDuration::getStatus, status);
        }
        
        wrapper.orderByDesc(ExternalPlatformDuration::getImportTime);
        return durationMapper.selectPage(page, wrapper);
    }

    /**
     * 获取支持的平台列表
     */
    public List<String> getSupportedPlatforms() {
        return List.of(SUPPORTED_PLATFORMS);
    }

    private boolean isPlatformSupported(String platformCode) {
        for (String supported : SUPPORTED_PLATFORMS) {
            if (supported.equalsIgnoreCase(platformCode)) {
                return true;
            }
        }
        return false;
    }

    private void validateImportPayload(String platformCode, BigDecimal durationHours) {
        if (!isPlatformSupported(platformCode)) {
            throw new BusinessException("Unsupported external platform: " + platformCode);
        }
        validatePositiveDuration(durationHours);
    }

    private void validatePositiveDuration(BigDecimal durationHours) {
        if (durationHours == null || durationHours.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Duration hours must be greater than zero");
        }
    }
}
