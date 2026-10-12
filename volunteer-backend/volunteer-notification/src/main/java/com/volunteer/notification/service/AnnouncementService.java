package com.volunteer.notification.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.volunteer.common.exception.BusinessException;
import com.volunteer.notification.entity.SystemAnnouncement;
import com.volunteer.notification.mapper.SystemAnnouncementMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * #12 系统公告/全员广播 - 服务层
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AnnouncementService {

    private final SystemAnnouncementMapper announcementMapper;

    public Page<SystemAnnouncement> getAnnouncementPage(Integer pageNum, Integer pageSize, String type) {
        Page<SystemAnnouncement> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<SystemAnnouncement> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SystemAnnouncement::getStatus, 1);
        if (type != null && !type.isEmpty()) {
            wrapper.eq(SystemAnnouncement::getType, type);
        }
        wrapper.and(w -> w.isNull(SystemAnnouncement::getExpireTime)
                .or().gt(SystemAnnouncement::getExpireTime, LocalDateTime.now()));
        wrapper.orderByDesc(SystemAnnouncement::getIsTop)
               .orderByDesc(SystemAnnouncement::getPriority)
               .orderByDesc(SystemAnnouncement::getCreateTime);
        return announcementMapper.selectPage(page, wrapper);
    }

    public List<SystemAnnouncement> getActiveAnnouncements() {
        LambdaQueryWrapper<SystemAnnouncement> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SystemAnnouncement::getStatus, 1);
        wrapper.and(w -> w.isNull(SystemAnnouncement::getExpireTime)
                .or().gt(SystemAnnouncement::getExpireTime, LocalDateTime.now()));
        wrapper.orderByDesc(SystemAnnouncement::getIsTop)
               .orderByDesc(SystemAnnouncement::getPriority)
               .orderByDesc(SystemAnnouncement::getCreateTime);
        wrapper.last("LIMIT 10");
        return announcementMapper.selectList(wrapper);
    }

    @Transactional
    public SystemAnnouncement createAnnouncement(SystemAnnouncement announcement) {
        announcement.setStatus(1);
        announcement.setViewCount(0);
        announcement.setPublishTime(LocalDateTime.now());
        announcementMapper.insert(announcement);
        log.info("公告发布成功, title: {}", announcement.getTitle());
        return announcement;
    }

    @Transactional
    public void updateAnnouncement(SystemAnnouncement announcement) {
        SystemAnnouncement existing = announcementMapper.selectById(announcement.getId());
        if (existing == null) {
            throw new BusinessException("公告不存在");
        }
        announcementMapper.updateById(announcement);
        log.info("公告更新成功, id: {}", announcement.getId());
    }

    @Transactional
    public void deleteAnnouncement(Long id) {
        SystemAnnouncement announcement = announcementMapper.selectById(id);
        if (announcement == null) {
            throw new BusinessException("公告不存在");
        }
        announcement.setStatus(0);
        announcementMapper.updateById(announcement);
        log.info("公告已下架, id: {}", id);
    }

    @Transactional
    public void incrementViewCount(Long id) {
        SystemAnnouncement announcement = announcementMapper.selectById(id);
        if (announcement != null) {
            announcement.setViewCount((announcement.getViewCount() != null ? announcement.getViewCount() : 0) + 1);
            announcementMapper.updateById(announcement);
        }
    }

    public SystemAnnouncement getAnnouncementById(Long id) {
        incrementViewCount(id);
        return announcementMapper.selectById(id);
    }
}