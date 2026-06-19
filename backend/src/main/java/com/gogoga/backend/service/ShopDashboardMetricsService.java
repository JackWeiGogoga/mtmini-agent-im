package com.gogoga.backend.service;

import com.gogoga.backend.entity.mt.MtPowerAccount;
import com.gogoga.backend.enums.DashboardStatsRangeTypeEnum;
import com.gogoga.backend.enums.MtOrderStatusEnum;
import com.gogoga.backend.mapper.MtOrderMapper;
import com.gogoga.backend.mapper.MtPoiTrafficDailyMapper;
import com.gogoga.backend.mapper.MtPowerConsumeMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 门店基础看板统计服务。
 */
@Service
@RequiredArgsConstructor
public class ShopDashboardMetricsService {

    private static final BigDecimal ZERO_MONEY = new BigDecimal("0.00");
    private static final BigDecimal ZERO_RATIO = new BigDecimal("0.000000");
    private static final ZoneId DEFAULT_ZONE_ID = ZoneId.of("Asia/Shanghai");

    private final MtOrderMapper mtOrderMapper;
    private final MtPoiTrafficDailyMapper mtPoiTrafficDailyMapper;
    private final MtPowerConsumeMapper mtPowerConsumeMapper;

    public MarketingStatsSnapshot buildMarketingStats(Long shopId) {
        return buildMarketingStats(shopId, DashboardStatsRangeTypeEnum.LAST_7_DAYS);
    }

    public MarketingStatsSnapshot buildMarketingStats(Long shopId, DashboardStatsRangeTypeEnum statsRange) {
        return buildDashboardStats(shopId, statsRange).getMarketingStats();
    }

    public OrderStatsSnapshot buildOrderStats(Long shopId) {
        return buildOrderStats(shopId, DashboardStatsRangeTypeEnum.LAST_7_DAYS);
    }

    public OrderStatsSnapshot buildOrderStats(Long shopId, DashboardStatsRangeTypeEnum statsRange) {
        return buildDashboardStats(shopId, statsRange).getOrderStats();
    }

    public DashboardStatsSnapshot buildDashboardStats(Long shopId, DashboardStatsRangeTypeEnum statsRange) {
        TimeRange range = buildTimeRange(statsRange);
        BigDecimal currentIncome = sumIncome(shopId, range.currentFrom(), range.currentTo());
        BigDecimal previousIncome = sumIncome(shopId, range.previousFrom(), range.previousTo());
        Integer currentOrderCount = countOrders(shopId, range.currentFrom(), range.currentTo());
        Integer previousOrderCount = countOrders(shopId, range.previousFrom(), range.previousTo());

        MarketingStatsSnapshot marketing = new MarketingStatsSnapshot();
        marketing.setCurrentExposure(sumExposure(shopId, MtPoiTrafficDailyService.FLOW_PROP_ALL,
                range.currentFrom().toLocalDate(), range.currentTo().toLocalDate().plusDays(1)));
        marketing.setPreviousExposure(sumExposure(shopId, MtPoiTrafficDailyService.FLOW_PROP_ALL,
                range.previousFrom().toLocalDate(), range.previousTo().toLocalDate().plusDays(1)));
        marketing.setCurrentIncome(currentIncome);
        marketing.setPreviousIncome(previousIncome);
        marketing.setCurrentCost(ZERO_MONEY);
        marketing.setPreviousCost(ZERO_MONEY);
        marketing.setCurrentCostRatio(ZERO_RATIO);
        marketing.setPreviousCostRatio(ZERO_RATIO);

        OrderStatsSnapshot orderStats = new OrderStatsSnapshot();
        orderStats.setCurrentCompletedIncome(currentIncome);
        orderStats.setPreviousCompletedIncome(previousIncome);
        orderStats.setCurrentCompletedOrderCount(currentOrderCount);
        orderStats.setPreviousCompletedOrderCount(previousOrderCount);

        DashboardStatsSnapshot snapshot = new DashboardStatsSnapshot();
        snapshot.setMarketingStats(marketing);
        snapshot.setOrderStats(orderStats);
        return snapshot;
    }

    public Map<Long, DashboardStatsSnapshot> buildDashboardStatsBatch(Collection<Long> shopIds,
                                                                      DashboardStatsRangeTypeEnum statsRange) {
        if (shopIds == null || shopIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, DashboardStatsSnapshot> result = new HashMap<>();
        for (Long shopId : shopIds) {
            if (shopId != null) {
                result.put(shopId, buildDashboardStats(shopId, statsRange));
            }
        }
        return result;
    }

    public PowerStatsSnapshot buildPowerStats(Long shopId, MtPowerAccount powerAccount) {
        return buildPowerStats(shopId, powerAccount, DashboardStatsRangeTypeEnum.LAST_7_DAYS);
    }

    public PowerStatsSnapshot buildPowerStats(Long shopId, MtPowerAccount powerAccount, DashboardStatsRangeTypeEnum statsRange) {
        TimeRange range = buildTimeRange(statsRange);
        PowerStatsSnapshot snapshot = new PowerStatsSnapshot();
        snapshot.setCurrentConsumedPower(defaultInteger(
                mtPowerConsumeMapper.sumPowerAmountByShopIdAndCreateTimeBetween(shopId, range.currentFrom(), range.currentTo())
        ));
        snapshot.setPreviousConsumedPower(defaultInteger(
                mtPowerConsumeMapper.sumPowerAmountByShopIdAndCreateTimeBetween(shopId, range.previousFrom(), range.previousTo())
        ));
        snapshot.setCurrentEstimatedPower(snapshot.getCurrentConsumedPower());
        snapshot.setPreviousEstimatedPower(snapshot.getPreviousConsumedPower());
        snapshot.setBalance(powerAccount == null ? 0 : defaultInteger(powerAccount.getBalance()));
        snapshot.setTotalRecharge(powerAccount == null ? 0 : defaultInteger(powerAccount.getTotalRecharge()));
        snapshot.setTotalConsume(powerAccount == null ? 0 : defaultInteger(powerAccount.getTotalConsume()));
        return snapshot;
    }

    public ExposureBreakdownSnapshot buildExposureBreakdown(Long shopId) {
        return buildExposureBreakdown(shopId, DashboardStatsRangeTypeEnum.LAST_7_DAYS);
    }

    public ExposureBreakdownSnapshot buildExposureBreakdown(Long shopId, DashboardStatsRangeTypeEnum statsRange) {
        TimeRange range = buildTimeRange(statsRange);
        ExposureBreakdownSnapshot snapshot = new ExposureBreakdownSnapshot();
        snapshot.setCurrentFreeExposure(sumExposure(shopId, MtPoiTrafficDailyService.FLOW_PROP_NATURAL,
                range.currentFrom().toLocalDate(), range.currentTo().toLocalDate().plusDays(1)));
        snapshot.setPreviousFreeExposure(sumExposure(shopId, MtPoiTrafficDailyService.FLOW_PROP_NATURAL,
                range.previousFrom().toLocalDate(), range.previousTo().toLocalDate().plusDays(1)));
        snapshot.setCurrentPaidExposure(sumExposure(shopId, MtPoiTrafficDailyService.FLOW_PROP_PAID,
                range.currentFrom().toLocalDate(), range.currentTo().toLocalDate().plusDays(1)));
        snapshot.setPreviousPaidExposure(sumExposure(shopId, MtPoiTrafficDailyService.FLOW_PROP_PAID,
                range.previousFrom().toLocalDate(), range.previousTo().toLocalDate().plusDays(1)));
        return snapshot;
    }

    private BigDecimal sumIncome(Long shopId, LocalDateTime from, LocalDateTime to) {
        BigDecimal value = mtOrderMapper.sumIncomeByShopIdAndOrderSendTimeBetweenExcludingStatus(
                shopId,
                MtOrderStatusEnum.CANCELED.getCode(),
                toEpochSeconds(from),
                toEpochSeconds(to)
        );
        return value == null ? ZERO_MONEY : value;
    }

    private Integer countOrders(Long shopId, LocalDateTime from, LocalDateTime to) {
        return defaultInteger(mtOrderMapper.countOrdersByShopIdAndOrderSendTimeBetweenExcludingStatus(
                shopId,
                MtOrderStatusEnum.CANCELED.getCode(),
                toEpochSeconds(from),
                toEpochSeconds(to)
        ));
    }

    private Integer sumExposure(Long shopId, Integer flowProp, LocalDate from, LocalDate to) {
        return defaultInteger(mtPoiTrafficDailyMapper.sumExposureCountByShopIdAndBizDateBetween(shopId, flowProp, from, to));
    }

    private TimeRange buildTimeRange(DashboardStatsRangeTypeEnum statsRange) {
        DashboardStatsRangeTypeEnum range = statsRange == null ? DashboardStatsRangeTypeEnum.LAST_7_DAYS : statsRange;
        LocalDate today = LocalDate.now(DEFAULT_ZONE_ID);
        LocalDateTime currentFrom = today.minusDays(range.getDays() - 1L).atStartOfDay();
        LocalDateTime currentTo = LocalDateTime.of(today, LocalTime.MAX);
        LocalDateTime previousTo = currentFrom;
        LocalDateTime previousFrom = previousTo.minusDays(range.getDays());
        return new TimeRange(currentFrom, currentTo, previousFrom, previousTo);
    }

    private Long toEpochSeconds(LocalDateTime time) {
        return time.atZone(DEFAULT_ZONE_ID).toEpochSecond();
    }

    private Integer defaultInteger(Integer value) {
        return value == null ? 0 : value;
    }

    private record TimeRange(
            LocalDateTime currentFrom,
            LocalDateTime currentTo,
            LocalDateTime previousFrom,
            LocalDateTime previousTo
    ) {
    }

    @Data
    public static class MarketingStatsSnapshot {
        private Integer currentExposure;
        private Integer previousExposure;
        private BigDecimal currentIncome = ZERO_MONEY;
        private BigDecimal previousIncome = ZERO_MONEY;
        private BigDecimal currentCost = ZERO_MONEY;
        private BigDecimal previousCost = ZERO_MONEY;
        private BigDecimal currentCostRatio = ZERO_RATIO;
        private BigDecimal previousCostRatio = ZERO_RATIO;
    }

    @Data
    public static class OrderStatsSnapshot {
        private BigDecimal currentCompletedIncome = ZERO_MONEY;
        private BigDecimal previousCompletedIncome = ZERO_MONEY;
        private Integer currentCompletedOrderCount = 0;
        private Integer previousCompletedOrderCount = 0;
    }

    @Data
    public static class PowerStatsSnapshot {
        private Integer currentConsumedPower = 0;
        private Integer previousConsumedPower = 0;
        private Integer currentEstimatedPower = 0;
        private Integer previousEstimatedPower = 0;
        private Integer balance = 0;
        private Integer totalRecharge = 0;
        private Integer totalConsume = 0;
    }

    @Data
    public static class ExposureBreakdownSnapshot {
        private Integer currentFreeExposure = 0;
        private Integer previousFreeExposure = 0;
        private Integer currentPaidExposure = 0;
        private Integer previousPaidExposure = 0;
    }

    @Data
    public static class DashboardStatsSnapshot {
        private MarketingStatsSnapshot marketingStats = new MarketingStatsSnapshot();
        private OrderStatsSnapshot orderStats = new OrderStatsSnapshot();
    }
}
