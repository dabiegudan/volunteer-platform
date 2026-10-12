package com.volunteer.activity.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.volunteer.activity.dto.ImportPreviewDTO;
import com.volunteer.activity.dto.ImportResultDTO;
import com.volunteer.activity.entity.ActivityImportLog;
import com.volunteer.auth.entity.SysUser;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.List;

public interface ActivityImportService {
    
    ImportPreviewDTO previewImport(Long activityId, MultipartFile file, SysUser currentUser);
    
    ImportResultDTO confirmImport(Long activityId, List<String> studentNos, SysUser currentUser);
    
    Page<ActivityImportLog> getImportLogs(Long activityId, Integer pageNum, Integer pageSize);
    
    ActivityImportLog getImportLogById(Long logId);
    
    ByteArrayOutputStream downloadTemplate(Long activityId);
    
    ByteArrayOutputStream downloadFailures(Long logId);
}