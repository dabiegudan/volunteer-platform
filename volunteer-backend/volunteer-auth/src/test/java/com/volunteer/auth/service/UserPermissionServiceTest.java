package com.volunteer.auth.service;

import com.volunteer.auth.mapper.SysRolePermissionMapper;
import com.volunteer.auth.mapper.SysUserRoleMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * R-01 实施:UserPermissionService 单元测试
 * <p>覆盖缓存命中、缓存未命中、角色为空、缓存异常降级四种情况。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserPermissionService 单元测试")
class UserPermissionServiceTest {

    @Mock private SysUserRoleMapper userRoleMapper;
    @Mock private SysRolePermissionMapper rolePermissionMapper;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;

    @InjectMocks private UserPermissionService service;

    @Test
    @DisplayName("缓存命中:直接返回缓存中的权限码集合")
    void cacheHit() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("user:perm:1")).thenReturn("system:user,activity:create");

        Set<String> result = service.loadPermissionCodes(1L);
        assertThat(result).containsExactlyInAnyOrder("system:user", "activity:create");
        verifyNoInteractions(userRoleMapper, rolePermissionMapper);
    }

    @Test
    @DisplayName("缓存未命中:回源 DB,写回缓存")
    void cacheMiss() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("user:perm:2")).thenReturn(null);
        when(userRoleMapper.selectRoleIdsByUserId(2L)).thenReturn(Arrays.asList(1L, 4L));
        when(rolePermissionMapper.selectPermissionCodesByRoleIds(any()))
                .thenReturn(Arrays.asList("system", "activity:create", "activity:audit"));

        Set<String> result = service.loadPermissionCodes(2L);

        assertThat(result).contains("system", "activity:create", "activity:audit");
        verify(valueOps).set(eq("user:perm:2"), anyString(), anyLong(), any());
    }

    @Test
    @DisplayName("无角色:返回空集合,不写缓存")
    void noRole() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("user:perm:3")).thenReturn(null);
        when(userRoleMapper.selectRoleIdsByUserId(3L)).thenReturn(Collections.emptyList());

        Set<String> result = service.loadPermissionCodes(3L);

        assertThat(result).isEmpty();
        verify(rolePermissionMapper, never()).selectPermissionCodesByRoleIds(any());
        verify(valueOps, never()).set(anyString(), anyString(), anyLong(), any());
    }

    @Test
    @DisplayName("Redis 异常降级:不影响主流程")
    void redisExceptionFallback() {
        when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("redis down"));
        when(userRoleMapper.selectRoleIdsByUserId(4L)).thenReturn(Arrays.asList(1L));
        when(rolePermissionMapper.selectPermissionCodesByRoleIds(any()))
                .thenReturn(Collections.singletonList("system:user"));

        Set<String> result = service.loadPermissionCodes(4L);

        assertThat(result).contains("system:user");
    }

    @Test
    @DisplayName("evict:删除缓存键")
    void evict() {
        when(redisTemplate.delete("user:perm:5")).thenReturn(true);
        service.evict(5L);
        verify(redisTemplate).delete("user:perm:5");
    }

    @Test
    @DisplayName("userId=null:直接返回空集")
    void nullUserId() {
        assertThat(service.loadPermissionCodes(null)).isEmpty();
        verifyNoInteractions(redisTemplate, userRoleMapper);
    }
}
