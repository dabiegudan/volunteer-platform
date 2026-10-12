package com.volunteer.report.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.volunteer.common.exception.BusinessException;
import com.volunteer.common.storage.FileStorage;
import com.volunteer.report.dto.ExportColumnDTO;
import com.volunteer.report.dto.ExportProgressDTO;
import com.volunteer.report.dto.ExportRequestDTO;
import com.volunteer.report.entity.ExportTask;
import com.volunteer.report.mapper.ExportTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExportTaskService extends ServiceImpl<ExportTaskMapper, ExportTask> {

    private static final String FORMAT_CSV = "csv";
    private static final String FORMAT_XLSX = "xlsx";

    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final FileStorage fileStorage;

    /** 导出产物的 MIME 类型。 */
    private static final String MIME_XLSX =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String MIME_CSV = "text/csv; charset=UTF-8";

    public ExportTask createTask(ExportRequestDTO request, Long userId, String userName) {
        if (request == null) {
            throw new BusinessException("Export request is required");
        }
        String exportType = normalizeExportType(request.getExportType());

        ExportTask task = new ExportTask();
        task.setTaskId(UUID.randomUUID().toString());
        task.setTaskName(request.getTaskName());
        task.setExportType(exportType);
        task.setStatus("PENDING");
        task.setProgress(0);
        try {
            task.setParams(objectMapper.writeValueAsString(request));
        } catch (Exception e) {
            log.error("Failed to serialize export request", e);
            task.setParams("{}");
        }
        task.setCreatedBy(userId);
        task.setCreatedByName(userName);
        task.setEnabled(1);
        task.setDeleted(0);
        this.save(task);

        // 异步执行：使用 Spring 托管的线程池（AsyncConfig 中的 exportTaskExecutor），
        // 不再自建 ExecutorService —— 便于统一配置队列容量、线程数与优雅停机。
        this.processExportAsync(task.getId());

        return task;
    }

    /**
     * 异步执行导出。
     *
     * <p>注意：必须通过代理调用（{@code this.processExportAsync(...)} 在同类中调用会绕过代理），
     * 因此这里把异步入口与实现分离：本方法只负责按 id 重新加载任务，
     * 实际逻辑在 {@link #processExport(ExportTask)} 中，由异步线程直接调用。
     *
     * @param taskId 任务主键
     */
    @Async("exportTaskExecutor")
    public void processExportAsync(Long taskId) {
        ExportTask task = this.getById(taskId);
        if (task == null) {
            log.warn("导出任务不存在, taskId={}", taskId);
            return;
        }
        if (!"PENDING".equals(task.getStatus())) {
            log.info("导出任务已被处理或取消, 跳过, taskId={}, status={}", taskId, task.getStatus());
            return;
        }
        processExport(task);
    }

    public void processExport(ExportTask task) {
        try {
            updateTaskStatus(task.getId(), "PROCESSING", 5, "Preparing export");

            ExportRequestDTO request = parseRequest(task);
            String exportType = normalizeExportType(firstNonBlank(request.getExportType(), task.getExportType()));
            String format = normalizeFormat(request.getFormat());
            ExportDataset dataset = buildDataset(exportType, request, task.getCreatedBy());

            updateTaskStatus(task.getId(), "PROCESSING", 70, "Writing export file");

            String fileName = exportType + "-" + task.getTaskId() + "." + format;
            byte[] content;
            if (FORMAT_CSV.equals(format)) {
                content = writeCsvToBytes(dataset);
            } else {
                content = writeXlsxToBytes(dataset, exportType);
            }

            // 产物写入对象存储，多副本共享、容器重建不丢失
            String contentType = FORMAT_CSV.equals(format) ? MIME_CSV : MIME_XLSX;
            String objectName = fileStorage.upload(
                    FileStorage.PREFIX_EXPORT, fileName, content, contentType);

            ExportTask fileUpdate = new ExportTask();
            fileUpdate.setId(task.getId());
            fileUpdate.setFileName(fileName);
            // filePath 保存对象存储中的完整对象名（含 export/ 前缀）
            fileUpdate.setFilePath(objectName);
            fileUpdate.setFileSize((long) content.length);
            this.updateById(fileUpdate);

            updateTaskStatus(task.getId(), "COMPLETED", 100, "Export completed");
        } catch (Exception e) {
            log.error("Export task processing failed", e);
            updateTaskStatus(task.getId(), "FAILED", 0, e.getMessage());
        }
    }

    public ExportProgressDTO getProgress(Long taskId) {
        ExportTask task = this.getById(taskId);
        if (task == null) {
            return null;
        }
        return buildProgress(task);
    }

    public ExportProgressDTO getProgress(Long taskId, Long userId) {
        ExportTask task = getOwnedTask(taskId, userId);
        if (task == null) {
            return null;
        }
        return buildProgress(task);
    }

    public List<ExportTask> getUserTasks(Long userId) {
        LambdaQueryWrapper<ExportTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ExportTask::getCreatedBy, userId)
                .orderByDesc(ExportTask::getCreateTime)
                .last("LIMIT 50");
        return this.list(wrapper);
    }

    public boolean cancelTask(Long taskId) {
        ExportTask task = this.getById(taskId);
        if (task == null) {
            return false;
        }
        return cancelTask(task);
    }

    public boolean cancelTask(Long taskId, Long userId) {
        ExportTask task = getOwnedTask(taskId, userId);
        if (task == null) {
            return false;
        }
        return cancelTask(task);
    }

    public ExportTask getDownloadableTask(Long taskId, Long userId) {
        ExportTask task = getOwnedTask(taskId, userId);
        if (task == null || !"COMPLETED".equals(task.getStatus())) {
            return null;
        }
        if (task.getFilePath() == null || task.getFilePath().isBlank()) {
            return null;
        }
        return task;
    }

    /**
     * 读取导出产物内容。
     *
     * <p>产物存放于对象存储（{@code export/} 前缀），因此任意实例都能读取，
     * 不再受「任务在哪个实例生成」的限制。
     *
     * @param task 已校验归属且状态为 COMPLETED 的导出任务
     * @return 文件字节内容
     */
    public byte[] readTaskFile(ExportTask task) throws IOException {
        String objectName = task.getFilePath();
        if (objectName == null || objectName.isBlank()) {
            throw new BusinessException("导出文件不存在或已被清理");
        }
        return fileStorage.download(objectName);
    }

    /**
     * 删除导出产物（用于任务取消或过期清理）。
     *
     * @param task 导出任务
     */
    public void deleteTaskFile(ExportTask task) {
        if (task == null || task.getFilePath() == null || task.getFilePath().isBlank()) {
            return;
        }
        fileStorage.delete(task.getFilePath());
    }

    private boolean cancelTask(ExportTask task) {
        if (!"PENDING".equals(task.getStatus()) && !"PROCESSING".equals(task.getStatus())) {
            return false;
        }

        ExportTask update = new ExportTask();
        update.setId(task.getId());
        update.setStatus("CANCELLED");
        update.setErrorMessage("User cancelled");

        return this.updateById(update);
    }

    private ExportProgressDTO buildProgress(ExportTask task) {
        ExportProgressDTO dto = new ExportProgressDTO();
        dto.setTaskId(task.getId());
        dto.setStatus(task.getStatus());
        dto.setProgress(task.getProgress());
        dto.setMessage(task.getErrorMessage());
        dto.setFileName(task.getFileName());
        if ("COMPLETED".equals(task.getStatus()) && task.getFileName() != null) {
            dto.setDownloadUrl("/api/export/task/" + task.getId() + "/download");
        }
        dto.setCreateTime(task.getCreateTime());
        dto.setCompleteTime(task.getCompleteTime());
        return dto;
    }

    private void updateTaskStatus(Long taskId, String status, Integer progress, String message) {
        ExportTask update = new ExportTask();
        update.setId(taskId);
        update.setStatus(status);
        update.setProgress(progress);

        if ("PROCESSING".equals(status) && progress != null && progress <= 5) {
            update.setStartTime(LocalDateTime.now());
        }

        if ("COMPLETED".equals(status) || "FAILED".equals(status) || "CANCELLED".equals(status)) {
            update.setCompleteTime(LocalDateTime.now());
            if (!"COMPLETED".equals(status)) {
                update.setErrorMessage(message);
            }
        }

        this.updateById(update);
    }

    private ExportDataset buildDataset(String exportType, ExportRequestDTO request, Long creatorId) {
        List<ExportField> fields = selectFields(exportType, request);
        List<Map<String, Object>> rows;
        switch (exportType) {
            case "activity-stats" -> rows = queryActivityStats(request);
            case "user-duration" -> rows = queryUserDuration(request, creatorId);
            case "attendance" -> rows = queryAttendance(request, creatorId);
            case "credit" -> rows = queryCreditTransactions(request, creatorId);
            default -> throw new BusinessException("Unsupported export type: " + exportType);
        }
        return new ExportDataset(fields, rows);
    }

    private List<Map<String, Object>> queryActivityStats(ExportRequestDTO request) {
        StringBuilder sql = new StringBuilder("""
                SELECT am.metric_date AS metricDate,
                       am.activity_id AS activityId,
                       am.metric_type AS metricType,
                       am.metric_value AS metricValue
                FROM activity_metric am
                """);
        List<Object> params = new ArrayList<>();
        if (request.getOrgId() != null) {
            sql.append(" JOIN activity a ON a.id = am.activity_id AND a.deleted = 0 ");
        }
        sql.append(" WHERE 1 = 1 ");
        addDateFilter(sql, params, "am.metric_date", request.getStartTime(), request.getEndTime());
        if (request.getOrgId() != null) {
            sql.append(" AND (a.organization_id = ? OR a.college_id = ?) ");
            params.add(request.getOrgId());
            params.add(request.getOrgId());
        }
        sql.append(" ORDER BY am.metric_date DESC, am.activity_id DESC LIMIT 10000");
        return jdbcTemplate.queryForList(sql.toString(), params.toArray());
    }

    private List<Map<String, Object>> queryUserDuration(ExportRequestDTO request, Long creatorId) {
        StringBuilder sql = new StringBuilder("""
                SELECT user_id AS userId,
                       activity_id AS activityId,
                       attendance_id AS attendanceId,
                       original_duration AS originalDuration,
                       adjusted_duration AS adjustedDuration,
                       status AS status,
                       create_time AS createTime
                FROM user_duration
                WHERE user_id = ?
                """);
        List<Object> params = new ArrayList<>();
        params.add(creatorId);
        addDateTimeFilter(sql, params, "create_time", request.getStartTime(), request.getEndTime());
        sql.append(" ORDER BY create_time DESC LIMIT 10000");
        return jdbcTemplate.queryForList(sql.toString(), params.toArray());
    }

    private List<Map<String, Object>> queryAttendance(ExportRequestDTO request, Long creatorId) {
        StringBuilder sql = new StringBuilder("""
                SELECT activity_id AS activityId,
                       check_in_time AS checkInTime,
                       check_out_time AS checkOutTime,
                       check_in_mode AS checkInMode,
                       status AS status,
                       duration_hours AS durationHours
                FROM attendance
                WHERE user_id = ?
                """);
        List<Object> params = new ArrayList<>();
        params.add(creatorId);
        addDateTimeFilter(sql, params, "create_time", request.getStartTime(), request.getEndTime());
        sql.append(" ORDER BY create_time DESC LIMIT 10000");
        return jdbcTemplate.queryForList(sql.toString(), params.toArray());
    }

    private List<Map<String, Object>> queryCreditTransactions(ExportRequestDTO request, Long creatorId) {
        StringBuilder sql = new StringBuilder("""
                SELECT amount AS amount,
                       type AS type,
                       source AS source,
                       description AS description,
                       balance_before AS balanceBefore,
                       balance_after AS balanceAfter,
                       create_time AS createTime
                FROM credit_transaction
                WHERE user_id = ?
                """);
        List<Object> params = new ArrayList<>();
        params.add(creatorId);
        addDateTimeFilter(sql, params, "create_time", request.getStartTime(), request.getEndTime());
        sql.append(" ORDER BY create_time DESC LIMIT 10000");
        return jdbcTemplate.queryForList(sql.toString(), params.toArray());
    }

    private List<ExportField> selectFields(String exportType, ExportRequestDTO request) {
        List<ExportField> defaults = defaultFields(exportType);
        if (request.getColumns() == null || request.getColumns().isEmpty()) {
            return defaults;
        }

        Map<String, ExportField> byKey = defaults.stream()
                .collect(Collectors.toMap(ExportField::key, field -> field));
        List<ExportColumnDTO> requested = request.getColumns().stream()
                .filter(column -> column.getKey() != null && !Boolean.FALSE.equals(column.getEnabled()))
                .sorted(Comparator.comparing(column -> column.getOrder() == null ? Integer.MAX_VALUE : column.getOrder()))
                .toList();

        List<ExportField> selected = new ArrayList<>();
        for (ExportColumnDTO column : requested) {
            ExportField defaultField = byKey.get(column.getKey());
            if (defaultField != null) {
                selected.add(new ExportField(defaultField.key(),
                        firstNonBlank(column.getLabel(), defaultField.label())));
            }
        }
        return selected.isEmpty() ? defaults : selected;
    }

    private List<ExportField> defaultFields(String exportType) {
        Map<String, List<ExportField>> fields = new HashMap<>();
        fields.put("activity-stats", List.of(
                new ExportField("metricDate", "Metric Date"),
                new ExportField("activityId", "Activity ID"),
                new ExportField("metricType", "Metric Type"),
                new ExportField("metricValue", "Metric Value")
        ));
        fields.put("user-duration", List.of(
                new ExportField("userId", "User ID"),
                new ExportField("activityId", "Activity ID"),
                new ExportField("attendanceId", "Attendance ID"),
                new ExportField("originalDuration", "Original Duration"),
                new ExportField("adjustedDuration", "Adjusted Duration"),
                new ExportField("status", "Status"),
                new ExportField("createTime", "Create Time")
        ));
        fields.put("attendance", List.of(
                new ExportField("activityId", "Activity ID"),
                new ExportField("checkInTime", "Check In Time"),
                new ExportField("checkOutTime", "Check Out Time"),
                new ExportField("checkInMode", "Check In Mode"),
                new ExportField("status", "Status"),
                new ExportField("durationHours", "Duration Hours")
        ));
        fields.put("credit", List.of(
                new ExportField("amount", "Amount"),
                new ExportField("type", "Type"),
                new ExportField("source", "Source"),
                new ExportField("description", "Description"),
                new ExportField("balanceBefore", "Balance Before"),
                new ExportField("balanceAfter", "Balance After"),
                new ExportField("createTime", "Create Time")
        ));
        List<ExportField> selected = fields.get(exportType);
        if (selected == null) {
            throw new BusinessException("Unsupported export type: " + exportType);
        }
        return selected;
    }

    /**
     * 生成 CSV 内容（含 UTF-8 BOM，保证 Excel 打开不乱码）。
     *
     * <p>返回字节数组而非写入文件：产物统一交由 {@link FileStorage} 写入对象存储。
     */
    private byte[] writeCsvToBytes(ExportDataset dataset) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (BufferedWriter writer = new BufferedWriter(
                new java.io.OutputStreamWriter(buffer, StandardCharsets.UTF_8))) {
            writer.write('\ufeff');
            writer.write(dataset.fields().stream().map(ExportField::label).map(this::escapeCsv)
                    .collect(java.util.stream.Collectors.joining(",")));
            writer.newLine();
            for (Map<String, Object> row : dataset.rows()) {
                writer.write(dataset.fields().stream()
                        .map(field -> escapeCsv(formatValue(row.get(field.key()))))
                        .collect(java.util.stream.Collectors.joining(",")));
                writer.newLine();
            }
        }
        return buffer.toByteArray();
    }

    /**
     * 生成 XLSX 内容。
     *
     * <p>返回字节数组而非写入文件：产物统一交由 {@link FileStorage} 写入对象存储。
     */
    private byte[] writeXlsxToBytes(ExportDataset dataset, String sheetName) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(sheetName.length() > 31 ? sheetName.substring(0, 31) : sheetName);
            Row header = sheet.createRow(0);
            for (int i = 0; i < dataset.fields().size(); i++) {
                header.createCell(i).setCellValue(dataset.fields().get(i).label());
            }
            for (int rowIndex = 0; rowIndex < dataset.rows().size(); rowIndex++) {
                Row row = sheet.createRow(rowIndex + 1);
                Map<String, Object> data = dataset.rows().get(rowIndex);
                for (int colIndex = 0; colIndex < dataset.fields().size(); colIndex++) {
                    Cell cell = row.createCell(colIndex);
                    setCellValue(cell, data.get(dataset.fields().get(colIndex).key()));
                }
            }
            for (int i = 0; i < dataset.fields().size(); i++) {
                sheet.autoSizeColumn(i);
            }
            workbook.write(buffer);
        }
        return buffer.toByteArray();
    }

    private void setCellValue(Cell cell, Object value) {
        if (value == null) {
            cell.setCellValue("");
        } else if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
        } else if (value instanceof java.util.Date date) {
            cell.setCellValue(date.toString());
        } else if (value instanceof LocalDateTime dateTime) {
            cell.setCellValue(dateTime.toString());
        } else if (value instanceof LocalDate date) {
            cell.setCellValue(date.toString());
        } else {
            cell.setCellValue(String.valueOf(value));
        }
    }

    private String formatValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.stripTrailingZeros().toPlainString();
        }
        return String.valueOf(value);
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private ExportRequestDTO parseRequest(ExportTask task) throws IOException {
        if (task.getParams() == null || task.getParams().isBlank()) {
            return new ExportRequestDTO();
        }
        return objectMapper.readValue(task.getParams(), ExportRequestDTO.class);
    }

    private void addDateFilter(StringBuilder sql, List<Object> params, String column, String start, String end) {
        LocalDate startDate = parseDate(start);
        LocalDate endDate = parseDate(end);
        if (startDate != null) {
            sql.append(" AND ").append(column).append(" >= ? ");
            params.add(Date.valueOf(startDate));
        }
        if (endDate != null) {
            sql.append(" AND ").append(column).append(" <= ? ");
            params.add(Date.valueOf(endDate));
        }
    }

    private void addDateTimeFilter(StringBuilder sql, List<Object> params, String column, String start, String end) {
        LocalDate startDate = parseDate(start);
        LocalDate endDate = parseDate(end);
        if (startDate != null) {
            sql.append(" AND ").append(column).append(" >= ? ");
            params.add(Timestamp.valueOf(startDate.atStartOfDay()));
        }
        if (endDate != null) {
            sql.append(" AND ").append(column).append(" < ? ");
            params.add(Timestamp.valueOf(endDate.plusDays(1).atStartOfDay()));
        }
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > 10) {
            normalized = normalized.substring(0, 10);
        }
        try {
            return LocalDate.parse(normalized);
        } catch (Exception e) {
            throw new BusinessException("Invalid date: " + value);
        }
    }

    private ExportTask getOwnedTask(Long taskId, Long userId) {
        if (taskId == null || userId == null) {
            return null;
        }
        ExportTask task = this.getById(taskId);
        if (task == null || task.getCreatedBy() == null || !userId.equals(task.getCreatedBy())) {
            return null;
        }
        return task;
    }

    private String normalizeExportType(String exportType) {
        if (exportType == null || exportType.isBlank()) {
            throw new BusinessException("Export type is required");
        }
        String normalized = exportType.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        return switch (normalized) {
            case "activity", "activity-stat", "activity-stats", "activity-metric", "activity-metrics" -> "activity-stats";
            case "duration", "user-duration" -> "user-duration";
            case "attendance", "checkin", "check-in" -> "attendance";
            case "credit", "credit-transaction", "credit-transactions" -> "credit";
            default -> throw new BusinessException("Unsupported export type: " + exportType);
        };
    }

    private String normalizeFormat(String format) {
        if (format == null || format.isBlank()) {
            return FORMAT_XLSX;
        }
        String normalized = format.trim().toLowerCase(Locale.ROOT);
        if ("excel".equals(normalized)) {
            return FORMAT_XLSX;
        }
        if (FORMAT_CSV.equals(normalized) || FORMAT_XLSX.equals(normalized)) {
            return normalized;
        }
        throw new BusinessException("Unsupported export format: " + format);
    }

    private String firstNonBlank(String first, String fallback) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return fallback;
    }

    private record ExportField(String key, String label) {
    }

    private record ExportDataset(List<ExportField> fields, List<Map<String, Object>> rows) {
    }
}
