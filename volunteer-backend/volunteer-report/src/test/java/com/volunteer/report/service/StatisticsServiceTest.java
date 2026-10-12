package com.volunteer.report.service;

import com.volunteer.report.dto.ActivityTrendDTO;
import com.volunteer.report.dto.ServiceDurationDTO;
import com.volunteer.report.dto.VolunteerProfileDTO;
import com.volunteer.report.entity.ActivityMetric;
import com.volunteer.report.entity.UserDuration;
import com.volunteer.report.mapper.ActivityMetricMapper;
import com.volunteer.report.mapper.UserDurationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("StatisticsService 单元测试")
class StatisticsServiceTest {

    @Mock
    private ActivityMetricMapper activityMetricMapper;

    @Mock
    private UserDurationMapper userDurationMapper;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private StatisticsService statisticsService;

    @BeforeEach
    void setUp() {
    }

    @Test
    @DisplayName("获取活动趋势数据 - 返回按日期聚合的结果")
    void testGetActivityTrend() {
        ActivityMetric metric1 = new ActivityMetric();
        metric1.setMetricDate(LocalDate.now().minusDays(1));
        metric1.setMetricValue(10.0);
        metric1.setActivityId(1L);

        ActivityMetric metric2 = new ActivityMetric();
        metric2.setMetricDate(LocalDate.now());
        metric2.setMetricValue(20.0);
        metric2.setActivityId(2L);

        when(activityMetricMapper.selectList(any()))
                .thenReturn(Arrays.asList(metric1, metric2));

        List<ActivityTrendDTO> result = statisticsService.getActivityTrend(
                LocalDate.now().minusDays(7), LocalDate.now(), "DAY");

        assertThat(result).isNotNull();
        assertThat(result).isNotEmpty();
        assertThat(result.stream().filter(d -> d.getCount() > 0).count()).isGreaterThan(0);
    }

    @Test
    @DisplayName("获取服务时长统计 - 按活动ID聚合")
    void testGetServiceDuration() {
        UserDuration d1 = new UserDuration();
        d1.setActivityId(1L);
        d1.setOriginalDuration(new BigDecimal("5.0"));
        d1.setUserId(1L);

        UserDuration d2 = new UserDuration();
        d2.setActivityId(1L);
        d2.setOriginalDuration(new BigDecimal("3.0"));
        d2.setUserId(2L);

        UserDuration d3 = new UserDuration();
        d3.setActivityId(2L);
        d3.setOriginalDuration(new BigDecimal("4.0"));
        d3.setUserId(3L);

        when(userDurationMapper.selectList(any()))
                .thenReturn(Arrays.asList(d1, d2, d3));

        List<ServiceDurationDTO> result = statisticsService.getServiceDuration(
                null, null, null);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getTotalDuration()).isEqualTo(8.0);
        assertThat(result.get(1).getTotalDuration()).isEqualTo(4.0);
    }

    @Test
    @DisplayName("获取签到率 - 返回百分比")
    void testGetCheckinRate() {
        when(jdbcTemplate.queryForObject(contains("COUNT(*) FROM activity_registration"), eq(Long.class), eq(1L)))
                .thenReturn(10L);
        when(jdbcTemplate.queryForObject(contains("COUNT(DISTINCT ar.id)"), eq(Long.class), eq(1L)))
                .thenReturn(8L);
        when(activityMetricMapper.selectList(any())).thenReturn(Arrays.asList());

        Map<String, Object> result = statisticsService.getCheckinRate(1L);

        assertThat(result).isNotNull();
        assertThat(result.get("rate")).isEqualTo(80.0);
        assertThat(result.get("participants")).isEqualTo(10L);
        assertThat(result.get("checkins")).isEqualTo(8L);
    }

    @Test
    @DisplayName("获取签到率 - 无数据时返回0")
    void testGetCheckinRate_Empty() {
        when(jdbcTemplate.queryForObject(contains("COUNT(*) FROM activity_registration"), eq(Long.class), eq(1L)))
                .thenReturn(0L);
        when(jdbcTemplate.queryForObject(contains("COUNT(DISTINCT ar.id)"), eq(Long.class), eq(1L)))
                .thenReturn(0L);
        when(activityMetricMapper.selectList(any())).thenReturn(Arrays.asList());

        Map<String, Object> result = statisticsService.getCheckinRate(1L);

        assertThat(result.get("rate")).isEqualTo(0);
        assertThat(result.get("participants")).isEqualTo(0);
    }

    @Test
    @DisplayName("获取志愿者画像 - 返回技能评分")
    void testGetVolunteerProfile() {
        UserDuration d = new UserDuration();
        d.setUserId(1L);
        d.setOriginalDuration(new BigDecimal("10.0"));

        when(userDurationMapper.selectList(any())).thenReturn(Arrays.asList(d));

        VolunteerProfileDTO result = statisticsService.getVolunteerProfile(1L);

        assertThat(result).isNotNull();
        assertThat(result.getSkillScores()).isNotEmpty();
        assertThat(result.getSkillScores()).containsKeys(
                "\u53c2\u4e0e\u6d3b\u8dc3\u5ea6",
                "\u670d\u52a1\u65f6\u957f",
                "\u5c65\u7ea6\u7a33\u5b9a\u6027",
                "\u6210\u957f\u8868\u73b0");
        assertThat(result.getSkillScores().get("\u53c2\u4e0e\u6d3b\u8dc3\u5ea6")).isEqualTo(55.0);
        assertThat(result.getSkillScores().get("\u670d\u52a1\u65f6\u957f")).isEqualTo(65.0);
        assertThat(result.getSkillScores().get("\u5c65\u7ea6\u7a33\u5b9a\u6027")).isEqualTo(64.0);
        assertThat(result.getSkillScores().get("\u6210\u957f\u8868\u73b0")).isEqualTo(60.0);
        assertThat(result.getCreditScore()).isEqualTo(61.3);
    }
}
