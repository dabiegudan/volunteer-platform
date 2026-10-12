package com.volunteer.report.controller;

import com.volunteer.common.result.Result;
import com.volunteer.report.entity.UserCredit;
import com.volunteer.report.service.StarCertificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "星级认证")
@RestController
@RequestMapping("/api/star-certification")
@RequiredArgsConstructor
public class StarCertificationController {

    private final StarCertificationService starCertificationService;

    @Operation(summary = "获取我的星级")
    @GetMapping("/level")
    public Result<Integer> getStarLevel(@RequestAttribute("userId") Long userId) {
        return Result.success(starCertificationService.getStarLevel(userId));
    }

    @Operation(summary = "获取我的星级认证信息")
    @GetMapping("/info")
    public Result<UserCredit> getStarCertificationInfo(@RequestAttribute("userId") Long userId) {
        return Result.success(starCertificationService.getStarCertificationInfo(userId));
    }

    @Operation(summary = "申请星级认证")
    @PostMapping("/apply")
    public Result<Integer> applyStarCertification(@RequestAttribute("userId") Long userId) {
        return Result.success(starCertificationService.applyStarCertification(userId));
    }

    @Operation(summary = "刷新星级")
    @PostMapping("/refresh")
    public Result<Integer> refreshStarLevel(@RequestAttribute("userId") Long userId) {
        return Result.success(starCertificationService.updateStarLevel(userId));
    }

    @Operation(summary = "获取星级条件")
    @GetMapping("/requirements/{targetLevel}")
    public Result<Map<String, Object>> getStarRequirements(@PathVariable int targetLevel) {
        return Result.success(starCertificationService.getStarRequirements(targetLevel));
    }
}
