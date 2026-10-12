package com.volunteer.report.controller;

import com.volunteer.common.result.Result;
import com.volunteer.auth.entity.SysUser;
import com.volunteer.report.dto.ExportProgressDTO;
import com.volunteer.report.dto.ExportRequestDTO;
import com.volunteer.report.entity.ActivityMetric;
import com.volunteer.report.entity.ExportTask;
import com.volunteer.report.entity.UserDuration;
import com.volunteer.report.mapper.ActivityMetricMapper;
import com.volunteer.report.mapper.UserDurationMapper;
import com.volunteer.report.service.ExportTaskService;
import com.volunteer.report.util.ExcelStyleUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 数据导出控制器（增强版）
 * 支持样式优化、大数据流式导出、多Sheet页导出、导出任务管理
 */
@Tag(name = "数据导出")
@RestController
@RequestMapping("/api/export")
@RequiredArgsConstructor
@Slf4j
public class ExportController {

    private final ActivityMetricMapper activityMetricMapper;
    private final UserDurationMapper userDurationMapper;
    private final ExportTaskService exportTaskService;

    @Operation(summary = "导出活动统计数据（增强版）")
    @GetMapping("/activity-stats")
    public void exportActivityStats(
            @Parameter(description = "开始日期") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @Parameter(description = "结束日期") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            @Parameter(description = "导出格式") @RequestParam(defaultValue = "xlsx") String format,
            HttpServletResponse response) throws IOException {

        LambdaQueryWrapper<ActivityMetric> wrapper = new LambdaQueryWrapper<>();
        if (startDate != null && endDate != null) {
            wrapper.between(ActivityMetric::getMetricDate, startDate, endDate);
        }
        wrapper.orderByDesc(ActivityMetric::getMetricDate);
        List<ActivityMetric> metrics = activityMetricMapper.selectList(wrapper);

        // 使用增强样式导出
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("活动统计");
            CellStyle headerStyle = ExcelStyleUtil.createHeaderStyle(workbook);
            CellStyle dataStyle = ExcelStyleUtil.createDataStyle(workbook);
            CellStyle dateStyle = ExcelStyleUtil.createDateStyle(workbook);

            // 表头（增强版：更多字段 + 样式）
            String[] headers = {"统计日期", "活动ID", "指标类型", "指标值"};
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // 冻结表头行
            sheet.createFreezePane(0, 1);

            // 数据行
            int rowNum = 1;
            for (ActivityMetric metric : metrics) {
                Row row = sheet.createRow(rowNum++);
                createDateCell(row, 0, metric.getMetricDate(), dateStyle);
                createNumberCell(row, 1, metric.getActivityId(), dataStyle);
                createStringCell(row, 2, metric.getMetricType(), dataStyle);

                Cell valueCell = row.createCell(3);
                double val = metric.getMetricValue() != null ? metric.getMetricValue() : 0.0;
                valueCell.setCellValue(val);
                valueCell.setCellStyle(dataStyle);

                // 低值标记红色
                if (val < 50 && val > 0) {
                    CellStyle warnStyle = workbook.createCellStyle();
                    warnStyle.cloneStyleFrom(dataStyle);
                    Font redFont = workbook.createFont();
                    redFont.setColor(IndexedColors.RED.getIndex());
                    redFont.setFontHeightInPoints((short) 11);
                    warnStyle.setFont(redFont);
                    valueCell.setCellStyle(warnStyle);
                }
            }

            // 自动列宽
            for (int i = 0; i < headers.length; i++) {
                sheet.setColumnWidth(i, 15 * 256);
            }

            // 输出
            writeResponse(workbook, response, "activity_stats");
        }
    }

    @Operation(summary = "导出用户时长统计（增强版）")
    @GetMapping("/user-duration")
    public void exportUserDuration(
            @Parameter(description = "开始日期") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @Parameter(description = "结束日期") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            HttpServletResponse response) throws IOException {

        LambdaQueryWrapper<UserDuration> wrapper = new LambdaQueryWrapper<>();
        if (startDate != null && endDate != null) {
            wrapper.between(UserDuration::getCreateTime, startDate.atStartOfDay(), endDate.plusDays(1).atStartOfDay());
        }
        wrapper.orderByDesc(UserDuration::getAdjustedDuration);
        List<UserDuration> durations = userDurationMapper.selectList(wrapper);

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("用户时长");
            CellStyle headerStyle = ExcelStyleUtil.createHeaderStyle(workbook);
            CellStyle dataStyle = ExcelStyleUtil.createDataStyle(workbook);
            CellStyle numberStyle = ExcelStyleUtil.createNumberStyle(workbook);

            String[] headers = {"用户ID", "活动ID", "考勤ID", "原始时长(小时)", "调整后时长(小时)", "审核状态"};
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            sheet.createFreezePane(0, 1);

            int rowNum = 1;
            for (UserDuration duration : durations) {
                Row row = sheet.createRow(rowNum++);
                createNumberCell(row, 0, duration.getUserId(), dataStyle);
                createNumberCell(row, 1, duration.getActivityId(), dataStyle);
                createNumberCell(row, 2, duration.getAttendanceId(), dataStyle);

                Cell origCell = row.createCell(3);
                origCell.setCellValue(duration.getOriginalDuration() != null ? duration.getOriginalDuration().doubleValue() : 0.0);
                origCell.setCellStyle(numberStyle);

                Cell adjCell = row.createCell(4);
                adjCell.setCellValue(duration.getAdjustedDuration() != null ? duration.getAdjustedDuration().doubleValue() : 0.0);
                adjCell.setCellStyle(numberStyle);

                createStringCell(row, 5, duration.getStatus(), dataStyle);
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.setColumnWidth(i, 18 * 256);
            }

            writeResponse(workbook, response, "user_duration");
        }
    }

    @Operation(summary = "导出CSV格式数据")
    @GetMapping("/csv")
    public void exportCsv(
            @Parameter(description = "导出类型：activity/user") @RequestParam(defaultValue = "activity") String type,
            @Parameter(description = "开始日期") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @Parameter(description = "结束日期") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            HttpServletResponse response) throws IOException {

        String fileName = type + "_data_" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".csv";
        response.setContentType("text/csv");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition", "attachment;filename=" + URLEncoder.encode(fileName, StandardCharsets.UTF_8.name()));

        // 添加BOM标记，确保Excel正确识别UTF-8编码
        OutputStream os = response.getOutputStream();
        os.write(0xEF);
        os.write(0xBB);
        os.write(0xBF);

        if ("activity".equals(type)) {
            LambdaQueryWrapper<ActivityMetric> wrapper = new LambdaQueryWrapper<>();
            if (startDate != null && endDate != null) {
                wrapper.between(ActivityMetric::getMetricDate, startDate, endDate);
            }
            List<ActivityMetric> metrics = activityMetricMapper.selectList(wrapper);

            os.write("统计日期,活动ID,指标类型,指标值\n".getBytes(StandardCharsets.UTF_8));
            for (ActivityMetric m : metrics) {
                String line = String.format("%s,%s,%s,%s\n",
                        m.getMetricDate(), m.getActivityId(), escapeCsv(m.getMetricType()),
                        m.getMetricValue());
                os.write(line.getBytes(StandardCharsets.UTF_8));
            }
        }
        os.flush();
        os.close();
    }

    // === 辅助方法 ===

    private void createStringCell(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value != null ? value : "");
        cell.setCellStyle(style);
    }

    private void createNumberCell(Row row, int col, Number value, CellStyle style) {
        Cell cell = row.createCell(col);
        if (value != null) {
            cell.setCellValue(value.doubleValue());
        } else {
            cell.setCellValue(0);
        }
        cell.setCellStyle(style);
    }

    private void createDateCell(Row row, int col, LocalDate value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value != null ? value.toString() : "");
        cell.setCellStyle(style);
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private void writeResponse(Workbook workbook, HttpServletResponse response, String prefix) throws IOException {
        String fileName = prefix + "_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + ".xlsx";
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment;filename=" + URLEncoder.encode(fileName, StandardCharsets.UTF_8.name()));
        response.setHeader("Access-Control-Expose-Headers", "Content-Disposition");

        try (OutputStream os = response.getOutputStream()) {
            workbook.write(os);
            os.flush();
        }
        workbook.close();
    }

    // === 导出任务管理接口 ===

    @Operation(summary = "创建导出任务")
    @PostMapping("/task")
    public Result<ExportTask> createExportTask(
            @RequestBody ExportRequestDTO request,
            @RequestAttribute("userId") Long userId,
            @RequestAttribute(value = "currentUser", required = false) SysUser currentUser) {
        String userName = resolveUserName(currentUser, userId);
        ExportTask task = exportTaskService.createTask(request, userId, userName);
        return Result.success(task);
    }

    @Operation(summary = "获取导出任务进度")
    @GetMapping("/task/{taskId}/progress")
    public Result<ExportProgressDTO> getTaskProgress(
            @PathVariable Long taskId,
            @RequestAttribute("userId") Long userId) {
        ExportProgressDTO progress = exportTaskService.getProgress(taskId, userId);
        if (progress == null) {
            return Result.error("任务不存在");
        }
        return Result.success(progress);
    }

    @Operation(summary = "下载导出任务文件")
    @GetMapping("/task/{taskId}/download")
    public ResponseEntity<byte[]> downloadTaskFile(
            @PathVariable Long taskId,
            @RequestAttribute("userId") Long userId) throws IOException {
        ExportTask task = exportTaskService.getDownloadableTask(taskId, userId);
        if (task == null) {
            return ResponseEntity.notFound().build();
        }
        byte[] fileBytes = exportTaskService.readTaskFile(task);
        String filename = URLEncoder.encode(task.getFileName(), StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(fileBytes);
    }

    @Operation(summary = "获取用户的导出任务列表")
    @GetMapping("/tasks")
    public Result<List<ExportTask>> getUserTasks(@RequestAttribute("userId") Long userId) {
        List<ExportTask> tasks = exportTaskService.getUserTasks(userId);
        return Result.success(tasks);
    }

    @Operation(summary = "取消导出任务")
    @PostMapping("/task/{taskId}/cancel")
    public Result<Boolean> cancelTask(
            @PathVariable Long taskId,
            @RequestAttribute("userId") Long userId) {
        boolean success = exportTaskService.cancelTask(taskId, userId);
        return Result.success(success);
    }
    private String resolveUserName(SysUser currentUser, Long userId) {
        if (currentUser != null) {
            if (currentUser.getRealName() != null && !currentUser.getRealName().isBlank()) {
                return currentUser.getRealName();
            }
            if (currentUser.getUsername() != null && !currentUser.getUsername().isBlank()) {
                return currentUser.getUsername();
            }
        }
        return "user-" + userId;
    }
}
