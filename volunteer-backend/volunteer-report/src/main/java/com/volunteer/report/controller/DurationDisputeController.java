package com.volunteer.report.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.volunteer.auth.annotation.RequiresAccess;
import com.volunteer.common.result.PageResult;
import com.volunteer.common.result.Result;
import com.volunteer.report.entity.DurationDispute;
import com.volunteer.report.service.DurationDisputeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

/**
 * #4 记录异议/修正工作流 - 控制器
 */
@Tag(name = "时长异议管理")
@RestController
@RequestMapping("/api/dispute")
@RequiredArgsConstructor
@Slf4j
public class DurationDisputeController {

    private final DurationDisputeService disputeService;

    @Operation(summary = "提交时长异议")
    @PostMapping
    @RequiresAccess(requireStudentId = true)
    public Result<DurationDispute> submitDispute(@RequestBody DurationDispute dispute,
                                                  @RequestAttribute("userId") Long userId) {
        dispute.setUserId(userId);
        return Result.success(disputeService.submitDispute(dispute));
    }

    @Operation(summary = "分页查询异议记录")
    @GetMapping("/page")
    public Result<PageResult<DurationDispute>> getDisputePage(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String status,
            @RequestAttribute("userId") Long userId) {
        Page<DurationDispute> page = disputeService.getDisputePage(pageNum, pageSize, userId, status);
        return Result.success(PageResult.of(page));
    }

    @Operation(summary = "获取异议详情")
    @GetMapping("/{id}")
    public Result<DurationDispute> getDisputeById(@PathVariable Long id) {
        return Result.success(disputeService.getDisputeById(id));
    }

    @Operation(summary = "审核异议(管理员)")
    @PutMapping("/{id}/review")
    @RequiresAccess("admin")
    public Result<Void> reviewDispute(@PathVariable Long id,
                                       @RequestBody Map<String, Object> body,
                                       @RequestAttribute("userId") Long reviewerId) {
        String status = (String) body.get("status");
        String comment = (String) body.get("comment");
        BigDecimal approvedDuration = body.get("approvedDuration") != null ?
                new BigDecimal(body.get("approvedDuration").toString()) : null;
        disputeService.reviewDispute(id, status, comment, approvedDuration, reviewerId);
        return Result.success();
    }
}