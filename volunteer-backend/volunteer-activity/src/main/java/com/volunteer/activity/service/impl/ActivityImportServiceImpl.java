package com.volunteer.activity.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.volunteer.activity.dto.ImportPreviewDTO;
import com.volunteer.activity.dto.ImportRecordDTO;
import com.volunteer.activity.dto.ImportResultDTO;
import com.volunteer.activity.entity.Activity;
import com.volunteer.activity.entity.ActivityImportLog;
import com.volunteer.activity.entity.ActivityRegistration;
import com.volunteer.activity.mapper.ActivityImportLogMapper;
import com.volunteer.activity.mapper.ActivityMapper;
import com.volunteer.activity.mapper.ActivityRegistrationMapper;
import com.volunteer.activity.service.ActivityImportService;
import com.volunteer.activity.service.ActivityCacheService;
import com.volunteer.auth.entity.SysUser;
import com.volunteer.auth.mapper.SysUserMapper;
import com.volunteer.common.enums.ActivityScopeEnum;
import com.volunteer.common.enums.ActivityStatusEnum;
import com.volunteer.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ActivityImportServiceImpl implements ActivityImportService {
    
    private final ActivityMapper activityMapper;
    private final ActivityRegistrationMapper registrationMapper;
    private final ActivityImportLogMapper importLogMapper;
    private final SysUserMapper userMapper;
    private final ActivityCacheService activityCacheService;
    private final ObjectMapper objectMapper;
    
    private static final int MAX_IMPORT_COUNT = 500;
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;
    private static final String JOIN_METHOD_IMPORT = "import";
    
    @Override
    public ImportPreviewDTO previewImport(Long activityId, MultipartFile file, SysUser currentUser) {
        validateFile(file);
        
        Activity activity = activityMapper.selectById(activityId);
        validateActivity(activity, currentUser);
        
        List<ImportRecordDTO> records = parseFile(file);
        validateRecordCount(records);
        
        Map<String, SysUser> studentNoToUserMap = buildStudentNoToUserMap(records);
        Set<String> existingStudentNos = getExistingStudentNos(activityId);
        
        List<ImportRecordDTO> validatedRecords = validateRecords(records, studentNoToUserMap, existingStudentNos, activity);
        
        int successCount = (int) validatedRecords.stream().filter(r -> "SUCCESS".equals(r.getStatus())).count();
        int skipCount = (int) validatedRecords.stream().filter(r -> "SKIP".equals(r.getStatus())).count();
        int failCount = (int) validatedRecords.stream().filter(r -> "FAIL".equals(r.getStatus())).count();
        
        return ImportPreviewDTO.builder()
                .totalCount(records.size())
                .successCount(successCount)
                .skipCount(skipCount)
                .failCount(failCount)
                .records(validatedRecords)
                .build();
    }
    
    @Override
    @Transactional
    public ImportResultDTO confirmImport(Long activityId, List<String> studentNos, SysUser currentUser) {
        Activity activity = activityMapper.selectById(activityId);
        validateActivity(activity, currentUser);
        
        Set<String> existingStudentNos = getExistingStudentNos(activityId);
        
        List<String> newStudentNos = studentNos.stream()
                .filter(s -> !existingStudentNos.contains(s))
                .distinct()
                .collect(Collectors.toList());
        
        Map<String, SysUser> studentNoToUserMap = buildStudentNoToUserMapByNos(newStudentNos);
        
        int successCount = 0;
        int skipCount = studentNos.size() - newStudentNos.size();
        List<ImportRecordDTO> failedRecords = new ArrayList<>();
        
        for (String studentNo : newStudentNos) {
            SysUser user = studentNoToUserMap.get(studentNo);
            if (user == null) {
                failedRecords.add(ImportRecordDTO.builder()
                        .studentNo(studentNo)
                        .status("FAIL")
                        .message("学号未注册或未绑定")
                        .build());
                continue;
            }
            
            if (!isValidCollege(user, activity)) {
                failedRecords.add(ImportRecordDTO.builder()
                        .studentNo(studentNo)
                        .systemName(user.getUsername())
                        .status("FAIL")
                        .message("该生非本学院，不可加入")
                        .build());
                continue;
            }
            
            try {
                createRegistration(activityId, user.getId(), currentUser.getId());
                successCount++;
            } catch (Exception e) {
                log.warn("导入学号 {} 失败: {}", studentNo, e.getMessage());
                failedRecords.add(ImportRecordDTO.builder()
                        .studentNo(studentNo)
                        .systemName(user.getUsername())
                        .status("FAIL")
                        .message("导入失败: " + e.getMessage())
                        .build());
            }
        }
        
        ActivityImportLog importLog = saveImportLog(activityId, currentUser.getId(), 
                "import.xlsx", studentNos.size(), successCount, skipCount, failedRecords.size(), failedRecords);
        
        activityCacheService.clearActivityCache(activityId);
        
        return ImportResultDTO.builder()
                .successCount(successCount)
                .skipCount(skipCount)
                .failCount(failedRecords.size())
                .logId(importLog.getId())
                .message(String.format("成功导入%d名参与者", successCount))
                .build();
    }
    
    @Override
    public Page<ActivityImportLog> getImportLogs(Long activityId, Integer pageNum, Integer pageSize) {
        Page<ActivityImportLog> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<ActivityImportLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ActivityImportLog::getActivityId, activityId)
                .orderByDesc(ActivityImportLog::getImportTime);
        return importLogMapper.selectPage(page, wrapper);
    }
    
    @Override
    public ActivityImportLog getImportLogById(Long logId) {
        return importLogMapper.selectById(logId);
    }
    
    @Override
    public ByteArrayOutputStream downloadTemplate(Long activityId) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            
            Sheet sheet = workbook.createSheet("导入模板");
            
            Row headerRow = sheet.createRow(0);
            Cell studentNoCell = headerRow.createCell(0);
            studentNoCell.setCellValue("学号");
            Cell nameCell = headerRow.createCell(1);
            nameCell.setCellValue("姓名（可选）");
            
            sheet.setColumnWidth(0, 5000);
            sheet.setColumnWidth(1, 5000);
            
            workbook.write(outputStream);
            return outputStream;
        } catch (IOException e) {
            log.error("下载模板失败", e);
            throw new BusinessException("下载模板失败");
        }
    }
    
    @Override
    public ByteArrayOutputStream downloadFailures(Long logId) {
        ActivityImportLog importLog = importLogMapper.selectById(logId);
        if (importLog == null) {
            throw new BusinessException("导入日志不存在");
        }
        
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            
            Sheet sheet = workbook.createSheet("失败明细");
            
            Row headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue("学号");
            headerRow.createCell(1).setCellValue("失败原因");
            
            if (StringUtils.hasText(importLog.getFailDetails())) {
                List<ImportRecordDTO> failedRecords = objectMapper.readValue(
                        importLog.getFailDetails(), 
                        objectMapper.getTypeFactory().constructCollectionType(List.class, ImportRecordDTO.class));
                
                int rowIndex = 1;
                for (ImportRecordDTO record : failedRecords) {
                    Row row = sheet.createRow(rowIndex++);
                    row.createCell(0).setCellValue(record.getStudentNo() != null ? record.getStudentNo() : "");
                    row.createCell(1).setCellValue(record.getMessage() != null ? record.getMessage() : "");
                }
            }
            
            sheet.setColumnWidth(0, 5000);
            sheet.setColumnWidth(1, 10000);
            
            workbook.write(outputStream);
            return outputStream;
        } catch (IOException e) {
            log.error("下载失败明细失败", e);
            throw new BusinessException("下载失败明细失败");
        }
    }
    
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择要上传的文件");
        }
        
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException("文件大小超过限制（最大10MB），请拆分文件后分批导入");
        }
        
        String filename = file.getOriginalFilename();
        if (filename == null || 
                (!filename.endsWith(".xlsx") && !filename.endsWith(".xls") && !filename.endsWith(".csv"))) {
            throw new BusinessException("不支持的文件格式，仅支持.xlsx、.xls、.csv格式");
        }
    }
    
    private void validateActivity(Activity activity, SysUser currentUser) {
        if (activity == null) {
            throw new BusinessException("活动不存在");
        }
        
        validatePermission(activity, currentUser);
        
        String status = activity.getStatus();
        if (!ActivityStatusEnum.RECRUITING.name().equals(status)
                && !ActivityStatusEnum.PUBLISHED.name().equals(status)
                && !ActivityStatusEnum.APPROVED.name().equals(status)) {
            throw new BusinessException("当前活动状态不允许导入参与者");
        }
    }
    
    private void validatePermission(Activity activity, SysUser currentUser) {
        boolean isAdmin = checkIsAdmin(currentUser);
        boolean isOrganizer = checkIsOrganizer(currentUser);
        
        if (!isAdmin && !isOrganizer) {
            throw new BusinessException("无权导入参与者");
        }
        
        if (isOrganizer && !ActivityScopeEnum.UNIVERSITY.getCode().equals(activity.getActivityScope())) {
            if (!Objects.equals(activity.getCollegeId(), currentUser.getCollegeId())) {
                throw new BusinessException("无权导入其他学院活动的参与者");
            }
        }
    }
    
    private List<ImportRecordDTO> parseFile(MultipartFile file) {
        List<ImportRecordDTO> records = new ArrayList<>();
        String filename = file.getOriginalFilename();
        
        try (InputStream inputStream = file.getInputStream()) {
            if (filename != null && filename.endsWith(".csv")) {
                records = parseCsv(inputStream);
            } else {
                records = parseExcel(inputStream);
            }
        } catch (IOException e) {
            log.error("解析文件失败", e);
            throw new BusinessException("解析文件失败：" + e.getMessage());
        }
        
        return records.stream()
                .filter(r -> StringUtils.hasText(r.getStudentNo()))
                .collect(Collectors.toList());
    }
    
    private List<ImportRecordDTO> parseExcel(InputStream inputStream) throws IOException {
        List<ImportRecordDTO> records = new ArrayList<>();
        
        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            
            int studentNoColIndex = findStudentNoColumn(sheet);
            int nameColIndex = findNameColumn(sheet);
            
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                
                String studentNo = getCellValueAsString(row.getCell(studentNoColIndex));
                String name = nameColIndex >= 0 ? getCellValueAsString(row.getCell(nameColIndex)) : null;
                
                if (StringUtils.hasText(studentNo)) {
                    records.add(ImportRecordDTO.builder()
                            .rowIndex(i + 1)
                            .studentNo(studentNo.trim())
                            .excelName(name != null ? name.trim() : null)
                            .build());
                }
            }
        }
        
        return records;
    }
    
    private List<ImportRecordDTO> parseCsv(InputStream inputStream) throws IOException {
        List<ImportRecordDTO> records = new ArrayList<>();
        String content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        
        String[] lines = content.split("\n");
        if (lines.length == 0) {
            return records;
        }
        
        String headerLine = lines[0].replace("\r", "");
        String[] headers = headerLine.split(",");
        
        int studentNoColIndex = -1;
        int nameColIndex = -1;
        
        for (int i = 0; i < headers.length; i++) {
            String header = headers[i].toLowerCase().trim();
            if (header.contains("学号") || header.contains("student") || header.contains("id")) {
                studentNoColIndex = i;
            }
            if (header.contains("姓名") || header.contains("name")) {
                nameColIndex = i;
            }
        }
        
        if (studentNoColIndex < 0) {
            throw new BusinessException("未找到学号列，请检查文件");
        }
        
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].replace("\r", "");
            if (line.trim().isEmpty()) continue;
            
            String[] values = line.split(",");
            String studentNo = values.length > studentNoColIndex ? values[studentNoColIndex].trim() : "";
            String name = nameColIndex >= 0 && values.length > nameColIndex ? values[nameColIndex].trim() : null;
            
            if (StringUtils.hasText(studentNo)) {
                records.add(ImportRecordDTO.builder()
                        .rowIndex(i + 1)
                        .studentNo(studentNo)
                        .excelName(name)
                        .build());
            }
        }
        
        return records;
    }
    
    private int findStudentNoColumn(Sheet sheet) {
        Row headerRow = sheet.getRow(0);
        if (headerRow == null) {
            throw new BusinessException("未找到标题行");
        }
        
        for (int i = 0; i < headerRow.getLastCellNum(); i++) {
            Cell cell = headerRow.getCell(i);
            if (cell != null) {
                String value = getCellValueAsString(cell).toLowerCase();
                if (value.contains("学号") || value.contains("student") || value.contains("id")) {
                    return i;
                }
            }
        }
        
        throw new BusinessException("未找到学号列，请检查文件或下载模板");
    }
    
    private int findNameColumn(Sheet sheet) {
        Row headerRow = sheet.getRow(0);
        if (headerRow == null) {
            return -1;
        }
        
        for (int i = 0; i < headerRow.getLastCellNum(); i++) {
            Cell cell = headerRow.getCell(i);
            if (cell != null) {
                String value = getCellValueAsString(cell).toLowerCase();
                if (value.contains("姓名") || value.contains("name")) {
                    return i;
                }
            }
        }
        
        return -1;
    }
    
    private String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return "";
        }
        
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                return String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return String.valueOf((long) cell.getNumericCellValue());
                } catch (Exception e) {
                    return cell.getStringCellValue();
                }
            default:
                return "";
        }
    }
    
    private void validateRecordCount(List<ImportRecordDTO> records) {
        if (records.size() > MAX_IMPORT_COUNT) {
            throw new BusinessException(String.format("单次导入最多支持%d条记录，请拆分文件后分批导入", MAX_IMPORT_COUNT));
        }
    }
    
    private Map<String, SysUser> buildStudentNoToUserMap(List<ImportRecordDTO> records) {
        Set<String> studentNos = records.stream()
                .map(ImportRecordDTO::getStudentNo)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        
        if (studentNos.isEmpty()) {
            return Collections.emptyMap();
        }
        
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(SysUser::getStudentId, studentNos);
        List<SysUser> users = userMapper.selectList(wrapper);
        
        return users.stream()
                .filter(u -> u.getStudentId() != null)
                .collect(Collectors.toMap(SysUser::getStudentId, u -> u));
    }
    
    private Map<String, SysUser> buildStudentNoToUserMapByNos(List<String> studentNos) {
        if (studentNos.isEmpty()) {
            return Collections.emptyMap();
        }
        
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(SysUser::getStudentId, studentNos);
        List<SysUser> users = userMapper.selectList(wrapper);
        
        return users.stream()
                .filter(u -> u.getStudentId() != null)
                .collect(Collectors.toMap(SysUser::getStudentId, u -> u));
    }
    
    private Set<String> getExistingStudentNos(Long activityId) {
        List<ActivityRegistration> registrations = registrationMapper.selectList(
                new LambdaQueryWrapper<ActivityRegistration>()
                        .eq(ActivityRegistration::getActivityId, activityId)
                        .ne(ActivityRegistration::getStatus, "CANCELLED")
        );
        
        Set<Long> userIds = registrations.stream()
                .map(ActivityRegistration::getUserId)
                .collect(Collectors.toSet());
        
        if (userIds.isEmpty()) {
            return Collections.emptySet();
        }
        
        List<SysUser> users = userMapper.selectList(
                new LambdaQueryWrapper<SysUser>()
                        .in(SysUser::getId, userIds)
        );
        
        return users.stream()
                .filter(u -> u.getStudentId() != null)
                .map(SysUser::getStudentId)
                .collect(Collectors.toSet());
    }
    
    private List<ImportRecordDTO> validateRecords(List<ImportRecordDTO> records, 
                                                  Map<String, SysUser> studentNoToUserMap,
                                                  Set<String> existingStudentNos,
                                                  Activity activity) {
        return records.stream().map(record -> {
            String studentNo = record.getStudentNo();
            
            if (!validateStudentNoFormat(studentNo)) {
                return record.toBuilder()
                        .status("FAIL")
                        .message("格式无效")
                        .build();
            }
            
            if (existingStudentNos.contains(studentNo)) {
                SysUser user = studentNoToUserMap.get(studentNo);
                return record.toBuilder()
                        .status("SKIP")
                        .systemName(user != null ? user.getUsername() : null)
                        .userId(user != null ? user.getId() : null)
                        .message("已在活动中")
                        .build();
            }
            
            SysUser user = studentNoToUserMap.get(studentNo);
            if (user == null) {
                return record.toBuilder()
                        .status("FAIL")
                        .message("学号未注册或未绑定")
                        .build();
            }
            
            if (!isValidCollege(user, activity)) {
                return record.toBuilder()
                        .status("FAIL")
                        .systemName(user.getUsername())
                        .userId(user.getId())
                        .message("该生非本学院，不可加入")
                        .build();
            }
            
            return record.toBuilder()
                    .status("SUCCESS")
                    .systemName(user.getUsername())
                    .userId(user.getId())
                    .message("校验通过")
                    .build();
        }).collect(Collectors.toList());
    }
    
    private boolean validateStudentNoFormat(String studentNo) {
        if (!StringUtils.hasText(studentNo)) {
            return false;
        }
        return studentNo.matches("^[A-Za-z0-9]+$");
    }
    
    private boolean isValidCollege(SysUser user, Activity activity) {
        if (ActivityScopeEnum.UNIVERSITY.getCode().equals(activity.getActivityScope())) {
            return true;
        }
        return activity.getCollegeId() != null && activity.getCollegeId().equals(user.getCollegeId());
    }
    
    @Transactional
    protected void createRegistration(Long activityId, Long userId, Long operatorId) {
        int updated = activityMapper.increaseParticipantsWithLock(activityId);
        if (updated == 0) {
            throw new BusinessException("Activity is full");
        }

        ActivityRegistration registration = new ActivityRegistration();
        registration.setActivityId(activityId);
        registration.setUserId(userId);
        registration.setStatus("APPROVED");
        registration.setRegistrationTime(LocalDateTime.now());
        registration.setApprovalTime(LocalDateTime.now());
        registration.setJoinMethod(JOIN_METHOD_IMPORT);
        registration.setOperatorId(operatorId);
        
        registrationMapper.insert(registration);
    }
    
    private ActivityImportLog saveImportLog(Long activityId, Long operatorId, String fileName,
                                            int totalCount, int successCount, int skipCount, 
                                            int failCount, List<ImportRecordDTO> failedRecords) {
        ActivityImportLog importLog = new ActivityImportLog();
        importLog.setActivityId(activityId);
        importLog.setOperatorId(operatorId);
        importLog.setFileName(fileName);
        importLog.setTotalCount(totalCount);
        importLog.setSuccessCount(successCount);
        importLog.setSkipCount(skipCount);
        importLog.setFailCount(failCount);
        importLog.setImportTime(LocalDateTime.now());
        
        if (!failedRecords.isEmpty()) {
            try {
                importLog.setFailDetails(objectMapper.writeValueAsString(failedRecords));
            } catch (Exception e) {
                log.warn("序列化失败记录失败", e);
            }
        }
        
        importLogMapper.insert(importLog);
        return importLog;
    }
    
    private boolean checkIsAdmin(SysUser user) {
        return user != null && "ADMIN".equals(user.getRoleCode());
    }
    
    private boolean checkIsOrganizer(SysUser user) {
        return user != null && "ORGANIZER".equals(user.getRoleCode());
    }
}
