package com.volunteer.report.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.volunteer.activity.entity.Activity;
import com.volunteer.activity.entity.ActivityRegistration;
import com.volunteer.activity.mapper.ActivityMapper;
import com.volunteer.activity.mapper.ActivityRegistrationMapper;
import com.volunteer.common.exception.BusinessException;
import com.volunteer.report.entity.ActivityReview;
import com.volunteer.report.entity.Feedback;
import com.volunteer.report.entity.VolunteerReview;
import com.volunteer.report.mapper.ActivityReviewMapper;
import com.volunteer.report.mapper.FeedbackMapper;
import com.volunteer.report.mapper.VolunteerReviewMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 评价与反馈服务
 * 提供活动评价、志愿者评价、用户反馈等功能
 */
@Service
@RequiredArgsConstructor
public class ReviewService {

    private static final Logger log = LoggerFactory.getLogger(ReviewService.class);

    private final ActivityReviewMapper activityReviewMapper;
    private final VolunteerReviewMapper volunteerReviewMapper;
    private final FeedbackMapper feedbackMapper;
    private final ActivityMapper activityMapper;
    private final ActivityRegistrationMapper registrationMapper;

    /**
     * 创建活动评价
     */
    @Transactional
    public ActivityReview createActivityReview(Long activityId, Long userId, Integer rating, 
                                              String content, String images, String tags) {
        if (rating == null) {
            throw new BusinessException("Rating is required");
        }
        log.info("创建活动评价, activityId: {}, userId: {}, rating: {}", activityId, userId, rating);
        
        // 验证评分范围
        if (rating < 1 || rating > 5) {
            throw new BusinessException("评分必须在1-5之间");
        }
        
        // 验证用户是否参与了活动
        Activity activity = activityMapper.selectById(activityId);
        validateReviewableActivity(activity);

        ActivityRegistration registration = registrationMapper.selectOne(
            new LambdaQueryWrapper<ActivityRegistration>()
                .eq(ActivityRegistration::getActivityId, activityId)
                .eq(ActivityRegistration::getUserId, userId)
                .eq(ActivityRegistration::getStatus, "APPROVED")
        );
        
        if (registration == null) {
            throw new BusinessException("只有已参与活动的用户才能评价");
        }
        
        // 检查是否已评价
        ActivityReview existing = activityReviewMapper.selectOne(
            new LambdaQueryWrapper<ActivityReview>()
                .eq(ActivityReview::getActivityId, activityId)
                .eq(ActivityReview::getUserId, userId)
        );
        
        if (existing != null) {
            throw new BusinessException("您已对该活动进行过评价");
        }
        
        ActivityReview review = new ActivityReview();
        review.setActivityId(activityId);
        review.setUserId(userId);
        review.setRating(rating);
        review.setContent(content);
        review.setImages(images);
        review.setTags(tags);
        review.setStatus("PUBLISHED");
        review.setCreateTime(LocalDateTime.now());
        review.setUpdateTime(LocalDateTime.now());
        
        activityReviewMapper.insert(review);
        return review;
    }

    /**
     * 更新活动评价
     */
    @Transactional
    public ActivityReview updateActivityReview(Long reviewId, Long userId, Integer rating, 
                                              String content, String images, String tags) {
        log.info("更新活动评价, reviewId: {}, userId: {}", reviewId, userId);
        
        ActivityReview review = activityReviewMapper.selectById(reviewId);
        if (review == null) {
            throw new BusinessException("评价不存在");
        }
        
        if (!review.getUserId().equals(userId)) {
            throw new BusinessException("只能修改自己的评价");
        }
        
        if (rating != null && (rating < 1 || rating > 5)) {
            throw new BusinessException("评分必须在1-5之间");
        }
        
        if (rating != null) {
            review.setRating(rating);
        }
        if (content != null) {
            review.setContent(content);
        }
        if (images != null) {
            review.setImages(images);
        }
        if (tags != null) {
            review.setTags(tags);
        }
        review.setUpdateTime(LocalDateTime.now());
        
        activityReviewMapper.updateById(review);
        return review;
    }

    /**
     * 删除活动评价
     */
    @Transactional
    public void deleteActivityReview(Long reviewId, Long userId) {
        log.info("删除活动评价, reviewId: {}, userId: {}", reviewId, userId);
        
        ActivityReview review = activityReviewMapper.selectById(reviewId);
        if (review == null) {
            throw new BusinessException("评价不存在");
        }
        
        if (!review.getUserId().equals(userId)) {
            throw new BusinessException("只能删除自己的评价");
        }
        
        activityReviewMapper.deleteById(reviewId);
    }

    /**
     * 分页查询活动评价
     */
    public Page<ActivityReview> getActivityReviews(Long activityId, Integer pageNum, Integer pageSize) {
        Page<ActivityReview> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<ActivityReview> wrapper = new LambdaQueryWrapper<>();
        
        if (activityId != null) {
            wrapper.eq(ActivityReview::getActivityId, activityId);
        }
        
        wrapper.eq(ActivityReview::getStatus, "PUBLISHED");
        wrapper.orderByDesc(ActivityReview::getCreateTime);
        return activityReviewMapper.selectPage(page, wrapper);
    }

    /**
     * 获取活动评分统计
     */
    public java.util.Map<String, Object> getActivityRatingStats(Long activityId) {
        java.util.Map<String, Object> stats = new java.util.HashMap<>();
        
        java.util.List<ActivityReview> reviews = activityReviewMapper.selectList(
            new LambdaQueryWrapper<ActivityReview>()
                .eq(ActivityReview::getActivityId, activityId)
                .eq(ActivityReview::getStatus, "PUBLISHED")
        );
        
        if (reviews.isEmpty()) {
            stats.put("averageRating", 0);
            stats.put("reviewCount", 0);
            stats.put("ratingDistribution", new int[6]);
            return stats;
        }
        
        int totalRating = reviews.stream().mapToInt(ActivityReview::getRating).sum();
        double averageRating = (double) totalRating / reviews.size();
        
        int[] distribution = new int[6];
        for (ActivityReview review : reviews) {
            distribution[review.getRating()]++;
        }
        
        stats.put("averageRating", Math.round(averageRating * 100.0) / 100.0);
        stats.put("reviewCount", reviews.size());
        stats.put("ratingDistribution", distribution);
        
        return stats;
    }

    /**
     * 创建志愿者评价
     */
    @Transactional
    public VolunteerReview createVolunteerReview(Long reviewedUserId, Long reviewerUserId, 
                                                 Long activityId, Integer rating, String content, String tags) {
        if (rating == null) {
            throw new BusinessException("Rating is required");
        }
        log.info("创建志愿者评价, reviewedUserId: {}, reviewerUserId: {}, rating: {}", reviewedUserId, reviewerUserId, rating);
        
        // 验证评分范围
        if (rating < 1 || rating > 5) {
            throw new BusinessException("评分必须在1-5之间");
        }
        
        // 不能评价自己
        if (reviewedUserId.equals(reviewerUserId)) {
            throw new BusinessException("不能评价自己");
        }
        
        // 验证活动存在
        Activity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            throw new BusinessException("活动不存在");
        }
        
        // 验证评价者是否参与了活动
        validateReviewableActivity(activity);

        ActivityRegistration reviewerReg = registrationMapper.selectOne(
            new LambdaQueryWrapper<ActivityRegistration>()
                .eq(ActivityRegistration::getActivityId, activityId)
                .eq(ActivityRegistration::getUserId, reviewerUserId)
                .eq(ActivityRegistration::getStatus, "APPROVED")
        );
        
        if (reviewerReg == null) {
            throw new BusinessException("只有活动参与者才能评价其他志愿者");
        }
        
        // 验证被评价者是否参与了活动
        ActivityRegistration reviewedReg = registrationMapper.selectOne(
            new LambdaQueryWrapper<ActivityRegistration>()
                .eq(ActivityRegistration::getActivityId, activityId)
                .eq(ActivityRegistration::getUserId, reviewedUserId)
                .eq(ActivityRegistration::getStatus, "APPROVED")
        );
        
        if (reviewedReg == null) {
            throw new BusinessException("被评价者未参与该活动");
        }
        
        // 检查是否已评价
        VolunteerReview existing = volunteerReviewMapper.selectOne(
            new LambdaQueryWrapper<VolunteerReview>()
                .eq(VolunteerReview::getReviewedUserId, reviewedUserId)
                .eq(VolunteerReview::getReviewerUserId, reviewerUserId)
                .eq(VolunteerReview::getActivityId, activityId)
        );
        
        if (existing != null) {
            throw new BusinessException("您已对该志愿者在此次活动中的表现进行过评价");
        }
        
        VolunteerReview review = new VolunteerReview();
        review.setReviewedUserId(reviewedUserId);
        review.setReviewerUserId(reviewerUserId);
        review.setActivityId(activityId);
        review.setRating(rating);
        review.setContent(content);
        review.setTags(tags);
        review.setStatus("PUBLISHED");
        review.setCreateTime(LocalDateTime.now());
        
        volunteerReviewMapper.insert(review);
        return review;
    }

    /**
     * 获取志愿者评价列表
     */
    public Page<VolunteerReview> getVolunteerReviews(Long reviewedUserId, Integer pageNum, Integer pageSize) {
        Page<VolunteerReview> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<VolunteerReview> wrapper = new LambdaQueryWrapper<>();
        
        if (reviewedUserId != null) {
            wrapper.eq(VolunteerReview::getReviewedUserId, reviewedUserId);
        }
        
        wrapper.eq(VolunteerReview::getStatus, "PUBLISHED");
        wrapper.orderByDesc(VolunteerReview::getCreateTime);
        return volunteerReviewMapper.selectPage(page, wrapper);
    }

    /**
     * 获取志愿者评分统计
     */
    public java.util.Map<String, Object> getVolunteerRatingStats(Long userId) {
        java.util.Map<String, Object> stats = new java.util.HashMap<>();
        
        java.util.List<VolunteerReview> reviews = volunteerReviewMapper.selectList(
            new LambdaQueryWrapper<VolunteerReview>()
                .eq(VolunteerReview::getReviewedUserId, userId)
                .eq(VolunteerReview::getStatus, "PUBLISHED")
        );
        
        if (reviews.isEmpty()) {
            stats.put("averageRating", 0);
            stats.put("reviewCount", 0);
            return stats;
        }
        
        int totalRating = reviews.stream().mapToInt(VolunteerReview::getRating).sum();
        double averageRating = (double) totalRating / reviews.size();
        
        stats.put("averageRating", Math.round(averageRating * 100.0) / 100.0);
        stats.put("reviewCount", reviews.size());
        
        return stats;
    }

    /**
     * 创建用户反馈
     */
    @Transactional
    public Feedback createFeedback(Long userId, String type, String title, String content, String images) {
        log.info("创建用户反馈, userId: {}, type: {}, title: {}", userId, type, title);
        
        Feedback feedback = new Feedback();
        feedback.setUserId(userId);
        feedback.setType(type);
        feedback.setTitle(title);
        feedback.setContent(content);
        feedback.setImages(images);
        feedback.setStatus("PENDING");
        feedback.setCreateTime(LocalDateTime.now());
        feedback.setUpdateTime(LocalDateTime.now());
        
        feedbackMapper.insert(feedback);
        return feedback;
    }

    /**
     * 回复反馈
     */
    @Transactional
    public Feedback replyFeedback(Long feedbackId, String reply, Long replyBy) {
        log.info("回复反馈, feedbackId: {}, replyBy: {}", feedbackId, replyBy);
        
        Feedback feedback = feedbackMapper.selectById(feedbackId);
        if (feedback == null) {
            throw new BusinessException("反馈不存在");
        }
        
        feedback.setReply(reply);
        feedback.setStatus("REPLIED");
        feedback.setReplyTime(LocalDateTime.now());
        feedback.setReplyBy(replyBy);
        feedback.setUpdateTime(LocalDateTime.now());
        
        feedbackMapper.updateById(feedback);
        return feedback;
    }

    /**
     * 关闭反馈
     */
    @Transactional
    public void closeFeedback(Long feedbackId) {
        log.info("关闭反馈, feedbackId: {}", feedbackId);
        
        Feedback feedback = feedbackMapper.selectById(feedbackId);
        if (feedback == null) {
            throw new BusinessException("反馈不存在");
        }
        
        feedback.setStatus("CLOSED");
        feedback.setUpdateTime(LocalDateTime.now());
        feedbackMapper.updateById(feedback);
    }

    /**
     * 分页查询反馈
     */
    public Page<Feedback> getFeedbackList(Integer pageNum, Integer pageSize, String status, Long userId) {
        Page<Feedback> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Feedback> wrapper = new LambdaQueryWrapper<>();
        
        if (status != null && !status.isEmpty()) {
            wrapper.eq(Feedback::getStatus, status);
        }
        
        if (userId != null) {
            wrapper.eq(Feedback::getUserId, userId);
        }
        
        wrapper.orderByDesc(Feedback::getCreateTime);
        return feedbackMapper.selectPage(page, wrapper);
    }

    /**
     * 获取反馈详情
     */
    public Feedback getFeedbackById(Long feedbackId) {
        return feedbackMapper.selectById(feedbackId);
    }
    private void validateReviewableActivity(Activity activity) {
        if (activity == null) {
            throw new BusinessException("Activity does not exist");
        }
        String status = activity.getStatus();
        if (!"COMPLETED".equals(status) && !"ARCHIVED".equals(status)) {
            throw new BusinessException("Activity can only be reviewed after completion");
        }
    }
}