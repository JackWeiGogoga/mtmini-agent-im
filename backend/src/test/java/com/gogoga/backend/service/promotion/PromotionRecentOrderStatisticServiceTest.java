package com.gogoga.backend.service.promotion;

import com.gogoga.backend.entity.mt.MtOrderHourlyAggregate;
import com.gogoga.backend.entity.mt.MtPromotionRecentOrderStatistic;
import com.gogoga.backend.enums.MtOrderStatusEnum;
import com.gogoga.backend.mapper.MtOrderMapper;
import com.gogoga.backend.mapper.MtPromotionRecentOrderStatisticMapper;
import com.gogoga.backend.util.JsonUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromotionRecentOrderStatisticServiceTest {

    @Mock
    private MtOrderMapper mtOrderMapper;

    @Mock
    private MtPromotionRecentOrderStatisticMapper mtPromotionRecentOrderStatisticMapper;

    @Test
    void shouldUseLatestSevenOrderDaysBeforeBizDateWithinFifteenDays() {
        PromotionRecentOrderStatisticService service = buildService();
        LocalDate bizDate = LocalDate.of(2026, 5, 19);
        ShopPeakWindowProfileLookupResult peakLookupResult = buildPeakLookup();

        when(mtPromotionRecentOrderStatisticMapper.selectByShopIdAndBizDate(1L, bizDate)).thenReturn(null);
        when(mtOrderMapper.selectHourlyAggregatesByShopIdAndOrderSendTimeBetweenExcludingStatus(
                eq(1L),
                eq(MtOrderStatusEnum.CANCELED.getCode()),
                eq(LocalDate.of(2026, 5, 4).atStartOfDay(ZoneId.of("Asia/Shanghai")).toEpochSecond()),
                eq(bizDate.atStartOfDay(ZoneId.of("Asia/Shanghai")).toEpochSecond())
        )).thenReturn(List.of(
                aggregate(LocalDate.of(2026, 5, 19), 12, 99),
                aggregate(LocalDate.of(2026, 5, 18), 12, 10),
                aggregate(LocalDate.of(2026, 5, 18), 18, 20),
                aggregate(LocalDate.of(2026, 5, 17), 11, 5),
                aggregate(LocalDate.of(2026, 5, 17), 18, 25),
                aggregate(LocalDate.of(2026, 5, 16), 18, 30),
                aggregate(LocalDate.of(2026, 5, 15), 18, 30),
                aggregate(LocalDate.of(2026, 5, 14), 18, 30),
                aggregate(LocalDate.of(2026, 5, 13), 18, 30),
                aggregate(LocalDate.of(2026, 5, 12), 18, 30),
                aggregate(LocalDate.of(2026, 5, 11), 18, 100)
        ));

        MtPromotionRecentOrderStatistic statistic = service.resolve(1L, bizDate, peakLookupResult);

        assertTrue(statistic.getTrusted());
        assertEquals(LocalDate.of(2026, 5, 4), statistic.getLookbackStartDate());
        assertEquals(LocalDate.of(2026, 5, 18), statistic.getLookbackEndDate());
        assertEquals(210, statistic.getTotalOrderCount());
        assertEquals(new BigDecimal("30.00"), statistic.getAverageDailyOrderCount());
        assertEquals(195, statistic.getLowPeakOrderCount());
        assertEquals(new BigDecimal("0.928571"), statistic.getLowPeakOrderShare());
        assertEquals(List.of(
                LocalDate.of(2026, 5, 12),
                LocalDate.of(2026, 5, 13),
                LocalDate.of(2026, 5, 14),
                LocalDate.of(2026, 5, 15),
                LocalDate.of(2026, 5, 16),
                LocalDate.of(2026, 5, 17),
                LocalDate.of(2026, 5, 18)
        ), JsonUtil.toList(statistic.getSampleDatesJson(), LocalDate.class));

        ArgumentCaptor<MtPromotionRecentOrderStatistic> captor =
                ArgumentCaptor.forClass(MtPromotionRecentOrderStatistic.class);
        verify(mtPromotionRecentOrderStatisticMapper).upsert(captor.capture());
        assertEquals(statistic, captor.getValue());
    }

    @Test
    void shouldMarkStatisticUntrustedWhenFifteenDaysCannotProvideSevenOrderDays() {
        PromotionRecentOrderStatisticService service = buildService();
        LocalDate bizDate = LocalDate.of(2026, 5, 19);

        when(mtPromotionRecentOrderStatisticMapper.selectByShopIdAndBizDate(1L, bizDate)).thenReturn(null);
        when(mtOrderMapper.selectHourlyAggregatesByShopIdAndOrderSendTimeBetweenExcludingStatus(
                eq(1L),
                eq(MtOrderStatusEnum.CANCELED.getCode()),
                eq(LocalDate.of(2026, 5, 4).atStartOfDay(ZoneId.of("Asia/Shanghai")).toEpochSecond()),
                eq(bizDate.atStartOfDay(ZoneId.of("Asia/Shanghai")).toEpochSecond())
        )).thenReturn(List.of(
                aggregate(LocalDate.of(2026, 5, 18), 18, 12),
                aggregate(LocalDate.of(2026, 5, 16), 18, 13),
                aggregate(LocalDate.of(2026, 5, 12), 18, 14)
        ));

        MtPromotionRecentOrderStatistic statistic = service.resolve(1L, bizDate, buildPeakLookup());

        assertFalse(statistic.getTrusted());
        assertEquals("INSUFFICIENT_ORDER_DAYS", statistic.getUntrustedReason());
        assertEquals(39, statistic.getTotalOrderCount());
        assertEquals(new BigDecimal("0.00"), statistic.getAverageDailyOrderCount());
    }

    @Test
    void shouldReuseSavedStatisticWhenPeakProfileContextIsUnchanged() {
        PromotionRecentOrderStatisticService service = buildService();
        LocalDate bizDate = LocalDate.of(2026, 5, 19);
        ShopPeakWindowProfileLookupResult peakLookupResult = buildPeakLookup();
        MtPromotionRecentOrderStatistic existing = new MtPromotionRecentOrderStatistic();
        existing.setShopId(1L);
        existing.setBizDate(bizDate);
        existing.setPeakWindowProfileDayType("WEEKDAY");
        existing.setPeakWindowProfileGeneratedAt(peakLookupResult.getGeneratedAt());

        when(mtPromotionRecentOrderStatisticMapper.selectByShopIdAndBizDate(1L, bizDate)).thenReturn(existing);

        MtPromotionRecentOrderStatistic statistic = service.resolve(1L, bizDate, peakLookupResult);

        assertSame(existing, statistic);
        verify(mtOrderMapper, never()).selectHourlyAggregatesByShopIdAndOrderSendTimeBetweenExcludingStatus(
                eq(1L),
                eq(MtOrderStatusEnum.CANCELED.getCode()),
                eq(LocalDate.of(2026, 5, 4).atStartOfDay(ZoneId.of("Asia/Shanghai")).toEpochSecond()),
                eq(bizDate.atStartOfDay(ZoneId.of("Asia/Shanghai")).toEpochSecond())
        );
        verify(mtPromotionRecentOrderStatisticMapper, never()).upsert(existing);
    }

    private PromotionRecentOrderStatisticService buildService() {
        return new PromotionRecentOrderStatisticService(mtOrderMapper, mtPromotionRecentOrderStatisticMapper);
    }

    private ShopPeakWindowProfileLookupResult buildPeakLookup() {
        ShopPeakWindow window = new ShopPeakWindow();
        window.setStartHour(11);
        window.setEndHour(14);
        window.setStartTime("11:00");
        window.setEndTime("14:00");

        ShopPeakWindowProfileLookupResult result = new ShopPeakWindowProfileLookupResult();
        result.setProfileStatus(ShopPeakWindowProfileLookupResult.STATUS_USABLE);
        result.setGatingEnabled(true);
        result.setProfileDayType(ShopPeakDayType.WEEKDAY);
        result.setGeneratedAt(LocalDateTime.of(2026, 5, 19, 4, 0));
        result.setPeakWindows(List.of(window));
        return result;
    }

    private MtOrderHourlyAggregate aggregate(LocalDate orderDate, int hourBucket, int orderCount) {
        MtOrderHourlyAggregate aggregate = new MtOrderHourlyAggregate();
        aggregate.setOrderDate(orderDate);
        aggregate.setHourBucket(hourBucket);
        aggregate.setOrderCount(orderCount);
        return aggregate;
    }
}
