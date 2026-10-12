package com.volunteer.auth.dto;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

/**
 * 密码强度校验器(R-03 实施)
 * <p>
 * 校验规则与 AuthService.validatePasswordStrength 完全一致,确保 DTO 注解
 * 与业务层校验口径统一,避免"DTO 通过但业务拒绝"或反之的不一致问题。
 * </p>
 */
public class PasswordStrengthValidator implements ConstraintValidator<PasswordStrength, String> {

    private static final int MIN_LENGTH = 8;
    private static final int MAX_LENGTH = 32;

    private static final Pattern HAS_UPPER = Pattern.compile(".*[A-Z].*");
    private static final Pattern HAS_LOWER = Pattern.compile(".*[a-z].*");
    private static final Pattern HAS_DIGIT = Pattern.compile(".*\\d.*");
    private static final Pattern HAS_SPECIAL =
            Pattern.compile(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?].*");

    @Override
    public boolean isValid(String password, ConstraintValidatorContext ctx) {
        if (password == null || password.isEmpty()) {
            return replaceMessage(ctx, "密码不能为空");
        }
        if (password.length() < MIN_LENGTH) {
            return replaceMessage(ctx, "密码长度不能少于" + MIN_LENGTH + "位");
        }
        if (password.length() > MAX_LENGTH) {
            return replaceMessage(ctx, "密码长度不能超过" + MAX_LENGTH + "位");
        }
        if (!HAS_UPPER.matcher(password).matches()) {
            return replaceMessage(ctx, "密码必须包含至少一个大写字母");
        }
        if (!HAS_LOWER.matcher(password).matches()) {
            return replaceMessage(ctx, "密码必须包含至少一个小写字母");
        }
        if (!HAS_DIGIT.matcher(password).matches()) {
            return replaceMessage(ctx, "密码必须包含至少一个数字");
        }
        if (!HAS_SPECIAL.matcher(password).matches()) {
            return replaceMessage(ctx, "密码必须包含至少一个特殊字符");
        }
        return true;
    }

    private boolean replaceMessage(ConstraintValidatorContext ctx, String message) {
        ctx.disableDefaultConstraintViolation();
        ctx.buildConstraintViolationWithTemplate(message).addConstraintViolation();
        return false;
    }
}
