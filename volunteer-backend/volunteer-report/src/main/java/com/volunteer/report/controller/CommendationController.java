package com.volunteer.report.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.volunteer.auth.annotation.RequiresAccess;
import com.volunteer.common.result.PageResult;
import com.volunteer.common.result.Result;
import com.volunteer.report.entity.Commendation;
import com.volunteer.report.service.CommendationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * #5 表彰奖励管理 - 控制器
 */
@Tag(name = "表彰奖励管理")
@RestController
@RequestMapping("/api/commendation")
@RequiredArgsConstructor
@Slf4j
public class CommendationController {

    private final CommendationService commendationService;

    @Operation(summary = "分页查询表彰记录(用户)")
    @GetMapping("/page")
    public Result<PageResult<Commendation>> getCommendationPage(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String level,
            @RequestAttribute("userId") Long userId) {
        Page<Commendation> page = commendationService.getCommendationPage(pageNum, pageSize, userId, level);
        return Result.success(PageResult.of(page));
    }

    @Operation(summary = "分页查询表彰记录(管理员)")
    @GetMapping("/admin/page")
    @RequiresAccess("admin")
    public Result<PageResult<Commendation>> getCommendationPageAdmin(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String status) {
        Page<Commendation> page = commendationService.getCommendationPageAdmin(pageNum, pageSize, userId, status);
        return Result.success(PageResult.of(page));
    }

    @Operation(summary = "创建表彰记录")
    @PostMapping
    @RequiresAccess("admin")
    public Result<Commendation> createCommendation(@RequestBody Commendation commendation) {
        return Result.success(commendationService.createCommendation(commendation));
    }

    @Operation(summary = "审核通过表彰")
    @PutMapping("/{id}/approve")
    @RequiresAccess("admin")
    public Result<Void> approveCommendation(@PathVariable Long id, @RequestAttribute("userId") Long approverId) {
        commendationService.approveCommendation(id, approverId);
        return Result.success();
    }

    @Operation(summary = "拒绝表彰")
    @PutMapping("/{id}/reject")
    @RequiresAccess("admin")
    public Result<Void> rejectCommendation(@PathVariable Long id, @RequestBody String remark) {
        commendationService.rejectCommendation(id, remark);
        return Result.success();
    }

    @Operation(summary = "删除表彰记录")
    @DeleteMapping("/{id}")
    @RequiresAccess("admin")
    public Result<Void> deleteCommendation(@PathVariable Long id) {
        commendationService.deleteCommendation(id);
        return Result.success();
    }

    @Operation(summary = "获取表彰详情")
    @GetMapping("/{id}")
    public Result<Commendation> getCommendationById(@PathVariable Long id) {
        return Result.success(commendationService.getCommendationById(id));
    }
}
