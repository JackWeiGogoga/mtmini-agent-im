package com.gogoga.backend.service.promotion;

import com.gogoga.backend.entity.mt.MtOrderHourlyAggregate;
import com.gogoga.backend.entity.mt.MtPromotionRecentOrderStatistic;
import com.gogoga.backend.enums.MtOrderStatusEnum;
import com.gogoga.backend.mapper.MtOrderMapper;
import com.gogoga.backend.mapper.MtPromotionRecentOrderStatisticMapper;
import com.gogoga.backend.util.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 核心定价最近订单统计服务。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PromotionRecentOrderStatisticService {

    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Shanghai");
    private static final int REQUIRED_ORDER_DAYS = 7;
    private static final int MAX_LOOKBACK_DAYS = 15;
    private static final long SLOW_RESOLVE_WARN_THRESHOLD_MS = 300L;
    private static final String UNTRUSTED_REASON_INSUFFICIENT_ORDER_DAYS = "INSUFFICIENT_ORDER_DAYS";

    private final MtOrderMapper mtOrderMapper;
    private final MtPromotionRecentOrderStatisticMapper mtPromotionRecentOrderStatisticMapper;

    public MtPromotionRecentOrderStatistic resolve(
            Long shopId,
            LocalDate bizDate,
            ShopPeakWindowProfileLookupResult peakLookupResult
    ) {
        if (shopId == null || bizDate == null) {
            return null;
        }
        long startNanos = System.nanoTime();
        MtPromotionRecentOrderStatistic existing =
                mtPromotionRecentOrderStatisticMapper.selectByShopIdAndBizDate(shopId, bizDate);
        if (isReusable(existing, peakLookupResult)) {
            if (log.isDebugEnabled()) {
                log.debug("最近订单统计缓存命中，shopId={}, bizDate={}, trusted={}, durationMs={}",
                        shopId,
                        bizDate,
                        existing.getTrusted(),
                        elapsedMillis(startNanos));
            }
            return existing;
        }
        MtPromotionRecentOrderStatistic statistic = buildStatistic(shopId, bizDate, peakLookupResult);
        mtPromotionRecentOrderStatisticMapper.upsert(statistic);
        logResolvedStatistic(statistic, startNanos);
        return statistic;
    }

    public void invalidateByShopIdAndBizDate(Long shopId, LocalDate bizDate) {
        if (shopId == null || bizDate == null) {
            return;
        }
        mtPromotionRecentOrderStatisticMapper.deleteByShopIdAndBizDate(shopId, bizDate);
    }

    public void invalidateByShopIdAndBizDateOnOrAfter(Long shopId, LocalDate bizDate) {
        if (shopId == null || bizDate == null) {
            return;
        }
        mtPromotionRecentOrderStatisticMapper.deleteByShopIdAndBizDateOnOrAfter(shopId, bizDate);
    }

    private boolean isReusable(MtPromotionRecentOrderStatistic existing, ShopPeakWindowProfileLookupResult peakLookupResult) {
        if (existing == null) {
            return false;
        }
        return sameProfileContext(existing, peakLookupResult);
    }

    private boolean sameProfileContext(MtPromotionRecentOrderStatistic existing, ShopPeakWindowProfileLookupResult peakLookupResult) {
        LocalDateTime existingGeneratedAt = existing.getPeakWindowProfileGeneratedAt();
        LocalDateTime currentGeneratedAt = peakLookupResult == null ? null : peakLookupResult.getGeneratedAt();
        String existingDayType = existing.getPeakWindowProfileDayType();
        String currentDayType = peakLookupResult == null || peakLookupResult.getProfileDayType() == null
                ? null : peakLookupResult.getProfileDayType().name();
        return java.util.Objects.equals(existingGeneratedAt, currentGeneratedAt)
                && java.util.Objects.equals(existingDayType, currentDayType);
    }

    private MtPromotionRecentOrderStatistic buildStatistic(
            Long shopId,
            LocalDate bizDate,
            ShopPeakWindowProfileLookupResult peakLookupResult
    ) {
        LocalDate lookbackEndDate = bizDate.minusDays(1);
        LocalDate lookbackStartDate = bizDate.minusDays(MAX_LOOKBACK_DAYS);
        List<MtOrderHourlyAggregate> aggregates = mtOrderMapper.selectHourlyAggregatesByShopIdAndOrderSendTimeBetweenExcludingStatus(
                shopId,
                MtOrderStatusEnum.CANCELED.getCode(),
                lookbackStartDate.atStartOfDay(ZONE_ID).toEpochSecond(),
                bizDate.atStartOfDay(ZONE_ID).toEpochSecond()
        );
        Map<LocalDate, List<MtOrderHourlyAggregate>> byDate = groupByDate(aggregates);
        List<LocalDate> sampleDates = byDate.keySet().stream()
                .filter(date -> !date.isBefore(lookbackStartDate) && !date.isAfter(lookbackEndDate))
                .sorted(Comparator.reverseOrder())
                .limit(REQUIRED_ORDER_DAYS)
                .sorted()
                .toList();

        MtPromotionRecentOrderStatistic statistic = new MtPromotionRecentOrderStatistic();
        LocalDateTime now = LocalDateTime.now();
        statistic.setShopId(shopId);
        statistic.setBizDate(bizDate);
        statistic.setLookbackStartDate(lookbackStartDate);
        statistic.setLookbackEndDate(lookbackEndDate);
        statistic.setSampleDatesJson(JsonUtil.toJson(sampleDates));
        statistic.setPeakWindowProfileDayType(peakLookupResult == null || peakLookupResult.getProfileDayType() == null
                ? null : peakLookupResult.getProfileDayType().name());
        statistic.setPeakWindowProfileGeneratedAt(peakLookupResult == null ? null : peakLookupResult.getGeneratedAt());
        statistic.setPeakWindowsJson(peakLookupResult == null || peakLookupResult.getPeakWindows() == null
                ? null : JsonUtil.toJson(peakLookupResult.getPeakWindows()));
        statistic.setGeneratedAt(now);
        statistic.setCreateTime(now);
        statistic.setUpdateTime(now);

        if (sampleDates.size() < REQUIRED_ORDER_DAYS) {
            statistic.setTrusted(false);
            statistic.setUntrustedReason(UNTRUSTED_REASON_INSUFFICIENT_ORDER_DAYS);
            statistic.setTotalOrderCount(sumOrders(sampleDates, byDate));
            statistic.setAverageDailyOrderCount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            statistic.setLowPeakOrderCount(null);
            statistic.setLowPeakOrderShare(null);
            return statistic;
        }

        int totalOrderCount = sumOrders(sampleDates, byDate);
        Integer lowPeakOrderCount = calculateLowPeakOrderCount(sampleDates, byDate, peakLookupResult);
        statistic.setTrusted(true);
        statistic.setUntrustedReason(null);
        statistic.setTotalOrderCount(totalOrderCount);
        statistic.setAverageDailyOrderCount(BigDecimal.valueOf(totalOrderCount)
                .divide(BigDecimal.valueOf(REQUIRED_ORDER_DAYS), 2, RoundingMode.HALF_UP));
        statistic.setLowPeakOrderCount(lowPeakOrderCount);
        statistic.setLowPeakOrderShare(lowPeakOrderCount == null || totalOrderCount <= 0
                ? null
                : BigDecimal.valueOf(lowPeakOrderCount)
                .divide(BigDecimal.valueOf(totalOrderCount), 6, RoundingMode.HALF_UP));
        return statistic;
    }

    private void logResolvedStatistic(MtPromotionRecentOrderStatistic statistic, long startNanos) {
        long durationMs = elapsedMillis(startNanos);
        int sampleDayCount = JsonUtil.toList(
                statistic == null ? null : statistic.getSampleDatesJson(),
                LocalDate.class
        ).size();
        int peakWindowCount = JsonUtil.toList(
                statistic == null ? null : statistic.getPeakWindowsJson(),
                ShopPeakWindow.class
        ).size();
        log.info("最近订单统计已重算并缓存，shopId={}, bizDate={}, trusted={}, reason={}, sampleDayCount={}, totalOrderCount={}, averageDailyOrderCount={}, lowPeakOrderShare={}, peakWindowCount={}, durationMs={}",
                statistic == null ? null : statistic.getShopId(),
                statistic == null ? null : statistic.getBizDate(),
                statistic == null ? null : statistic.getTrusted(),
                statistic == null ? null : statistic.getUntrustedReason(),
                sampleDayCount,
                statistic == null ? null : statistic.getTotalOrderCount(),
                statistic == null ? null : statistic.getAverageDailyOrderCount(),
                statistic == null ? null : statistic.getLowPeakOrderShare(),
                peakWindowCount,
                durationMs);
        if (durationMs > SLOW_RESOLVE_WARN_THRESHOLD_MS) {
            log.warn("最近订单统计重算耗时较高，shopId={}, bizDate={}, durationMs={}, thresholdMs={}",
                    statistic == null ? null : statistic.getShopId(),
                    statistic == null ? null : statistic.getBizDate(),
                    durationMs,
                    SLOW_RESOLVE_WARN_THRESHOLD_MS);
        }
    }

    private long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    private Map<LocalDate, List<MtOrderHourlyAggregate>> groupByDate(List<MtOrderHourlyAggregate> aggregates) {
        Map<LocalDate, List<MtOrderHourlyAggregate>> result = new LinkedHashMap<>();
        if (aggregates == null) {
            return result;
        }
        for (MtOrderHourlyAggregate aggregate : aggregates) {
            if (aggregate == null || aggregate.getOrderDate() == null) {
                continue;
            }
            result.computeIfAbsent(aggregate.getOrderDate(), key -> new ArrayList<>()).add(aggregate);
        }
        return result;
    }

    private int sumOrders(List<LocalDate> sampleDates, Map<LocalDate, List<MtOrderHourlyAggregate>> byDate) {
        if (sampleDates == null || byDate == null) {
            return 0;
        }
        int total = 0;
        for (LocalDate sampleDate : sampleDates) {
            List<MtOrderHourlyAggregate> aggregates = byDate.get(sampleDate);
            if (aggregates == null) {
                continue;
            }
            for (MtOrderHourlyAggregate aggregate : aggregates) {
                total += aggregate.getOrderCount() == null ? 0 : aggregate.getOrderCount();
            }
        }
        return total;
    }

    private Integer calculateLowPeakOrderCount(
            List<LocalDate> sampleDates,
            Map<LocalDate, List<MtOrderHourlyAggregate>> byDate,
            ShopPeakWindowProfileLookupResult peakLookupResult
    ) {
        if (CollectionUtils.isEmpty(sampleDates)
                || byDate == null
                || peakLookupResult == null
                || !peakLookupResult.isGatingEnabled()
                || CollectionUtils.isEmpty(peakLookupResult.getPeakWindows())) {
            return null;
        }
        Set<Integer> peakHours = peakLookupResult.getPeakWindows().stream()
                .flatMap(window -> resolvePeakHours(window).stream())
                .collect(Collectors.toSet());
        if (peakHours.isEmpty()) {
            return null;
        }
        int lowPeakOrderCount = 0;
        for (LocalDate sampleDate : sampleDates) {
            List<MtOrderHourlyAggregate> aggregates = byDate.get(sampleDate);
            if (aggregates == null) {
                continue;
            }
            for (MtOrderHourlyAggregate aggregate : aggregates) {
                Integer hourBucket = aggregate.getHourBucket();
                if (hourBucket == null || !peakHours.contains(hourBucket)) {
                    lowPeakOrderCount += aggregate.getOrderCount() == null ? 0 : aggregate.getOrderCount();
                }
            }
        }
        return lowPeakOrderCount;
    }

    private List<Integer> resolvePeakHours(ShopPeakWindow window) {
        if (window == null || window.getStartHour() == null || window.getEndHour() == null) {
            return List.of();
        }
        int startHour = Math.max(0, Math.min(23, window.getStartHour()));
        int endHour = Math.max(startHour, Math.min(24, window.getEndHour()));
        List<Integer> hours = new ArrayList<>();
        for (int hour = startHour; hour < endHour; hour++) {
            hours.add(hour);
        }
        return hours;
    }
}
