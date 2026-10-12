package com.volunteer.report.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.volunteer.common.exception.BusinessException;
import com.volunteer.report.entity.Certification;
import com.volunteer.report.entity.TrainingCourse;
import com.volunteer.report.entity.TrainingRecord;
import com.volunteer.report.entity.UserCredit;
import com.volunteer.report.mapper.CertificationMapper;
import com.volunteer.report.mapper.TrainingCourseMapper;
import com.volunteer.report.mapper.TrainingRecordMapper;
import com.volunteer.report.mapper.UserCreditMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 培训认证服务
 * 提供在线学习、培训记录管理、证书颁发等功能
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrainingService {

    private final TrainingCourseMapper courseMapper;
    private final TrainingRecordMapper recordMapper;
    private final CertificationMapper certificationMapper;
    private final UserCreditMapper userCreditMapper;
    private final StarCertificationService starCertificationService;

    /**
     * 创建培训课程
     */
    @Transactional
    public TrainingCourse createCourse(TrainingCourse course, Long userId) {
        log.info("创建培训课程, userId: {}, title: {}", userId, course.getTitle());
        course.setStatus("ACTIVE");
        course.setViewCount(0);
        course.setCompletionCount(0);
        course.setCreatedBy(userId);
        course.setCreateTime(LocalDateTime.now());
        course.setUpdateTime(LocalDateTime.now());
        courseMapper.insert(course);
        return course;
    }

    /**
     * 更新培训课程
     */
    @Transactional
    public TrainingCourse updateCourse(Long id, TrainingCourse course) {
        log.info("更新培训课程, id: {}", id);
        TrainingCourse existing = courseMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("课程不存在");
        }
        
        course.setId(id);
        course.setViewCount(existing.getViewCount());
        course.setCompletionCount(existing.getCompletionCount());
        course.setCreatedBy(existing.getCreatedBy());
        course.setCreateTime(existing.getCreateTime());
        course.setUpdateTime(LocalDateTime.now());
        courseMapper.updateById(course);
        
        return courseMapper.selectById(id);
    }

    /**
     * 删除培训课程
     */
    @Transactional
    public void deleteCourse(Long id) {
        log.info("删除培训课程, id: {}", id);
        TrainingCourse course = courseMapper.selectById(id);
        if (course == null) {
            throw new BusinessException("课程不存在");
        }
        
        courseMapper.deleteById(id);
    }

    /**
     * 获取课程详情
     */
    public TrainingCourse getCourseById(Long id) {
        return courseMapper.selectById(id);
    }

    /**
     * 分页查询课程
     */
    public Page<TrainingCourse> getCoursePage(Integer pageNum, Integer pageSize, String category, String level) {
        Page<TrainingCourse> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<TrainingCourse> wrapper = new LambdaQueryWrapper<>();
        
        wrapper.eq(TrainingCourse::getStatus, "ACTIVE");
        
        if (category != null && !category.isEmpty()) {
            wrapper.eq(TrainingCourse::getCategory, category);
        }
        
        if (level != null && !level.isEmpty()) {
            wrapper.eq(TrainingCourse::getLevel, level);
        }
        
        wrapper.orderByDesc(TrainingCourse::getCreateTime);
        return courseMapper.selectPage(page, wrapper);
    }

    /**
     * 开始学习课程
     */
    @Transactional
    public TrainingRecord startCourse(Long userId, Long courseId) {
        log.info("开始学习课程, userId: {}, courseId: {}", userId, courseId);
        
        TrainingCourse course = courseMapper.selectById(courseId);
        if (course == null) {
            throw new BusinessException("课程不存在");
        }
        
        // 检查是否已有学习记录
        TrainingRecord existing = recordMapper.selectOne(
            new LambdaQueryWrapper<TrainingRecord>()
                .eq(TrainingRecord::getUserId, userId)
                .eq(TrainingRecord::getCourseId, courseId)
        );
        
        if (existing != null) {
            // 如果已完成，可以重新学习
            if ("COMPLETED".equals(existing.getStatus())) {
                existing.setStatus("IN_PROGRESS");
                existing.setProgress(0);
                existing.setStartTime(LocalDateTime.now());
                recordMapper.updateById(existing);
                return existing;
            }
            return existing;
        }
        
        TrainingRecord record = new TrainingRecord();
        record.setUserId(userId);
        record.setCourseId(courseId);
        record.setStatus("IN_PROGRESS");
        record.setProgress(0);
        record.setStartTime(LocalDateTime.now());
        record.setCreateTime(LocalDateTime.now());
        record.setUpdateTime(LocalDateTime.now());
        recordMapper.insert(record);
        
        // 更新课程浏览次数
        course.setViewCount(course.getViewCount() != null ? course.getViewCount() + 1 : 1);
        courseMapper.updateById(course);
        
        return record;
    }

    /**
     * 更新学习进度
     */
    @Transactional
    public TrainingRecord updateProgress(Long userId, Long courseId, Integer progress) {
        if (progress == null) {
            throw new BusinessException("Progress is required");
        }
        log.info("更新学习进度, userId: {}, courseId: {}, progress: {}", userId, courseId, progress);
        
        TrainingRecord record = recordMapper.selectOne(
            new LambdaQueryWrapper<TrainingRecord>()
                .eq(TrainingRecord::getUserId, userId)
                .eq(TrainingRecord::getCourseId, courseId)
        );
        
        if (record == null) {
            throw new BusinessException("学习记录不存在，请先开始学习");
        }
        
        record.setProgress(Math.min(100, Math.max(0, progress)));
        record.setUpdateTime(LocalDateTime.now());
        
        // 如果进度达到100%，标记为完成
        if (record.getProgress() >= 100) {
            completeCourse(record);
        }
        
        recordMapper.updateById(record);
        return record;
    }

    /**
     * 完成课程学习
     */
    @Transactional
    public TrainingRecord completeCourse(TrainingRecord record) {
        if ("COMPLETED".equals(record.getStatus())) {
            return record;
        }
        log.info("完成课程学习, userId: {}, courseId: {}", record.getUserId(), record.getCourseId());
        
        TrainingCourse course = courseMapper.selectById(record.getCourseId());
        if (course == null) {
            throw new BusinessException("Course does not exist");
        }
        
        record.setStatus("COMPLETED");
        record.setProgress(100);
        record.setCompletionTime(LocalDateTime.now());
        record.setUpdateTime(LocalDateTime.now());
        recordMapper.updateById(record);
        
        // 更新课程完成人数
        course.setCompletionCount(course.getCompletionCount() != null ? course.getCompletionCount() + 1 : 1);
        courseMapper.updateById(course);
        
        // 发放学分奖励
        grantCreditReward(record.getUserId(), course);
        
        // 颁发证书（如果课程有认证）
        if ("CERTIFIED".equals(course.getLevel())) {
            issueCertificate(record.getUserId(), course);
        }
        
        // 更新星级认证
        starCertificationService.updateStarLevel(record.getUserId());
        
        return record;
    }

    /**
     * 发放学分奖励
     */
    private void grantCreditReward(Long userId, TrainingCourse course) {
        UserCredit credit = userCreditMapper.selectOne(
            new LambdaQueryWrapper<UserCredit>().eq(UserCredit::getUserId, userId)
        );
        
        if (credit == null) {
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
        
        int reward = course.getCreditReward() != null ? course.getCreditReward().intValue() : 0;
        credit.setTotalCredit(safeInt(credit.getTotalCredit()) + reward);
        credit.setAvailableCredit(safeInt(credit.getAvailableCredit()) + reward);
        credit.setGrowthValue(safeInt(credit.getGrowthValue()) + reward * 2);
        credit.setUpdateTime(LocalDateTime.now());
        
        if (credit.getId() == null) {
            userCreditMapper.insert(credit);
        } else {
            userCreditMapper.updateById(credit);
        }
    }

    /**
     * 颁发证书
     */
    @Transactional
    public Certification issueCertificate(Long userId, TrainingCourse course) {
        log.info("颁发证书, userId: {}, course: {}", userId, course.getTitle());
        
        Certification certification = new Certification();
        certification.setUserId(userId);
        certification.setName(course.getTitle());
        certification.setCode(UUID.randomUUID().toString().toUpperCase().replace("-", ""));
        certification.setType(course.getCategory());
        certification.setIssuer("志愿者服务平台");
        certification.setDescription("完成" + course.getTitle() + "培训课程");
        certification.setIssueDate(LocalDateTime.now());
        certification.setStatus("ACTIVE");
        certification.setCreateTime(LocalDateTime.now());
        
        certificationMapper.insert(certification);
        
        // 更新用户证书数量
        UserCredit credit = userCreditMapper.selectOne(
            new LambdaQueryWrapper<UserCredit>().eq(UserCredit::getUserId, userId)
        );
        if (credit != null) {
            credit.setCertificateCount(credit.getCertificateCount() != null ? credit.getCertificateCount() + 1 : 1);
            userCreditMapper.updateById(credit);
        }
        
        return certification;
    }

    /**
     * 获取用户学习记录
     */
    public Page<TrainingRecord> getUserRecords(Long userId, Integer pageNum, Integer pageSize) {
        Page<TrainingRecord> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<TrainingRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TrainingRecord::getUserId, userId);
        wrapper.orderByDesc(TrainingRecord::getUpdateTime);
        return recordMapper.selectPage(page, wrapper);
    }

    /**
     * 获取用户证书列表
     */
    public Page<Certification> getUserCertifications(Long userId, Integer pageNum, Integer pageSize) {
        Page<Certification> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Certification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Certification::getUserId, userId);
        wrapper.eq(Certification::getStatus, "ACTIVE");
        wrapper.orderByDesc(Certification::getIssueDate);
        return certificationMapper.selectPage(page, wrapper);
    }

    /**
     * 获取课程分类列表
     */
    public java.util.List<String> getCourseCategories() {
        return java.util.List.of("基础培训", "专业技能", "安全培训", "管理培训", "心理健康");
    }
    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

}
