package com.volunteer.auth.controller;

import com.volunteer.auth.service.CaptchaService;
import com.volunteer.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "验证码", description = "滑块验证码接口")
@RestController
@RequestMapping("/api/captcha")
@RequiredArgsConstructor
@Slf4j
public class CaptchaController {
    
    private final CaptchaService captchaService;
    
    @Operation(summary = "生成滑块验证码")
    @GetMapping("/slider")
    public Result<Map<String, Object>> generateSliderCaptcha() {
        return Result.success(captchaService.generateCaptcha());
    }

    @Operation(summary = "生成验证码（兼容旧接口）")
    @GetMapping
    public Result<Map<String, Object>> generateCaptcha() {
        return Result.success(captchaService.generateCaptcha());
    }
    
    @Operation(summary = "验证滑块验证码")
    @PostMapping("/slider/verify")
    public Result<Boolean> verifySliderCaptcha(
            @Parameter(description = "验证码ID") @RequestParam(required = false) String captchaId,
            @Parameter(description = "滑块位置X") @RequestParam(required = false) Integer sliderX,
            @RequestBody(required = false) Map<String, Object> body) {
        return Result.success(doVerifyCaptcha(captchaId, sliderX, body));
    }

    @Operation(summary = "验证验证码（兼容旧接口）")
    @PostMapping("/verify")
    public Result<Boolean> verifyCaptchaLegacy(
            @Parameter(description = "验证码ID") @RequestParam(required = false) String captchaId,
            @Parameter(description = "滑块位置X") @RequestParam(required = false) Integer sliderX,
            @RequestBody(required = false) Map<String, Object> body) {
        return Result.success(doVerifyCaptcha(captchaId, sliderX, body));
    }

    private boolean doVerifyCaptcha(String captchaId, Integer sliderX, Map<String, Object> body) {
        String resolvedCaptchaId = captchaId;
        Integer resolvedSliderX = sliderX;
        if (body != null) {
            if (resolvedCaptchaId == null && body.get("captchaId") != null) {
                resolvedCaptchaId = String.valueOf(body.get("captchaId"));
            }
            if (resolvedSliderX == null && body.get("sliderX") != null) {
                resolvedSliderX = Integer.valueOf(String.valueOf(body.get("sliderX")));
            }
            if (resolvedSliderX == null && body.get("x") != null) {
                resolvedSliderX = Integer.valueOf(String.valueOf(body.get("x")));
            }
        }
        if (resolvedCaptchaId == null || resolvedSliderX == null) {
            return false;
        }
        return captchaService.verifyCaptcha(resolvedCaptchaId, resolvedSliderX);
    }
}
