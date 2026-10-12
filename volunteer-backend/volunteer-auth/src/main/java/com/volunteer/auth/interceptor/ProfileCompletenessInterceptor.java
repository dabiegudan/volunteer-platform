package com.volunteer.auth.interceptor;

import com.volunteer.auth.entity.SysUser;
import com.volunteer.auth.service.UserService;
import com.volunteer.common.result.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * #2 志愿者档案完整度门控拦截器
 * <p>注册后资料不完整禁止报名，强制补全。</p>
 * <p>拦截路径：activity/register, attendance/checkin</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProfileCompletenessInterceptor implements HandlerInterceptor {

    private final UserService userService;
    private final ObjectMapper objectMapper;

    private static final String[] PROTECTED_PATHS = {
        "/activity/",
        "/attendance/checkin"
    };

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return true;
        }

        String uri = request.getRequestURI();
        boolean needsProfileCheck = false;
        for (String path : PROTECTED_PATHS) {
            if (uri.contains(path)) {
                needsProfileCheck = true;
                break;
            }
        }
        if (!needsProfileCheck) {
            return true;
        }

        // 只拦截报名类操作(register)和签到
        if (!uri.contains("/register") && !uri.contains("/checkin")) {
            return true;
        }

        SysUser user = userService.getUserById(userId);
        if (user == null) {
            return true;
        }

        if (user.getProfileComplete() == null || user.getProfileComplete() == 0) {
            log.warn("档案完整度门控拦截: 用户{} 资料不完整，禁止操作 {}", userId, uri);
            sendProfileIncompleteResponse(response);
            return false;
        }

        return true;
    }

    private void sendProfileIncompleteResponse(HttpServletResponse response) throws Exception {
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setStatus(460);
        Result<Void> result = Result.error(460, "请先完善个人档案资料后再进行操作");
        response.getWriter().write(objectMapper.writeValueAsString(result));
    }
}