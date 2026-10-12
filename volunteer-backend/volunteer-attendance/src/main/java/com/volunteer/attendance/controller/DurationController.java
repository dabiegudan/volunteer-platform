package com.volunteer.attendance.controller;

import com.volunteer.attendance.service.AttendanceService;
import com.volunteer.auth.annotation.RequiresAccess;
import com.volunteer.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@Tag(name = "时长审核")
@RestController
@RequestMapping("/api/duration")
@RequiredArgsConstructor
public class DurationController {

    private final AttendanceService attendanceService;

    @Operation(summary = "审核时长")
    @PostMapping("/{id}/approve")
    @RequiresAccess("admin")
    public Result<Void> approveDuration(@PathVariable Long id,
                                        @RequestParam(required = false) BigDecimal adjustedHours,
                                        @RequestBody(required = false) Map<String, Object> body) {
        attendanceService.approveDuration(id, adjustedHours != null ? adjustedHours : readDecimal(body, "adjustedHours"));
        return Result.success();
    }

    private BigDecimal readDecimal(Map<String, Object> body, String key) {
        if (body == null || body.get(key) == null) {
            return null;
        }
        return new BigDecimal(String.valueOf(body.get(key)));
    }
}
