package com.volunteer.report.controller;

import com.volunteer.auth.annotation.RequiresAccess;
import com.volunteer.common.result.PageResult;
import com.volunteer.common.result.Result;
import com.volunteer.report.entity.SystemConfig;
import com.volunteer.report.service.SystemConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "系统配置")
@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
public class ConfigController {

    private final SystemConfigService configService;

    @Operation(summary = "查询配置列表")
    @GetMapping("/page")
    @RequiresAccess("admin")
    public Result<PageResult<SystemConfig>> getConfigPage(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String configType) {
        return Result.success(PageResult.of(configService.getConfigPage(pageNum, pageSize, keyword, configType)));
    }

    @Operation(summary = "修改配置")
    @PutMapping("/{key}")
    @RequiresAccess("admin")
    public Result<SystemConfig> updateConfig(@PathVariable String key, @RequestBody SystemConfig payload) {
        return Result.success(configService.updateConfig(key, payload));
    }
}
