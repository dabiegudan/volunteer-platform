package com.volunteer.auth.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;

@Data
public class LoginRequest {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

    /**
     * 滑块验证码会话 id(R-05 实施)
     */
    private String captchaId;

    /**
     * 滑块 x 坐标(R-05 实施)
     */
    private Integer sliderX;
}
