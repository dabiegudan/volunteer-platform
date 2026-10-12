package com.volunteer.activity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportResultDTO {
    
    private Integer successCount;
    
    private Integer skipCount;
    
    private Integer failCount;
    
    private Long logId;
    
    private String message;
}