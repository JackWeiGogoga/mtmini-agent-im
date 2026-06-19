package com.gogoga.backend.service;

import com.gogoga.backend.entity.mt.MtPowerAccount;
import com.gogoga.backend.enums.DashboardStatsRangeTypeEnum;
import com.gogoga.backend.entity.mt.MtCommentStatsAggregate;
import com.gogoga.backend.mapper.MtCommentMapper;
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

    private static final BigDecimal ZERO_RATIO = new BigDecimal("0.000000");
    private static final ZoneId DEFAULT_ZONE_ID = ZoneId.of("Asia/Shanghai");

    private final MtCommentMapper mtCommentMapper;
    private final MtPowerConsumeMapper mtPowerConsumeMapper;

    public CommentStatsSnapshot buildCommentStats(Long shopId) {
        return buildCommentStats(shopId, DashboardStatsRangeTypeEnum.LAST_7_DAYS);
    }

    public CommentStatsSnapshot buildCommentStats(Long shopId, DashboardStatsRangeTypeEnum statsRange) {
        return buildDashboardStats(shopId, statsRange).getCommentStats();
    }

    public DashboardStatsSnapshot buildDashboardStats(Long shopId, DashboardStatsRangeTypeEnum statsRange) {
        TimeRange range = buildTimeRange(statsRange);
        CommentStatsSnapshot commentStats = buildCommentStatsSnapshot(shopId, range.currentFrom(), range.currentTo());
        MtCommentStatsAggregate previous = selectCommentStats(shopId, range.previousFrom(), range.previousTo());
        commentStats.setPreviousGoodCommentCount(defaultInteger(previous.getGoodCommentCount()));
        commentStats.setPreviousBadCommentCount(defaultInteger(previous.getBadCommentCount()));
        commentStats.setPreviousBadCommentReplyRate(calculateReplyRate(
                previous.getBadCommentCount(),
                previous.getRepliedBadCommentCount()
        ));

        DashboardStatsSnapshot snapshot = new DashboardStatsSnapshot();
        snapshot.setCommentStats(commentStats);
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

    private TimeRange buildTimeRange(DashboardStatsRangeTypeEnum statsRange) {
        DashboardStatsRangeTypeEnum range = statsRange == null ? DashboardStatsRangeTypeEnum.LAST_7_DAYS : statsRange;
        LocalDate today = LocalDate.now(DEFAULT_ZONE_ID);
        LocalDateTime currentFrom = today.minusDays(range.getDays() - 1L).atStartOfDay();
        LocalDateTime currentTo = LocalDateTime.of(today.plusDays(1), LocalTime.MIDNIGHT);
        LocalDateTime previousTo = currentFrom;
        LocalDateTime previousFrom = previousTo.minusDays(range.getDays());
        return new TimeRange(currentFrom, currentTo, previousFrom, previousTo);
    }

    private CommentStatsSnapshot buildCommentStatsSnapshot(Long shopId, LocalDateTime from, LocalDateTime to) {
        MtCommentStatsAggregate current = selectCommentStats(shopId, from, to);
        CommentStatsSnapshot snapshot = new CommentStatsSnapshot();
        snapshot.setGoodCommentCount(defaultInteger(current.getGoodCommentCount()));
        snapshot.setNeutralCommentCount(defaultInteger(current.getNeutralCommentCount()));
        snapshot.setBadCommentCount(defaultInteger(current.getBadCommentCount()));
        snapshot.setRepliedBadCommentCount(defaultInteger(current.getRepliedBadCommentCount()));
        snapshot.setBadCommentReplyRate(calculateReplyRate(
                current.getBadCommentCount(),
                current.getRepliedBadCommentCount()
        ));
        return snapshot;
    }

    private MtCommentStatsAggregate selectCommentStats(Long shopId, LocalDateTime from, LocalDateTime to) {
        MtCommentStatsAggregate aggregate = mtCommentMapper.selectStatsByShopIdAndCreateTimeBetween(shopId, from, to);
        return aggregate == null ? new MtCommentStatsAggregate() : aggregate;
    }

    private BigDecimal calculateReplyRate(Integer badCommentCount, Integer repliedBadCommentCount) {
        int total = defaultInteger(badCommentCount);
        if (total <= 0) {
            return ZERO_RATIO;
        }
        return BigDecimal.valueOf(defaultInteger(repliedBadCommentCount))
                .divide(BigDecimal.valueOf(total), 6, RoundingMode.HALF_UP);
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
    public static class CommentStatsSnapshot {
        private Integer goodCommentCount = 0;
        private Integer neutralCommentCount = 0;
        private Integer badCommentCount = 0;
        private Integer repliedBadCommentCount = 0;
        private BigDecimal badCommentReplyRate = ZERO_RATIO;
        private Integer previousGoodCommentCount = 0;
        private Integer previousBadCommentCount = 0;
        private BigDecimal previousBadCommentReplyRate = ZERO_RATIO;
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
    public static class DashboardStatsSnapshot {
        private CommentStatsSnapshot commentStats = new CommentStatsSnapshot();
    }
}
