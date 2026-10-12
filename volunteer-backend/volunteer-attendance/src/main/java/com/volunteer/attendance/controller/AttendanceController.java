package com.volunteer.attendance.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.volunteer.attendance.entity.Attendance;
import com.volunteer.attendance.entity.ViolationRecord;
import com.volunteer.attendance.service.AttendanceService;
import com.volunteer.attendance.service.SupplementaryAnalysisService;
import com.volunteer.auth.annotation.RequiresAccess;
import com.volunteer.common.result.PageResult;
import com.volunteer.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "考勤管理")
@RestController
@RequestMapping("/api/attendance")
@RequiredArgsConstructor
@Slf4j
public class AttendanceController {
    
    private final AttendanceService attendanceService;
    private final SupplementaryAnalysisService supplementaryAnalysisService;
    
    @Operation(summary = "验证位置（按活动的真实签到坐标计算距离）")
    @PostMapping("/location/validate")
    @RequiresAccess(requireStudentId = true)
    public Result<Map<String, Object>> validateLocation(
            @RequestBody Map<String, Object> params,
            @RequestAttribute("userId") Long userId
    ) {
        if (params.get("activityId") == null) {
            return Result.error(400, "缺少参数 activityId");
        }
        String userGps = (String) params.get("userGps");
        Long activityId;
        try {
            activityId = Long.valueOf(params.get("activityId").toString().trim());
        } catch (NumberFormatException e) {
            return Result.error(400, "参数 activityId 必须为数字");
        }
        return Result.success(attendanceService.validateLocationForActivity(activityId, userGps));
    }
    
    @Operation(summary = "AI分析补签理由")
    @PostMapping("/supplementary/analyze")
    public Result<Map<String, Object>> analyzeSupplementary(
            @RequestBody Map<String, Object> params,
            @RequestAttribute("userId") Long userId
    ) {
        Long activityId = Long.valueOf(params.get("activityId").toString());
        String reason = (String) params.get("reason");
        return Result.success(supplementaryAnalysisService.analyzeSupplementaryReason(activityId, userId, reason));
    }
    
    @Operation(summary = "定位签到")
    @PostMapping("/checkin")
    public Result<Attendance> checkIn(
            @RequestBody Map<String, Object> params,
            @RequestAttribute("userId") Long userId
    ) {
        Long activityId = Long.valueOf(params.get("activityId").toString());
        String location = (String) params.get("location");
        String gps = (String) params.get("gps");
        return Result.success(attendanceService.checkIn(activityId, userId, location, gps));
    }
    
    @Operation(summary = "定位签退")
    @PostMapping("/checkout")
    public Result<Attendance> checkOut(
            @RequestBody Map<String, Object> params,
            @RequestAttribute("userId") Long userId
    ) {
        Long activityId = Long.valueOf(params.get("activityId").toString());
        String location = (String) params.get("location");
        String gps = (String) params.get("gps");
        return Result.success(attendanceService.checkOut(activityId, userId, location, gps));
    }
    
    @Operation(summary = "生成签到二维码")
    @PostMapping("/qrcode/{activityId}")
    @RequiresAccess("organizer")
    public Result<Map<String, Object>> generateQRCode(@PathVariable Long activityId) {
        return Result.success(attendanceService.generateQRCode(activityId));
    }
    
    @Operation(summary = "二维码签到")
    @PostMapping("/qrcode/checkin")
    public Result<Attendance> checkInByQRCode(
            @RequestBody Map<String, Object> params,
            @RequestAttribute("userId") Long userId
    ) {
        Long activityId = Long.valueOf(params.get("activityId").toString());
        String qrCodeToken = (String) params.get("qrCodeToken");
        String location = (String) params.get("location");
        String gps = (String) params.get("gps");
        return Result.success(attendanceService.checkInByQRCode(activityId, qrCodeToken, userId, location, gps));
    }
    
    @Operation(summary = "人脸识别签到")
    @PostMapping("/face/checkin")
    public Result<Attendance> checkInByFace(
            @RequestBody Map<String, Object> params,
            @RequestAttribute("userId") Long userId
    ) {
        Long activityId = Long.valueOf(params.get("activityId").toString());
        String faceData = (String) params.get("faceData");
        String location = (String) params.get("location");
        String gps = (String) params.get("gps");
        return Result.success(attendanceService.checkInByFace(activityId, userId, faceData, location, gps));
    }
    
    @Operation(summary = "NFC签到")
    @PostMapping("/nfc/checkin")
    public Result<Attendance> checkInByNFC(
            @RequestBody Map<String, Object> params,
            @RequestAttribute("userId") Long userId
    ) {
        Long activityId = Long.valueOf(params.get("activityId").toString());
        String nfcCardId = (String) params.get("nfcCardId");
        String location = (String) params.get("location");
        String gps = (String) params.get("gps");
        return Result.success(attendanceService.checkInByNFC(activityId, userId, nfcCardId, location, gps));
    }
    
    @Operation(summary = "蓝牙签到")
    @PostMapping("/bluetooth/checkin")
    public Result<Attendance> checkInByBluetooth(
            @RequestBody Map<String, Object> params,
            @RequestAttribute("userId") Long userId
    ) {
        Long activityId = Long.valueOf(params.get("activityId").toString());
        String bluetoothDeviceId = (String) params.get("bluetoothDeviceId");
        String location = (String) params.get("location");
        String gps = (String) params.get("gps");
        return Result.success(attendanceService.checkInByBluetooth(activityId, userId, bluetoothDeviceId, location, gps));
    }
    
    @Operation(summary = "申请补签")
    @PostMapping("/supplementary")
    public Result<Attendance> applySupplementary(
            @RequestBody Map<String, Object> params,
            @RequestAttribute("userId") Long userId
    ) {
        Long activityId = Long.valueOf(params.get("activityId").toString());
        String reason = (String) params.get("reason");
        String proof = (String) params.get("proof");
        return Result.success(attendanceService.applySupplementary(activityId, userId, reason, proof));
    }

    @Operation(summary = "获取活动考勤记录")
    @GetMapping("/activity/{activityId}")
    @RequiresAccess("organizer")
    public Result<PageResult<Attendance>> getActivityAttendances(
            @PathVariable Long activityId,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") @Min(1) Integer pageNum,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") @Min(1) @Max(100) Integer pageSize,
            @Parameter(description = "考勤状态") @RequestParam(required = false) String status) {
        Page<Attendance> page = attendanceService.getActivityAttendances(activityId, pageNum, pageSize, status);
        return Result.success(PageResult.of(page));
    }

    @Operation(summary = "通过补签")
    @PostMapping("/supplementary/{id}/approve")
    @RequiresAccess("organizer")
    public Result<Void> approveSupplementary(@PathVariable Long id) {
        attendanceService.approveSupplementary(id);
        return Result.success();
    }

    @Operation(summary = "拒绝补签")
    @PostMapping("/supplementary/{id}/reject")
    @RequiresAccess("organizer")
    public Result<Void> rejectSupplementary(@PathVariable Long id,
                                            @RequestParam(required = false) String reason,
                                            @RequestBody(required = false) Map<String, Object> body) {
        attendanceService.rejectSupplementary(id, reason != null ? reason : readText(body, "reason", "审核拒绝"));
        return Result.success();
    }
    
    @Operation(summary = "创建失信记录")
    @PostMapping("/violation")
    @RequiresAccess("admin")
    public Result<ViolationRecord> createViolation(
            @RequestBody Map<String, Object> params
    ) {
        Long userId = Long.valueOf(params.get("userId").toString());
        Long activityId = params.get("activityId") != null ? Long.valueOf(params.get("activityId").toString()) : null;
        String violationType = (String) params.get("violationType");
        String violationReason = (String) params.get("violationReason");
        String restrictionType = (String) params.get("restrictionType");
        int restrictionDays = params.get("restrictionDays") != null ? Integer.parseInt(params.get("restrictionDays").toString()) : 7;
        return Result.success(attendanceService.createViolation(userId, activityId, violationType, 
                violationReason, restrictionType, restrictionDays));
    }
    
    @Operation(summary = "解除限制")
    @PostMapping("/violation/{violationId}/release")
    @RequiresAccess("admin")
    public Result<Void> releaseRestriction(@PathVariable Long violationId) {
        attendanceService.releaseRestriction(violationId);
        return Result.success();
    }
    
    @Operation(summary = "获取用户失信记录")
    @GetMapping("/violation/user/{userId}")
    @RequiresAccess("admin")
    public Result<List<ViolationRecord>> getUserViolations(@PathVariable Long userId) {
        return Result.success(attendanceService.getUserViolations(userId));
    }

    @Operation(summary = "获取我的失信记录")
    @GetMapping("/violation/my")
    public Result<List<ViolationRecord>> getMyViolations(@RequestAttribute("userId") Long userId) {
        return Result.success(attendanceService.getUserViolations(userId));
    }

    @Operation(summary = "分页查询失信记录")
    @GetMapping("/violation/page")
    @RequiresAccess("admin")
    public Result<PageResult<ViolationRecord>> getViolationPage(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") @Min(1) Integer pageNum,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") @Min(1) @Max(100) Integer pageSize,
            @Parameter(description = "用户ID") @RequestParam(required = false) Long userId,
            @Parameter(description = "状态") @RequestParam(required = false) String status) {
        Page<ViolationRecord> page = attendanceService.getViolationPage(pageNum, pageSize, userId, status);
        return Result.success(PageResult.of(page));
    }
    
    @Operation(summary = "检查用户是否受限")
    @GetMapping("/restriction/check")
    public Result<Boolean> checkRestriction(@RequestAttribute("userId") Long userId) {
        return Result.success(attendanceService.isUserRestricted(userId));
    }

    /**
     * 登记签到凭据（人脸 / NFC / 蓝牙）。
     *
     * <p>新增接口的原因：人脸、NFC、蓝牙签到此前不校验任何凭据，任意值都能签到成功。
     * 修复后「凭据必须已登记且属于当前用户」，因此必须提供登记入口，否则这三条签到
     * 通道会整体不可用。登记是自助操作，只能登记到当前登录用户。</p>
     */
    @Operation(summary = "登记签到凭据(人脸/NFC/蓝牙)")
    @PostMapping("/credential/register")
    @RequiresAccess(requireStudentId = true)
    public Result<Map<String, Object>> registerCredential(
            @RequestBody Map<String, Object> params,
            @RequestAttribute("userId") Long userId
    ) {
        String type = params.get("credentialType") == null ? null : params.get("credentialType").toString();
        String value = params.get("credentialValue") == null ? null : params.get("credentialValue").toString();
        String displayName = params.get("displayName") == null ? null : params.get("displayName").toString();
        return Result.success(attendanceService.registerCredential(userId, type, value, displayName));
    }

    private String readText(Map<String, Object> body, String key, String fallback) {
        if (body == null || body.get(key) == null) {
            return fallback;
        }
        String value = String.valueOf(body.get(key));
        return value.isBlank() ? fallback : value;
    }
}
