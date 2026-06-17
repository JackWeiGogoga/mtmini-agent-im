package com.gogoga.backend.service.promotion;

import com.gogoga.backend.entity.mt.MtPromotionAutoBidCandidate;
import com.gogoga.backend.entity.mt.MtPromotionAdRealtimeSnapshot;
import com.gogoga.backend.entity.mt.MtPromotionMetricSnapshot;
import com.gogoga.backend.mapper.MtOrderMapper;
import com.gogoga.backend.mapper.MtPromotionMetricSnapshotMapper;
import com.gogoga.backend.service.MtPromotionAdRealtimeSnapshotService;
import com.gogoga.backend.service.MtWaimaiAdDecryptService;
import com.gogoga.backend.util.JsonUtil;
import com.meituan.sdk.MeituanClient;
import com.meituan.sdk.MeituanResponse;
import com.meituan.sdk.model.wmoperNg.waimaiad.adBalanceInfo.AdBalanceInfoResponse;
import com.meituan.sdk.model.wmoperNg.waimaiad.adBalanceInfo.Result;
import com.meituan.sdk.model.wmoperNg.waimaiad.decrypt.DecryptResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 美团推广指标提供器测试
 */
@ExtendWith(MockitoExtension.class)
class MtPromotionMetricsProviderTest {

    private static final String MT_SUCCESS_CODE = "OP_SUCCESS";

    @Mock
    private MeituanClient meituanClient;

    @Mock
    private MtOrderMapper mtOrderMapper;

    @Mock
    private MtPromotionMetricSnapshotMapper mtPromotionMetricSnapshotMapper;

    @Mock
    private MtPromotionAdRealtimeSnapshotService mtPromotionAdRealtimeSnapshotService;

    private MtPromotionMetricsProvider mtPromotionMetricsProvider;

    @BeforeEach
    void setUp() {
        MtWaimaiAdDecryptService mtWaimaiAdDecryptService = new MtWaimaiAdDecryptService(meituanClient);
        mtPromotionMetricsProvider = new MtPromotionMetricsProvider(
                meituanClient,
                mtOrderMapper,
                mtPromotionMetricSnapshotMapper,
                mtPromotionAdRealtimeSnapshotService,
                mtWaimaiAdDecryptService
        );
    }

    @Test
    void shouldBuildCumulativeMetricsAndSlotDeltaFromExecutionWindow() throws Exception {
        MtPromotionAutoBidCandidate candidate = buildCandidate();
        LocalDateTime slotTime = LocalDateTime.of(2026, 3, 28, 10, 0);
        PromotionExecutionWindow executionWindow = PromotionExecutionWindow.of(
                LocalDateTime.of(2026, 3, 28, 9, 30),
                LocalDateTime.of(2026, 3, 28, 22, 0)
        );
        MtPromotionAdRealtimeSnapshot currentRealtimeSnapshot = buildRealtimeSnapshot(slotTime, 1000L);
        MtPromotionAdRealtimeSnapshot windowBaselineSnapshot = buildRealtimeSnapshot(executionWindow.getWindowStartAt(), 900L);
        MtPromotionAdRealtimeSnapshot previousSlotSnapshot = buildRealtimeSnapshot(slotTime.minusMinutes(10), 970L);
        mockAdBalance("encrypted-debit", "2050");
        when(mtOrderMapper.sumIncomeByShopIdAndOrderSendTimeBetweenExcludingStatus(
                eq(1001L),
                any(Integer.class),
                any(Long.class),
                any(Long.class)
        )).thenReturn(new BigDecimal("200.00"));
        when(mtPromotionAdRealtimeSnapshotService.captureCurrentSlotSnapshot(
                eq(1001L),
                eq("token"),
                eq(slotTime)
        )).thenReturn(currentRealtimeSnapshot);
        when(mtPromotionAdRealtimeSnapshotService.selectByShopIdAndBizDateAndSlotTime(
                eq(1001L),
                eq(slotTime.toLocalDate()),
                eq(slotTime)
        )).thenReturn(null);
        when(mtPromotionAdRealtimeSnapshotService.selectByShopIdAndBizDateAndSlotTime(eq(1001L), eq(slotTime.toLocalDate()), eq(executionWindow.getWindowStartAt())))
                .thenReturn(windowBaselineSnapshot);
        when(mtPromotionAdRealtimeSnapshotService.selectByShopIdAndBizDateAndSlotTime(eq(1001L), eq(slotTime.minusMinutes(10).toLocalDate()), eq(slotTime.minusMinutes(10))))
                .thenReturn(previousSlotSnapshot);

        PromotionMetricsSnapshot snapshot = mtPromotionMetricsProvider.queryMetrics(candidate, executionWindow, slotTime);

        assertEquals(new BigDecimal("41.00"), snapshot.getPromotionBalance());
        assertEquals(new BigDecimal("10.00"), snapshot.getCostCounterValue());
        assertEquals(new BigDecimal("1.00"), snapshot.getWindowCost());
        assertEquals(new BigDecimal("200.00"), snapshot.getWindowIncome());
        assertEquals(new BigDecimal("0.005000"), snapshot.getWindowCostRatio());
        assertEquals(new BigDecimal("0.30"), snapshot.getSlotCostDelta());
        assertTrue(snapshot.isMetricsTrusted());
        assertTrue(snapshot.getPayloadJson().contains("mt.waimaiad.adDataDetail.realtime"));
    }

    @Test
    void shouldReusePreviousDayLastSnapshotAsCrossDayBaseline() throws Exception {
        MtPromotionAutoBidCandidate candidate = buildCandidate();
        LocalDateTime slotTime = LocalDateTime.of(2026, 3, 29, 0, 10);
        PromotionExecutionWindow executionWindow = PromotionExecutionWindow.of(
                LocalDateTime.of(2026, 3, 28, 22, 0),
                LocalDateTime.of(2026, 3, 29, 2, 0)
        );
        MtPromotionAdRealtimeSnapshot currentRealtimeSnapshot = buildRealtimeSnapshot(slotTime, 50L);
        MtPromotionAdRealtimeSnapshot crossDayBaselineSnapshot = buildRealtimeSnapshot(LocalDateTime.of(2026, 3, 28, 22, 0), 1800L);
        MtPromotionAdRealtimeSnapshot previousDayLastSnapshot = buildRealtimeSnapshot(LocalDateTime.of(2026, 3, 28, 23, 50), 1820L);
        MtPromotionAdRealtimeSnapshot previousSlotSnapshot = buildRealtimeSnapshot(LocalDateTime.of(2026, 3, 29, 0, 0), 20L);

        mockAdBalance("encrypted-debit", "1850");
        when(mtOrderMapper.sumIncomeByShopIdAndOrderSendTimeBetweenExcludingStatus(
                eq(1001L),
                any(Integer.class),
                any(Long.class),
                any(Long.class)
        )).thenReturn(new BigDecimal("120.00"));
        when(mtPromotionAdRealtimeSnapshotService.captureCurrentSlotSnapshot(
                eq(1001L),
                eq("token"),
                eq(slotTime)
        )).thenReturn(currentRealtimeSnapshot);
        when(mtPromotionAdRealtimeSnapshotService.selectByShopIdAndBizDateAndSlotTime(
                eq(1001L),
                eq(slotTime.toLocalDate()),
                eq(slotTime)
        )).thenReturn(null);
        when(mtPromotionAdRealtimeSnapshotService.selectByShopIdAndBizDateAndSlotTime(
                eq(1001L),
                eq(executionWindow.getWindowStartAt().toLocalDate()),
                eq(executionWindow.getWindowStartAt())
        )).thenReturn(crossDayBaselineSnapshot);
        when(mtPromotionAdRealtimeSnapshotService.selectLatestByShopIdAndSlotTimeRange(
                eq(1001L),
                eq(executionWindow.getWindowStartAt()),
                eq(slotTime.toLocalDate().atStartOfDay())
        )).thenReturn(previousDayLastSnapshot);
        when(mtPromotionAdRealtimeSnapshotService.selectByShopIdAndBizDateAndSlotTime(
                eq(1001L),
                eq(slotTime.minusMinutes(10).toLocalDate()),
                eq(slotTime.minusMinutes(10))
        )).thenReturn(previousSlotSnapshot);

        PromotionMetricsSnapshot snapshot = mtPromotionMetricsProvider.queryMetrics(candidate, executionWindow, slotTime);

        assertEquals(new BigDecimal("0.70"), snapshot.getWindowCost());
        assertEquals(new BigDecimal("0.30"), snapshot.getSlotCostDelta());
        assertTrue(snapshot.isMetricsTrusted());
        assertTrue(snapshot.getPayloadJson().contains("CROSS_DAY_WINDOW_START_AND_PREVIOUS_DAY_LAST_REALTIME_SNAPSHOT"));
    }

    @Test
    void shouldMarkMetricsUntrustedWhenExecutionWindowBaselineMissing() throws Exception {
        MtPromotionAutoBidCandidate candidate = buildCandidate();
        LocalDateTime slotTime = LocalDateTime.of(2026, 3, 28, 10, 0);
        PromotionExecutionWindow executionWindow = PromotionExecutionWindow.of(
                LocalDateTime.of(2026, 3, 28, 9, 30),
                LocalDateTime.of(2026, 3, 28, 22, 0)
        );
        MtPromotionAdRealtimeSnapshot currentRealtimeSnapshot = buildRealtimeSnapshot(slotTime, 1000L);

        mockAdBalance("encrypted-debit", "2050");
        when(mtOrderMapper.sumIncomeByShopIdAndOrderSendTimeBetweenExcludingStatus(
                eq(1001L),
                any(Integer.class),
                any(Long.class),
                any(Long.class)
        )).thenReturn(new BigDecimal("200.00"));
        when(mtPromotionAdRealtimeSnapshotService.captureCurrentSlotSnapshot(
                eq(1001L),
                eq("token"),
                eq(slotTime)
        )).thenReturn(currentRealtimeSnapshot);
        when(mtPromotionAdRealtimeSnapshotService.selectByShopIdAndBizDateAndSlotTime(
                eq(1001L),
                eq(slotTime.toLocalDate()),
                eq(slotTime)
        )).thenReturn(null);
        when(mtPromotionAdRealtimeSnapshotService.selectByShopIdAndBizDateAndSlotTime(
                eq(1001L),
                eq(executionWindow.getWindowStartAt().toLocalDate()),
                eq(executionWindow.getWindowStartAt())
        )).thenReturn(null);
        when(mtPromotionAdRealtimeSnapshotService.selectByShopIdAndBizDateAndSlotTime(
                eq(1001L),
                eq(slotTime.minusMinutes(10).toLocalDate()),
                eq(slotTime.minusMinutes(10))
        )).thenReturn(null);

        PromotionMetricsSnapshot snapshot = mtPromotionMetricsProvider.queryMetrics(candidate, executionWindow, slotTime);

        assertEquals(new BigDecimal("0.00"), snapshot.getWindowCost());
        assertTrue(snapshot.isMetricsTrusted());
        assertEquals(new BigDecimal("0.00"), snapshot.getSlotCostDelta());
        assertEquals(null, snapshot.getUntrustedReason());
        assertTrue(snapshot.isTemporaryRecoveryWindow());
        assertTrue(snapshot.isRecoveryWindowInitializationRequired());
        assertEquals(slotTime, snapshot.getEffectiveWindowStartAt());
        assertEquals("TEMPORARY_RECOVERY_REALTIME", snapshot.getBaselineType());
        assertTrue(snapshot.getPayloadJson().contains("\"temporaryRecoveryWindowStartAt\":\"2026-03-28T10:00\""));
    }

    @Test
    void shouldMarkMetricsUntrustedWhenExecutionWindowCounterRollsBack() throws Exception {
        MtPromotionAutoBidCandidate candidate = buildCandidate();
        LocalDateTime slotTime = LocalDateTime.of(2026, 3, 28, 10, 0);
        PromotionExecutionWindow executionWindow = PromotionExecutionWindow.of(
                LocalDateTime.of(2026, 3, 28, 9, 30),
                LocalDateTime.of(2026, 3, 28, 22, 0)
        );
        MtPromotionAdRealtimeSnapshot currentRealtimeSnapshot = buildRealtimeSnapshot(slotTime, 1000L);
        MtPromotionAdRealtimeSnapshot windowBaselineSnapshot = buildRealtimeSnapshot(executionWindow.getWindowStartAt(), 1060L);
        MtPromotionAdRealtimeSnapshot previousSlotSnapshot = buildRealtimeSnapshot(slotTime.minusMinutes(10), 970L);

        mockAdBalance("encrypted-debit", "2050");
        when(mtOrderMapper.sumIncomeByShopIdAndOrderSendTimeBetweenExcludingStatus(
                eq(1001L),
                any(Integer.class),
                any(Long.class),
                any(Long.class)
        )).thenReturn(new BigDecimal("200.00"));
        when(mtPromotionAdRealtimeSnapshotService.captureCurrentSlotSnapshot(
                eq(1001L),
                eq("token"),
                eq(slotTime)
        )).thenReturn(currentRealtimeSnapshot);
        when(mtPromotionAdRealtimeSnapshotService.selectByShopIdAndBizDateAndSlotTime(
                eq(1001L),
                eq(slotTime.toLocalDate()),
                eq(slotTime)
        )).thenReturn(null);
        when(mtPromotionAdRealtimeSnapshotService.selectByShopIdAndBizDateAndSlotTime(
                eq(1001L),
                eq(executionWindow.getWindowStartAt().toLocalDate()),
                eq(executionWindow.getWindowStartAt())
        )).thenReturn(windowBaselineSnapshot);
        when(mtPromotionAdRealtimeSnapshotService.selectByShopIdAndBizDateAndSlotTime(
                eq(1001L),
                eq(slotTime.minusMinutes(10).toLocalDate()),
                eq(slotTime.minusMinutes(10))
        )).thenReturn(previousSlotSnapshot);

        PromotionMetricsSnapshot snapshot = mtPromotionMetricsProvider.queryMetrics(candidate, executionWindow, slotTime);

        assertEquals(new BigDecimal("0.00"), snapshot.getWindowCost());
        assertFalse(snapshot.isMetricsTrusted());
        assertEquals("EXECUTION_WINDOW_REALTIME_COUNTER_ROLLED_BACK", snapshot.getUntrustedReason());
    }

    @Test
    void shouldReuseTemporaryRecoveryWindowForLaterSameDaySlot() throws Exception {
        MtPromotionAutoBidCandidate candidate = buildCandidate();
        LocalDateTime recoveryStartAt = LocalDateTime.of(2026, 3, 28, 10, 0);
        LocalDateTime slotTime = recoveryStartAt.plusMinutes(10);
        PromotionExecutionWindow executionWindow = PromotionExecutionWindow.of(
                LocalDateTime.of(2026, 3, 28, 9, 30),
                LocalDateTime.of(2026, 3, 28, 22, 0)
        );
        MtPromotionAdRealtimeSnapshot currentRealtimeSnapshot = buildRealtimeSnapshot(slotTime, 1030L);
        MtPromotionAdRealtimeSnapshot previousSlotSnapshot = buildRealtimeSnapshot(recoveryStartAt, 1000L);
        MtPromotionMetricSnapshot recoveryMetricSnapshot = new MtPromotionMetricSnapshot();
        recoveryMetricSnapshot.setShopId(1001L);
        recoveryMetricSnapshot.setBizDate(recoveryStartAt.toLocalDate());
        recoveryMetricSnapshot.setSlotTime(recoveryStartAt);
        recoveryMetricSnapshot.setPayloadJson(JsonUtil.toJson(java.util.Map.of(
                "temporaryRecoveryWindowStartAt", recoveryStartAt.toString(),
                "temporaryRecoveryWindowSource", "INITIALIZED_FROM_FIRST_OBSERVED_SLOT"
        )));

        mockAdBalance("encrypted-debit", "2050");
        when(mtOrderMapper.sumIncomeByShopIdAndOrderSendTimeBetweenExcludingStatus(
                eq(1001L),
                any(Integer.class),
                any(Long.class),
                any(Long.class)
        )).thenReturn(new BigDecimal("30.00"));
        when(mtPromotionAdRealtimeSnapshotService.selectByShopIdAndBizDateAndSlotTime(
                eq(1001L),
                eq(slotTime.toLocalDate()),
                eq(slotTime)
        )).thenReturn(currentRealtimeSnapshot);
        when(mtPromotionAdRealtimeSnapshotService.selectByShopIdAndBizDateAndSlotTime(
                eq(1001L),
                eq(executionWindow.getWindowStartAt().toLocalDate()),
                eq(executionWindow.getWindowStartAt())
        )).thenReturn(null);
        when(mtPromotionMetricSnapshotMapper.selectLatestByShopIdAndSlotTimeRange(
                eq(1001L),
                eq(executionWindow.getWindowStartAt()),
                eq(slotTime)
        )).thenReturn(recoveryMetricSnapshot);
        when(mtPromotionAdRealtimeSnapshotService.selectByShopIdAndBizDateAndSlotTime(
                eq(1001L),
                eq(slotTime.minusMinutes(10).toLocalDate()),
                eq(slotTime.minusMinutes(10))
        )).thenReturn(previousSlotSnapshot);

        PromotionMetricsSnapshot snapshot = mtPromotionMetricsProvider.queryMetrics(candidate, executionWindow, slotTime);

        assertTrue(snapshot.isMetricsTrusted());
        assertTrue(snapshot.isTemporaryRecoveryWindow());
        assertFalse(snapshot.isRecoveryWindowInitializationRequired());
        assertEquals(recoveryStartAt, snapshot.getEffectiveWindowStartAt());
        assertEquals(new BigDecimal("0.30"), snapshot.getWindowCost());
        assertEquals(new BigDecimal("30.00"), snapshot.getWindowIncome());
        assertEquals("TEMPORARY_RECOVERY_REALTIME", snapshot.getBaselineType());
    }

    @Test
    void shouldReuseTemporaryRecoveryWindowAcrossDayBoundary() throws Exception {
        MtPromotionAutoBidCandidate candidate = buildCandidate();
        LocalDateTime recoveryStartAt = LocalDateTime.of(2026, 3, 28, 23, 50);
        LocalDateTime slotTime = LocalDateTime.of(2026, 3, 29, 0, 10);
        PromotionExecutionWindow executionWindow = PromotionExecutionWindow.of(
                LocalDateTime.of(2026, 3, 28, 22, 0),
                LocalDateTime.of(2026, 3, 29, 2, 0)
        );
        MtPromotionAdRealtimeSnapshot currentRealtimeSnapshot = buildRealtimeSnapshot(slotTime, 50L);
        MtPromotionAdRealtimeSnapshot recoveryRealtimeSnapshot = buildRealtimeSnapshot(recoveryStartAt, 1820L);
        MtPromotionAdRealtimeSnapshot previousDayLastSnapshot = buildRealtimeSnapshot(recoveryStartAt, 1820L);
        MtPromotionAdRealtimeSnapshot previousSlotSnapshot = buildRealtimeSnapshot(LocalDateTime.of(2026, 3, 29, 0, 0), 20L);
        MtPromotionMetricSnapshot recoveryMetricSnapshot = new MtPromotionMetricSnapshot();
        recoveryMetricSnapshot.setShopId(1001L);
        recoveryMetricSnapshot.setBizDate(recoveryStartAt.toLocalDate());
        recoveryMetricSnapshot.setSlotTime(recoveryStartAt);
        recoveryMetricSnapshot.setPayloadJson(JsonUtil.toJson(java.util.Map.of(
                "temporaryRecoveryWindowStartAt", recoveryStartAt.toString(),
                "temporaryRecoveryWindowSource", "INITIALIZED_FROM_FIRST_OBSERVED_SLOT"
        )));

        mockAdBalance("encrypted-debit", "1850");
        when(mtOrderMapper.sumIncomeByShopIdAndOrderSendTimeBetweenExcludingStatus(
                eq(1001L),
                any(Integer.class),
                any(Long.class),
                any(Long.class)
        )).thenReturn(new BigDecimal("18.00"));
        when(mtPromotionAdRealtimeSnapshotService.selectByShopIdAndBizDateAndSlotTime(
                eq(1001L),
                eq(slotTime.toLocalDate()),
                eq(slotTime)
        )).thenReturn(currentRealtimeSnapshot);
        when(mtPromotionAdRealtimeSnapshotService.selectByShopIdAndBizDateAndSlotTime(
                eq(1001L),
                eq(executionWindow.getWindowStartAt().toLocalDate()),
                eq(executionWindow.getWindowStartAt())
        )).thenReturn(null);
        when(mtPromotionAdRealtimeSnapshotService.selectLatestByShopIdAndSlotTimeRange(
                eq(1001L),
                eq(executionWindow.getWindowStartAt()),
                eq(slotTime.toLocalDate().atStartOfDay())
        )).thenReturn(null);
        when(mtPromotionMetricSnapshotMapper.selectLatestByShopIdAndSlotTimeRange(
                eq(1001L),
                eq(executionWindow.getWindowStartAt()),
                eq(slotTime)
        )).thenReturn(recoveryMetricSnapshot);
        when(mtPromotionAdRealtimeSnapshotService.selectByShopIdAndBizDateAndSlotTime(
                eq(1001L),
                eq(recoveryStartAt.toLocalDate()),
                eq(recoveryStartAt)
        )).thenReturn(recoveryRealtimeSnapshot);
        when(mtPromotionAdRealtimeSnapshotService.selectLatestByShopIdAndSlotTimeRange(
                eq(1001L),
                eq(recoveryStartAt),
                eq(slotTime.toLocalDate().atStartOfDay())
        )).thenReturn(previousDayLastSnapshot);
        when(mtPromotionAdRealtimeSnapshotService.selectByShopIdAndBizDateAndSlotTime(
                eq(1001L),
                eq(slotTime.minusMinutes(10).toLocalDate()),
                eq(slotTime.minusMinutes(10))
        )).thenReturn(previousSlotSnapshot);

        PromotionMetricsSnapshot snapshot = mtPromotionMetricsProvider.queryMetrics(candidate, executionWindow, slotTime);

        assertTrue(snapshot.isMetricsTrusted());
        assertTrue(snapshot.isTemporaryRecoveryWindow());
        assertEquals(recoveryStartAt, snapshot.getEffectiveWindowStartAt());
        assertEquals(new BigDecimal("0.50"), snapshot.getWindowCost());
        assertEquals(new BigDecimal("18.00"), snapshot.getWindowIncome());
    }

    @Test
    void shouldFailWhenDecryptResultIsInvalid() throws Exception {
        MtPromotionAutoBidCandidate candidate = buildCandidate();
        PromotionExecutionWindow executionWindow = PromotionExecutionWindow.of(
                LocalDateTime.of(2026, 3, 28, 9, 50),
                LocalDateTime.of(2026, 3, 28, 22, 0)
        );

        when(meituanClient.invokeApi(
                any(com.meituan.sdk.model.wmoperNg.waimaiad.adBalanceInfo.AdBalanceInfoRequest.class),
                eq("token")
        )).thenReturn(buildAdBalanceResponse("encrypted-debit"));
        when(meituanClient.invokeApi(
                any(com.meituan.sdk.model.wmoperNg.waimaiad.decrypt.DecryptRequest.class),
                eq("token")
        )).thenReturn(buildDecryptResponse("invalid-money"));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> mtPromotionMetricsProvider.queryMetrics(candidate, executionWindow, LocalDateTime.of(2026, 3, 28, 10, 0))
        );

        assertEquals("解析美团广告金额异常", exception.getMessage());
    }

    @Test
    void shouldShortCircuitKnownZeroCiphertextWithoutCallingDecryptApi() throws Exception {
        when(mtPromotionAdRealtimeSnapshotService.captureCurrentSlotSnapshot(any(), any(), any()))
                .thenThrow(new IllegalStateException("no realtime"));
        MtPromotionAutoBidCandidate candidate = buildCandidate();
        LocalDateTime slotTime = LocalDateTime.of(2026, 3, 28, 10, 0);
        PromotionExecutionWindow executionWindow = PromotionExecutionWindow.of(
                LocalDateTime.of(2026, 3, 28, 9, 50),
                LocalDateTime.of(2026, 3, 28, 22, 0)
        );
        MtPromotionMetricSnapshot windowBaselineSnapshot = new MtPromotionMetricSnapshot();
        windowBaselineSnapshot.setCostCounterValue(BigDecimal.ZERO.setScale(2));
        MtPromotionMetricSnapshot previousSlotSnapshot = new MtPromotionMetricSnapshot();
        previousSlotSnapshot.setCostCounterValue(BigDecimal.ZERO.setScale(2));

        when(meituanClient.invokeApi(
                any(com.meituan.sdk.model.wmoperNg.waimaiad.adBalanceInfo.AdBalanceInfoRequest.class),
                eq("token")
        )).thenReturn(buildAdBalanceResponseWithValues(
                "UdK7p6F4o8KhAXeeeni8Pw==",
                "UdK7p6F4o8KhAXeeeni8Pw==",
                "UdK7p6F4o8KhAXeeeni8Pw=="
        ));
        when(mtOrderMapper.sumIncomeByShopIdAndOrderSendTimeBetweenExcludingStatus(
                eq(1001L),
                any(Integer.class),
                any(Long.class),
                any(Long.class)
        )).thenReturn(BigDecimal.ZERO.setScale(2));
        when(mtPromotionMetricSnapshotMapper.selectByShopIdAndBizDateAndSlotTime(
                eq(1001L),
                eq(slotTime.minusMinutes(10).toLocalDate()),
                eq(slotTime.minusMinutes(10))
        )).thenReturn(previousSlotSnapshot);

        PromotionMetricsSnapshot snapshot = mtPromotionMetricsProvider.queryMetrics(candidate, executionWindow, slotTime);

        assertEquals(BigDecimal.ZERO.setScale(2), snapshot.getPromotionBalance());
        assertEquals(BigDecimal.ZERO.setScale(2), snapshot.getCostCounterValue());
        verify(meituanClient, never()).invokeApi(
                any(com.meituan.sdk.model.wmoperNg.waimaiad.decrypt.DecryptRequest.class),
                eq("token")
        );
    }

    @Test
    void shouldReuseDecryptResultForDuplicateCiphertextsInSingleQuery() throws Exception {
        when(mtPromotionAdRealtimeSnapshotService.captureCurrentSlotSnapshot(any(), any(), any()))
                .thenThrow(new IllegalStateException("no realtime"));
        MtPromotionAutoBidCandidate candidate = buildCandidate();
        LocalDateTime slotTime = LocalDateTime.of(2026, 3, 28, 10, 0);
        PromotionExecutionWindow executionWindow = PromotionExecutionWindow.of(
                LocalDateTime.of(2026, 3, 28, 9, 50),
                LocalDateTime.of(2026, 3, 28, 22, 0)
        );
        MtPromotionMetricSnapshot windowBaselineSnapshot = new MtPromotionMetricSnapshot();
        windowBaselineSnapshot.setCostCounterValue(new BigDecimal("7.00"));
        MtPromotionMetricSnapshot previousSlotSnapshot = new MtPromotionMetricSnapshot();
        previousSlotSnapshot.setCostCounterValue(new BigDecimal("7.00"));

        when(meituanClient.invokeApi(
                any(com.meituan.sdk.model.wmoperNg.waimaiad.adBalanceInfo.AdBalanceInfoRequest.class),
                eq("token")
        )).thenReturn(buildAdBalanceResponseWithValues(
                "same-cipher",
                "same-cipher",
                "same-cipher"
        ));
        when(meituanClient.invokeApi(
                any(com.meituan.sdk.model.wmoperNg.waimaiad.decrypt.DecryptRequest.class),
                eq("token")
        )).thenReturn(buildDecryptResponse("700"));
        when(mtOrderMapper.sumIncomeByShopIdAndOrderSendTimeBetweenExcludingStatus(
                eq(1001L),
                any(Integer.class),
                any(Long.class),
                any(Long.class)
        )).thenReturn(BigDecimal.ZERO.setScale(2));
        when(mtPromotionMetricSnapshotMapper.selectByShopIdAndBizDateAndSlotTime(
                eq(1001L),
                eq(slotTime.minusMinutes(10).toLocalDate()),
                eq(slotTime.minusMinutes(10))
        )).thenReturn(previousSlotSnapshot);

        mtPromotionMetricsProvider.queryMetrics(candidate, executionWindow, slotTime);

        verify(meituanClient, times(1)).invokeApi(
                any(com.meituan.sdk.model.wmoperNg.waimaiad.decrypt.DecryptRequest.class),
                eq("token")
        );
    }

    private void mockAdBalance(String debit, String decryptedValue) throws Exception {
        when(meituanClient.invokeApi(
                any(com.meituan.sdk.model.wmoperNg.waimaiad.adBalanceInfo.AdBalanceInfoRequest.class),
                eq("token")
        )).thenReturn(buildAdBalanceResponse(debit));
        when(meituanClient.invokeApi(
                any(com.meituan.sdk.model.wmoperNg.waimaiad.decrypt.DecryptRequest.class),
                eq("token")
        )).thenReturn(buildDecryptResponse(decryptedValue));
    }

    private MtPromotionAutoBidCandidate buildCandidate() {
        MtPromotionAutoBidCandidate candidate = new MtPromotionAutoBidCandidate();
        candidate.setShopId(1001L);
        candidate.setAccessToken("token");
        return candidate;
    }

    private MeituanResponse<AdBalanceInfoResponse> buildAdBalanceResponse(String debit) {
        return buildAdBalanceResponseWithValues("encrypted-primary", "encrypted-common", debit);
    }

    private MeituanResponse<AdBalanceInfoResponse> buildAdBalanceResponseWithValues(
            String primaryBalance,
            String commonBalance,
            String debit
    ) {
        Result result = new Result();
        result.setSuccess(true);
        result.setCode("0");
        result.setMessage("success");

        AdBalanceInfoResponse payload = new AdBalanceInfoResponse();
        payload.setResult(result);
        payload.setPrimaryBalance(primaryBalance);
        payload.setCommonBalance(commonBalance);
        payload.setDebit(debit);

        MeituanResponse<AdBalanceInfoResponse> response = new MeituanResponse<>();
        response.setCode(MT_SUCCESS_CODE);
        response.setMsg("success");
        response.setData(payload);
        return response;
    }

    private MeituanResponse<DecryptResponse> buildDecryptResponse(String value) {
        com.meituan.sdk.model.wmoperNg.waimaiad.decrypt.Result result =
                new com.meituan.sdk.model.wmoperNg.waimaiad.decrypt.Result();
        result.setSuccess(true);
        result.setCode("0");
        result.setMessage("success");

        DecryptResponse payload = new DecryptResponse();
        payload.setResult(result);
        payload.setValue(value);

        MeituanResponse<DecryptResponse> response = new MeituanResponse<>();
        response.setCode(MT_SUCCESS_CODE);
        response.setMsg("success");
        response.setData(payload);
        return response;
    }

    private MtPromotionAdRealtimeSnapshot buildRealtimeSnapshot(LocalDateTime slotTime, long dayDebitCents) {
        MtPromotionAdRealtimeSnapshot snapshot = new MtPromotionAdRealtimeSnapshot();
        snapshot.setShopId(1001L);
        snapshot.setBizDate(slotTime.toLocalDate());
        snapshot.setSlotTime(slotTime);
        snapshot.setChannel(1);
        snapshot.setCurrentHourRange(String.format("%02d:00-%02d:00", slotTime.getHour(), (slotTime.getHour() + 1) % 24));
        snapshot.setDayDebitCents(dayDebitCents);
        snapshot.setHourDebitCents(dayDebitCents);
        return snapshot;
    }
}
