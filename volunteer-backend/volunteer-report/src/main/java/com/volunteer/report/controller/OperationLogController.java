package com.volunteer.report.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.volunteer.auth.annotation.RequiresAccess;
import com.volunteer.common.entity.SysOperationLog;
import com.volunteer.common.mapper.SysOperationLogMapper;
import com.volunteer.common.result.PageResult;
import com.volunteer.common.result.Result;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * #15 操作审计日志 - 查询控制器
 */
@Tag(name = "操作审计日志")
@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
@Slf4j
public class OperationLogController {

    private final SysOperationLogMapper operationLogMapper;

    @Operation(summary = "分页查询操作日志(管理员)")
    @GetMapping("/page")
    @RequiresAccess("admin")
    public Result<PageResult<SysOperationLog>> getLogPage(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) Long operatorId,
            @RequestParam(required = false) String opType,
            @RequestParam(required = false) String opModule) {
        Page<SysOperationLog> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<SysOperationLog> wrapper = new LambdaQueryWrapper<>();
        if (operatorId != null) {
            wrapper.eq(SysOperationLog::getOperatorId, operatorId);
        }
        if (opType != null && !opType.isEmpty()) {
            wrapper.eq(SysOperationLog::getOpType, opType);
        }
        if (opModule != null && !opModule.isEmpty()) {
            wrapper.eq(SysOperationLog::getOpModule, opModule);
        }
        wrapper.orderByDesc(SysOperationLog::getOpTime);
        return Result.success(PageResult.of(operationLogMapper.selectPage(page, wrapper)));
    }
}
