package com.volunteer.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.volunteer.auth.entity.SysOrganization;
import com.volunteer.auth.mapper.SysOrganizationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * R-06 实施:CollegeService 单元测试
 * <p>覆盖接口实现、缓存命中、缓存未命中、空集、脏缓存自愈、Redis 降级。</p>
 *
 * <p><b>回归保护:</b>历史实现把哨兵字符串 {@code "OK"} 写入缓存,命中分支却返回空列表,
 * 使学院下拉在 TTL 内恒为空。这里的 {@code cacheHit} 用例断言"命中即返回真实列表",
 * 用于锁死该缺陷不再回归。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CollegeService 单元测试")
class CollegeServiceTest {

    private static final String CACHE_KEY = "colleges:active:v1";

    @Mock private SysOrganizationMapper organizationMapper;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;

    private CollegeService service;

    @BeforeEach
    void setUp() {
        service = new CollegeService(organizationMapper, redisTemplate, new ObjectMapper());
    }

    private SysOrganization org(long id, String name) {
        SysOrganization o = new SysOrganization();
        o.setId(id);
        o.setOrgName(name);
        return o;
    }

    @Test
    @DisplayName("正常返回学院列表,并以 JSON 数组写入缓存")
    void normal() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(CACHE_KEY)).thenReturn(null);
        when(organizationMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Arrays.asList(org(1L, "计算机学院"), org(2L, "软件学院")));

        List<SysOrganization> result = service.listActiveColleges();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getOrgName()).isEqualTo("计算机学院");

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(eq(CACHE_KEY), captor.capture(), anyLong(), any());
        String cached = captor.getValue();
        assertThat(cached).startsWith("[").endsWith("]").contains("计算机学院").contains("软件学院");
        assertThat(cached).isNotEqualTo("OK");
        assertThat(cached).isNotEqualTo("EMPTY");
    }

    @Test
    @DisplayName("缓存命中:返回缓存的真实列表,不再查 DB")
    void cacheHit() throws Exception {
        String json = new ObjectMapper()
                .writeValueAsString(Arrays.asList(org(1L, "计算机学院"), org(2L, "软件学院")));
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(CACHE_KEY)).thenReturn(json);

        List<SysOrganization> result = service.listActiveColleges();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getOrgName()).isEqualTo("计算机学院");
        verifyNoInteractions(organizationMapper);
    }

    @Test
    @DisplayName("缓存命中空数组:返回空集,不查 DB")
    void cacheHitEmptyArray() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(CACHE_KEY)).thenReturn("[]");

        List<SysOrganization> result = service.listActiveColleges();

        assertThat(result).isEmpty();
        verifyNoInteractions(organizationMapper);
    }

    @Test
    @DisplayName("脏缓存(历史 OK 哨兵/非法 JSON):忽略并回落数据库,同时覆盖自愈")
    void corruptedCacheFallsBackToDatabase() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(CACHE_KEY)).thenReturn("OK");
        when(organizationMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.singletonList(org(1L, "计算机学院")));

        List<SysOrganization> result = service.listActiveColleges();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getOrgName()).isEqualTo("计算机学院");
        verify(valueOps).set(eq(CACHE_KEY), any(), anyLong(), any());
    }

    @Test
    @DisplayName("数据库无数据:返回空集,并缓存为空 JSON 数组")
    void empty() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(CACHE_KEY)).thenReturn(null);
        when(organizationMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());

        List<SysOrganization> result = service.listActiveColleges();

        assertThat(result).isEmpty();
        verify(valueOps).set(eq(CACHE_KEY), eq("[]"), anyLong(), any());
    }

    @Test
    @DisplayName("Redis 异常降级:仍能查 DB")
    void redisDown() {
        when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("down"));
        when(organizationMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.singletonList(org(1L, "计算机学院")));

        List<SysOrganization> result = service.listActiveColleges();

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("evictCache:删除缓存键")
    void evictCache() {
        when(redisTemplate.delete(CACHE_KEY)).thenReturn(true);
        service.evictCache();
        verify(redisTemplate).delete(CACHE_KEY);
    }
}
