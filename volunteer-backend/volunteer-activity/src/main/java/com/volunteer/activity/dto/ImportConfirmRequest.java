package com.volunteer.activity.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImportConfirmRequest {
    
    @NotEmpty(message = "学号列表不能为空")
    private List<String> studentNos;
}