package com.volunteer.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.volunteer.auth.dto.LoginRequest;
import com.volunteer.auth.dto.RegisterRequest;
import com.volunteer.auth.entity.SysUser;
import com.volunteer.auth.enums.AccessLevelEnum;
import com.volunteer.auth.mapper.SysUserMapper;
import com.volunteer.auth.util.JwtUtil;
import com.volunteer.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * R-02 / R-03 / R-05 实施:AuthService 单元测试
 * <p>覆盖 accessLevel 兜底、密码强度、滑块验证与失败计数。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService 单元测试")
class AuthServiceTest {

    @Mock private SysUserMapper userMapper;
    @Mock private JwtUtil jwtUtil;
    @Mock private CaptchaService captchaService;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;
    @Spy private BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @InjectMocks private AuthService authService;

    @BeforeEach
    void setUp() {
        // 默认关闭 captcha,走纯密码路径
        ReflectionTestUtils.setField(authService, "captchaRequired", false);
        ReflectionTestUtils.setField(authService, "loginFailLockThreshold", 0);
    }

    @Test
    @DisplayName("R-02:accessLevel=null 兜底为 visitor")
    void accessLevelNullFallback() {
        SysUser user = new SysUser();
        user.setId(1L);
        user.setAccessLevel(null);

        String level = authService.resolveAccessLevel(user);
        assertThat(level).isEqualTo("visitor");
    }

    @Test
    @DisplayName("R-02:accessLevel=1 返回 full")
    void accessLevelFull() {
        SysUser user = new SysUser();
        user.setAccessLevel(AccessLevelEnum.FULL_ACCESS.getCode());

        assertThat(authService.resolveAccessLevel(user)).isEqualTo("full");
    }

    @Test
    @DisplayName("R-02:user 为 null 仍返回 visitor")
    void nullUser() {
        assertThat(authService.resolveAccessLevel(null)).isEqualTo("visitor");
    }

    @Test
    @DisplayName("R-03:弱口令被业务拒绝")
    void weakPasswordRejected() {
        assertThat(authService.validatePasswordStrength(null)).isNotNull();
        assertThat(authService.validatePasswordStrength("abc")).isNotNull();
        assertThat(authService.validatePasswordStrength("abcdefgh")).isNotNull();
        assertThat(authService.validatePasswordStrength("ABCDEFGH")).isNotNull();
        assertThat(authService.validatePasswordStrength("ABCDefgh")).isNotNull();
        assertThat(authService.validatePasswordStrength("ABCDefg1")).isNotNull();
        assertThat(authService.validatePasswordStrength("Admin2026")).isNotNull();
    }

    @Test
    @DisplayName("R-03:合规密码通过")
    void strongPasswordAccepted() {
        assertThat(authService.validatePasswordStrength("Admin@2026")).isNull();
    }

    @Test
    @DisplayName("R-05:captcha 关闭时登录走纯密码路径")
    void loginWithoutCaptcha() {
        SysUser user = new SysUser();
        user.setId(1L);
        user.setUsername("u1");
        user.setPassword(new BCryptPasswordEncoder().encode("Admin@2026"));
        user.setStatus(1);
        user.setAccessLevel(0);

        LoginRequest req = new LoginRequest();
        req.setUsername("u1");
        req.setPassword("Admin@2026");

        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
        when(jwtUtil.generateToken(eq(1L), anyString())).thenReturn("token-abc");

        var resp = authService.login(req);
        assertThat(resp.getToken()).isEqualTo("token-abc");
        assertThat(resp.getAccessLevel()).isEqualTo("visitor");
        verify(captchaService, never()).verifyCaptcha(anyString(), anyInt());
    }

    @Test
    @DisplayName("R-05:登录失败达到阈值时拦截登录")
    void loginLockedAfterFailures() {
        ReflectionTestUtils.setField(authService, "loginFailLockThreshold", 3);

        SysUser user = new SysUser();
        user.setId(1L);
        user.setUsername("u1");
        user.setPassword(new BCryptPasswordEncoder().encode("Admin@2026"));
        user.setStatus(1);

        LoginRequest req = new LoginRequest();
        req.setUsername("u1");
        req.setPassword("Admin@2026");

        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("login:fail:u1")).thenReturn("3");

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("登录失败次数过多");
        verify(jwtUtil, never()).generateToken(anyLong(), anyString());
    }

    @Test
    @DisplayName("R-05:refreshToken 使用有效 token 换新 token")
    void refreshTokenSuccess() {
        SysUser user = new SysUser();
        user.setId(1L);
        user.setUsername("u1");
        user.setRealName("测试用户");
        user.setStatus(1);
        user.setAccessLevel(AccessLevelEnum.FULL_ACCESS.getCode());

        when(jwtUtil.validateToken("old-token")).thenReturn(true);
        when(jwtUtil.getUserIdFromToken("old-token")).thenReturn(1L);
        when(userMapper.selectById(1L)).thenReturn(user);
        when(jwtUtil.generateToken(1L, "u1")).thenReturn("new-token");

        var resp = authService.refreshToken("Bearer old-token");

        assertThat(resp.getToken()).isEqualTo("new-token");
        assertThat(resp.getUserId()).isEqualTo(1L);
        assertThat(resp.getAccessLevel()).isEqualTo("full");
    }

    @Test
    @DisplayName("R-05:captcha 开启但未传参时拒绝登录")
    void loginRequireCaptcha() {
        ReflectionTestUtils.setField(authService, "captchaRequired", true);

        SysUser user = new SysUser();
        user.setId(1L);
        user.setStatus(1);

        LoginRequest req = new LoginRequest();
        req.setUsername("u1");
        req.setPassword("Admin@2026");

        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("滑块");
    }

    @Test
    @DisplayName("R-05:captcha 开启但验证失败时拒绝")
    void loginCaptchaInvalid() {
        ReflectionTestUtils.setField(authService, "captchaRequired", true);

        SysUser user = new SysUser();
        user.setId(1L);
        user.setStatus(1);

        LoginRequest req = new LoginRequest();
        req.setUsername("u1");
        req.setPassword("Admin@2026");
        req.setCaptchaId("c1");
        req.setSliderX(50);

        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
        when(captchaService.verifyCaptcha(eq("c1"), eq(50))).thenReturn(false);

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("滑块");
    }

    @Test
    @DisplayName("R-05:登录失败计数 - 阈值=0 不启用")
    void failCounterDisabled() {
        // 当前阈值=0,无论怎么调用 isLoginLocked 都返回 false
        assertThat(authService.isLoginLocked("u1")).isFalse();
    }

    @Test
    @DisplayName("R-05:登录失败计数 - 启用时根据 Redis 值判断")
    void failCounterEnabled() {
        ReflectionTestUtils.setField(authService, "loginFailLockThreshold", 3);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("login:fail:u1")).thenReturn("3");
        assertThat(authService.isLoginLocked("u1")).isTrue();
    }

    @Test
    @DisplayName("R-05:登录成功时 clearLoginFailure 走 Redis")
    void clearLoginFailureSuccess() {
        ReflectionTestUtils.setField(authService, "loginFailLockThreshold", 3);
        when(redisTemplate.delete(anyString())).thenReturn(true);

        authService.isLoginLocked("u1");

        // 验证 clearLoginFailure 被调用
        // 通过反射调用 private 方法
        java.lang.reflect.Method m;
        try {
            m = AuthService.class.getDeclaredMethod("clearLoginFailure", String.class);
            m.setAccessible(true);
            m.invoke(authService, "u1");
            verify(redisTemplate, atLeastOnce()).delete("login:fail:u1");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("R-02:register 显式赋 VISITOR,杜绝 null")
    void registerSetsVisitor() {
        RegisterRequest req = new RegisterRequest();
        req.setPhone("13800138999");
        req.setPassword("Admin@2026");
        req.setRealName("测试");

        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        SysUser created = authService.register(req);

        assertThat(created.getAccessLevel()).isEqualTo(AccessLevelEnum.VISITOR.getCode());
        assertThat(created.getStatus()).isEqualTo(1);
        verify(userMapper).insert(any(SysUser.class));
    }
}
