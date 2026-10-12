package com.volunteer.report.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.volunteer.common.exception.BusinessException;
import com.volunteer.report.entity.Commendation;
import com.volunteer.report.mapper.CommendationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * #5 表彰奖励管理 - 服务层
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CommendationService {

    private final CommendationMapper commendationMapper;

    public Page<Commendation> getCommendationPage(Integer pageNum, Integer pageSize, Long userId, String level) {
        Page<Commendation> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Commendation> wrapper = new LambdaQueryWrapper<>();
        if (userId != null) {
            wrapper.eq(Commendation::getUserId, userId);
        }
        if (level != null && !level.isEmpty()) {
            wrapper.eq(Commendation::getLevel, level);
        }
        wrapper.eq(Commendation::getStatus, "APPROVED");
        wrapper.orderByDesc(Commendation::getIssueDate);
        return commendationMapper.selectPage(page, wrapper);
    }

    public Page<Commendation> getCommendationPageAdmin(Integer pageNum, Integer pageSize, Long userId, String status) {
        Page<Commendation> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Commendation> wrapper = new LambdaQueryWrapper<>();
        if (userId != null) {
            wrapper.eq(Commendation::getUserId, userId);
        }
        if (status != null && !status.isEmpty()) {
            wrapper.eq(Commendation::getStatus, status);
        }
        wrapper.orderByDesc(Commendation::getCreateTime);
        return commendationMapper.selectPage(page, wrapper);
    }

    @Transactional
    public Commendation createCommendation(Commendation commendation) {
        commendation.setCommendationNo(generateCommendationNo());
        commendation.setStatus("PENDING");
        commendationMapper.insert(commendation);
        log.info("表彰记录创建成功, commendationNo: {}", commendation.getCommendationNo());
        return commendation;
    }

    @Transactional
    public void approveCommendation(Long id, Long approverId) {
        Commendation c = commendationMapper.selectById(id);
        if (c == null) {
            throw new BusinessException("表彰记录不存在");
        }
        c.setStatus("APPROVED");
        c.setApprovedBy(approverId);
        c.setApprovedAt(java.time.LocalDateTime.now());
        commendationMapper.updateById(c);
        log.info("表彰记录审核通过, id: {}", id);
    }

    @Transactional
    public void rejectCommendation(Long id, String remark) {
        Commendation c = commendationMapper.selectById(id);
        if (c == null) {
            throw new BusinessException("表彰记录不存在");
        }
        c.setStatus("REJECTED");
        c.setRemark(remark);
        commendationMapper.updateById(c);
        log.info("表彰记录已拒绝, id: {}", id);
    }

    @Transactional
    public void deleteCommendation(Long id) {
        commendationMapper.deleteById(id);
        log.info("表彰记录已删除, id: {}", id);
    }

    public Commendation getCommendationById(Long id) {
        return commendationMapper.selectById(id);
    }

    private String generateCommendationNo() {
        return "CMD-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-" +
               String.format("%04d", System.currentTimeMillis() % 10000);
    }
}