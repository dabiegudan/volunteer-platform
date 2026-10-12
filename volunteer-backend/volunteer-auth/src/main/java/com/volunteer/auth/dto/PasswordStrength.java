package com.volunteer.auth.dto;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * 密码强度注解(R-03 实施)
 * <p>
 * 校验规则:8-32 位,必须同时包含 大写字母 / 小写字母 / 数字 / 特殊字符;
 * 与 AuthService.validatePasswordStrength 业务校验同源,保证 DTO 与业务一致。
 * </p>
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PasswordStrengthValidator.class)
@Documented
public @interface PasswordStrength {

    String message() default "密码必须为 8-32 位,且同时包含大小写字母、数字和特殊字符";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
