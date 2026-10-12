package com.volunteer.activity.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.volunteer.activity.entity.Activity;
import com.volunteer.activity.entity.ActivityTemplate;
import com.volunteer.activity.mapper.ActivityMapper;
import com.volunteer.activity.mapper.ActivityTemplateMapper;
import com.volunteer.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 活动模板服务
 * 提供活动模板的创建、查询、使用模板创建活动等功能
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityTemplateService {

    private final ActivityTemplateMapper templateMapper;
    private final ActivityMapper activityMapper;

    /**
     * 创建活动模板
     */
    @Transactional
    public ActivityTemplate createTemplate(ActivityTemplate template, Long userId) {
        log.info("创建活动模板, userId: {}, name: {}", userId, template.getName());
        template.setCreatedBy(userId);
        template.setStatus("ACTIVE");
        template.setCreateTime(LocalDateTime.now());
        template.setUpdateTime(LocalDateTime.now());
        templateMapper.insert(template);
        return template;
    }

    /**
     * 更新活动模板
     */
    @Transactional
    public ActivityTemplate updateTemplate(Long id, ActivityTemplate template, Long userId) {
        log.info("更新活动模板, id: {}, userId: {}", id, userId);
        ActivityTemplate existing = templateMapper.selectById(id);
        
        if (existing == null) {
            throw new BusinessException("模板不存在");
        }
        
        if (!existing.getCreatedBy().equals(userId)) {
            throw new BusinessException("只能修改自己创建的模板");
        }
        
        template.setId(id);
        template.setCreatedBy(existing.getCreatedBy());
        template.setStatus(existing.getStatus());
        template.setCreateTime(existing.getCreateTime());
        template.setUpdateTime(LocalDateTime.now());
        templateMapper.updateById(template);
        
        return templateMapper.selectById(id);
    }

    /**
     * 删除活动模板
     */
    @Transactional
    public void deleteTemplate(Long id, Long userId) {
        log.info("删除活动模板, id: {}, userId: {}", id, userId);
        ActivityTemplate template = templateMapper.selectById(id);
        
        if (template == null) {
            throw new BusinessException("模板不存在");
        }
        
        if (!template.getCreatedBy().equals(userId)) {
            throw new BusinessException("只能删除自己创建的模板");
        }
        
        templateMapper.deleteById(id);
    }

    /**
     * 获取活动模板详情
     */
    public ActivityTemplate getTemplateById(Long id) {
        return templateMapper.selectById(id);
    }

    /**
     * 分页查询活动模板
     */
    public Page<ActivityTemplate> getTemplatePage(Integer pageNum, Integer pageSize, String category, Long userId) {
        Page<ActivityTemplate> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<ActivityTemplate> wrapper = new LambdaQueryWrapper<>();
        
        wrapper.eq(ActivityTemplate::getStatus, "ACTIVE");
        
        if (category != null && !category.isEmpty()) {
            wrapper.eq(ActivityTemplate::getCategory, category);
        }
        
        if (userId != null) {
            wrapper.eq(ActivityTemplate::getCreatedBy, userId);
        }
        
        wrapper.orderByDesc(ActivityTemplate::getCreateTime);
        return templateMapper.selectPage(page, wrapper);
    }

    /**
     * 根据模板创建活动
     */
    @Transactional
    public Activity createActivityFromTemplate(Long templateId, Long userId, String title, 
                                               LocalDateTime startTime, LocalDateTime endTime,
                                               Integer maxParticipants) {
        log.info("根据模板创建活动, templateId: {}, userId: {}", templateId, userId);
        
        ActivityTemplate template = templateMapper.selectById(templateId);
        if (template == null) {
            throw new BusinessException("模板不存在");
        }
        
        Activity activity = new Activity();
        activity.setTitle(title);
        activity.setDescription(template.getDescription());
        activity.setCategory(template.getCategory());
        activity.setTags(template.getTags());
        activity.setStartTime(startTime);
        activity.setEndTime(endTime);
        activity.setLocation(template.getLocation());
        activity.setLocationGpsLat(template.getLocationGpsLat());
        activity.setLocationGpsLng(template.getLocationGpsLng());
        activity.setProvince(template.getProvince());
        activity.setCity(template.getCity());
        activity.setDistrict(template.getDistrict());
        activity.setStreet(template.getStreet());
        activity.setMaxParticipants(maxParticipants != null ? maxParticipants : template.getMaxParticipants());
        activity.setMinParticipants(template.getMinParticipants());
        activity.setCreditReward(template.getCreditReward());
        activity.setDurationHours(template.getDurationHours());
        activity.setCheckInRadius(template.getCheckInRadius());
        activity.setRequiresApproval(template.getRequiresApproval());
        activity.setOrganizationId(template.getOrganizationId());
        activity.setOrganizerId(userId);
        activity.setStatus("DRAFT");
        activity.setCurrentParticipants(0);
        activity.setCreatedBy(userId);
        activity.setUpdatedBy(userId);
        
        activityMapper.insert(activity);
        log.info("根据模板创建活动成功, activityId: {}, templateId: {}", activity.getId(), templateId);
        return activity;
    }

    /**
     * 获取用户创建的模板列表
     */
    public List<ActivityTemplate> getUserTemplates(Long userId) {
        return templateMapper.selectList(
            new LambdaQueryWrapper<ActivityTemplate>()
                .eq(ActivityTemplate::getCreatedBy, userId)
                .eq(ActivityTemplate::getStatus, "ACTIVE")
                .orderByDesc(ActivityTemplate::getCreateTime)
        );
    }

    /**
     * 获取热门模板列表（使用次数多的模板）
     */
    public List<ActivityTemplate> getHotTemplates(Integer limit) {
        // 简化实现，实际应基于使用次数排序
        Page<ActivityTemplate> page = new Page<>(1, limit);
        LambdaQueryWrapper<ActivityTemplate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ActivityTemplate::getStatus, "ACTIVE");
        wrapper.orderByDesc(ActivityTemplate::getCreateTime);
        return templateMapper.selectPage(page, wrapper).getRecords();
    }
}