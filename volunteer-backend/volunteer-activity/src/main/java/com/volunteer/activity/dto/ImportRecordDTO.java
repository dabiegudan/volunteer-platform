package com.volunteer.activity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class ImportRecordDTO {
    
    private Integer rowIndex;
    
    private String studentNo;
    
    private String excelName;
    
    private String systemName;
    
    private Long userId;
    
    private String status;
    
    private String message;
}