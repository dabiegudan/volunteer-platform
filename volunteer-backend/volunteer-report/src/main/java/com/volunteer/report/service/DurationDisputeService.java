package com.volunteer.report.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.volunteer.common.exception.BusinessException;
import com.volunteer.report.entity.DurationDispute;
import com.volunteer.report.mapper.DurationDisputeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * #4 记录异议/修正工作流 - 服务层
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DurationDisputeService {

    private final DurationDisputeMapper disputeMapper;

    public Page<DurationDispute> getDisputePage(Integer pageNum, Integer pageSize, Long userId, String status) {
        Page<DurationDispute> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<DurationDispute> wrapper = new LambdaQueryWrapper<>();
        if (userId != null) {
            wrapper.eq(DurationDispute::getUserId, userId);
        }
        if (status != null && !status.isEmpty()) {
            wrapper.eq(DurationDispute::getStatus, status);
        }
        wrapper.orderByDesc(DurationDispute::getCreateTime);
        return disputeMapper.selectPage(page, wrapper);
    }

    @Transactional
    public DurationDispute submitDispute(DurationDispute dispute) {
        dispute.setDisputeNo(generateDisputeNo());
        dispute.setStatus("PENDING");
        disputeMapper.insert(dispute);
        log.info("时长异议提交成功, disputeNo: {}, userId: {}", dispute.getDisputeNo(), dispute.getUserId());
        return dispute;
    }

    @Transactional
    public void reviewDispute(Long disputeId, String status, String comment, BigDecimal approvedDuration, Long reviewerId) {
        DurationDispute dispute = disputeMapper.selectById(disputeId);
        if (dispute == null) {
            throw new BusinessException("异议记录不存在");
        }
        if (!"PENDING".equals(dispute.getStatus())) {
            throw new BusinessException("该异议已处理");
        }
        dispute.setStatus(status);
        dispute.setReviewComment(comment);
        dispute.setReviewerId(reviewerId);
        dispute.setReviewTime(LocalDateTime.now());
        if (approvedDuration != null) {
            dispute.setApprovedDuration(approvedDuration);
        }
        String trail = (dispute.getAuditTrail() != null ? dispute.getAuditTrail() + ";" : "")
                + "审核人:" + reviewerId + "|结果:" + status + "|时间:" + LocalDateTime.now();
        dispute.setAuditTrail(trail);
        disputeMapper.updateById(dispute);
        log.info("时长异议审核完成, disputeId: {}, status: {}", disputeId, status);
    }

    public DurationDispute getDisputeById(Long id) {
        return disputeMapper.selectById(id);
    }

    private String generateDisputeNo() {
        return "DSP-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-" +
               String.format("%04d", System.currentTimeMillis() % 10000);
    }
}