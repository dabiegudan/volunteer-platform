package com.volunteer.activity.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.volunteer.activity.entity.Activity;
import com.volunteer.activity.service.ActivityService;
import com.volunteer.common.enums.ActivityStatusEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.Arrays;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 活动控制器 Web MVC 测试
 * 测试 REST API 端点的请求/响应
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ActivityController API 测试")
class ActivityControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ActivityService activityService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ActivityController(activityService)).build();
        // 准备测试数据
    }

    @Test
    @DisplayName("GET /activity/page - 分页查询活动列表")
    void testGetActivityPage() throws Exception {
        // 准备模拟数据
        Page<Activity> mockPage = new Page<>(1, 10);
        Activity activity = new Activity();
        activity.setId(1L);
        activity.setTitle("测试活动");
        activity.setCategory("环保");
        activity.setStatus(ActivityStatusEnum.RECRUITING.name());
        activity.setLocation("市中心公园");
        activity.setCreateTime(LocalDateTime.now());
        mockPage.setRecords(Arrays.asList(activity));
        mockPage.setTotal(1);

        when(activityService.getActivityPage(anyInt(), anyInt(), isNull(), isNull(),
                isNull(), isNull(), isNull(), isNull(), isNull(),
                isNull(), isNull(), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(mockPage);

        // 执行请求
        mockMvc.perform(get("/api/activity/page")
                .param("pageNum", "1")
                .param("pageSize", "10")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.list").isArray())
            .andExpect(jsonPath("$.data.list[0].title").value("测试活动"))
            .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    @DisplayName("GET /activity/page - 带筛选参数")
    void testGetActivityPage_WithFilters() throws Exception {
        Page<Activity> mockPage = new Page<>(1, 10);
        mockPage.setRecords(Arrays.asList());
        mockPage.setTotal(0);

        when(activityService.getActivityPage(anyInt(), anyInt(), any(), any(),
                isNull(), isNull(), isNull(), isNull(), isNull(),
                isNull(), isNull(), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(mockPage);

        mockMvc.perform(get("/api/activity/page")
                .param("pageNum", "1")
                .param("pageSize", "10")
                .param("category", "环保")
                .param("keyword", "清洁")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("GET /activity/{id} - 获取活动详情")
    void testGetActivityById() throws Exception {
        Activity activity = new Activity();
        activity.setId(1L);
        activity.setTitle("测试活动");
        activity.setDescription("这是一个测试活动");

        when(activityService.getActivityById(1L, null)).thenReturn(activity);

        mockMvc.perform(get("/api/activity/1")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.title").value("测试活动"))
            .andExpect(jsonPath("$.data.description").value("这是一个测试活动"));
    }
}
