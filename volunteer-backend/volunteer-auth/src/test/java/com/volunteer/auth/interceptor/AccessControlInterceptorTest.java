package com.volunteer.auth.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.volunteer.auth.annotation.RequiresAccess;
import com.volunteer.auth.annotation.RequiresPermission;
import com.volunteer.auth.entity.SysUser;
import com.volunteer.auth.service.AuthService;
import com.volunteer.auth.service.UserPermissionService;
import com.volunteer.auth.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import java.util.Collections;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * R-01 实施:AccessControlInterceptor 单元测试
 * <p>验证 @RequiresPermission 注解触发的鉴权路径,以及不拦截未登录路径。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AccessControlInterceptor 单元测试")
class AccessControlInterceptorTest {

    @Mock private AuthService authService;
    @Mock private UserService userService;
    @Mock private UserPermissionService userPermissionService;

    private AccessControlInterceptor interceptor;
    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        interceptor = new AccessControlInterceptor(authService, objectMapper, userService, userPermissionService);
    }

    @Test
    @DisplayName("未登录访问 @RequiresPermission 接口 - 拒绝并返回 403")
    void rejectWhenNotLoggedIn() throws Exception {
        HttpServletRequest request = org.mockito.Mockito.mock(HttpServletRequest.class);
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean passed = interceptor.preHandle(request, response, handlerMethod("securedList"));

        assertThat(passed).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("登录但缺权限 - 拒绝")
    void rejectWhenNoPermission() throws Exception {
        HttpServletRequest request = org.mockito.Mockito.mock(HttpServletRequest.class);
        when(request.getAttribute("userId")).thenReturn(100L);

        SysUser user = new SysUser();
        user.setId(100L);
        when(userService.getUserById(100L)).thenReturn(user);
        when(authService.hasFullAccess(100L)).thenReturn(true);
        when(userPermissionService.loadPermissionCodes(100L))
                .thenReturn(Collections.singleton("activity:list"));

        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean passed = interceptor.preHandle(request, response, handlerMethod("securedList"));

        assertThat(passed).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("登录且拥有权限 - 放行")
    void allowWhenHasPermission() throws Exception {
        HttpServletRequest request = org.mockito.Mockito.mock(HttpServletRequest.class);
        when(request.getAttribute("userId")).thenReturn(100L);

        SysUser user = new SysUser();
        user.setId(100L);
        when(userService.getUserById(100L)).thenReturn(user);
        when(authService.hasFullAccess(100L)).thenReturn(true);
        when(userPermissionService.loadPermissionCodes(100L))
                .thenReturn(Set.of("system:user", "activity:list"));

        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean passed = interceptor.preHandle(request, response, handlerMethod("securedList"));

        assertThat(passed).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("管理员访问 requireStudentId 的组织者接口 - 未绑定学号也放行")
    void allowAdminWithoutStudentIdForOrganizerAccess() throws Exception {
        HttpServletRequest request = org.mockito.Mockito.mock(HttpServletRequest.class);
        when(request.getAttribute("userId")).thenReturn(100L);

        SysUser user = new SysUser();
        user.setId(100L);
        when(userService.getUserById(100L)).thenReturn(user);
        when(authService.hasFullAccess(100L)).thenReturn(false);
        when(userService.checkIsAdmin(100L)).thenReturn(true);

        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean passed = interceptor.preHandle(request, response, handlerMethod("organizerCreate"));

        assertThat(passed).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("非 HandlerMethod - 直接放行")
    void allowForNonHandler() throws Exception {
        HttpServletRequest request = org.mockito.Mockito.mock(HttpServletRequest.class);
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
    }

    private HandlerMethod handlerMethod(String name) throws NoSuchMethodException {
        return new HandlerMethod(new Target(), name);
    }

    static class Target {
        @RequiresPermission("system:user")
        public void securedList() {}

        @RequiresAccess(value = "organizer", requireStudentId = true)
        public void organizerCreate() {}
    }
}
