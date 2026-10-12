package com.volunteer.report.controller;

import com.volunteer.auth.annotation.RequiresAccess;
import com.volunteer.common.result.PageResult;
import com.volunteer.common.result.Result;
import com.volunteer.report.entity.ActivityReview;
import com.volunteer.report.entity.Feedback;
import com.volunteer.report.entity.VolunteerReview;
import com.volunteer.report.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "评价反馈")
@RestController
@RequestMapping("/api/review")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @Operation(summary = "创建活动评价")
    @PostMapping("/activity/{activityId}")
    public Result<ActivityReview> createActivityReview(
            @PathVariable Long activityId,
            @RequestBody Map<String, Object> body,
            @RequestAttribute("userId") Long userId) {
        return Result.success(reviewService.createActivityReview(
                activityId,
                userId,
                intValue(body.get("rating"), null),
                stringValue(body.get("content")),
                stringValue(body.get("images")),
                stringValue(body.get("tags"))));
    }

    @Operation(summary = "更新活动评价")
    @PutMapping("/activity/{reviewId}")
    public Result<ActivityReview> updateActivityReview(
            @PathVariable Long reviewId,
            @RequestBody Map<String, Object> body,
            @RequestAttribute("userId") Long userId) {
        return Result.success(reviewService.updateActivityReview(
                reviewId,
                userId,
                intValue(body.get("rating"), null),
                stringValue(body.get("content")),
                stringValue(body.get("images")),
                stringValue(body.get("tags"))));
    }

    @Operation(summary = "删除活动评价")
    @DeleteMapping("/activity/{reviewId}")
    public Result<Void> deleteActivityReview(
            @PathVariable Long reviewId,
            @RequestAttribute("userId") Long userId) {
        reviewService.deleteActivityReview(reviewId, userId);
        return Result.success();
    }

    @Operation(summary = "分页查询活动评价")
    @GetMapping("/activity")
    public Result<PageResult<ActivityReview>> getActivityReviews(
            @RequestParam(required = false) Long activityId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(PageResult.of(reviewService.getActivityReviews(activityId, pageNum, pageSize)));
    }

    @Operation(summary = "获取活动评分统计")
    @GetMapping("/activity/{activityId}/stats")
    public Result<Map<String, Object>> getActivityRatingStats(@PathVariable Long activityId) {
        return Result.success(reviewService.getActivityRatingStats(activityId));
    }

    @Operation(summary = "创建志愿者评价")
    @PostMapping("/volunteer/{reviewedUserId}")
    public Result<VolunteerReview> createVolunteerReview(
            @PathVariable Long reviewedUserId,
            @RequestBody Map<String, Object> body,
            @RequestAttribute("userId") Long reviewerUserId) {
        return Result.success(reviewService.createVolunteerReview(
                reviewedUserId,
                reviewerUserId,
                longValue(body.get("activityId"), null),
                intValue(body.get("rating"), null),
                stringValue(body.get("content")),
                stringValue(body.get("tags"))));
    }

    @Operation(summary = "分页查询志愿者评价")
    @GetMapping("/volunteer")
    public Result<PageResult<VolunteerReview>> getVolunteerReviews(
            @RequestParam(required = false) Long reviewedUserId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(PageResult.of(reviewService.getVolunteerReviews(reviewedUserId, pageNum, pageSize)));
    }

    @Operation(summary = "获取志愿者评分统计")
    @GetMapping("/volunteer/{userId}/stats")
    public Result<Map<String, Object>> getVolunteerRatingStats(@PathVariable Long userId) {
        return Result.success(reviewService.getVolunteerRatingStats(userId));
    }

    @Operation(summary = "创建用户反馈")
    @PostMapping("/feedback")
    public Result<Feedback> createFeedback(
            @RequestBody Map<String, Object> body,
            @RequestAttribute("userId") Long userId) {
        return Result.success(reviewService.createFeedback(
                userId,
                stringValue(body.get("type")),
                stringValue(body.get("title")),
                stringValue(body.get("content")),
                stringValue(body.get("images"))));
    }

    @Operation(summary = "查询我的反馈")
    @GetMapping("/feedback")
    public Result<PageResult<Feedback>> getMyFeedback(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String status,
            @RequestAttribute("userId") Long userId) {
        return Result.success(PageResult.of(reviewService.getFeedbackList(pageNum, pageSize, status, userId)));
    }

    @Operation(summary = "管理员查询反馈")
    @GetMapping("/feedback/admin")
    @RequiresAccess("admin")
    public Result<PageResult<Feedback>> getFeedbackAdmin(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long userId) {
        return Result.success(PageResult.of(reviewService.getFeedbackList(pageNum, pageSize, status, userId)));
    }

    @Operation(summary = "获取反馈详情")
    @GetMapping("/feedback/{feedbackId}")
    public Result<Feedback> getFeedback(@PathVariable Long feedbackId) {
        return Result.success(reviewService.getFeedbackById(feedbackId));
    }

    @Operation(summary = "回复反馈")
    @PutMapping("/feedback/{feedbackId}/reply")
    @RequiresAccess("admin")
    public Result<Feedback> replyFeedback(
            @PathVariable Long feedbackId,
            @RequestBody Map<String, Object> body,
            @RequestAttribute("userId") Long replyBy) {
        return Result.success(reviewService.replyFeedback(feedbackId, stringValue(body.get("reply")), replyBy));
    }

    @Operation(summary = "关闭反馈")
    @PutMapping("/feedback/{feedbackId}/close")
    @RequiresAccess("admin")
    public Result<Void> closeFeedback(@PathVariable Long feedbackId) {
        reviewService.closeFeedback(feedbackId);
        return Result.success();
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private Long longValue(Object value, Long fallback) {
        if (value == null) {
            return fallback;
        }
        return Long.parseLong(value.toString());
    }

    private Integer intValue(Object value, Integer fallback) {
        if (value == null) {
            return fallback;
        }
        return Integer.parseInt(value.toString());
    }
}
