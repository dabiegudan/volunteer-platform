package com.volunteer.auth.service;

import com.volunteer.auth.mapper.SysRolePermissionMapper;
import com.volunteer.auth.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 用户权限加载服务(R-01 实施)
 * <p>
 * 取代旧版本基于"角色码硬编码"的判定,真正消费 sys_role_permission 表;
 * 通过 Redis 缓存(5 分钟)降低数据库压力,key=user:perm:{userId}。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserPermissionService {

    private final SysUserRoleMapper userRoleMapper;
    private final SysRolePermissionMapper rolePermissionMapper;
    private final StringRedisTemplate redisTemplate;

    private static final long CACHE_TTL_MINUTES = 5L;
    private static final String CACHE_KEY_PREFIX = "user:perm:";

    /**
     * 加载用户拥有的权限码(从 Redis 优先,未命中回源 DB)
     *
     * @param userId 用户 id
     * @return 权限码集合(可能为空)
     */
    public Set<String> loadPermissionCodes(Long userId) {
        if (userId == null) {
            return Collections.emptySet();
        }
        String cacheKey = CACHE_KEY_PREFIX + userId;
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null && !cached.isEmpty()) {
                return splitCodes(cached);
            }
        } catch (Exception ex) {
            log.warn("Redis 读取权限缓存失败,降级到数据库 userId={}", userId, ex);
        }

        Set<String> codes = loadFromDb(userId);
        if (codes.isEmpty()) {
            return codes;
        }
        try {
            redisTemplate.opsForValue().set(
                    cacheKey,
                    joinCodes(codes),
                    CACHE_TTL_MINUTES,
                    TimeUnit.MINUTES);
        } catch (Exception ex) {
            log.warn("Redis 写入权限缓存失败 userId={}", userId, ex);
        }
        return codes;
    }

    /**
     * 失效用户权限缓存(角色变更、权限分配后调用)
     */
    public void evict(Long userId) {
        if (userId == null) {
            return;
        }
        try {
            redisTemplate.delete(CACHE_KEY_PREFIX + userId);
        } catch (Exception ex) {
            log.warn("Redis 清除权限缓存失败 userId={}", userId, ex);
        }
    }

    private Set<String> loadFromDb(Long userId) {
        List<Long> roleIds = userRoleMapper.selectRoleIdsByUserId(userId);
        if (roleIds == null || roleIds.isEmpty()) {
            return Collections.emptySet();
        }
        List<String> codes = rolePermissionMapper.selectPermissionCodesByRoleIds(roleIds);
        if (codes == null || codes.isEmpty()) {
            return Collections.emptySet();
        }
        return codes.stream().filter(c -> c != null && !c.isEmpty()).collect(Collectors.toSet());
    }

    private String joinCodes(Set<String> codes) {
        return String.join(",", codes);
    }

    private Set<String> splitCodes(String cached) {
        if (cached == null || cached.isEmpty()) {
            return Collections.emptySet();
        }
        return java.util.Arrays.stream(cached.split(","))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }
}
