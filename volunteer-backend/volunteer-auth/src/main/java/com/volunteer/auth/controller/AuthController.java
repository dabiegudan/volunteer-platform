package com.volunteer.auth.controller;

import com.volunteer.auth.annotation.RequiresAccess;
import com.volunteer.auth.dto.LoginRequest;
import com.volunteer.auth.dto.LoginResponse;
import com.volunteer.auth.dto.RegisterRequest;
import com.volunteer.auth.dto.StudentIdBindRequest;
import com.volunteer.auth.entity.SysUser;
import com.volunteer.auth.service.AuthService;
import com.volunteer.auth.service.UserService;
import com.volunteer.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Tag(name = "认证管理", description = "用户登录、注册、学号绑定等认证相关接口")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {
    
    private final AuthService authService;
    private final UserService userService;
    
    @Operation(summary = "用户登录", description = "支持手机号或学号登录")
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("用户登录请求, username: {}", request.getUsername());
        return Result.success(authService.login(request));
    }
    
    @Operation(summary = "用户注册", description = "使用手机号注册，注册后默认为访客权限")
    @PostMapping("/register")
    public Result<Map<String, Object>> register(@Valid @RequestBody RegisterRequest request) {
        log.info("用户注册请求, phone: {}", request.getPhone());
        SysUser user = authService.register(request);
        
        Map<String, Object> response = new HashMap<>();
        response.put("message", "注册成功，请登录");
        response.put("phone", user.getPhone());
        response.put("redirect", "/auth/login");
        
        return Result.success(response);
    }

    @Operation(summary = "刷新Token", description = "使用当前有效Token换取新的Token")
    @PostMapping("/refresh")
    public Result<LoginResponse> refreshToken(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody(required = false) Map<String, Object> body) {
        String token = authorization;
        if ((token == null || token.isBlank()) && body != null && body.get("token") != null) {
            token = String.valueOf(body.get("token"));
        }
        return Result.success(authService.refreshToken(token));
    }
    
    @Operation(summary = "获取当前用户信息")
    @GetMapping("/user/info")
    public Result<Map<String, Object>> getUserInfo(@RequestAttribute(value = "userId", required = false) Long userId) {
        log.info("获取用户信息, userId: {}", userId);
        if (userId == null) {
            log.warn("获取用户信息失败: userId为空");
            return Result.error("请先登录");
        }
        
        SysUser user = authService.getUserInfo(userId);
        if (user == null) {
            log.warn("用户不存在, userId: {}", userId);
            return Result.error("用户不存在");
        }
        
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("id", user.getId());
        userInfo.put("username", user.getUsername());
        userInfo.put("realName", user.getRealName());
        userInfo.put("phone", user.getPhone());
        userInfo.put("email", user.getEmail());
        userInfo.put("avatar", user.getAvatar());
        userInfo.put("studentId", user.getStudentId());
        userInfo.put("studentNo", user.getStudentId());
        userInfo.put("college", user.getCollegeName());
        userInfo.put("collegeName", user.getCollegeName());
        userInfo.put("major", user.getMajor());
        userInfo.put("className", user.getClassName());
        userInfo.put("grade", user.getGrade());
        userInfo.put("accessLevel", user.getAccessLevel() != null && user.getAccessLevel() == 1 ? "full" : "visitor");
        userInfo.put("status", user.getStatus());
        
        return Result.success(userInfo);
    }
    
    @Operation(summary = "学号绑定", description = "绑定学号后获得完整系统访问权限，每个学号只能绑定一个账号")
    @PostMapping("/user/bind-student-id")
    @RequiresAccess("visitor")
    public Result<Map<String, Object>> bindStudentId(
            @RequestAttribute(value = "userId") Long userId,
            @Valid @RequestBody StudentIdBindRequest request) {
        log.info("学号绑定请求, userId: {}, studentId: {}", userId, request.getStudentId());
        
        SysUser user = authService.bindStudentId(userId, request);
        
        Map<String, Object> response = new HashMap<>();
        response.put("message", "学号绑定成功，您已获得完整系统访问权限");
        response.put("studentId", user.getStudentId());
        response.put("accessLevel", "full");
        
        return Result.success(response);
    }
    
    @Operation(summary = "检查学号是否已绑定")
    @GetMapping("/user/check-student-id")
    public Result<Map<String, Object>> checkStudentId(@Parameter(description = "学号") @RequestParam String studentId) {
        log.info("检查学号是否已绑定, studentId: {}", studentId);
        
        boolean isBound = authService.isStudentIdBound(studentId);
        
        Map<String, Object> result = new HashMap<>();
        result.put("studentId", studentId);
        result.put("isBound", isBound);
        
        return Result.success(result);
    }

    // ===== #2 志愿者档案完整度门控 - 档案管理接口 =====

    @Operation(summary = "获取用户档案完整度")
    @GetMapping("/user/profile/status")
    public Result<Map<String, Object>> getProfileStatus(@RequestAttribute("userId") Long userId) {
        SysUser user = userService.getUserById(userId);
        // 用户被物理删除后旧 token 仍可解析出 userId，此处若不判空会 NPE → 500，
        // 前端无法区分「会话失效」与「服务端故障」
        if (user == null) {
            return Result.error(401, "用户不存在或已被删除，请重新登录");
        }
        Map<String, Object> result = new HashMap<>();
        result.put("profileComplete", user.getProfileComplete() != null && user.getProfileComplete() == 1);
        result.put("realName", user.getRealName());
        result.put("phone", user.getPhone());
        result.put("studentId", user.getStudentId());
        result.put("studentNo", user.getStudentId());
        result.put("collegeName", user.getCollegeName());
        result.put("major", user.getMajor());
        result.put("className", user.getClassName());
        result.put("grade", user.getGrade());
        result.put("gender", user.getGender());
        result.put("birthDate", user.getBirthDate());
        result.put("politicalStatus", user.getPoliticalStatus());
        result.put("emergencyContact", user.getEmergencyContact());
        result.put("emergencyPhone", user.getEmergencyPhone());
        result.put("dormArea", user.getDormArea());
        result.put("serviceCategoryTags", user.getServiceCategoryTags());
        return Result.success(result);
    }

    @Operation(summary = "更新用户档案(补全资料)")
    @PutMapping("/user/profile")
    public Result<Map<String, Object>> updateProfile(@RequestAttribute("userId") Long userId,
                                                      @RequestBody Map<String, Object> profile) {
        SysUser user = userService.getUserById(userId);
        if (user == null) {
            return Result.error(401, "用户不存在或已被删除，请重新登录");
        }
        if (profile.containsKey("realName")) {
            user.setRealName((String) profile.get("realName"));
        }
        // 【安全】phone 同时是登录标识（AuthService.login 会按手机号查用户），
        // 且 sys_user.phone 在 DDL 中只有普通索引、没有唯一约束。若允许用户自助改号：
        //   1) 可把手机号改成他人号码，导致对方按手机号登录时命中多行 → TooManyResultsException
        //      （登录接口 500，等同于账号被锁死）；
        //   2) 可抢占尚未注册的号码。改号必须走「验证码校验新号码」的独立流程，
        //      因此这里显式拒绝任何变更（保持原值不算变更，前端回显提交不会被误伤）。
        if (profile.containsKey("phone")) {
            String newPhone = profile.get("phone") == null ? null : String.valueOf(profile.get("phone"));
            String currentPhone = user.getPhone();
            boolean changed = newPhone == null
                    ? currentPhone != null
                    : !newPhone.equals(currentPhone);
            if (changed) {
                return Result.error("手机号属于登录凭证，不支持直接修改，请通过绑定新手机号的验证流程操作");
            }
        }
        if (profile.containsKey("major")) {
            user.setMajor((String) profile.get("major"));
        }
        if (profile.containsKey("className")) {
            user.setClassName((String) profile.get("className"));
        }
        if (profile.containsKey("grade")) {
            user.setGrade((String) profile.get("grade"));
        }
        if (profile.containsKey("gender")) {
            user.setGender((String) profile.get("gender"));
        }
        if (profile.containsKey("birthDate")) {
            Object birthDate = profile.get("birthDate");
            if (birthDate == null || String.valueOf(birthDate).isBlank()) {
                user.setBirthDate(null);
            } else {
                try {
                    user.setBirthDate(java.time.LocalDate.parse(String.valueOf(birthDate).trim()));
                } catch (java.time.format.DateTimeParseException e) {
                    // 历史实现直接 parse，非法日期会抛异常并被兜底成 500「系统繁忙」，
                    // 用户完全不知道该改什么；这里显式返回可读的 400
                    return Result.error(400, "出生日期格式不正确，应为 yyyy-MM-dd");
                }
            }
        }
        if (profile.containsKey("politicalStatus")) {
            user.setPoliticalStatus((String) profile.get("politicalStatus"));
        }
        if (profile.containsKey("emergencyContact")) {
            user.setEmergencyContact((String) profile.get("emergencyContact"));
        }
        if (profile.containsKey("emergencyPhone")) {
            user.setEmergencyPhone((String) profile.get("emergencyPhone"));
        }
        if (profile.containsKey("dormArea")) {
            user.setDormArea((String) profile.get("dormArea"));
        }
        if (profile.containsKey("serviceCategoryTags")) {
            user.setServiceCategoryTags((String) profile.get("serviceCategoryTags"));
        }

        // 自动计算档案完整度
        boolean complete = user.getPhone() != null && !user.getPhone().isEmpty()
                && user.getBirthDate() != null
                && user.getEmergencyContact() != null && !user.getEmergencyContact().isEmpty()
                && user.getEmergencyPhone() != null && !user.getEmergencyPhone().isEmpty();
        user.setProfileComplete(complete ? 1 : 0);

        userService.updateUser(user);
        log.info("用户档案更新成功, userId: {}, complete: {}", userId, complete);

        Map<String, Object> result = new HashMap<>();
        result.put("message", "档案更新成功");
        result.put("profileComplete", complete);
        return Result.success(result);
    }
}
