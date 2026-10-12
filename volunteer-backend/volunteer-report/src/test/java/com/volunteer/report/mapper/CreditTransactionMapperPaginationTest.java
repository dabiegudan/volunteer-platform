package com.volunteer.report.mapper;

import com.volunteer.report.entity.CreditTransaction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * R-08 实施:CreditTransactionMapper 分页查询契约测试
 * <p>本测试为 Mapper 契约级单元测试,使用 Mockito 验证 SQL 调用与参数绑定,
 * 真实 DB 集成测试需要在 profile=integration 环境跑(留待后续)。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CreditTransactionMapper 分页契约测试")
class CreditTransactionMapperPaginationTest {

    @Mock
    private CreditTransactionMapper mapper;

    @Test
    @DisplayName("findByUserIdPage 应按 offset/size 限制返回")
    void findByUserIdPageBounds() {
        when(mapper.findByUserIdPage(eq(1L), eq(20), eq(10)))
                .thenReturn(Collections.emptyList());

        List<CreditTransaction> page = mapper.findByUserIdPage(1L, 20, 10);
        assertThat(page).isEmpty();

        ArgumentCaptor<Integer> offsetCap = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> sizeCap = ArgumentCaptor.forClass(Integer.class);
        verify(mapper).findByUserIdPage(eq(1L), offsetCap.capture(), sizeCap.capture());
        assertThat(offsetCap.getValue()).isEqualTo(20);
        assertThat(sizeCap.getValue()).isEqualTo(10);
    }

    @Test
    @DisplayName("findByUserIdAndTypePage 应绑定 type 参数")
    void findByUserIdAndTypePage() {
        when(mapper.findByUserIdAndTypePage(eq(2L), eq("EARN"), eq(0), eq(20)))
                .thenReturn(Collections.emptyList());

        List<CreditTransaction> page = mapper.findByUserIdAndTypePage(2L, "EARN", 0, 20);
        assertThat(page).isEmpty();
        verify(mapper).findByUserIdAndTypePage(2L, "EARN", 0, 20);
    }

    @Test
    @DisplayName("findByUserIdAndTimeRangePage 应绑定时间范围")
    void findByUserIdAndTimeRangePage() {
        LocalDateTime start = LocalDateTime.of(2026, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 1, 31, 23, 59, 59);
        when(mapper.findByUserIdAndTimeRangePage(eq(3L), eq(start), eq(end), eq(0), eq(50)))
                .thenReturn(Collections.emptyList());

        List<CreditTransaction> page = mapper.findByUserIdAndTimeRangePage(3L, start, end, 0, 50);
        assertThat(page).isEmpty();
        verify(mapper).findByUserIdAndTimeRangePage(3L, start, end, 0, 50);
    }

    @Test
    @DisplayName("countByUserId 返回 long,可用于分页 total")
    void countByUserId() {
        when(mapper.countByUserId(anyLong())).thenReturn(123L);
        long total = mapper.countByUserId(1L);
        assertThat(total).isEqualTo(123L);
    }

    @Test
    @DisplayName("countByUserIdAndType 与 countByUserIdAndTimeRange 返回 long")
    void countByUserIdAndTypeAndRange() {
        LocalDateTime start = LocalDateTime.now().minusDays(7);
        LocalDateTime end = LocalDateTime.now();
        when(mapper.countByUserIdAndType(1L, "EARN")).thenReturn(5L);
        when(mapper.countByUserIdAndTimeRange(1L, start, end)).thenReturn(11L);

        assertThat(mapper.countByUserIdAndType(1L, "EARN")).isEqualTo(5L);
        assertThat(mapper.countByUserIdAndTimeRange(1L, start, end)).isEqualTo(11L);
    }
}
