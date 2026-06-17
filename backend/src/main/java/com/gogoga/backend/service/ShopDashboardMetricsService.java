package com.gogoga.backend.service;

import com.gogoga.backend.enums.DashboardStatsRangeTypeEnum;
import com.gogoga.backend.enums.MtOrderStatusEnum;
import com.gogoga.backend.entity.mt.MtPowerAccount;
import com.gogoga.backend.entity.mt.MtPromotionAdHourlyDetail;
import com.gogoga.backend.entity.mt.MtPromotionAdRealtimeSnapshot;
import com.gogoga.backend.entity.mt.MtPromotionBidRecord;
import com.gogoga.backend.entity.mt.MtPromotionPeriodMetricDaily;
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
import com.gogoga.backend.util.JsonUtil;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 门店看板统计服务，供 H5 首页与后台总控台复用。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ShopDashboardMetricsService {

    private static final BigDecimal ZERO_MONEY = new BigDecimal("0.00");
    private static final BigDecimal ZERO_RATIO = new BigDecimal("0.000000");
    private static final ZoneId DEFAULT_ZONE_ID = ZoneId.of("Asia/Shanghai");
    private static final Duration DASHBOARD_STATS_CACHE_TTL = Duration.ofSeconds(30);
    private static final int DASHBOARD_STATS_ALIGNMENT_MINUTES = 10;

    private final MtOrderMapper mtOrderMapper;
    private final MtPoiTrafficDailyMapper mtPoiTrafficDailyMapper;
    private final MtPowerConsumeMapper mtPowerConsumeMapper;
    private final MtPromotionBidRecordMapper mtPromotionBidRecordMapper;
    private final MtPromotionAdHourlyDetailMapper mtPromotionAdHourlyDetailMapper;
    private final MtPromotionAdRealtimeSnapshotService mtPromotionAdRealtimeSnapshotService;
    private final MtPromotionPeriodMetricDailyMapper mtPromotionPeriodMetricDailyMapper;
    private final MtPromotionSettingHistoryMapper mtPromotionSettingHistoryMapper;
    private final MtPromotionSettingMapper mtPromotionSettingMapper;
    private final ConcurrentMap<String, CacheEntry<DashboardStatsSnapshot>> dashboardStatsCache = new ConcurrentHashMap<>();
    private Supplier<LocalDateTime> nowSupplier = () -> LocalDateTime.now(DEFAULT_ZONE_ID);

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
        DashboardStatsRangeTypeEnum normalizedStatsRange = normalizeStatsRange(statsRange);
        TimeRange range = buildCurrentAndPreviousSameProgressTimeRange(normalizedStatsRange);
        String cacheKey = buildDashboardStatsCacheKey(shopId, normalizedStatsRange, range);
        CacheEntry<DashboardStatsSnapshot> cached = dashboardStatsCache.get(cacheKey);
        LocalDateTime now = currentNow();
        if (cached != null && cached.expiresAt().isAfter(now)) {
            return cached.value();
        }
        DashboardStatsSnapshot snapshot = buildDashboardStatsUncached(shopId, range);
        dashboardStatsCache.put(cacheKey, new CacheEntry<>(snapshot, now.plus(DASHBOARD_STATS_CACHE_TTL)));
        return snapshot;
    }

    public Map<Long, DashboardStatsSnapshot> buildDashboardStatsBatch(Collection<Long> shopIds,
                                                                      DashboardStatsRangeTypeEnum statsRange) {
        if (shopIds == null || shopIds.isEmpty()) {
            return Collections.emptyMap();
        }
        long startNanos = System.nanoTime();
        DashboardStatsRangeTypeEnum normalizedStatsRange = normalizeStatsRange(statsRange);
        Map<Long, DashboardStatsSnapshot> result = new HashMap<>();
        List<Long> distinctShopIds = shopIds.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
        for (Long shopId : distinctShopIds) {
            result.put(shopId, buildDashboardStats(shopId, normalizedStatsRange));
        }
        if (log.isDebugEnabled()) {
            log.debug(
                    "推广期看板批量统计完成，shopCount={}, statsRange={}, elapsedMs={}",
                    distinctShopIds.size(),
                    normalizedStatsRange,
                    (System.nanoTime() - startNanos) / 1_000_000
            );
        }
        return result;
    }

    private DashboardStatsSnapshot buildDashboardStatsUncached(Long shopId, TimeRange activeRange) {
        List<PromotionActiveInterval> currentIntervals = buildPromotionActiveIntervals(
                shopId,
                activeRange.currentFrom(),
                activeRange.currentToExclusive()
        );
        List<PromotionActiveInterval> previousIntervals = buildPromotionActiveIntervals(
                shopId,
                activeRange.previousFrom(),
                activeRange.previousToExclusive()
        );

        BigDecimal currentIncome = sumIncome(shopId, currentIntervals);
        BigDecimal previousIncome = sumIncome(shopId, previousIntervals);
        PromotionAdMetrics currentAdMetrics = loadPromotionAdMetrics(shopId, currentIntervals);
        PromotionAdMetrics previousAdMetrics = loadPromotionAdMetrics(shopId, previousIntervals);

        MarketingStatsSnapshot snapshot = new MarketingStatsSnapshot();
        snapshot.setCurrentExposure(currentAdMetrics.showCount());
        snapshot.setPreviousExposure(previousAdMetrics.showCount());
        snapshot.setCurrentIncome(currentIncome);
        snapshot.setPreviousIncome(previousIncome);
        snapshot.setCurrentCost(currentAdMetrics.cost());
        snapshot.setPreviousCost(previousAdMetrics.cost());
        snapshot.setCurrentCostRatio(calculateCostRatio(currentAdMetrics.cost(), currentIncome));
        snapshot.setPreviousCostRatio(calculateCostRatio(previousAdMetrics.cost(), previousIncome));
        OrderStatsSnapshot orderStats = new OrderStatsSnapshot();
        orderStats.setCurrentCompletedIncome(currentIncome);
        orderStats.setPreviousCompletedIncome(previousIncome);
        orderStats.setCurrentCompletedOrderCount(countCompletedOrders(shopId, currentIntervals));
        orderStats.setPreviousCompletedOrderCount(countCompletedOrders(shopId, previousIntervals));

        DashboardStatsSnapshot dashboardStats = new DashboardStatsSnapshot();
        dashboardStats.setMarketingStats(snapshot);
        dashboardStats.setOrderStats(orderStats);
        return dashboardStats;
    }

    public PowerStatsSnapshot buildPowerStats(Long shopId, MtPowerAccount powerAccount) {
        return buildPowerStats(shopId, powerAccount, DashboardStatsRangeTypeEnum.LAST_7_DAYS);
    }

    public PowerStatsSnapshot buildPowerStats(Long shopId, MtPowerAccount powerAccount, DashboardStatsRangeTypeEnum statsRange) {
        TimeRange range = buildCurrentAndPreviousSameProgressTimeRange(statsRange);

        PowerStatsSnapshot snapshot = new PowerStatsSnapshot();
        snapshot.setCurrentConsumedPower(defaultInteger(
                mtPowerConsumeMapper.sumPowerAmountByShopIdAndCreateTimeBetween(shopId, range.currentFrom(), range.currentToExclusive())
        ));
        snapshot.setPreviousConsumedPower(defaultInteger(
                mtPowerConsumeMapper.sumPowerAmountByShopIdAndCreateTimeBetween(shopId, range.previousFrom(), range.previousToExclusive())
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
        WindowRange range = buildCurrentAndPreviousDateRange(statsRange);

        ExposureBreakdownSnapshot snapshot = new ExposureBreakdownSnapshot();
        snapshot.setCurrentFreeExposure(sumExposure(shopId, MtPoiTrafficDailyService.FLOW_PROP_NATURAL, range.currentFrom(), range.currentToExclusive()));
        snapshot.setPreviousFreeExposure(sumExposure(shopId, MtPoiTrafficDailyService.FLOW_PROP_NATURAL, range.previousFrom(), range.previousToExclusive()));
        snapshot.setCurrentPaidExposure(sumExposure(shopId, MtPoiTrafficDailyService.FLOW_PROP_PAID, range.currentFrom(), range.currentToExclusive()));
        snapshot.setPreviousPaidExposure(sumExposure(shopId, MtPoiTrafficDailyService.FLOW_PROP_PAID, range.previousFrom(), range.previousToExclusive()));
        return snapshot;
    }

    public BigDecimal findLatestPromotionBalanceSnapshot(Long shopId) {
        MtPromotionBidRecord record = mtPromotionBidRecordMapper.selectLatestSuccessByShopId(shopId);
        if (record == null || record.getMetricsPayloadJson() == null || record.getMetricsPayloadJson().isBlank()) {
            return null;
        }

        Map<String, Object> payload = JsonUtil.toMap(record.getMetricsPayloadJson());
        if (payload == null || payload.isEmpty()) {
            return null;
        }

        Object value = payload.get("promotionBalance");
        if (value == null) {
            return null;
        }

        try {
            return new BigDecimal(String.valueOf(value)).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public BigDecimal findLatestPromotionBidSnapshot(Long shopId) {
        MtPromotionBidRecord record = mtPromotionBidRecordMapper.selectLatestSuccessByShopId(shopId);
        if (record == null || record.getNextBidAmount() == null) {
            return null;
        }
        return record.getNextBidAmount().setScale(2, RoundingMode.HALF_UP);
    }

    private WindowRange buildCurrentAndPreviousDateRange(DashboardStatsRangeTypeEnum statsRange) {
        int days = normalizeStatsRange(statsRange).getDays();
        LocalDate today = LocalDate.now(DEFAULT_ZONE_ID);
        LocalDate currentFrom = today.minusDays(days - 1L);
        LocalDate currentToExclusive = today.plusDays(1L);
        LocalDate previousFrom = currentFrom.minusDays(days);
        LocalDate previousToExclusive = currentFrom;
        return new WindowRange(currentFrom, currentToExclusive, previousFrom, previousToExclusive);
    }

    private TimeRange buildCurrentAndPreviousSameProgressTimeRange(DashboardStatsRangeTypeEnum statsRange) {
        int days = normalizeStatsRange(statsRange).getDays();
        LocalDateTime alignedNow = alignToDashboardStatsSlot(currentNow());
        LocalDate today = alignedNow.toLocalDate();
        LocalDateTime currentFrom = today.minusDays(days - 1L).atStartOfDay();
        LocalDateTime currentToExclusive = alignedNow;
        LocalDateTime previousFrom = currentFrom.minusDays(days);
        LocalDateTime previousToExclusive = currentToExclusive.minusDays(days);
        return new TimeRange(currentFrom, currentToExclusive, previousFrom, previousToExclusive);
    }

    static LocalDateTime alignToDashboardStatsSlot(LocalDateTime value) {
        LocalDateTime truncated = value.truncatedTo(ChronoUnit.MINUTES);
        int alignedMinute = truncated.getMinute() / DASHBOARD_STATS_ALIGNMENT_MINUTES * DASHBOARD_STATS_ALIGNMENT_MINUTES;
        return truncated.withMinute(alignedMinute).withSecond(0).withNano(0);
    }

    private BigDecimal sumIncome(Long shopId, List<PromotionActiveInterval> intervals) {
        BigDecimal total = ZERO_MONEY;
        for (PromotionActiveInterval interval : intervals) {
            total = total.add(defaultMoney(
                    mtOrderMapper.sumIncomeByShopIdAndOrderSendTimeBetweenExcludingStatus(
                            shopId,
                            MtOrderStatusEnum.CANCELED.getCode(),
                            toEpochSecond(interval.startAt()),
                            toEpochSecond(interval.endAt())
                    )
            ));
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    private Integer countCompletedOrders(Long shopId, List<PromotionActiveInterval> intervals) {
        int total = 0;
        for (PromotionActiveInterval interval : intervals) {
            total += defaultInteger(
                    mtOrderMapper.countOrdersByShopIdAndOrderSendTimeBetweenExcludingStatus(
                            shopId,
                            MtOrderStatusEnum.CANCELED.getCode(),
                            toEpochSecond(interval.startAt()),
                            toEpochSecond(interval.endAt())
                    )
            );
        }
        return total;
    }

    private Integer sumExposure(Long shopId, Integer flowProp, LocalDate from, LocalDate toExclusive) {
        return defaultInteger(
                mtPoiTrafficDailyMapper.sumExposureCountByShopIdAndBizDateBetween(shopId, flowProp, from, toExclusive)
        );
    }

    private PromotionAdMetrics loadPromotionAdMetrics(Long shopId, List<PromotionActiveInterval> intervals) {
        if (shopId == null || intervals == null || intervals.isEmpty()) {
            return PromotionAdMetrics.empty();
        }
        LocalDate today = LocalDate.now(DEFAULT_ZONE_ID);
        BigDecimal showCount = BigDecimal.ZERO;
        BigDecimal debitCents = BigDecimal.ZERO;
        for (PromotionActiveInterval interval : intervals) {
            LocalDate cursor = interval.startAt().toLocalDate();
            LocalDate endDate = interval.endAt().minusNanos(1).toLocalDate();
            while (!cursor.isAfter(endDate)) {
                LocalDateTime dayStart = cursor.atStartOfDay();
                LocalDateTime dayEnd = cursor.plusDays(1).atStartOfDay();
                LocalDateTime startAt = max(interval.startAt(), dayStart);
                LocalDateTime endAt = min(interval.endAt(), dayEnd);
                if (endAt.isAfter(startAt)) {
                    PromotionAdCounters counters = cursor.equals(today)
                            ? loadTodayRealtimePromotionAdCounters(shopId, startAt, endAt)
                            : loadHistoricalPromotionPeriodDailyCounters(shopId, cursor, startAt, endAt);
                    showCount = showCount.add(counters.showCount());
                    debitCents = debitCents.add(counters.debitCents());
                }
                cursor = cursor.plusDays(1);
            }
        }
        return new PromotionAdMetrics(
                showCount.setScale(0, RoundingMode.HALF_UP).intValue(),
                centsToYuan(debitCents)
        );
    }

    private PromotionAdCounters loadHistoricalPromotionPeriodDailyCounters(Long shopId,
                                                                           LocalDate bizDate,
                                                                           LocalDateTime intervalStartAt,
                                                                           LocalDateTime intervalEndAt) {
        boolean fullDay = intervalStartAt.toLocalTime().equals(LocalTime.MIDNIGHT)
                && intervalEndAt.equals(bizDate.plusDays(1).atStartOfDay());
        if (fullDay) {
            MtPromotionPeriodMetricDaily cached = mtPromotionPeriodMetricDailyMapper.selectByShopIdAndBizDate(shopId, bizDate);
            if (cached != null) {
                return new PromotionAdCounters(
                        BigDecimal.valueOf(defaultInteger(cached.getShowCount())),
                        BigDecimal.valueOf(cached.getDebitCents() == null ? 0L : cached.getDebitCents())
                );
            }
        }

        PromotionAdCounters counters = loadHistoricalHourlyPromotionAdCounters(shopId, bizDate, intervalStartAt, intervalEndAt);
        if (fullDay && counters.hasPositiveValue()) {
            upsertDailyPreAggregate(shopId, bizDate, counters, intervalStartAt, intervalEndAt);
        }
        return counters;
    }

    private void upsertDailyPreAggregate(Long shopId,
                                         LocalDate bizDate,
                                         PromotionAdCounters counters,
                                         LocalDateTime startAt,
                                         LocalDateTime endAt) {
        LocalDateTime now = LocalDateTime.now();
        MtPromotionPeriodMetricDaily metric = new MtPromotionPeriodMetricDaily();
        metric.setShopId(shopId);
        metric.setBizDate(bizDate);
        metric.setShowCount(counters.showCount().setScale(0, RoundingMode.HALF_UP).intValue());
        metric.setDebitCents(counters.debitCents().setScale(0, RoundingMode.HALF_UP).longValue());
        metric.setIncome(ZERO_MONEY);
        metric.setOrderCount(0);
        metric.setSourceHash(buildDailyPreAggregateSourceHash(shopId, bizDate));
        metric.setCalculatedAt(now);
        metric.setCreateTime(now);
        metric.setUpdateTime(now);
        mtPromotionPeriodMetricDailyMapper.insertOrUpdate(metric);
    }

    private PromotionAdCounters loadHistoricalHourlyPromotionAdCounters(Long shopId,
                                                                        LocalDate bizDate,
                                                                        LocalDateTime intervalStartAt,
                                                                        LocalDateTime intervalEndAt) {
        List<MtPromotionAdHourlyDetail> rows = mtPromotionAdHourlyDetailMapper.selectByShopIdAndBizDateAndChannel(
                shopId,
                bizDate,
                MtPromotionAdDataService.CHANNEL_DIANJIN
        );
        if (rows == null || rows.isEmpty()) {
            return PromotionAdCounters.empty();
        }
        BigDecimal showCount = BigDecimal.ZERO;
        BigDecimal debitCents = BigDecimal.ZERO;
        for (MtPromotionAdHourlyDetail row : rows) {
            HourRange hourRange = parseHourRange(bizDate, row.getTimeRange());
            if (hourRange == null) {
                continue;
            }
            long overlapSeconds = overlapSeconds(intervalStartAt, intervalEndAt, hourRange.startAt(), hourRange.endAt());
            if (overlapSeconds <= 0L) {
                continue;
            }
            BigDecimal ratio = BigDecimal.valueOf(overlapSeconds)
                    .divide(BigDecimal.valueOf(Duration.between(hourRange.startAt(), hourRange.endAt()).getSeconds()), 10, RoundingMode.HALF_UP);
            showCount = showCount.add(BigDecimal.valueOf(defaultInteger(row.getShowCount())).multiply(ratio));
            debitCents = debitCents.add(BigDecimal.valueOf(row.getDebitCents() == null ? 0L : row.getDebitCents()).multiply(ratio));
        }
        return new PromotionAdCounters(showCount, debitCents);
    }

    private PromotionAdCounters loadTodayRealtimePromotionAdCounters(Long shopId,
                                                                     LocalDateTime intervalStartAt,
                                                                     LocalDateTime intervalEndAt) {
        LocalDateTime dayStartAt = intervalStartAt.toLocalDate().atStartOfDay();
        MtPromotionAdRealtimeSnapshot endSnapshot =
                mtPromotionAdRealtimeSnapshotService.selectLatestByShopIdAndSlotTimeRangeBeforeOrEqual(
                        shopId,
                        dayStartAt,
                        intervalEndAt
                );
        if (endSnapshot == null) {
            return PromotionAdCounters.empty();
        }
        MtPromotionAdRealtimeSnapshot baselineSnapshot = intervalStartAt.toLocalTime().equals(LocalTime.MIDNIGHT)
                ? null
                : mtPromotionAdRealtimeSnapshotService.selectLatestByShopIdAndSlotTimeRangeBeforeOrEqual(
                        shopId,
                        dayStartAt,
                        intervalStartAt
                );
        if (baselineSnapshot == null && !intervalStartAt.toLocalTime().equals(LocalTime.MIDNIGHT)) {
            baselineSnapshot = mtPromotionAdRealtimeSnapshotService.selectEarliestByShopIdAndSlotTimeRange(
                    shopId,
                    intervalStartAt,
                    intervalEndAt
            );
        }
        long showDelta = nonNegativeDelta(
                endSnapshot.getDayShowCount() == null ? 0L : endSnapshot.getDayShowCount().longValue(),
                baselineSnapshot == null || baselineSnapshot.getDayShowCount() == null ? 0L : baselineSnapshot.getDayShowCount().longValue()
        );
        long debitDelta = nonNegativeDelta(
                endSnapshot.getDayDebitCents() == null ? 0L : endSnapshot.getDayDebitCents(),
                baselineSnapshot == null || baselineSnapshot.getDayDebitCents() == null ? 0L : baselineSnapshot.getDayDebitCents()
        );
        return new PromotionAdCounters(BigDecimal.valueOf(showDelta), BigDecimal.valueOf(debitDelta));
    }

    private List<PromotionActiveInterval> buildPromotionActiveIntervals(Long shopId,
                                                                        LocalDateTime rangeStartAt,
                                                                        LocalDateTime rangeEndAt) {
        if (shopId == null || rangeStartAt == null || rangeEndAt == null || !rangeEndAt.isAfter(rangeStartAt)) {
            return Collections.emptyList();
        }
        MtPromotionSettingHistory latestBeforeRange =
                mtPromotionSettingHistoryMapper.selectLatestByShopIdBeforeCreateTime(shopId, rangeStartAt);
        List<MtPromotionSettingHistory> changes = mtPromotionSettingHistoryMapper.selectByShopIdAndCreateTimeRange(
                shopId,
                rangeStartAt,
                rangeEndAt
        );
        if (latestBeforeRange == null && (changes == null || changes.isEmpty())) {
            Integer maxVersionNo = mtPromotionSettingHistoryMapper.selectMaxVersionNoByShopId(shopId);
            if (maxVersionNo != null && maxVersionNo > 0) {
                return Collections.emptyList();
            }
            MtPromotionSetting setting = mtPromotionSettingMapper.selectByShopId(shopId);
            return setting != null && Boolean.TRUE.equals(setting.getPromotionEnabled())
                    ? List.of(new PromotionActiveInterval(rangeStartAt, rangeEndAt))
                    : Collections.emptyList();
        }

        List<PromotionActiveInterval> intervals = new ArrayList<>();
        boolean active = latestBeforeRange != null && Boolean.TRUE.equals(latestBeforeRange.getPromotionEnabled());
        LocalDateTime activeStartAt = active ? rangeStartAt : null;
        for (MtPromotionSettingHistory change : changes == null ? Collections.<MtPromotionSettingHistory>emptyList() : changes) {
            LocalDateTime changeTime = change.getCreateTime();
            if (changeTime == null) {
                continue;
            }
            boolean nextActive = Boolean.TRUE.equals(change.getPromotionEnabled());
            if (active && !nextActive && changeTime.isAfter(activeStartAt)) {
                intervals.add(new PromotionActiveInterval(activeStartAt, min(changeTime, rangeEndAt)));
                activeStartAt = null;
            } else if (!active && nextActive) {
                activeStartAt = max(changeTime, rangeStartAt);
            }
            active = nextActive;
        }
        if (active && activeStartAt != null && rangeEndAt.isAfter(activeStartAt)) {
            intervals.add(new PromotionActiveInterval(activeStartAt, rangeEndAt));
        }
        return intervals;
    }

    private BigDecimal calculateCostRatio(BigDecimal totalCost, BigDecimal totalIncome) {
        if (totalIncome == null || totalIncome.compareTo(BigDecimal.ZERO) <= 0) {
            if (totalCost == null || totalCost.compareTo(BigDecimal.ZERO) <= 0) {
                return ZERO_RATIO;
            }
            return BigDecimal.ONE.setScale(6, RoundingMode.HALF_UP);
        }
        return totalCost.divide(totalIncome, 6, RoundingMode.HALF_UP);
    }

    private String buildDashboardStatsCacheKey(Long shopId, DashboardStatsRangeTypeEnum statsRange, TimeRange range) {
        return shopId + ":" + normalizeStatsRange(statsRange).name()
                + ":" + range.currentFrom()
                + ":" + range.currentToExclusive()
                + ":" + range.previousFrom()
                + ":" + range.previousToExclusive();
    }

    private String buildDailyPreAggregateSourceHash(Long shopId, LocalDate bizDate) {
        return shopId + ":" + bizDate + ":promotion-period-v1";
    }

    private long toEpochSecond(LocalDateTime value) {
        return value.atZone(DEFAULT_ZONE_ID).toEpochSecond();
    }

    private HourRange parseHourRange(LocalDate bizDate, String timeRange) {
        if (bizDate == null || timeRange == null || !timeRange.matches("\\d{2}:\\d{2}-\\d{2}:\\d{2}")) {
            return null;
        }
        try {
            int startHour = Integer.parseInt(timeRange.substring(0, 2));
            int endHour = Integer.parseInt(timeRange.substring(6, 8));
            LocalDateTime startAt = bizDate.atTime(startHour, 0);
            LocalDateTime endAt = endHour == 0 ? bizDate.plusDays(1).atStartOfDay() : bizDate.atTime(endHour, 0);
            if (!endAt.isAfter(startAt)) {
                return null;
            }
            return new HourRange(startAt, endAt);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private long overlapSeconds(LocalDateTime leftStartAt,
                                LocalDateTime leftEndAt,
                                LocalDateTime rightStartAt,
                                LocalDateTime rightEndAt) {
        LocalDateTime startAt = max(leftStartAt, rightStartAt);
        LocalDateTime endAt = min(leftEndAt, rightEndAt);
        return endAt.isAfter(startAt) ? Duration.between(startAt, endAt).getSeconds() : 0L;
    }

    private LocalDateTime max(LocalDateTime left, LocalDateTime right) {
        return left.isAfter(right) ? left : right;
    }

    private LocalDateTime min(LocalDateTime left, LocalDateTime right) {
        return left.isBefore(right) ? left : right;
    }

    private long nonNegativeDelta(long endValue, long startValue) {
        return Math.max(0L, endValue - startValue);
    }

    private BigDecimal defaultMoney(BigDecimal value) {
        return value == null ? ZERO_MONEY : value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal centsToYuan(BigDecimal cents) {
        if (cents == null || cents.compareTo(BigDecimal.ZERO) <= 0) {
            return ZERO_MONEY;
        }
        return cents.divide(BigDecimal.valueOf(100L), 2, RoundingMode.HALF_UP);
    }

    private Integer defaultInteger(Integer value) {
        return value == null ? 0 : value;
    }

    private DashboardStatsRangeTypeEnum normalizeStatsRange(DashboardStatsRangeTypeEnum statsRange) {
        return statsRange == null ? DashboardStatsRangeTypeEnum.LAST_7_DAYS : statsRange;
    }

    void setNowSupplier(Supplier<LocalDateTime> nowSupplier) {
        this.nowSupplier = nowSupplier == null ? () -> LocalDateTime.now(DEFAULT_ZONE_ID) : nowSupplier;
        dashboardStatsCache.clear();
    }

    private LocalDateTime currentNow() {
        return nowSupplier.get();
    }

    private record WindowRange(LocalDate currentFrom,
                               LocalDate currentToExclusive,
                               LocalDate previousFrom,
                               LocalDate previousToExclusive) {
    }

    private record TimeRange(LocalDateTime currentFrom,
                             LocalDateTime currentToExclusive,
                             LocalDateTime previousFrom,
                             LocalDateTime previousToExclusive) {
    }

    private record PromotionActiveInterval(LocalDateTime startAt, LocalDateTime endAt) {
    }

    private record PromotionAdMetrics(Integer showCount, BigDecimal cost) {

        private static PromotionAdMetrics empty() {
            return new PromotionAdMetrics(0, ZERO_MONEY);
        }
    }

    private record PromotionAdCounters(BigDecimal showCount, BigDecimal debitCents) {

        private static PromotionAdCounters empty() {
            return new PromotionAdCounters(BigDecimal.ZERO, BigDecimal.ZERO);
        }

        private boolean hasPositiveValue() {
            return (showCount != null && showCount.compareTo(BigDecimal.ZERO) > 0)
                    || (debitCents != null && debitCents.compareTo(BigDecimal.ZERO) > 0);
        }
    }

    private record HourRange(LocalDateTime startAt, LocalDateTime endAt) {
    }

    private record CacheEntry<T>(T value, LocalDateTime expiresAt) {
    }

    @Data
    public static class DashboardStatsSnapshot {
        private MarketingStatsSnapshot marketingStats;
        private OrderStatsSnapshot orderStats;
    }

    @Data
    public static class MarketingStatsSnapshot {
        private Integer currentExposure;
        private Integer previousExposure;
        private BigDecimal currentCost;
        private BigDecimal previousCost;
        private BigDecimal currentCostRatio;
        private BigDecimal previousCostRatio;
        private BigDecimal currentIncome;
        private BigDecimal previousIncome;
    }

    @Data
    public static class PowerStatsSnapshot {
        private Integer currentConsumedPower;
        private Integer previousConsumedPower;
        private Integer currentEstimatedPower;
        private Integer previousEstimatedPower;
        private Integer balance;
        private Integer totalRecharge;
        private Integer totalConsume;
    }

    @Data
    public static class ExposureBreakdownSnapshot {
        private Integer currentFreeExposure;
        private Integer previousFreeExposure;
        private Integer currentPaidExposure;
        private Integer previousPaidExposure;
    }

    @Data
    public static class OrderStatsSnapshot {
        private Integer currentCompletedOrderCount;
        private Integer previousCompletedOrderCount;
        private BigDecimal currentCompletedIncome;
        private BigDecimal previousCompletedIncome;
    }
}
