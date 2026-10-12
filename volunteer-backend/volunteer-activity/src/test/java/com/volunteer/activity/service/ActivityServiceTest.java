package com.volunteer.activity.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.volunteer.activity.entity.Activity;
import com.volunteer.activity.mapper.ActivityMapper;
import com.volunteer.common.enums.ActivityStatusEnum;
import com.volunteer.common.result.PageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 活动服务单元测试
 * 覆盖活动分页查询、缓存、CRUD核心逻辑
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ActivityService 单元测试")
class ActivityServiceTest {

    @Mock
    private ActivityMapper activityMapper;

    @Mock
    private ActivityCacheService activityCacheService;

    @InjectMocks
    private ActivityService activityService;

    private Activity testActivity;

    @BeforeEach
    void setUp() {
        testActivity = new Activity();
        testActivity.setId(1L);
        testActivity.setTitle("城市环保志愿活动");
        testActivity.setCategory("环保");
        testActivity.setStatus(ActivityStatusEnum.RECRUITING.name());
        testActivity.setOrganizerId(1L);
        testActivity.setCreateTime(LocalDateTime.now());
        testActivity.setCurrentParticipants(10);
    }

    @Test
    @DisplayName("分页查询活动 - 正常返回数据")
    void testGetActivityPage_Success() {
        // 准备
        int pageNum = 1;
        int pageSize = 10;
        List<Activity> mockList = Arrays.asList(
            createMockActivity(1L, "活动1", ActivityStatusEnum.RECRUITING),
            createMockActivity(2L, "活动2", ActivityStatusEnum.RECRUITING)
        );

        Page<Activity> mockPage = new Page<>(pageNum, pageSize);
        mockPage.setRecords(mockList);
        mockPage.setTotal(2);

        when(activityCacheService.buildCacheKey(anyInt(), anyInt(), isNull(), isNull())).thenReturn("test-cache-key");
        when(activityCacheService.getFromCache(eq("test-cache-key"))).thenReturn(null);
        when(activityMapper.selectPage(any(Page.class), any())).thenReturn(mockPage);

        // 执行
        Page<Activity> result = activityService.getActivityPage(pageNum, pageSize, null, null);

        // 验证
        assertThat(result).isNotNull();
        assertThat(result.getRecords()).hasSize(2);
        assertThat(result.getTotal()).isEqualTo(2);
        assertThat(result.getRecords().get(0).getTitle()).isEqualTo("活动1");

        // 验证方法调用
        verify(activityCacheService).buildCacheKey(anyInt(), anyInt(), isNull(), isNull());
        verify(activityCacheService).getFromCache(anyString());
        verify(activityMapper).selectPage(any(Page.class), any());
        verify(activityCacheService).putToCache(anyString(), any(PageResult.class));
    }

    @Test
    @DisplayName("分页查询活动 - 命中缓存")
    void testGetActivityPage_CacheHit() {
        // 准备
        PageResult<Activity> cachedResult = new PageResult<>();
        cachedResult.setList(Arrays.asList(testActivity));
        cachedResult.setTotal(1L);
        cachedResult.setPageNum(1);
        cachedResult.setPageSize(10);

        when(activityCacheService.buildCacheKey(anyInt(), anyInt(), isNull(), isNull())).thenReturn("test-cache-key");
        doReturn(cachedResult).when(activityCacheService).getFromCache(eq("test-cache-key"));

        // 执行
        Page<Activity> result = activityService.getActivityPage(1, 10, null, null);

        // 验证
        assertThat(result).isNotNull();
        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getTotal()).isEqualTo(1);

        // 缓存命中，不应查询数据库
        verify(activityMapper, never()).selectPage(any(), any());
        verify(activityCacheService, never()).putToCache(anyString(), any());
    }

    @Test
    @DisplayName("分页查询活动 - 带筛选条件")
    void testGetActivityPage_WithFilters() {
        // 准备
        Page<Activity> mockPage = new Page<>(1, 10);
        mockPage.setRecords(Arrays.asList(testActivity));
        mockPage.setTotal(1);

        when(activityMapper.selectPage(any(Page.class), any())).thenReturn(mockPage);

        // 执行（带分类和关键词筛选）
        Page<Activity> result = activityService.getActivityPage(
            1, 10, "环保", "城市", null, null, null, null, null
        );

        // 验证
        assertThat(result.getRecords()).hasSize(1);
    }

    @Test
    @DisplayName("获取活动详情 - 存在")
    void testGetActivityById_Found() {
        when(activityMapper.selectById(1L)).thenReturn(testActivity);

        Activity result = activityService.getActivityById(1L);

        assertThat(result).isNotNull();
        assertThat(result.getTitle()).isEqualTo("城市环保志愿活动");
        assertThat(result.getCategory()).isEqualTo("环保");
    }

    @Test
    @DisplayName("获取活动详情 - 不存在返回null")
    void testGetActivityById_NotFound() {
        when(activityMapper.selectById(999L)).thenReturn(null);

        Activity result = activityService.getActivityById(999L);

        assertThat(result).isNull();
    }

    private Activity createMockActivity(Long id, String title, ActivityStatusEnum status) {
        Activity activity = new Activity();
        activity.setId(id);
        activity.setTitle(title);
        activity.setStatus(status.name());
        activity.setCreateTime(LocalDateTime.now());
        activity.setOrganizerId(1L);
        return activity;
    }
}
