package com.volunteer.auth.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
public class JwtUtil {
    
    /**
     * JWT 签名密钥。
     *
     * <p>默认值故意留空（{@code ${jwt.secret:}}），而不是在源码里兜底一个"看起来能用"的密钥：
     * 一旦有兜底值，本地/生产漏配时应用会带着公开密钥正常启动，
     * 任何人拿到仓库即可伪造任意用户的 Token。因此这里选择启动即失败，见 {@link #init()}。
     *
     * <p>{@code jwt.secret} 的实际来源（优先级由高到低）：
     * <ol>
     *   <li>环境变量 {@code JWT_SECRET}（容器由 docker-compose 注入；本机由 load-env 脚本导入）</li>
     *   <li>{@code application-local.yml} —— dev profile 通过 {@code spring.config.import} 加载，
     *       文件已被 .gitignore 忽略，本机开发无需任何环境变量即可启动</li>
     * </ol>
     */
    @Value("${jwt.secret:}")
    private String secret;
    
    @Value("${jwt.expiration:604800000}")
    private Long expiration;
    
    /**
     * 启动期校验：密钥缺失、仍是模板占位值、或过短，都是致命配置错误，直接终止启动（快速失败）。
     * 放在启动期而不是签发时，是为了让问题在部署阶段暴露，而不是等用户登录才 500。
     */
    @PostConstruct
    public void init() {
        if (secret == null || secret.isEmpty()) {
            throw new IllegalStateException(
                    "JWT secret must be configured: set jwt.secret in application-local.yml "
                            + "(dev) or the JWT_SECRET environment variable");
        }
        if (secret.startsWith("CHANGE_ME")) {
            // 典型场景：只复制了 application-local.yml.example，忘记替换示例值。
            // 若放行，服务照常启动但签名密钥是公开的，属于"看起来正常"的高危配置。
            throw new IllegalStateException(
                    "jwt.secret 仍是 application-local.yml.example 中的示例值（CHANGE_ME...）："
                            + "请生成随机密钥后写入 application-local.yml");
        }
        if (secret.length() < 32) {
            throw new IllegalStateException(
                    "JWT secret must be at least 32 characters (HS256 安全下限)");
        }
    }
    
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }
    
    public String generateToken(Long userId, String username) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("username", username);
        return createToken(claims, username);
    }
    
    private String createToken(Map<String, Object> claims, String subject) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);
        
        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }
    
    public Claims getClaimsFromToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
    
    public Long getUserIdFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims.get("userId", Long.class);
    }
    
    public String getUsernameFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims.getSubject();
    }
    
    public Boolean isTokenExpired(String token) {
        Date expiration = getClaimsFromToken(token).getExpiration();
        return expiration.before(new Date());
    }
    
    public Boolean validateToken(String token) {
        try {
            return !isTokenExpired(token);
        } catch (Exception e) {
            return false;
        }
    }
}
