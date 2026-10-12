package com.volunteer.report.service;

import com.volunteer.report.entity.CreditTransaction;
import com.volunteer.report.mapper.CreditTransactionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * R-08 实施:CreditService.getTransactionsPage 单元测试
 * <p>覆盖默认参数、size 上限、按 type 路由、按时间范围路由、total/records 结构。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CreditService.getTransactionsPage 单元测试")
class CreditServicePaginationTest {

    @Mock
    private CreditTransactionMapper transactionMapper;

    private CreditService creditService;

    @BeforeEach
    void setUp() {
        // 仅构造被测 service 必需的 mapper,避免引入其他 mapper 依赖
        creditService = new CreditService(
                null, transactionMapper, null, null, null, null, null, null, null);
    }

    @Test
    @DisplayName("默认分页:page=1,size=20 → offset=0,size=20")
    void defaultPaging() {
        when(transactionMapper.countByUserId(1L)).thenReturn(100L);
        when(transactionMapper.findByUserIdPage(eq(1L), eq(0), eq(20)))
                .thenReturn(Collections.emptyList());

        Map<String, Object> result = creditService.getTransactionsPage(1L, null, null, null, 1, 20);

        assertThat(result).containsEntry("total", 100L);
        assertThat(result).containsEntry("page", 1);
        assertThat(result).containsEntry("size", 20);
        assertThat(result.get("records")).isInstanceOf(java.util.List.class);
    }

    @Test
    @DisplayName("size 上限保护:size=9999 → 实际 size=100")
    void sizeClampedToMax() {
        when(transactionMapper.countByUserId(1L)).thenReturn(0L);
        when(transactionMapper.findByUserIdPage(eq(1L), anyInt(), eq(100)))
                .thenReturn(Collections.emptyList());

        Map<String, Object> result = creditService.getTransactionsPage(1L, null, null, null, 1, 9999);

        assertThat(result.get("size")).isEqualTo(100);
        verify(transactionMapper).findByUserIdPage(1L, 0, 100);
    }

    @Test
    @DisplayName("page 边界保护:page<=0 → 实际 page=1")
    void pageClampedToMin() {
        when(transactionMapper.countByUserId(1L)).thenReturn(0L);
        when(transactionMapper.findByUserIdPage(eq(1L), eq(0), eq(20)))
                .thenReturn(Collections.emptyList());

        Map<String, Object> result = creditService.getTransactionsPage(1L, null, null, null, 0, 20);

        assertThat(result.get("page")).isEqualTo(1);
        assertThat(result.get("size")).isEqualTo(20);
    }

    @Test
    @DisplayName("按 type 路由:应调用 findByUserIdAndTypePage 与对应 count")
    void routedByType() {
        when(transactionMapper.countByUserIdAndType(1L, "EARN")).thenReturn(7L);
        when(transactionMapper.findByUserIdAndTypePage(eq(1L), eq("EARN"), eq(0), eq(20)))
                .thenReturn(Collections.emptyList());

        Map<String, Object> result = creditService.getTransactionsPage(1L, "EARN", null, null, 1, 20);

        assertThat(result.get("total")).isEqualTo(7L);
        verify(transactionMapper).countByUserIdAndType(1L, "EARN");
        verify(transactionMapper).findByUserIdAndTypePage(1L, "EARN", 0, 20);
    }

    @Test
    @DisplayName("按时间范围路由:应调用 findByUserIdAndTimeRangePage")
    void routedByTimeRange() {
        when(transactionMapper.countByUserIdAndTimeRange(anyLong(), any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(15L);
        when(transactionMapper.findByUserIdAndTimeRangePage(anyLong(), any(LocalDateTime.class), any(LocalDateTime.class), eq(0), eq(20)))
                .thenReturn(Collections.emptyList());

        Map<String, Object> result = creditService.getTransactionsPage(
                1L, null, "2026-01-01", "2026-01-31", 1, 20);

        assertThat(result.get("total")).isEqualTo(15L);
        ArgumentCaptor<LocalDateTime> startCap = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> endCap = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(transactionMapper).countByUserIdAndTimeRange(eq(1L), startCap.capture(), endCap.capture());
        // 起始 00:00:00,结束 23:59:59
        assertThat(startCap.getValue().toLocalTime()).isEqualTo(java.time.LocalTime.MIDNIGHT);
        assertThat(endCap.getValue().getHour()).isEqualTo(23);
    }

    @Test
    @DisplayName("offset 计算:page=3,size=10 → offset=20")
    void offsetCalculation() {
        when(transactionMapper.countByUserId(1L)).thenReturn(0L);
        when(transactionMapper.findByUserIdPage(eq(1L), eq(20), eq(10)))
                .thenReturn(Collections.emptyList());

        Map<String, Object> result = creditService.getTransactionsPage(1L, null, null, null, 3, 10);

        assertThat(result.get("page")).isEqualTo(3);
        verify(transactionMapper).findByUserIdPage(1L, 20, 10);
    }

    @Test
    @DisplayName("records 应为 CreditTransaction 列表")
    void recordsType() {
        when(transactionMapper.countByUserId(1L)).thenReturn(0L);
        when(transactionMapper.findByUserIdPage(eq(1L), eq(0), eq(20)))
                .thenReturn(Collections.singletonList(new CreditTransaction()));

        Map<String, Object> result = creditService.getTransactionsPage(1L, null, null, null, 1, 20);

        @SuppressWarnings("unchecked")
        List<CreditTransaction> records = (List<CreditTransaction>) result.get("records");
        assertThat(records).hasSize(1);
    }
}
