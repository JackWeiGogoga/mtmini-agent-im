package com.gogoga.backend.service;

import com.gogoga.backend.entity.mt.MtPowerAccount;
import com.gogoga.backend.enums.DashboardStatsRangeTypeEnum;
import com.gogoga.backend.mapper.MtImAutoReplyMapper;
import com.gogoga.backend.mapper.MtPowerConsumeMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private final MtImAutoReplyMapper mtImAutoReplyMapper;

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

    public MessageStatsSnapshot buildMessageStats(Long shopId, DashboardStatsRangeTypeEnum statsRange) {
        TimeRange range = buildTimeRange(statsRange);
        MessageStatsSnapshot snapshot = new MessageStatsSnapshot();

        int currentReply = mtImAutoReplyMapper.countSentBetween(shopId, range.currentFrom(), range.currentTo());
        int previousReply = mtImAutoReplyMapper.countSentBetween(shopId, range.previousFrom(), range.previousTo());
        int currentAfterSale = mtImAutoReplyMapper.countAfterSaleBetween(shopId, range.currentFrom(), range.currentTo());
        int previousAfterSale = mtImAutoReplyMapper.countAfterSaleBetween(shopId, range.previousFrom(), range.previousTo());
        int currentTriggered = mtImAutoReplyMapper.countTriggeredBetween(shopId, range.currentFrom(), range.currentTo());
        int previousTriggered = mtImAutoReplyMapper.countTriggeredBetween(shopId, range.previousFrom(), range.previousTo());

        snapshot.setCurrentMessageReplyCount(currentReply);
        snapshot.setPreviousMessageReplyCount(previousReply);
        snapshot.setCurrentAfterSaleMessageCount(currentAfterSale);
        snapshot.setPreviousAfterSaleMessageCount(previousAfterSale);
        snapshot.setMessageReplyRate(replyRate(currentReply, currentTriggered));
        snapshot.setPreviousMessageReplyRate(replyRate(previousReply, previousTriggered));
        return snapshot;
    }

    private BigDecimal replyRate(int replyCount, int triggeredCount) {
        if (triggeredCount <= 0) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(replyCount)
                .divide(BigDecimal.valueOf(triggeredCount), 4, RoundingMode.HALF_UP);
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

    @Data
    public static class MessageStatsSnapshot {
        private Integer currentMessageReplyCount = 0;
        private Integer previousMessageReplyCount = 0;
        private Integer currentAfterSaleMessageCount = 0;
        private Integer previousAfterSaleMessageCount = 0;
        private BigDecimal messageReplyRate = BigDecimal.ZERO;
        private BigDecimal previousMessageReplyRate = BigDecimal.ZERO;
    }
}
