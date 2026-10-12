package com.volunteer.report.controller;

import com.volunteer.auth.annotation.RequiresAccess;
import com.volunteer.common.result.PageResult;
import com.volunteer.common.result.Result;
import com.volunteer.report.entity.Certification;
import com.volunteer.report.entity.TrainingCourse;
import com.volunteer.report.entity.TrainingRecord;
import com.volunteer.report.service.TrainingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "培训认证")
@RestController
@RequestMapping("/api/training")
@RequiredArgsConstructor
public class TrainingController {

    private final TrainingService trainingService;

    @Operation(summary = "分页查询培训课程")
    @GetMapping("/courses")
    public Result<PageResult<TrainingCourse>> getCourses(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String level) {
        return Result.success(PageResult.of(trainingService.getCoursePage(pageNum, pageSize, category, level)));
    }

    @Operation(summary = "获取培训课程详情")
    @GetMapping("/courses/{id}")
    public Result<TrainingCourse> getCourse(@PathVariable Long id) {
        return Result.success(trainingService.getCourseById(id));
    }

    @Operation(summary = "创建培训课程")
    @PostMapping("/courses")
    @RequiresAccess("organizer")
    public Result<TrainingCourse> createCourse(
            @RequestBody TrainingCourse course,
            @RequestAttribute("userId") Long userId) {
        return Result.success(trainingService.createCourse(course, userId));
    }

    @Operation(summary = "更新培训课程")
    @PutMapping("/courses/{id}")
    @RequiresAccess("organizer")
    public Result<TrainingCourse> updateCourse(@PathVariable Long id, @RequestBody TrainingCourse course) {
        return Result.success(trainingService.updateCourse(id, course));
    }

    @Operation(summary = "删除培训课程")
    @DeleteMapping("/courses/{id}")
    @RequiresAccess("organizer")
    public Result<Void> deleteCourse(@PathVariable Long id) {
        trainingService.deleteCourse(id);
        return Result.success();
    }

    @Operation(summary = "开始学习课程")
    @PostMapping("/courses/{courseId}/start")
    public Result<TrainingRecord> startCourse(
            @PathVariable Long courseId,
            @RequestAttribute("userId") Long userId) {
        return Result.success(trainingService.startCourse(userId, courseId));
    }

    @Operation(summary = "更新学习进度")
    @PutMapping("/courses/{courseId}/progress")
    public Result<TrainingRecord> updateProgress(
            @PathVariable Long courseId,
            @RequestBody Map<String, Object> body,
            @RequestAttribute("userId") Long userId) {
        return Result.success(trainingService.updateProgress(userId, courseId, intValue(body.get("progress"), 0)));
    }

    @Operation(summary = "完成课程学习")
    @PostMapping("/courses/{courseId}/complete")
    public Result<TrainingRecord> completeCourse(
            @PathVariable Long courseId,
            @RequestAttribute("userId") Long userId) {
        return Result.success(trainingService.updateProgress(userId, courseId, 100));
    }

    @Operation(summary = "获取我的学习记录")
    @GetMapping("/records")
    public Result<PageResult<TrainingRecord>> getRecords(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestAttribute("userId") Long userId) {
        return Result.success(PageResult.of(trainingService.getUserRecords(userId, pageNum, pageSize)));
    }

    @Operation(summary = "获取我的培训证书")
    @GetMapping("/certifications")
    public Result<PageResult<Certification>> getCertifications(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestAttribute("userId") Long userId) {
        return Result.success(PageResult.of(trainingService.getUserCertifications(userId, pageNum, pageSize)));
    }

    @Operation(summary = "获取课程分类")
    @GetMapping("/categories")
    public Result<List<String>> getCategories() {
        return Result.success(trainingService.getCourseCategories());
    }

    private int intValue(Object value, int fallback) {
        if (value == null) {
            return fallback;
        }
        return Integer.parseInt(value.toString());
    }
}
