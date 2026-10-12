package com.volunteer.auth.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Data
public class StudentIdBindRequest {
    
    @NotBlank(message = "学号不能为空")
    @Pattern(regexp = "^[a-zA-Z0-9]{6,20}$", message = "学号格式不正确，应为6-20位字母或数字")
    private String studentId;
    
    @NotBlank(message = "密码不能为空")
    private String password;
}
