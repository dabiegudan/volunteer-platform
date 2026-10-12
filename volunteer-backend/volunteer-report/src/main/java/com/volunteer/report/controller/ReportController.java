package com.volunteer.report.controller;

import com.volunteer.report.entity.OrganizationStatistic;
import com.volunteer.report.entity.UserDuration;
import com.volunteer.report.service.OrganizationReportService;
import com.volunteer.report.service.ReportService;
import com.volunteer.report.service.ReportSummaryService;
import com.volunteer.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Tag(name = "报表管理")
@RestController
@RequestMapping("/api/report")
@RequiredArgsConstructor
@Slf4j
public class ReportController {
    
    private final ReportService reportService;
    private final OrganizationReportService organizationReportService;
    private final ReportSummaryService reportSummaryService;
    
    @Operation(summary = "获取个人时长统计")
    @GetMapping("/duration")
    public Result<Map<String, Object>> getUserDurationReport(@RequestAttribute("userId") Long userId) {
        return Result.success(reportService.getUserDurationReport(userId));
    }

    @Operation(summary = "获取时长明细")
    @GetMapping("/duration/detail")
    public Result<List<UserDuration>> getUserDurationDetails(
            @RequestAttribute("userId") Long userId,
            @Parameter(description = "审核状态") @RequestParam(required = false) String status) {
        return Result.success(reportService.getUserDurationDetails(userId, status));
    }
    
    @Operation(summary = "AI生成报表摘要")
    @GetMapping("/summary")
    public Result<Map<String, Object>> getReportSummary(@RequestAttribute("userId") Long userId) {
        return Result.success(reportSummaryService.generateUserReportSummary(userId));
    }
    
    @Operation(summary = "导出考勤记录Excel")
    @GetMapping("/export/attendance")
    public ResponseEntity<byte[]> exportAttendanceExcel(@RequestAttribute("userId") Long userId) throws IOException {
        byte[] excelData = reportService.exportAttendanceExcel(userId);
        String filename = URLEncoder.encode("考勤记录.xlsx", StandardCharsets.UTF_8);
        
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(excelData);
    }
    
    @Operation(summary = "导出时长明细Excel")
    @GetMapping("/export/duration")
    public ResponseEntity<byte[]> exportDurationExcel(@RequestAttribute("userId") Long userId) throws IOException {
        byte[] excelData = reportService.exportDurationExcel(userId);
        String filename = URLEncoder.encode("时长明细.xlsx", StandardCharsets.UTF_8);
        
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(excelData);
    }
    
    @Operation(summary = "生成时长证明PDF")
    @GetMapping("/certificate")
    public ResponseEntity<byte[]> generateCertificate(@RequestAttribute("userId") Long userId) throws Exception {
        byte[] pdfData = reportService.generateDurationCertificate(userId);
        String filename = URLEncoder.encode("时长证明.pdf", StandardCharsets.UTF_8);
        
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfData);
    }
    
    @Operation(summary = "验证证书真伪")
    @GetMapping("/certificate/verify")
    public Result<Boolean> verifyCertificate(
            @RequestParam Long userId,
            @RequestParam String hash
    ) {
        return Result.success(reportService.verifyCertificate(userId, hash));
    }
    
    @Operation(summary = "获取所有组织统计")
    @GetMapping("/organization")
    public Result<List<OrganizationStatistic>> getOrganizationStatistics() {
        return Result.success(organizationReportService.getOrganizationStatistics());
    }
    
    @Operation(summary = "获取单个组织统计")
    @GetMapping("/organization/{id}")
    public Result<OrganizationStatistic> getOrganizationStatistic(
            @Parameter(description = "组织ID") @PathVariable Long id) {
        return Result.success(organizationReportService.getOrganizationStatistic(id));
    }
    
    @Operation(summary = "获取所有学院统计")
    @GetMapping("/college")
    public Result<List<Map<String, Object>>> getAllCollegeStatistics() {
        return Result.success(organizationReportService.getAllCollegeStatistics());
    }
    
    @Operation(summary = "获取单个学院统计")
    @GetMapping("/college/detail")
    public Result<Map<String, Object>> getCollegeStatistics(
            @Parameter(description = "学院名称") @RequestParam String collegeName) {
        return Result.success(organizationReportService.getCollegeStatistics(collegeName));
    }
}
