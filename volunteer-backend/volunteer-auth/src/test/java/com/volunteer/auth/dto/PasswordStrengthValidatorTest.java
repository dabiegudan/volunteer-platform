package com.volunteer.auth.dto;

import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

/**
 * R-03 实施:密码强度校验器单元测试
 * <p>覆盖 DTO 注解与 AuthService 业务侧校验口径一致性。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PasswordStrengthValidator 单元测试")
class PasswordStrengthValidatorTest {

    private PasswordStrengthValidator validator;

    @Mock
    private ConstraintValidatorContext ctx;

    @Mock
    private ConstraintValidatorContext.ConstraintViolationBuilder builder;

    @BeforeEach
    void setUp() {
        validator = new PasswordStrengthValidator();
        lenient().when(ctx.buildConstraintViolationWithTemplate(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(builder);
    }

    @Test
    @DisplayName("合法密码通过校验")
    void valid() {
        assertThat(validator.isValid("Admin@2026Safe", ctx)).isTrue();
    }

    @Test
    @DisplayName("空密码失败")
    void empty() {
        assertThat(validator.isValid("", ctx)).isFalse();
        assertThat(validator.isValid(null, ctx)).isFalse();
    }

    @Test
    @DisplayName("长度小于 8 失败")
    void tooShort() {
        assertThat(validator.isValid("Aa1@bb", ctx)).isFalse();
    }

    @Test
    @DisplayName("长度大于 32 失败")
    void tooLong() {
        assertThat(validator.isValid("Aa1@" + "x".repeat(30), ctx)).isFalse();
    }

    @Test
    @DisplayName("缺少大写字母失败")
    void noUpper() {
        assertThat(validator.isValid("admin@2026", ctx)).isFalse();
    }

    @Test
    @DisplayName("缺少小写字母失败")
    void noLower() {
        assertThat(validator.isValid("ADMIN@2026", ctx)).isFalse();
    }

    @Test
    @DisplayName("缺少数字失败")
    void noDigit() {
        assertThat(validator.isValid("Admin@Safe", ctx)).isFalse();
    }

    @Test
    @DisplayName("缺少特殊字符失败")
    void noSpecial() {
        assertThat(validator.isValid("Admin2026", ctx)).isFalse();
    }
}
