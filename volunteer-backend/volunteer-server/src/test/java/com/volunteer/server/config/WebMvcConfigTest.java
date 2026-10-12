package com.volunteer.server.config;

import com.volunteer.auth.interceptor.ProfileCompletenessInterceptor;
import com.volunteer.config.RateLimitInterceptor;
import com.volunteer.config.WebMvcConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.handler.MappedInterceptor;
import org.springframework.web.util.UrlPathHelper;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * {@link WebMvcConfig} 的拦截器注册范围锁定。
 *
 * <p>限流与档案完整度门控的生效范围完全由这里的路径模式决定：一旦被误删，
 * 限流与档案门控会静默失效；一旦被放宽，正常浏览又会被 429 或 460 拦住。
 * 本测试把「限流覆盖登录+活动+签到、门控只覆盖活动+签到」这一约定固定下来。</p>
 *
 * <p>实现说明：{@code InterceptorRegistry#getInterceptors()} 是 protected 且返回
 * {@code List<Object>}（Spring 6.2 起内部存放 {@code MappedInterceptor}），
 * 因此这里用子类暴露该方法后按运行时类型筛选。</p>
 */
class WebMvcConfigTest {

    private final RateLimitInterceptor rateLimitInterceptor = mock(RateLimitInterceptor.class);
    private final ProfileCompletenessInterceptor profileCompletenessInterceptor =
            mock(ProfileCompletenessInterceptor.class);

    /** 暴露 protected 的 getInterceptors()，避免依赖 Spring 内部实现细节。 */
    private static class InspectableInterceptorRegistry extends InterceptorRegistry {
        List<Object> interceptors() {
            return getInterceptors();
        }
    }

    private InspectableInterceptorRegistry registryWithRegisteredInterceptors() {
        InspectableInterceptorRegistry registry = new InspectableInterceptorRegistry();
        new WebMvcConfig(rateLimitInterceptor, profileCompletenessInterceptor)
                .addInterceptors(registry);
        return registry;
    }

    private MappedInterceptor mappedFor(HandlerInterceptor interceptor) {
        return registryWithRegisteredInterceptors().interceptors().stream()
                .filter(MappedInterceptor.class::isInstance)
                .map(MappedInterceptor.class::cast)
                .filter(mapped -> mapped.getInterceptor() == interceptor)
                .findFirst()
                .orElseThrow(() -> new AssertionError("拦截器未被注册: " + interceptor));
    }

    private void assertMatchesAll(MappedInterceptor mapped, String... uris) {
        for (String uri : uris) {
            assertThat(mapped.matches(requestTo(uri)))
                    .as("期望被拦截: %s，实际路径模式=%s", uri, Arrays.toString(mapped.getPathPatterns()))
                    .isTrue();
        }
    }

    private void assertMatchesNone(MappedInterceptor mapped, String... uris) {
        for (String uri : uris) {
            assertThat(mapped.matches(requestTo(uri)))
                    .as("期望不被拦截: %s，实际路径模式=%s", uri, Arrays.toString(mapped.getPathPatterns()))
                    .isFalse();
        }
    }

    private MockHttpServletRequest requestTo(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRequestURI(uri);
        // 【修复】Spring 6.x 的 MappedInterceptor#matches 不再自行解析路径，它会依次取
        // RequestPath 或 request 属性 UrlPathHelper.PATH_ATTRIBUTE；两者都缺失时直接抛
        // IllegalArgumentException("Neither a pre-parsed RequestPath nor a pre-resolved
        // String lookupPath is available")。真实容器里由 DispatcherServlet 事先写入该属性，
        // 手工构造的 MockHttpServletRequest 必须自己补上。
        request.setAttribute(UrlPathHelper.PATH_ATTRIBUTE, uri);
        return request;
    }

    @Test
    @DisplayName("限流拦截器覆盖登录、活动、签到三类路径")
    void rateLimitCoversLoginActivityAttendance() {
        MappedInterceptor mapped = mappedFor(rateLimitInterceptor);

        assertMatchesAll(mapped,
                "/api/auth/login",
                "/api/activity/page",
                "/api/activity/123/register",
                "/api/attendance/checkin");
        assertMatchesNone(mapped, "/api/user/page", "/api/actuator/health");
    }

    @Test
    @DisplayName("档案完整度门控只覆盖活动与签到，不覆盖登录")
    void profileGateCoversActivityAndAttendanceOnly() {
        MappedInterceptor mapped = mappedFor(profileCompletenessInterceptor);

        assertMatchesAll(mapped, "/api/activity/register", "/api/attendance/checkin");
        assertMatchesNone(mapped, "/api/auth/login", "/api/college/list");
    }

    @Test
    @DisplayName("两个拦截器都被注册（防止漏注册导致门控静默失效）")
    void bothInterceptorsAreRegistered() {
        assertThat(registryWithRegisteredInterceptors().interceptors()).hasSize(2);
    }
}
