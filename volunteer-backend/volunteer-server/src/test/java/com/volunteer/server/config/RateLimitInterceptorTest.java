package com.volunteer.server.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.volunteer.config.RateLimitInterceptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link RateLimitInterceptor} 的行为锁定。
 *
 * <h2>为什么必须存在这个测试</h2>
 * <p>历史实现用 {@code count == null} 判断「Redis 异常」，并在该分支放行。但
 * {@code ValueOperations.increment()} 在连接失败时是<b>抛异常</b>而不是返回 {@code null}，
 * 因此该分支是<b>死代码</b>：Redis 一挂，被拦截的 {@code /api/auth/login}、
 * {@code /api/activity/**}、{@code /api/attendance/**} 全部返回 500，而错误信息还是
 * 误导性的「数据操作失败」。本测试直接锁定「Redis 故障必须降级放行」这一契约，
 * 防止后人再次用返回值判空的方式写防护。
 */
class RateLimitInterceptorTest {

    private static final String URI = "/api/activity/page";
    private static final String REMOTE_IP = "10.0.0.1";

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOps = mock(ValueOperations.class);
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private RateLimitInterceptor interceptor(int maxPerMinute) {
        return interceptor(maxPerMinute, true);
    }

    private RateLimitInterceptor interceptor(int maxPerMinute, boolean enabled) {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        return new RateLimitInterceptor(redisTemplate, objectMapper, maxPerMinute, enabled);
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", URI);
        request.setRequestURI(URI);
        request.setRemoteAddr(REMOTE_IP);
        return request;
    }

    @Test
    @DisplayName("窗口内首个请求：放行并把 key 设为 1 分钟过期")
    void firstRequestWithinWindowIsAllowedAndSetsExpiry() throws Exception {
        when(valueOps.increment("ratelimit:" + REMOTE_IP + ":" + URI)).thenReturn(1L);

        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean allowed = interceptor(60).preHandle(request(), response, new Object());

        assertThat(allowed).isTrue();
        verify(redisTemplate).expire("ratelimit:" + REMOTE_IP + ":" + URI, Duration.ofMinutes(1));
    }

    @Test
    @DisplayName("未达阈值：放行且不重复设置过期时间")
    void belowThresholdIsAllowedWithoutResettingExpiry() throws Exception {
        when(valueOps.increment(anyString())).thenReturn(30L);

        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean allowed = interceptor(60).preHandle(request(), response, new Object());

        assertThat(allowed).isTrue();
        verify(redisTemplate, never()).expire(anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("超过阈值：返回 429 并写入统一错误结构")
    void overThresholdReturns429() throws Exception {
        when(valueOps.increment(anyString())).thenReturn(61L);

        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean allowed = interceptor(60).preHandle(request(), response, new Object());

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                .contains("429")
                .contains("请求过于频繁");
    }

    @Test
    @DisplayName("Redis 不可用（increment 抛异常）：降级放行，绝不让请求 500")
    void redisFailureDegradesToAllow() throws Exception {
        when(valueOps.increment(anyString()))
                .thenThrow(new RedisConnectionFailureException("Unable to connect to Redis"));

        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean allowed = interceptor(60).preHandle(request(), response, new Object());

        assertThat(allowed).as("Redis 故障时必须是放行（true）而不是抛异常").isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("Redis 在 expire 阶段失败：同样降级放行")
    void expireFailureDegradesToAllow() throws Exception {
        when(valueOps.increment(anyString())).thenReturn(1L);
        when(redisTemplate.expire(anyString(), any(Duration.class)))
                .thenThrow(new RedisConnectionFailureException("Unable to connect to Redis"));

        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean allowed = interceptor(60).preHandle(request(), response, new Object());

        assertThat(allowed).as("expire 失败也不得影响主流程").isTrue();
    }

    @Test
    @DisplayName("increment 返回 null（理论上不应发生）：放行且不抛异常")
    void nullCountIsAllowed() throws Exception {
        when(valueOps.increment(anyString())).thenReturn(null);

        MockHttpServletResponse response = new MockHttpServletResponse();
        assertThat(interceptor(60).preHandle(request(), response, new Object())).isTrue();
    }

    @Test
    @DisplayName("限流开关关闭：不访问 Redis 直接放行")
    void disabledSkipsRedisEntirely() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean allowed = interceptor(60, false).preHandle(request(), response, new Object());

        assertThat(allowed).isTrue();
        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    @DisplayName("阈值为 0 或负数：视为关闭限流")
    void nonPositiveThresholdDisablesLimiting() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertThat(interceptor(0).preHandle(request(), response, new Object())).isTrue();
        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    @DisplayName("客户端 IP 优先取 X-Forwarded-For 的第一段")
    void clientIpPrefersFirstForwardedForEntry() throws Exception {
        when(valueOps.increment(anyString())).thenReturn(1L);

        MockHttpServletRequest request = request();
        request.addHeader("X-Forwarded-For", "203.0.113.7, 10.1.1.1, 10.2.2.2");

        interceptor(60).preHandle(request, new MockHttpServletResponse(), new Object());

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(valueOps).increment(key.capture());
        assertThat(key.getValue()).isEqualTo("ratelimit:203.0.113.7:" + URI);
    }

    @Test
    @DisplayName("无 X-Forwarded-For 时回退到 X-Real-IP")
    void clientIpFallsBackToRealIp() throws Exception {
        when(valueOps.increment(anyString())).thenReturn(1L);

        MockHttpServletRequest request = request();
        request.addHeader("X-Real-IP", "198.51.100.9");

        interceptor(60).preHandle(request, new MockHttpServletResponse(), new Object());

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(valueOps).increment(key.capture());
        assertThat(key.getValue()).isEqualTo("ratelimit:198.51.100.9:" + URI);
    }

    @Test
    @DisplayName("X-Forwarded-For 为 unknown 时回退到 remoteAddr")
    void unknownForwardedForFallsBackToRemoteAddr() throws Exception {
        when(valueOps.increment(anyString())).thenReturn(1L);

        MockHttpServletRequest request = request();
        request.addHeader("X-Forwarded-For", "unknown");

        interceptor(60).preHandle(request, new MockHttpServletResponse(), new Object());

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(valueOps).increment(key.capture());
        assertThat(key.getValue()).isEqualTo("ratelimit:" + REMOTE_IP + ":" + URI);
    }
}
