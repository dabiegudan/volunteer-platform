package com.volunteer.auth.interceptor;

import com.volunteer.auth.annotation.RequiresAccess;
import com.volunteer.auth.annotation.RequiresPermission;
import com.volunteer.auth.entity.SysUser;
import com.volunteer.auth.service.AuthService;
import com.volunteer.auth.service.UserPermissionService;
import com.volunteer.auth.service.UserService;
import com.volunteer.common.result.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 访问控制拦截器(R-01 重构)
 *
 * <p>本类同时支持两种注解:
 * <ul>
 *   <li>{@link RequiresAccess} - 旧的访问级别注解,值为 "full"/"visitor"/"admin"/"organizer",保持向后兼容。</li>
 *   <li>{@link RequiresPermission} - 新的细粒度权限注解,真正消费 sys_role_permission 表,完成动态鉴权。</li>
 * </ul>
 * </p>
 *
 * <p>判定优先级:RequiresPermission > RequiresAccess;两者都未声明时直接放行(由 SecurityConfig 控制登录态)。</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AccessControlInterceptor implements HandlerInterceptor {

    private final AuthService authService;
    private final ObjectMapper objectMapper;
    private final UserService userService;
    private final UserPermissionService userPermissionService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Long userId = (Long) request.getAttribute("userId");

        if (userId != null) {
            SysUser currentUser = userService.getUserById(userId);
            if (currentUser != null) {
                request.setAttribute("currentUser", currentUser);
                request.setAttribute("currentCollegeId", currentUser.getCollegeId());
            }
        }

        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        HandlerMethod handlerMethod = (HandlerMethod) handler;

        // 1) 优先:细粒度权限注解
        RequiresPermission requiresPermission = handlerMethod.getMethodAnnotation(RequiresPermission.class);
        if (requiresPermission != null) {
            return checkPermission(userId, requiresPermission, response);
        }

        // 2) 兼容:旧版访问级别注解
        RequiresAccess requiresAccess = handlerMethod.getMethodAnnotation(RequiresAccess.class);
        if (requiresAccess == null) {
            return true;
        }
        return checkAccessLevel(userId, requiresAccess, response);
    }

    private boolean checkPermission(Long userId, RequiresPermission anno, HttpServletResponse response) throws Exception {
        if (userId == null) {
            log.warn("权限拦截: 用户未登录");
            sendErrorResponse(response, "请先登录");
            return false;
        }

        boolean hasFullAccess = authService.hasFullAccess(userId);
        boolean isAdmin = anno.requireStudentId() && userService.checkIsAdmin(userId);
        if (anno.requireStudentId() && !hasFullAccess && !isAdmin) {
            log.warn("权限拦截: 用户{} 需要绑定学号", userId);
            sendErrorResponse(response, "请先绑定学号");
            return false;
        }

        String[] required = anno.value();
        if (required == null || required.length == 0) {
            // 只校验登录 + 学号绑定
            return true;
        }

        Set<String> userPerms = userPermissionService.loadPermissionCodes(userId);
        boolean hit = Arrays.stream(required).anyMatch(userPerms::contains);
        if (!hit) {
            log.warn("权限拦截: 用户{} 缺少权限码, 需要 {} 之一, 实际 {}", userId,
                    Arrays.toString(required),
                    userPerms.stream().sorted().collect(Collectors.joining(",")));
            sendErrorResponse(response, "权限不足");
            return false;
        }
        return true;
    }

    private boolean checkAccessLevel(Long userId, RequiresAccess anno, HttpServletResponse response) throws Exception {
        if (userId == null) {
            log.warn("访问级别拦截: 用户未登录");
            sendErrorResponse(response, "请先登录");
            return false;
        }

        String requiredLevel = anno.value();
        boolean requireStudentId = anno.requireStudentId();

        boolean hasFullAccess = authService.hasFullAccess(userId);
        boolean isAdmin = userService.checkIsAdmin(userId);
        String normalizedLevel = requiredLevel == null
                ? "visitor"
                : requiredLevel.trim().toLowerCase(Locale.ROOT);

        if ("admin".equals(normalizedLevel) && !isAdmin) {
            log.warn("Access denied: user {} requires admin role", userId);
            sendErrorResponse(response, "权限不足");
            return false;
        }

        if ("organizer".equals(normalizedLevel)
                && !isAdmin
                && !userService.checkIsOrganizer(userId)) {
            log.warn("Access denied: user {} requires organizer role", userId);
            sendErrorResponse(response, "权限不足");
            return false;
        }

        if ("full".equals(normalizedLevel) && !hasFullAccess) {
            log.warn("访问级别拦截: 用户{} 需要完整访问权限", userId);
            sendErrorResponse(response, "请先绑定学号以获得完整访问权限");
            return false;
        }

        if (requireStudentId && !hasFullAccess && !isAdmin) {
            log.warn("访问级别拦截: 用户{} 需要绑定学号", userId);
            sendErrorResponse(response, "请先绑定学号");
            return false;
        }

        return true;
    }

    private void sendErrorResponse(HttpServletResponse response, String message) throws Exception {
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        // 缺陷背景：此处原先用 Result.error(message)，其 code 固定为 500，
        // 导致 HTTP 状态码 403 与 body 里的 code=500 自相矛盾，前端无法按 code 分流
        // （request.js 只在 statusCode===200 时才看 body.code，403 分支拿到的 code 与 HTTP 状态码不一致）。
        // 现与 HTTP 状态码保持一致。
        Result<Void> result = Result.error(HttpServletResponse.SC_FORBIDDEN, message);
        response.getWriter().write(objectMapper.writeValueAsString(result));
    }
}
