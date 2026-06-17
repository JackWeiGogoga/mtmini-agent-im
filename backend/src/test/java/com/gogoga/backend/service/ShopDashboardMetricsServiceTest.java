package com.gogoga.backend.service;

import com.gogoga.backend.enums.DashboardStatsRangeTypeEnum;
import com.gogoga.backend.entity.mt.MtPowerAccount;
import com.gogoga.backend.entity.mt.MtPromotionAdHourlyDetail;
import com.gogoga.backend.entity.mt.MtPromotionAdRealtimeSnapshot;
import com.gogoga.backend.entity.mt.MtPromotionBidRecord;
import com.gogoga.backend.entity.mt.MtPromotionSetting;
import com.gogoga.backend.entity.mt.MtPromotionSettingHistory;
import com.gogoga.backend.mapper.MtOrderMapper;
import com.gogoga.backend.mapper.MtPoiTrafficDailyMapper;
import com.gogoga.backend.mapper.MtPowerConsumeMapper;
import com.gogoga.backend.mapper.MtPromotionAdHourlyDetailMapper;
import com.gogoga.backend.mapper.MtPromotionBidRecordMapper;
import com.gogoga.backend.mapper.MtPromotionPeriodMetricDailyMapper;
import com.gogoga.backend.mapper.MtPromotionSettingHistoryMapper;
import com.gogoga.backend.mapper.MtPromotionSettingMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShopDashboardMetricsServiceTest {

    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Shanghai");
    private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 5, 24, 16, 29, 57);
    private static final LocalDateTime ALIGNED_NOW = LocalDateTime.of(2026, 5, 24, 16, 20);

    @Mock
    private MtOrderMapper mtOrderMapper;

    @Mock
    private MtPoiTrafficDailyMapper mtPoiTrafficDailyMapper;

    @Mock
    private MtPowerConsumeMapper mtPowerConsumeMapper;

    @Mock
    private MtPromotionBidRecordMapper mtPromotionBidRecordMapper;

    @Mock
    private MtPromotionPeriodMetricDailyMapper mtPromotionPeriodMetricDailyMapper;

    @Mock
    private MtPromotionAdHourlyDetailMapper mtPromotionAdHourlyDetailMapper;

    @Mock
    private MtPromotionAdRealtimeSnapshotService mtPromotionAdRealtimeSnapshotService;

    @Mock
    private MtPromotionSettingHistoryMapper mtPromotionSettingHistoryMapper;

    @Mock
    private MtPromotionSettingMapper mtPromotionSettingMapper;

    @InjectMocks
    private ShopDashboardMetricsService shopDashboardMetricsService;

    @BeforeEach
    void setUp() {
        shopDashboardMetricsService.setNowSupplier(() -> FIXED_NOW);
    }

    @Test
    void shouldAggregateOrderExposureAndPowerStats() {
        mockPromotionEnabled(1L);
        when(mtOrderMapper.sumIncomeByShopIdAndOrderSendTimeBetweenExcludingStatus(anyLong(), any(Integer.class), anyLong(), anyLong()))
                .thenReturn(new BigDecimal("500.00"), new BigDecimal("400.00"), new BigDecimal("500.00"), new BigDecimal("400.00"));
        when(mtOrderMapper.countOrdersByShopIdAndOrderSendTimeBetweenExcludingStatus(anyLong(), any(Integer.class), anyLong(), anyLong()))
                .thenReturn(12, 8);
        when(mtPoiTrafficDailyMapper.sumExposureCountByShopIdAndBizDateBetween(anyLong(), eq(MtPoiTrafficDailyService.FLOW_PROP_NATURAL), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(180, 120);
        when(mtPoiTrafficDailyMapper.sumExposureCountByShopIdAndBizDateBetween(anyLong(), eq(MtPoiTrafficDailyService.FLOW_PROP_PAID), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(120, 80);
        MtPromotionAdHourlyDetail historicalHour = new MtPromotionAdHourlyDetail();
        historicalHour.setTimeRange("00:00-01:00");
        historicalHour.setShowCount(80);
        historicalHour.setDebitCents(6000L);
        when(mtPromotionAdHourlyDetailMapper.selectByShopIdAndBizDateAndChannel(
                anyLong(),
                any(LocalDate.class),
                eq(MtPromotionAdDataService.CHANNEL_DIANJIN)
        )).thenReturn(List.of(historicalHour));
        MtPromotionAdRealtimeSnapshot realtimeSnapshot = new MtPromotionAdRealtimeSnapshot();
        realtimeSnapshot.setDayShowCount(30);
        realtimeSnapshot.setDayDebitCents(1000L);
        when(mtPromotionAdRealtimeSnapshotService.selectLatestByShopIdAndSlotTimeRangeBeforeOrEqual(anyLong(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(realtimeSnapshot);
        when(mtPowerConsumeMapper.sumPowerAmountByShopIdAndCreateTimeBetween(anyLong(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(22, 11);

        MtPowerAccount account = new MtPowerAccount();
        account.setBalance(88);
        account.setTotalRecharge(120);
        account.setTotalConsume(32);

        ShopDashboardMetricsService.MarketingStatsSnapshot marketing =
                shopDashboardMetricsService.buildMarketingStats(1L, DashboardStatsRangeTypeEnum.TODAY);
        ShopDashboardMetricsService.OrderStatsSnapshot orderStats =
                shopDashboardMetricsService.buildOrderStats(1L, DashboardStatsRangeTypeEnum.TODAY);
        ShopDashboardMetricsService.ExposureBreakdownSnapshot exposure = shopDashboardMetricsService.buildExposureBreakdown(1L);
        ShopDashboardMetricsService.PowerStatsSnapshot power = shopDashboardMetricsService.buildPowerStats(1L, account);

        assertEquals(30, marketing.getCurrentExposure());
        assertEquals(80, marketing.getPreviousExposure());
        assertEquals(new BigDecimal("10.00"), marketing.getCurrentCost());
        assertEquals(new BigDecimal("0.020000"), marketing.getCurrentCostRatio());
        assertEquals(12, orderStats.getCurrentCompletedOrderCount());
        assertEquals(new BigDecimal("500.00"), orderStats.getCurrentCompletedIncome());
        assertEquals(180, exposure.getCurrentFreeExposure());
        assertEquals(120, exposure.getCurrentPaidExposure());
        assertEquals(22, power.getCurrentConsumedPower());
        assertEquals(88, power.getBalance());
    }

    @Test
    void shouldFallbackAndReadLatestPromotionBalanceSnapshot() {
        when(mtPowerConsumeMapper.sumPowerAmountByShopIdAndCreateTimeBetween(anyLong(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(null);

        MtPromotionBidRecord record = new MtPromotionBidRecord();
        record.setMetricsPayloadJson("{\"promotionBalance\":123.45}");
        when(mtPromotionBidRecordMapper.selectLatestSuccessByShopId(9L)).thenReturn(record);

        ShopDashboardMetricsService.MarketingStatsSnapshot marketing = shopDashboardMetricsService.buildMarketingStats(9L);
        ShopDashboardMetricsService.OrderStatsSnapshot orders = shopDashboardMetricsService.buildOrderStats(9L);
        ShopDashboardMetricsService.PowerStatsSnapshot power = shopDashboardMetricsService.buildPowerStats(9L, null);

        assertEquals(0, marketing.getCurrentExposure());
        assertEquals(new BigDecimal("0.00"), marketing.getCurrentCost());
        assertEquals(0, orders.getCurrentCompletedOrderCount());
        assertEquals(new BigDecimal("0.00"), orders.getCurrentCompletedIncome());
        assertEquals(0, power.getBalance());
        assertEquals(new BigDecimal("123.45"), shopDashboardMetricsService.findLatestPromotionBalanceSnapshot(9L));
        assertNull(shopDashboardMetricsService.findLatestPromotionBalanceSnapshot(10L));
    }

    @Test
    void shouldRespectTodayAndThirtyDayRanges() {
        mockPromotionEnabled(6L);
        when(mtPowerConsumeMapper.sumPowerAmountByShopIdAndCreateTimeBetween(anyLong(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(9, 8);
        when(mtOrderMapper.sumIncomeByShopIdAndOrderSendTimeBetweenExcludingStatus(anyLong(), anyInt(), anyLong(), anyLong()))
                .thenReturn(new BigDecimal("300.00"), new BigDecimal("280.00"));
        when(mtOrderMapper.countOrdersByShopIdAndOrderSendTimeBetweenExcludingStatus(anyLong(), anyInt(), anyLong(), anyLong()))
                .thenReturn(6, 5);

        shopDashboardMetricsService.buildPowerStats(6L, null, DashboardStatsRangeTypeEnum.TODAY);
        shopDashboardMetricsService.buildOrderStats(6L, DashboardStatsRangeTypeEnum.LAST_30_DAYS);

        LocalDate today = FIXED_NOW.toLocalDate();
        verify(mtPowerConsumeMapper).sumPowerAmountByShopIdAndCreateTimeBetween(
                6L,
                today.atStartOfDay(),
                ALIGNED_NOW
        );
        verify(mtPowerConsumeMapper).sumPowerAmountByShopIdAndCreateTimeBetween(
                6L,
                today.minusDays(1).atStartOfDay(),
                ALIGNED_NOW.minusDays(1)
        );
        verify(mtOrderMapper).sumIncomeByShopIdAndOrderSendTimeBetweenExcludingStatus(
                6L,
                com.gogoga.backend.enums.MtOrderStatusEnum.CANCELED.getCode(),
                today.minusDays(29).atStartOfDay(ZONE_ID).toEpochSecond(),
                ALIGNED_NOW.atZone(ZONE_ID).toEpochSecond()
        );
        verify(mtOrderMapper).sumIncomeByShopIdAndOrderSendTimeBetweenExcludingStatus(
                6L,
                com.gogoga.backend.enums.MtOrderStatusEnum.CANCELED.getCode(),
                today.minusDays(59).atStartOfDay(ZONE_ID).toEpochSecond(),
                ALIGNED_NOW.minusDays(30).atZone(ZONE_ID).toEpochSecond()
        );
    }

    @Test
    void shouldLimitMarketingStatsToPromotionActivePeriod() {
        Long shopId = 12L;
        LocalDate today = FIXED_NOW.toLocalDate();
        LocalDateTime enabledAt = today.atTime(12, 0);
        MtPromotionSettingHistory disabledBeforeRange = new MtPromotionSettingHistory();
        disabledBeforeRange.setPromotionEnabled(false);
        MtPromotionSettingHistory enabledChange = new MtPromotionSettingHistory();
        enabledChange.setPromotionEnabled(true);
        enabledChange.setCreateTime(enabledAt);
        when(mtPromotionSettingHistoryMapper.selectLatestByShopIdBeforeCreateTime(eq(shopId), any(LocalDateTime.class)))
                .thenReturn(disabledBeforeRange);
        when(mtPromotionSettingHistoryMapper.selectByShopIdAndCreateTimeRange(eq(shopId), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(enabledChange));
        when(mtOrderMapper.sumIncomeByShopIdAndOrderSendTimeBetweenExcludingStatus(eq(shopId), anyInt(), anyLong(), anyLong()))
                .thenReturn(new BigDecimal("300.00"));

        MtPromotionAdRealtimeSnapshot endSnapshot = new MtPromotionAdRealtimeSnapshot();
        endSnapshot.setDayShowCount(100);
        endSnapshot.setDayDebitCents(10000L);
        MtPromotionAdRealtimeSnapshot baselineSnapshot = new MtPromotionAdRealtimeSnapshot();
        baselineSnapshot.setDayShowCount(40);
        baselineSnapshot.setDayDebitCents(4000L);
        when(mtPromotionAdRealtimeSnapshotService.selectLatestByShopIdAndSlotTimeRangeBeforeOrEqual(eq(shopId), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(endSnapshot);
        when(mtPromotionAdRealtimeSnapshotService.selectLatestByShopIdAndSlotTimeRangeBeforeOrEqual(
                eq(shopId),
                eq(today.atStartOfDay()),
                eq(enabledAt)
        ))
                .thenReturn(null);
        when(mtPromotionAdRealtimeSnapshotService.selectEarliestByShopIdAndSlotTimeRange(eq(shopId), eq(enabledAt), any(LocalDateTime.class)))
                .thenReturn(baselineSnapshot);

        ShopDashboardMetricsService.MarketingStatsSnapshot marketing =
                shopDashboardMetricsService.buildMarketingStats(shopId, DashboardStatsRangeTypeEnum.TODAY);

        assertEquals(60, marketing.getCurrentExposure());
        assertEquals(new BigDecimal("60.00"), marketing.getCurrentCost());
        assertEquals(new BigDecimal("0.200000"), marketing.getCurrentCostRatio());
        verify(mtOrderMapper).sumIncomeByShopIdAndOrderSendTimeBetweenExcludingStatus(
                shopId,
                com.gogoga.backend.enums.MtOrderStatusEnum.CANCELED.getCode(),
                enabledAt.atZone(ZONE_ID).toEpochSecond(),
                ALIGNED_NOW.atZone(ZONE_ID).toEpochSecond()
        );
    }

    @Test
    void shouldUseSameDayRealtimeBaselineWhenTodaySnapshotStartsAfterPromotionEnabled() {
        Long shopId = 511L;
        LocalDate today = FIXED_NOW.toLocalDate();
        LocalDateTime enabledAt = today.atTime(13, 52, 5);
        MtPromotionSettingHistory disabledBeforeRange = new MtPromotionSettingHistory();
        disabledBeforeRange.setPromotionEnabled(false);
        MtPromotionSettingHistory enabledChange = new MtPromotionSettingHistory();
        enabledChange.setPromotionEnabled(true);
        enabledChange.setCreateTime(enabledAt);
        when(mtPromotionSettingHistoryMapper.selectLatestByShopIdBeforeCreateTime(eq(shopId), any(LocalDateTime.class)))
                .thenReturn(disabledBeforeRange);
        when(mtPromotionSettingHistoryMapper.selectByShopIdAndCreateTimeRange(eq(shopId), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(enabledChange))
                .thenReturn(List.of());
        when(mtOrderMapper.sumIncomeByShopIdAndOrderSendTimeBetweenExcludingStatus(eq(shopId), anyInt(), anyLong(), anyLong()))
                .thenReturn(new BigDecimal("389.94"));

        MtPromotionAdRealtimeSnapshot latestSameDaySnapshot = new MtPromotionAdRealtimeSnapshot();
        latestSameDaySnapshot.setDayShowCount(1555);
        latestSameDaySnapshot.setDayDebitCents(5135L);
        MtPromotionAdRealtimeSnapshot firstSameDaySnapshot = new MtPromotionAdRealtimeSnapshot();
        firstSameDaySnapshot.setDayShowCount(908);
        firstSameDaySnapshot.setDayDebitCents(3705L);
        when(mtPromotionAdRealtimeSnapshotService.selectLatestByShopIdAndSlotTimeRangeBeforeOrEqual(
                eq(shopId),
                eq(today.atStartOfDay()),
                eq(ALIGNED_NOW)
        )).thenReturn(latestSameDaySnapshot);
        when(mtPromotionAdRealtimeSnapshotService.selectLatestByShopIdAndSlotTimeRangeBeforeOrEqual(
                eq(shopId),
                eq(today.atStartOfDay()),
                eq(enabledAt)
        )).thenReturn(null);
        when(mtPromotionAdRealtimeSnapshotService.selectEarliestByShopIdAndSlotTimeRange(
                eq(shopId),
                eq(enabledAt),
                eq(ALIGNED_NOW)
        )).thenReturn(firstSameDaySnapshot);

        ShopDashboardMetricsService.MarketingStatsSnapshot marketing =
                shopDashboardMetricsService.buildMarketingStats(shopId, DashboardStatsRangeTypeEnum.TODAY);

        assertEquals(647, marketing.getCurrentExposure());
        assertEquals(new BigDecimal("14.30"), marketing.getCurrentCost());
        assertEquals(new BigDecimal("0.036672"), marketing.getCurrentCostRatio());
        verify(mtPromotionAdRealtimeSnapshotService, never())
                .selectLatestByShopIdAndSlotTimeBeforeOrEqual(eq(shopId), any(LocalDateTime.class));
    }

    @Test
    void shouldHandleDuplicatePromotionSwitchChanges() {
        Long shopId = 13L;
        LocalDate today = FIXED_NOW.toLocalDate();
        LocalDateTime enabledAt = today.atTime(9, 0);
        LocalDateTime duplicatedEnabledAt = today.atTime(10, 0);
        LocalDateTime disabledAt = today.atTime(12, 0);
        LocalDateTime duplicatedDisabledAt = today.atTime(13, 0);
        MtPromotionSettingHistory disabledBeforeRange = new MtPromotionSettingHistory();
        disabledBeforeRange.setPromotionEnabled(false);
        MtPromotionSettingHistory enabledChange = new MtPromotionSettingHistory();
        enabledChange.setPromotionEnabled(true);
        enabledChange.setCreateTime(enabledAt);
        MtPromotionSettingHistory duplicatedEnabledChange = new MtPromotionSettingHistory();
        duplicatedEnabledChange.setPromotionEnabled(true);
        duplicatedEnabledChange.setCreateTime(duplicatedEnabledAt);
        MtPromotionSettingHistory disabledChange = new MtPromotionSettingHistory();
        disabledChange.setPromotionEnabled(false);
        disabledChange.setCreateTime(disabledAt);
        MtPromotionSettingHistory duplicatedDisabledChange = new MtPromotionSettingHistory();
        duplicatedDisabledChange.setPromotionEnabled(false);
        duplicatedDisabledChange.setCreateTime(duplicatedDisabledAt);
        when(mtPromotionSettingHistoryMapper.selectLatestByShopIdBeforeCreateTime(eq(shopId), any(LocalDateTime.class)))
                .thenReturn(disabledBeforeRange);
        when(mtPromotionSettingHistoryMapper.selectByShopIdAndCreateTimeRange(eq(shopId), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(enabledChange, duplicatedEnabledChange, disabledChange, duplicatedDisabledChange));
        when(mtOrderMapper.sumIncomeByShopIdAndOrderSendTimeBetweenExcludingStatus(eq(shopId), anyInt(), anyLong(), anyLong()))
                .thenReturn(new BigDecimal("120.00"));
        MtPromotionAdRealtimeSnapshot endSnapshot = new MtPromotionAdRealtimeSnapshot();
        endSnapshot.setDayShowCount(90);
        endSnapshot.setDayDebitCents(9000L);
        MtPromotionAdRealtimeSnapshot baselineSnapshot = new MtPromotionAdRealtimeSnapshot();
        baselineSnapshot.setDayShowCount(30);
        baselineSnapshot.setDayDebitCents(3000L);
        when(mtPromotionAdRealtimeSnapshotService.selectLatestByShopIdAndSlotTimeRangeBeforeOrEqual(eq(shopId), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(endSnapshot);
        when(mtPromotionAdRealtimeSnapshotService.selectLatestByShopIdAndSlotTimeRangeBeforeOrEqual(
                eq(shopId),
                eq(today.atStartOfDay()),
                eq(enabledAt)
        ))
                .thenReturn(baselineSnapshot);

        ShopDashboardMetricsService.MarketingStatsSnapshot marketing =
                shopDashboardMetricsService.buildMarketingStats(shopId, DashboardStatsRangeTypeEnum.TODAY);

        assertEquals(60, marketing.getCurrentExposure());
        assertEquals(new BigDecimal("60.00"), marketing.getCurrentCost());
        verify(mtOrderMapper).sumIncomeByShopIdAndOrderSendTimeBetweenExcludingStatus(
                shopId,
                com.gogoga.backend.enums.MtOrderStatusEnum.CANCELED.getCode(),
                enabledAt.atZone(ZONE_ID).toEpochSecond(),
                disabledAt.atZone(ZONE_ID).toEpochSecond()
        );
    }

    @Test
    void shouldProrateHistoricalHourlyPromotionCostByActiveOverlap() {
        Long shopId = 14L;
        LocalDate yesterday = FIXED_NOW.toLocalDate().minusDays(1);
        LocalDateTime enabledAt = yesterday.atTime(12, 30);
        LocalDateTime disabledAt = yesterday.atTime(13, 0);
        MtPromotionSettingHistory disabledBeforeRange = new MtPromotionSettingHistory();
        disabledBeforeRange.setPromotionEnabled(false);
        MtPromotionSettingHistory enabledChange = new MtPromotionSettingHistory();
        enabledChange.setPromotionEnabled(true);
        enabledChange.setCreateTime(enabledAt);
        MtPromotionSettingHistory disabledChange = new MtPromotionSettingHistory();
        disabledChange.setPromotionEnabled(false);
        disabledChange.setCreateTime(disabledAt);
        when(mtPromotionSettingHistoryMapper.selectLatestByShopIdBeforeCreateTime(eq(shopId), any(LocalDateTime.class)))
                .thenReturn(disabledBeforeRange);
        when(mtPromotionSettingHistoryMapper.selectByShopIdAndCreateTimeRange(eq(shopId), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(enabledChange, disabledChange));
        when(mtOrderMapper.sumIncomeByShopIdAndOrderSendTimeBetweenExcludingStatus(eq(shopId), anyInt(), anyLong(), anyLong()))
                .thenReturn(new BigDecimal("0.00"));
        MtPromotionAdHourlyDetail historicalHour = new MtPromotionAdHourlyDetail();
        historicalHour.setTimeRange("12:00-13:00");
        historicalHour.setShowCount(60);
        historicalHour.setDebitCents(6000L);
        when(mtPromotionAdHourlyDetailMapper.selectByShopIdAndBizDateAndChannel(
                eq(shopId),
                eq(yesterday),
                eq(MtPromotionAdDataService.CHANNEL_DIANJIN)
        )).thenReturn(List.of(historicalHour));

        ShopDashboardMetricsService.MarketingStatsSnapshot marketing =
                shopDashboardMetricsService.buildMarketingStats(shopId, DashboardStatsRangeTypeEnum.LAST_7_DAYS);

        assertEquals(30, marketing.getCurrentExposure());
        assertEquals(new BigDecimal("30.00"), marketing.getCurrentCost());
        assertEquals(new BigDecimal("1.000000"), marketing.getCurrentCostRatio());
    }

    @Test
    void shouldSplitPromotionActiveIntervalAcrossDays() {
        Long shopId = 15L;
        LocalDate today = FIXED_NOW.toLocalDate();
        LocalDate yesterday = today.minusDays(1);
        LocalDateTime enabledAt = yesterday.atTime(23, 30);
        LocalDateTime disabledAt = today.atTime(0, 30);
        MtPromotionSettingHistory disabledBeforeRange = new MtPromotionSettingHistory();
        disabledBeforeRange.setPromotionEnabled(false);
        MtPromotionSettingHistory enabledChange = new MtPromotionSettingHistory();
        enabledChange.setPromotionEnabled(true);
        enabledChange.setCreateTime(enabledAt);
        MtPromotionSettingHistory disabledChange = new MtPromotionSettingHistory();
        disabledChange.setPromotionEnabled(false);
        disabledChange.setCreateTime(disabledAt);
        when(mtPromotionSettingHistoryMapper.selectLatestByShopIdBeforeCreateTime(eq(shopId), any(LocalDateTime.class)))
                .thenReturn(disabledBeforeRange);
        when(mtPromotionSettingHistoryMapper.selectByShopIdAndCreateTimeRange(eq(shopId), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(enabledChange, disabledChange));
        when(mtOrderMapper.sumIncomeByShopIdAndOrderSendTimeBetweenExcludingStatus(eq(shopId), anyInt(), anyLong(), anyLong()))
                .thenReturn(new BigDecimal("100.00"));
        MtPromotionAdHourlyDetail historicalHour = new MtPromotionAdHourlyDetail();
        historicalHour.setTimeRange("23:00-00:00");
        historicalHour.setShowCount(60);
        historicalHour.setDebitCents(6000L);
        when(mtPromotionAdHourlyDetailMapper.selectByShopIdAndBizDateAndChannel(
                eq(shopId),
                eq(yesterday),
                eq(MtPromotionAdDataService.CHANNEL_DIANJIN)
        )).thenReturn(List.of(historicalHour));
        MtPromotionAdRealtimeSnapshot endSnapshot = new MtPromotionAdRealtimeSnapshot();
        endSnapshot.setDayShowCount(20);
        endSnapshot.setDayDebitCents(2000L);
        when(mtPromotionAdRealtimeSnapshotService.selectLatestByShopIdAndSlotTimeRangeBeforeOrEqual(
                eq(shopId),
                eq(today.atStartOfDay()),
                eq(disabledAt)
        ))
                .thenReturn(endSnapshot);

        ShopDashboardMetricsService.MarketingStatsSnapshot marketing =
                shopDashboardMetricsService.buildMarketingStats(shopId, DashboardStatsRangeTypeEnum.LAST_7_DAYS);

        assertEquals(50, marketing.getCurrentExposure());
        assertEquals(new BigDecimal("50.00"), marketing.getCurrentCost());
        assertEquals(new BigDecimal("0.500000"), marketing.getCurrentCostRatio());
    }

    @Test
    void shouldNotFallbackCurrentSettingWhenWindowIsBeforeExistingHistory() {
        Long shopId = 16L;
        when(mtPromotionSettingHistoryMapper.selectLatestByShopIdBeforeCreateTime(eq(shopId), any(LocalDateTime.class)))
                .thenReturn(null);
        when(mtPromotionSettingHistoryMapper.selectByShopIdAndCreateTimeRange(eq(shopId), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(mtPromotionSettingHistoryMapper.selectMaxVersionNoByShopId(shopId)).thenReturn(1);

        ShopDashboardMetricsService.MarketingStatsSnapshot marketing =
                shopDashboardMetricsService.buildMarketingStats(shopId, DashboardStatsRangeTypeEnum.LAST_7_DAYS);

        assertEquals(0, marketing.getCurrentExposure());
        assertEquals(new BigDecimal("0.00"), marketing.getCurrentCost());
        verify(mtPromotionSettingMapper, never()).selectByShopId(shopId);
        verify(mtPromotionAdHourlyDetailMapper, never()).selectByShopIdAndBizDateAndChannel(anyLong(), any(), anyInt());
        verify(mtPromotionPeriodMetricDailyMapper, never()).insertOrUpdate(any());
    }

    @Test
    void shouldNotWriteZeroHistoricalPreAggregate() {
        Long shopId = 17L;
        mockPromotionEnabled(shopId);
        when(mtPromotionAdHourlyDetailMapper.selectByShopIdAndBizDateAndChannel(
                eq(shopId),
                any(LocalDate.class),
                eq(MtPromotionAdDataService.CHANNEL_DIANJIN)
        )).thenReturn(List.of());

        ShopDashboardMetricsService.MarketingStatsSnapshot marketing =
                shopDashboardMetricsService.buildMarketingStats(shopId, DashboardStatsRangeTypeEnum.LAST_7_DAYS);

        assertEquals(0, marketing.getPreviousExposure());
        assertEquals(new BigDecimal("0.00"), marketing.getPreviousCost());
        verify(mtPromotionPeriodMetricDailyMapper, never()).insertOrUpdate(any());
    }

    private void mockPromotionEnabled(Long shopId) {
        MtPromotionSetting setting = new MtPromotionSetting();
        setting.setShopId(shopId);
        setting.setPromotionEnabled(true);
        when(mtPromotionSettingMapper.selectByShopId(shopId)).thenReturn(setting);
    }
}
