package com.volunteer.config;

import com.volunteer.common.result.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.time.Duration;

/**
 * 基于 Redis 的固定窗口限流拦截器。
 *
 * <p>关键设计：限流是<b>非业务</b>的旁路能力，Redis 不可用时必须放行而不是让请求失败。
 * 早期实现用 {@code count == null} 判断 Redis 异常，但 {@code increment()} 在连接失败时是
 * <b>直接抛异常</b>而不会返回 null，导致该分支成为死代码 —— Redis 一挂，被拦截的
 * {@code /api/auth/login}、{@code /api/activity/**}、{@code /api/attendance/**} 全部 500。
 * 现在统一用 try-catch 兜住所有 Redis 交互，失败即降级放行。</p>
 */
@Slf4j
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /** 每个「IP + URI」组合在窗口内允许的请求数。 */
    private final int maxRequestsPerWindow;

    /** 是否启用限流（Redis 故障时自动降级放行，与该开关无关）。 */
    private final boolean enabled;

    public RateLimitInterceptor(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${app.rate-limit.max-requests-per-minute:60}") int maxRequestsPerWindow,
            @Value("${app.rate-limit.enabled:true}") boolean enabled) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.maxRequestsPerWindow = maxRequestsPerWindow;
        this.enabled = enabled;
    }

    private static final String RATE_LIMIT_PREFIX = "ratelimit:";
    private static final Duration WINDOW = Duration.ofMinutes(1);

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!enabled || maxRequestsPerWindow <= 0) {
            return true;
        }

        String ip = getClientIp(request);
        String uri = request.getRequestURI();
        String key = RATE_LIMIT_PREFIX + ip + ":" + uri;

        final Long count;
        try {
            // 原子自增：避免「读取-判断-写入」三步之间的竞态（并发下会放行超额请求）
            count = redisTemplate.opsForValue().increment(key);
            // 首次请求时设置过期时间，形成固定的 1 分钟窗口
            if (count != null && count == 1L) {
                redisTemplate.expire(key, WINDOW);
            }
        } catch (RuntimeException ex) {
            // Redis 不可用：限流降级放行，绝不能让旁路能力拖垮主流程
            log.warn("限流组件不可用,本次放行 uri={}, ip={}, cause={}", uri, ip, ex.getMessage());
            return true;
        }

        if (count == null) {
            return true;
        }
        if (count > maxRequestsPerWindow) {
            log.warn("Rate limit exceeded for IP: {}, URI: {}, count: {}", ip, uri, count);
            sendRateLimitResponse(response);
            return false;
        }

        return true;
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    private void sendRateLimitResponse(HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setStatus(429);

        Result<Void> result = Result.error(429, "请求过于频繁，请稍后再试");
        response.getWriter().write(objectMapper.writeValueAsString(result));
    }
}
