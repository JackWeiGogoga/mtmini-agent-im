package com.gogoga.backend.service;

import com.gogoga.backend.entity.mt.MtPowerAccount;
import com.gogoga.backend.enums.DashboardStatsRangeTypeEnum;
import com.gogoga.backend.mapper.MtPowerConsumeMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * 门店算力看板统计服务。
 */
@Service
@RequiredArgsConstructor
public class ShopDashboardMetricsService {

    private static final ZoneId DEFAULT_ZONE_ID = ZoneId.of("Asia/Shanghai");
    private final MtPowerConsumeMapper mtPowerConsumeMapper;

    public PowerStatsSnapshot buildPowerStats(Long shopId, MtPowerAccount powerAccount) {
        return buildPowerStats(shopId, powerAccount, DashboardStatsRangeTypeEnum.LAST_7_DAYS);
    }

    public PowerStatsSnapshot buildPowerStats(Long shopId,
                                               MtPowerAccount powerAccount,
                                               DashboardStatsRangeTypeEnum statsRange) {
        TimeRange range = buildTimeRange(statsRange);
        PowerStatsSnapshot snapshot = new PowerStatsSnapshot();
        snapshot.setCurrentConsumedPower(defaultInteger(
                mtPowerConsumeMapper.sumPowerAmountByShopIdAndCreateTimeBetween(shopId, range.currentFrom(), range.currentTo())));
        snapshot.setPreviousConsumedPower(defaultInteger(
                mtPowerConsumeMapper.sumPowerAmountByShopIdAndCreateTimeBetween(shopId, range.previousFrom(), range.previousTo())));
        snapshot.setCurrentEstimatedPower(snapshot.getCurrentConsumedPower());
        snapshot.setPreviousEstimatedPower(snapshot.getPreviousConsumedPower());
        snapshot.setBalance(powerAccount == null ? 0 : defaultInteger(powerAccount.getBalance()));
        snapshot.setTotalRecharge(powerAccount == null ? 0 : defaultInteger(powerAccount.getTotalRecharge()));
        snapshot.setTotalConsume(powerAccount == null ? 0 : defaultInteger(powerAccount.getTotalConsume()));
        return snapshot;
    }

    private TimeRange buildTimeRange(DashboardStatsRangeTypeEnum statsRange) {
        DashboardStatsRangeTypeEnum range = statsRange == null ? DashboardStatsRangeTypeEnum.LAST_7_DAYS : statsRange;
        LocalDate today = LocalDate.now(DEFAULT_ZONE_ID);
        LocalDateTime currentFrom = today.minusDays(range.getDays() - 1L).atStartOfDay();
        LocalDateTime currentTo = LocalDateTime.of(today.plusDays(1), LocalTime.MIDNIGHT);
        return new TimeRange(currentFrom, currentTo, currentFrom.minusDays(range.getDays()), currentFrom);
    }

    private Integer defaultInteger(Integer value) {
        return value == null ? 0 : value;
    }

    private record TimeRange(LocalDateTime currentFrom,
                             LocalDateTime currentTo,
                             LocalDateTime previousFrom,
                             LocalDateTime previousTo) {
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
}
