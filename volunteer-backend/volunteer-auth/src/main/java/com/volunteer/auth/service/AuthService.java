package com.volunteer.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.volunteer.auth.dto.LoginRequest;
import com.volunteer.auth.dto.LoginResponse;
import com.volunteer.auth.dto.RegisterRequest;
import com.volunteer.auth.dto.StudentIdBindRequest;
import com.volunteer.auth.entity.SysUser;
import com.volunteer.auth.enums.AccessLevelEnum;
import com.volunteer.auth.mapper.SysUserMapper;
import com.volunteer.auth.util.JwtUtil;
import com.volunteer.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

/**
 * 认证服务(R-02/R-03/R-05 实施)
 *
 * <ul>
 *   <li>R-02:accessLevel 兜底 - 登录与 user/info 永远返回 "full"/"visitor" 字符串,不再出现 null。</li>
 *   <li>R-03:密码规则 - 业务侧 validatePasswordStrength 与 DTO @PasswordStrength 校验口径统一。</li>
 *   <li>R-05:滑块验证 - 登录主流程强制串联 captcha 校验,失败计数 3 次锁定 IP 5 分钟。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final SysUserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final BCryptPasswordEncoder passwordEncoder;
    private final CaptchaService captchaService;
    private final StringRedisTemplate redisTemplate;

    @Value("${captcha.required:false}")
    private boolean captchaRequired;

    @Value("${captcha.login-fail-lock-threshold:3}")
    private int loginFailLockThreshold;

    @Value("${captcha.login-fail-lock-minutes:5}")
    private long loginFailLockMinutes;

    public LoginResponse login(LoginRequest request) {
        log.info("用户登录, username: {}", request.getUsername());

        String loginInput = request.getUsername();
        SysUser user = null;

        if (isPhoneNumber(loginInput)) {
            user = userMapper.selectOne(
                    new LambdaQueryWrapper<SysUser>()
                            .eq(SysUser::getPhone, loginInput)
            );
        } else {
            user = userMapper.selectOne(
                    new LambdaQueryWrapper<SysUser>()
                            .eq(SysUser::getStudentId, loginInput)
            );

            if (user == null) {
                user = userMapper.selectOne(
                        new LambdaQueryWrapper<SysUser>()
                                .eq(SysUser::getUsername, loginInput)
                );
            }
        }

        if (user == null) {
            throw new BusinessException("用户不存在，请先注册");
        }

        if (user.getStatus() == 0) {
            throw new BusinessException("用户已被禁用");
        }

        if (isLoginLocked(loginInput)) {
            throw new BusinessException("登录失败次数过多，请稍后再试");
        }

        // R-05:滑块验证强制串联(R-05 配置开关默认关闭,生产环境通过 yml 开启)
        if (captchaRequired) {
            String captchaId = request.getCaptchaId();
            Integer sliderX = request.getSliderX();
            if (captchaId == null || captchaId.isEmpty() || sliderX == null) {
                throw new BusinessException("请先完成滑块验证");
            }
            if (!captchaService.verifyCaptcha(captchaId, sliderX)) {
                throw new BusinessException("滑块验证失败");
            }
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            // R-05:登录失败计数(按用户名+IP 二选一,这里用用户名简化)
            recordLoginFailure(loginInput);
            throw new BusinessException("密码错误");
        }

        // R-05:登录成功,清除失败计数
        clearLoginFailure(loginInput);

        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        log.info("用户登录成功, username: {}, userId: {}", loginInput, user.getId());

        // R-02:accessLevel 兜底
        String accessLevel = resolveAccessLevel(user);

        return new LoginResponse(token, user.getId(), user.getUsername(), user.getRealName(), user.getAvatar(), accessLevel);
    }

    public LoginResponse refreshToken(String token) {
        String normalizedToken = normalizeBearerToken(token);
        if (normalizedToken == null || normalizedToken.isEmpty() || !jwtUtil.validateToken(normalizedToken)) {
            throw new BusinessException("token无效或已过期，请重新登录");
        }

        Long userId = jwtUtil.getUserIdFromToken(normalizedToken);
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在，请重新登录");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException("用户已被禁用");
        }

        String refreshedToken = jwtUtil.generateToken(user.getId(), user.getUsername());
        return new LoginResponse(refreshedToken, user.getId(), user.getUsername(), user.getRealName(), user.getAvatar(), resolveAccessLevel(user));
    }

    @Transactional
    public SysUser register(RegisterRequest request) {
        log.info("用户注册, phone: {}", request.getPhone());

        String phone = request.getPhone();

        if (!isPhoneNumber(phone)) {
            throw new BusinessException("手机号格式不正确");
        }

        String password = request.getPassword();
        String passwordError = validatePasswordStrength(password);
        if (passwordError != null) {
            throw new BusinessException(passwordError);
        }

        Long countByPhone = userMapper.selectCount(
            new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getPhone, phone)
        );
        if (countByPhone > 0) {
            throw new BusinessException("手机号已被注册");
        }

        String username = phone;

        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setPhone(phone);
        user.setRealName(request.getRealName() != null ? request.getRealName() : "用户" + phone.substring(7));
        user.setStatus(1);
        // R-02:显式设置 VISITOR,杜绝 null
        user.setAccessLevel(AccessLevelEnum.VISITOR.getCode());

        userMapper.insert(user);
        log.info("用户注册成功, phone: {}, userId: {}", phone, user.getId());

        return user;
    }

    /**
     * 公开的密码强度校验方法(R-03 实施,供外部复用)
     */
    public String validatePasswordStrength(String password) {
        if (password == null || password.isEmpty()) {
            return "密码不能为空";
        }
        if (password.length() < 8) {
            return "密码长度不能少于8位";
        }
        if (password.length() > 32) {
            return "密码长度不能超过32位";
        }
        if (!password.matches(".*[A-Z].*")) {
            return "密码必须包含至少一个大写字母";
        }
        if (!password.matches(".*[a-z].*")) {
            return "密码必须包含至少一个小写字母";
        }
        if (!password.matches(".*\\d.*")) {
            return "密码必须包含至少一个数字";
        }
        if (!password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?].*")) {
            return "密码必须包含至少一个特殊字符";
        }
        return null;
    }

    @Transactional
    public SysUser bindStudentId(Long userId, StudentIdBindRequest request) {
        log.info("学号绑定, userId: {}, studentId: {}", userId, request.getStudentId());

        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        if (user.getStudentId() != null && !user.getStudentId().isEmpty()) {
            throw new BusinessException("该账号已绑定学号");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException("密码错误");
        }

        String studentId = request.getStudentId();

        Long countByStudentId = userMapper.selectCount(
            new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getStudentId, studentId)
        );
        if (countByStudentId > 0) {
            throw new BusinessException("该学号已被绑定到其他账号");
        }

        user.setStudentId(studentId);
        user.setAccessLevel(AccessLevelEnum.FULL_ACCESS.getCode());
        userMapper.updateById(user);

        log.info("学号绑定成功, userId: {}, studentId: {}", userId, studentId);
        return user;
    }

    public SysUser getUserInfo(Long userId) {
        return userMapper.selectById(userId);
    }

    /**
     * 解析 accessLevel 字符串(R-02 实施)
     * <p>统一返回 "full"/"visitor",从源头消除 null 分支。</p>
     */
    public String resolveAccessLevel(SysUser user) {
        if (user == null) {
            return "visitor";
        }
        Integer level = user.getAccessLevel();
        if (level == null) {
            return "visitor";
        }
        return level.intValue() == AccessLevelEnum.FULL_ACCESS.getCode() ? "full" : "visitor";
    }

    public boolean hasFullAccess(Long userId) {
        SysUser user = userMapper.selectById(userId);
        return user != null
                && user.getAccessLevel() != null
                && user.getAccessLevel() == AccessLevelEnum.FULL_ACCESS.getCode();
    }

    private boolean isPhoneNumber(String input) {
        return input != null && input.matches("^1[3-9]\\d{9}$");
    }

    private String normalizeBearerToken(String token) {
        if (token == null) {
            return null;
        }
        String trimmed = token.trim();
        if (trimmed.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return trimmed.substring(7).trim();
        }
        return trimmed;
    }

    public boolean isStudentIdBound(String studentId) {
        log.info("检查学号是否已绑定, studentId: {}", studentId);

        Long count = userMapper.selectCount(
            new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getStudentId, studentId)
        );

        return count > 0;
    }

    // ---------------------- R-05:登录失败计数 ----------------------

    private String loginFailKey(String username) {
        return "login:fail:" + username;
    }

    private void recordLoginFailure(String username) {
        if (loginFailLockThreshold <= 0) {
            return;
        }
        try {
            String key = loginFailKey(username);
            Long current = redisTemplate.opsForValue().increment(key);
            if (current != null && current == 1L) {
                redisTemplate.expire(key, loginFailLockMinutes, TimeUnit.MINUTES);
            }
            if (current != null && current >= loginFailLockThreshold) {
                log.warn("登录失败次数达到阈值,锁定 user={}, count={}", username, current);
            }
        } catch (Exception ex) {
            log.warn("记录登录失败次数失败,降级不影响主流程 user={}", username, ex);
        }
    }

    private void clearLoginFailure(String username) {
        try {
            redisTemplate.delete(loginFailKey(username));
        } catch (Exception ex) {
            log.warn("清除登录失败计数失败,降级不影响主流程 user={}", username, ex);
        }
    }

    /**
     * 公开方法:判断给定用户名是否处于锁定状态
     * <p>供测试与外部服务调用。</p>
     */
    public boolean isLoginLocked(String username) {
        if (loginFailLockThreshold <= 0) {
            return false;
        }
        try {
            String val = redisTemplate.opsForValue().get(loginFailKey(username));
            if (val == null) {
                return false;
            }
            return Long.parseLong(val) >= loginFailLockThreshold;
        } catch (Exception ex) {
            return false;
        }
    }
}
