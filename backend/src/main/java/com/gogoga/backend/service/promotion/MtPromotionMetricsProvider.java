package com.gogoga.backend.service.promotion;

import com.gogoga.backend.entity.mt.MtPromotionAutoBidCandidate;
import com.gogoga.backend.entity.mt.MtPromotionAdRealtimeSnapshot;
import com.gogoga.backend.entity.mt.MtPromotionMetricSnapshot;
import com.gogoga.backend.enums.MtOrderStatusEnum;
import com.gogoga.backend.mapper.MtOrderMapper;
import com.gogoga.backend.mapper.MtPromotionMetricSnapshotMapper;
import com.gogoga.backend.service.MtPromotionAdRealtimeSnapshotService;
import com.gogoga.backend.service.MtWaimaiAdDecryptService;
import com.gogoga.backend.util.JsonUtil;
import com.meituan.sdk.MeituanClient;
import com.meituan.sdk.MeituanResponse;
import com.meituan.sdk.internal.exceptions.MtSdkException;
import com.meituan.sdk.model.wmoperNg.waimaiad.adBalanceInfo.AdBalanceInfoRequest;
import com.meituan.sdk.model.wmoperNg.waimaiad.adBalanceInfo.AdBalanceInfoResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 美团推广指标提供器
 *
 * @author system
 * @since 2026-03-26
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MtPromotionMetricsProvider implements PromotionMetricsProvider {

    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final ZoneId DEFAULT_ZONE_ID = ZoneId.of("Asia/Shanghai");
    private static final String BASELINE_TYPE_REALTIME = "REALTIME";
    private static final String BASELINE_TYPE_FALLBACK = "FALLBACK_COUNTER";
    private static final String BASELINE_TYPE_TEMPORARY_RECOVERY_REALTIME = "TEMPORARY_RECOVERY_REALTIME";
    private static final String BASELINE_TYPE_TEMPORARY_RECOVERY_FALLBACK = "TEMPORARY_RECOVERY_FALLBACK_COUNTER";
    private static final String TEMPORARY_RECOVERY_WINDOW_START_AT_KEY = "temporaryRecoveryWindowStartAt";
    private static final String TEMPORARY_RECOVERY_WINDOW_SOURCE_KEY = "temporaryRecoveryWindowSource";

    private final MeituanClient meituanClient;
    private final MtOrderMapper mtOrderMapper;
    private final MtPromotionMetricSnapshotMapper mtPromotionMetricSnapshotMapper;
    private final MtPromotionAdRealtimeSnapshotService mtPromotionAdRealtimeSnapshotService;
    private final MtWaimaiAdDecryptService mtWaimaiAdDecryptService;

    @Override
    public PromotionMetricsSnapshot queryMetrics(
            MtPromotionAutoBidCandidate candidate,
            PromotionExecutionWindow executionWindow,
            LocalDateTime slotTime
    ) {
        validateCandidate(candidate);
        if (executionWindow == null || executionWindow.getWindowStartAt() == null) {
            throw new IllegalArgumentException("自动调价缺少执行窗口信息");
        }

        LocalDate bizDate = slotTime.toLocalDate();
        LocalDateTime windowStartAt = executionWindow.getWindowStartAt();
        AdBalanceInfoResponse adBalanceInfo = fetchAdBalanceInfo(candidate.getShopId(), candidate.getAccessToken());
        Map<String, String> decryptCache = new HashMap<>();
        BigDecimal primaryBalance = decryptMoney(
                candidate.getShopId(),
                candidate.getAccessToken(),
                adBalanceInfo.getPrimaryBalance(),
                decryptCache
        );
        BigDecimal commonBalance = decryptMoney(
                candidate.getShopId(),
                candidate.getAccessToken(),
                adBalanceInfo.getCommonBalance(),
                decryptCache
        );
        BigDecimal costCounterValue = decryptMoneyLenient(
                candidate.getShopId(),
                candidate.getAccessToken(),
                adBalanceInfo.getDebit(),
                decryptCache
        );
        BigDecimal promotionBalance = primaryBalance.add(commonBalance).setScale(2, RoundingMode.HALF_UP);
        MtPromotionMetricSnapshot currentSnapshot = buildSnapshot(
                candidate.getShopId(),
                bizDate,
                slotTime,
                primaryBalance,
                commonBalance,
                promotionBalance,
                costCounterValue
        );
        mtPromotionMetricSnapshotMapper.insertOrUpdate(currentSnapshot);
        RealtimeMetricResult realtimeMetricResult = resolveRealtimeMetrics(candidate, executionWindow, slotTime, costCounterValue);
        BaselineResult baselineResult = realtimeMetricResult.baselineResult;
        LocalDateTime effectiveWindowStartAt = resolveEffectiveWindowStartAt(windowStartAt, baselineResult);
        BigDecimal windowIncome = loadWindowIncome(candidate.getShopId(), effectiveWindowStartAt, slotTime);

        PromotionMetricsSnapshot snapshot = new PromotionMetricsSnapshot();
        snapshot.setPromotionBalance(promotionBalance);
        snapshot.setCostCounterValue(realtimeMetricResult.currentRealtimeSnapshot == null
                ? costCounterValue : centsToYuan(BigDecimal.valueOf(realtimeMetricResult.currentRealtimeSnapshot.getDayDebitCents())));
        snapshot.setWindowCost(realtimeMetricResult.cumulativeCostTrustResult.windowCost);
        snapshot.setWindowIncome(windowIncome);
        snapshot.setWindowCostRatio(calculateRatio(realtimeMetricResult.cumulativeCostTrustResult.windowCost, windowIncome));
        snapshot.setSlotCostDelta(realtimeMetricResult.slotCostDeltaTrustResult.windowCost);
        snapshot.setMetricsTrusted(realtimeMetricResult.cumulativeCostTrustResult.trusted
                && realtimeMetricResult.slotCostDeltaTrustResult.trusted);
        snapshot.setUntrustedReason(resolveUntrustedReason(
                realtimeMetricResult.cumulativeCostTrustResult,
                realtimeMetricResult.slotCostDeltaTrustResult
        ));
        snapshot.setBaselineType(resolveBaselineType(realtimeMetricResult.payloadSource, baselineResult));
        snapshot.setEffectiveWindowStartAt(effectiveWindowStartAt);
        snapshot.setBusinessWindowStartAt(windowStartAt);
        snapshot.setTemporaryRecoveryWindow(baselineResult.temporaryRecoveryWindow);
        snapshot.setRecoveryWindowInitializationRequired(baselineResult.initializationRequired);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("source", realtimeMetricResult.payloadSource);
        payload.put("shopId", candidate.getShopId());
        payload.put("slotTime", slotTime.toString());
        payload.put("executionWindowStartAt", windowStartAt.toString());
        payload.put("effectiveWindowStartAt", effectiveWindowStartAt == null ? null : effectiveWindowStartAt.toString());
        payload.put("executionWindowEndAt", executionWindow.getWindowEndAt() == null ? null : executionWindow.getWindowEndAt().toString());
        payload.put("primaryBalance", adBalanceInfo.getPrimaryBalance());
        payload.put("commonBalance", adBalanceInfo.getCommonBalance());
        payload.put("primaryBalanceDecrypted", primaryBalance);
        payload.put("commonBalanceDecrypted", commonBalance);
        payload.put("promotionBalance", promotionBalance);
        payload.put("debit", adBalanceInfo.getDebit());
        payload.put("costCounterValue", costCounterValue);
        payload.put("realtimeSnapshotSlotTime", realtimeMetricResult.currentRealtimeSnapshot == null
                ? null : realtimeMetricResult.currentRealtimeSnapshot.getSlotTime().toString());
        payload.put("realtimeCurrentHourRange", realtimeMetricResult.currentRealtimeSnapshot == null
                ? null : realtimeMetricResult.currentRealtimeSnapshot.getCurrentHourRange());
        payload.put("realtimeDayDebitCents", realtimeMetricResult.currentRealtimeSnapshot == null
                ? null : realtimeMetricResult.currentRealtimeSnapshot.getDayDebitCents());
        payload.put("realtimeHourDebitCents", realtimeMetricResult.currentRealtimeSnapshot == null
                ? null : realtimeMetricResult.currentRealtimeSnapshot.getHourDebitCents());
        payload.put("executionWindowBaselineSlotTime", baselineResult.snapshot != null && baselineResult.snapshot.getSlotTime() != null
                ? baselineResult.snapshot.getSlotTime().toString()
                : baselineResult.realtimeSnapshot != null && baselineResult.realtimeSnapshot.getSlotTime() != null
                ? baselineResult.realtimeSnapshot.getSlotTime().toString()
                : null);
        payload.put("executionWindowBaselineSource", baselineResult.source);
        payload.put("executionWindowBaselineCostCounterValue", baselineResult.snapshot == null ? null : baselineResult.snapshot.getCostCounterValue());
        payload.put("executionWindowBaselineDayDebitCents", baselineResult.realtimeSnapshot == null ? null : baselineResult.realtimeSnapshot.getDayDebitCents());
        payload.put("executionWindowBaselineBridgeDayDebitCents", baselineResult.bridgeRealtimeSnapshot == null
                ? null : baselineResult.bridgeRealtimeSnapshot.getDayDebitCents());
        payload.put(TEMPORARY_RECOVERY_WINDOW_START_AT_KEY,
                baselineResult.recoveryWindowStartAt == null ? null : baselineResult.recoveryWindowStartAt.toString());
        payload.put(TEMPORARY_RECOVERY_WINDOW_SOURCE_KEY, baselineResult.recoveryWindowSource);
        payload.put("baselineType", snapshot.getBaselineType());
        payload.put("temporaryRecoveryWindow", baselineResult.temporaryRecoveryWindow);
        payload.put("recoveryWindowInitializationRequired", baselineResult.initializationRequired);
        payload.put("previousSlotTime", realtimeMetricResult.previousRealtimeSnapshot == null || realtimeMetricResult.previousRealtimeSnapshot.getSlotTime() == null
                ? null : realtimeMetricResult.previousRealtimeSnapshot.getSlotTime().toString());
        payload.put("previousCostCounterValue", realtimeMetricResult.previousMetricSnapshot == null
                ? null : realtimeMetricResult.previousMetricSnapshot.getCostCounterValue());
        payload.put("previousRealtimeDayDebitCents", realtimeMetricResult.previousRealtimeSnapshot == null
                ? null : realtimeMetricResult.previousRealtimeSnapshot.getDayDebitCents());
        payload.put("windowCost", realtimeMetricResult.cumulativeCostTrustResult.windowCost);
        payload.put("windowIncome", windowIncome);
        payload.put("windowCostRatio", snapshot.getWindowCostRatio());
        payload.put("slotCostDelta", realtimeMetricResult.slotCostDeltaTrustResult.windowCost);
        payload.put("executionWindowMetricsTrusted", realtimeMetricResult.cumulativeCostTrustResult.trusted);
        payload.put("executionWindowUntrustedReason", realtimeMetricResult.cumulativeCostTrustResult.untrustedReason);
        payload.put("slotCostDeltaTrusted", realtimeMetricResult.slotCostDeltaTrustResult.trusted);
        payload.put("slotCostDeltaUntrustedReason", realtimeMetricResult.slotCostDeltaTrustResult.untrustedReason);
        payload.put("metricsTrusted", snapshot.isMetricsTrusted());
        payload.put("untrustedReason", snapshot.getUntrustedReason());
        snapshot.setPayloadJson(JsonUtil.toJson(payload));
        currentSnapshot.setMetricsTrusted(snapshot.isMetricsTrusted());
        currentSnapshot.setUntrustedReason(snapshot.getUntrustedReason());
        currentSnapshot.setPayloadJson(snapshot.getPayloadJson());
        mtPromotionMetricSnapshotMapper.insertOrUpdate(currentSnapshot);
        return snapshot;
    }

    private RealtimeMetricResult resolveRealtimeMetrics(
            MtPromotionAutoBidCandidate candidate,
            PromotionExecutionWindow executionWindow,
            LocalDateTime slotTime,
            BigDecimal currentCounterValue
    ) {
        MtPromotionMetricSnapshot previousMetricSnapshot = mtPromotionMetricSnapshotMapper.selectByShopIdAndBizDateAndSlotTime(
                candidate.getShopId(),
                slotTime.minusMinutes(10).toLocalDate(),
                slotTime.minusMinutes(10)
        );
        try {
            MtPromotionAdRealtimeSnapshot currentRealtimeSnapshot =
                    mtPromotionAdRealtimeSnapshotService.selectByShopIdAndBizDateAndSlotTime(
                            candidate.getShopId(),
                            slotTime.toLocalDate(),
                            slotTime
                    );
            if (currentRealtimeSnapshot == null) {
                currentRealtimeSnapshot = mtPromotionAdRealtimeSnapshotService.captureCurrentSlotSnapshot(
                        candidate.getShopId(),
                        candidate.getAccessToken(),
                        slotTime
                );
            }
            MtPromotionAdRealtimeSnapshot previousRealtimeSnapshot =
                    mtPromotionAdRealtimeSnapshotService.selectByShopIdAndBizDateAndSlotTime(
                            candidate.getShopId(),
                            slotTime.minusMinutes(10).toLocalDate(),
                            slotTime.minusMinutes(10)
                    );
            BaselineResult baselineResult = resolveExecutionWindowRealtimeBaseline(candidate.getShopId(), executionWindow, slotTime);
            TrustResult cumulativeCostTrustResult = resolveRealtimeCostFromBaseline(
                    currentRealtimeSnapshot,
                    baselineResult,
                    "EXECUTION_WINDOW_REALTIME_BASELINE_MISSING",
                    "EXECUTION_WINDOW_REALTIME_COUNTER_MISSING",
                    "EXECUTION_WINDOW_REALTIME_COUNTER_ROLLED_BACK"
            );
            TrustResult slotCostDeltaTrustResult = resolveRealtimeSlotCostDelta(
                    currentRealtimeSnapshot,
                    previousRealtimeSnapshot,
                    baselineResult,
                    slotTime
            );
            return new RealtimeMetricResult(
                    currentRealtimeSnapshot,
                    previousRealtimeSnapshot,
                    previousMetricSnapshot,
                    baselineResult,
                    cumulativeCostTrustResult,
                    slotCostDeltaTrustResult,
                    "mt.waimaiad.adDataDetail.realtime"
            );
        } catch (RuntimeException e) {
            log.warn("实时推广广告明细不可用，回退广告余额累计消耗口径，shopId={}, slotTime={}, reason={}",
                    candidate.getShopId(), slotTime, e.getMessage());
            BaselineResult baselineResult = resolveExecutionWindowBaseline(candidate.getShopId(), executionWindow, slotTime);
            return buildFallbackMetricResult(currentCounterValue, baselineResult, previousMetricSnapshot, slotTime);
        }
    }

    private BigDecimal loadWindowIncome(Long shopId, LocalDateTime windowStartAt, LocalDateTime windowEndAt) {
        long startEpochSeconds = windowStartAt.atZone(DEFAULT_ZONE_ID).toEpochSecond();
        long endEpochSeconds = windowEndAt.atZone(DEFAULT_ZONE_ID).toEpochSecond();
        BigDecimal income = mtOrderMapper.sumIncomeByShopIdAndOrderSendTimeBetweenExcludingStatus(
                shopId,
                MtOrderStatusEnum.CANCELED.getCode(),
                startEpochSeconds,
                endEpochSeconds
        );
        return income == null ? ZERO : income.setScale(2, RoundingMode.HALF_UP);
    }

    private BaselineResult resolveExecutionWindowBaseline(Long shopId, PromotionExecutionWindow executionWindow, LocalDateTime slotTime) {
        MtPromotionMetricSnapshot exactBaselineSnapshot = mtPromotionMetricSnapshotMapper.selectByShopIdAndBizDateAndSlotTime(
                shopId,
                executionWindow.getWindowStartAt().toLocalDate(),
                executionWindow.getWindowStartAt()
        );
        if (exactBaselineSnapshot != null) {
            return new BaselineResult(exactBaselineSnapshot, null, null, "WINDOW_START_SNAPSHOT");
        }
        if (!executionWindow.getWindowStartAt().toLocalDate().isBefore(slotTime.toLocalDate())) {
            return new BaselineResult(exactBaselineSnapshot, null, null, "WINDOW_START_SNAPSHOT");
        }
        LocalDateTime currentDayStartAt = slotTime.toLocalDate().atStartOfDay();
        MtPromotionMetricSnapshot crossDayFallbackSnapshot = mtPromotionMetricSnapshotMapper.selectLatestByShopIdAndSlotTimeRange(
                shopId,
                executionWindow.getWindowStartAt(),
                currentDayStartAt
        );
        if (crossDayFallbackSnapshot != null) {
            return new BaselineResult(crossDayFallbackSnapshot, null, null, "CROSS_DAY_PREVIOUS_DAY_LAST_SNAPSHOT");
        }
        RecoveryWindowState recoveryWindowState = resolveRecoveryWindowState(shopId, executionWindow.getWindowStartAt(), slotTime);
        if (recoveryWindowState != null) {
            MtPromotionMetricSnapshot recoveryBaselineSnapshot = mtPromotionMetricSnapshotMapper.selectByShopIdAndBizDateAndSlotTime(
                    shopId,
                    recoveryWindowState.startAt.toLocalDate(),
                    recoveryWindowState.startAt
            );
            if (recoveryBaselineSnapshot != null) {
                log.info("推广指标回退口径复用临时恢复窗口基线，shopId={}, slotTime={}, businessWindowStartAt={}, recoveryWindowStartAt={}, source={}",
                        shopId, slotTime, executionWindow.getWindowStartAt(), recoveryWindowState.startAt, recoveryWindowState.source);
                return new BaselineResult(
                        recoveryBaselineSnapshot,
                        null,
                        null,
                        "TEMPORARY_RECOVERY_WINDOW_START_SNAPSHOT",
                        true,
                        false,
                        recoveryWindowState.startAt,
                        recoveryWindowState.source
                );
            }
        }
        log.info("推广指标回退口径初始化临时恢复窗口基线，shopId={}, slotTime={}, businessWindowStartAt={}, recoveryWindowStartAt={}",
                shopId, slotTime, executionWindow.getWindowStartAt(), slotTime);
        return new BaselineResult(
                null,
                null,
                null,
                "CROSS_DAY_PREVIOUS_DAY_LAST_SNAPSHOT",
                true,
                true,
                slotTime,
                "INITIALIZED_FROM_FIRST_OBSERVED_SLOT"
        );
    }

    private BaselineResult resolveExecutionWindowRealtimeBaseline(Long shopId,
                                                                  PromotionExecutionWindow executionWindow,
                                                                  LocalDateTime slotTime) {
        LocalDateTime windowStartAt = executionWindow.getWindowStartAt();
        MtPromotionAdRealtimeSnapshot exactBaselineSnapshot =
                mtPromotionAdRealtimeSnapshotService.selectByShopIdAndBizDateAndSlotTime(
                        shopId,
                        windowStartAt.toLocalDate(),
                        windowStartAt
                );
        if (!windowStartAt.toLocalDate().isBefore(slotTime.toLocalDate())) {
            if (exactBaselineSnapshot != null) {
                return new BaselineResult(null, exactBaselineSnapshot, null, "WINDOW_START_REALTIME_SNAPSHOT");
            }
            RecoveryWindowState recoveryWindowState = resolveRecoveryWindowState(shopId, windowStartAt, slotTime);
            if (recoveryWindowState != null) {
                MtPromotionAdRealtimeSnapshot recoverySnapshot =
                        mtPromotionAdRealtimeSnapshotService.selectByShopIdAndBizDateAndSlotTime(
                                shopId,
                                recoveryWindowState.startAt.toLocalDate(),
                                recoveryWindowState.startAt
                        );
                if (recoverySnapshot != null) {
                    log.info("推广指标实时口径复用临时恢复窗口基线，shopId={}, slotTime={}, businessWindowStartAt={}, recoveryWindowStartAt={}, source={}",
                            shopId, slotTime, windowStartAt, recoveryWindowState.startAt, recoveryWindowState.source);
                    return new BaselineResult(
                            null,
                            recoverySnapshot,
                            null,
                            "TEMPORARY_RECOVERY_WINDOW_START_REALTIME_SNAPSHOT",
                            true,
                            false,
                            recoveryWindowState.startAt,
                            recoveryWindowState.source
                    );
                }
            }
            log.info("推广指标实时口径初始化临时恢复窗口基线，shopId={}, slotTime={}, businessWindowStartAt={}, recoveryWindowStartAt={}",
                    shopId, slotTime, windowStartAt, slotTime);
            return new BaselineResult(
                    null,
                    null,
                    null,
                    "WINDOW_START_REALTIME_SNAPSHOT",
                    true,
                    true,
                    slotTime,
                    "INITIALIZED_FROM_FIRST_OBSERVED_SLOT"
            );
        }
        LocalDateTime currentDayStartAt = slotTime.toLocalDate().atStartOfDay();
        MtPromotionAdRealtimeSnapshot previousDayLastSnapshot =
                mtPromotionAdRealtimeSnapshotService.selectLatestByShopIdAndSlotTimeRange(
                        shopId,
                        windowStartAt,
                        currentDayStartAt
                );
        if (exactBaselineSnapshot != null) {
            String source = previousDayLastSnapshot == null
                    ? "WINDOW_START_REALTIME_SNAPSHOT"
                    : "CROSS_DAY_WINDOW_START_AND_PREVIOUS_DAY_LAST_REALTIME_SNAPSHOT";
            return new BaselineResult(null, exactBaselineSnapshot, previousDayLastSnapshot, source);
        }
        if (previousDayLastSnapshot != null) {
            return new BaselineResult(null, previousDayLastSnapshot, null, "CROSS_DAY_PREVIOUS_DAY_LAST_REALTIME_SNAPSHOT");
        }
        RecoveryWindowState recoveryWindowState = resolveRecoveryWindowState(shopId, windowStartAt, slotTime);
        if (recoveryWindowState != null) {
            MtPromotionAdRealtimeSnapshot recoverySnapshot =
                    mtPromotionAdRealtimeSnapshotService.selectByShopIdAndBizDateAndSlotTime(
                            shopId,
                            recoveryWindowState.startAt.toLocalDate(),
                            recoveryWindowState.startAt
                    );
            if (recoverySnapshot != null) {
                MtPromotionAdRealtimeSnapshot recoveryBridgeSnapshot =
                        recoveryWindowState.startAt.toLocalDate().isBefore(slotTime.toLocalDate())
                                ? mtPromotionAdRealtimeSnapshotService.selectLatestByShopIdAndSlotTimeRange(
                                shopId,
                                recoveryWindowState.startAt,
                                currentDayStartAt
                        )
                                : null;
                return new BaselineResult(
                        null,
                        recoverySnapshot,
                        recoveryBridgeSnapshot,
                        recoveryBridgeSnapshot == null
                                ? "TEMPORARY_RECOVERY_WINDOW_START_REALTIME_SNAPSHOT"
                                : "TEMPORARY_RECOVERY_WINDOW_START_AND_PREVIOUS_DAY_LAST_REALTIME_SNAPSHOT",
                        true,
                        false,
                        recoveryWindowState.startAt,
                        recoveryWindowState.source
                );
            }
        }
        log.info("推广指标实时口径初始化临时恢复窗口基线，shopId={}, slotTime={}, businessWindowStartAt={}, recoveryWindowStartAt={}",
                shopId, slotTime, windowStartAt, slotTime);
        return new BaselineResult(
                null,
                null,
                null,
                "CROSS_DAY_PREVIOUS_DAY_LAST_REALTIME_SNAPSHOT",
                true,
                true,
                slotTime,
                "INITIALIZED_FROM_FIRST_OBSERVED_SLOT"
        );
    }

    private TrustResult resolveCostFromBaseline(
            BigDecimal currentCounterValue,
            MtPromotionMetricSnapshot baselineSnapshot,
            String baselineMissingReason,
            String baselineCounterMissingReason,
            String rollbackReason
    ) {
        if (currentCounterValue == null) {
            return new TrustResult(ZERO, false, "COST_COUNTER_UNREADABLE");
        }
        if (baselineSnapshot == null) {
            return new TrustResult(ZERO, false, baselineMissingReason);
        }
        if (baselineSnapshot.getCostCounterValue() == null) {
            return new TrustResult(ZERO, false, baselineCounterMissingReason);
        }
        BigDecimal previousCounterValue = baselineSnapshot.getCostCounterValue().setScale(2, RoundingMode.HALF_UP);
        BigDecimal current = currentCounterValue.setScale(2, RoundingMode.HALF_UP);
        if (current.compareTo(previousCounterValue) < 0) {
            return new TrustResult(ZERO, false, rollbackReason);
        }
        return new TrustResult(current.subtract(previousCounterValue).setScale(2, RoundingMode.HALF_UP), true, null);
    }

    private String resolveUntrustedReason(TrustResult cumulativeCostTrustResult, TrustResult slotCostDeltaTrustResult) {
        if (!cumulativeCostTrustResult.trusted) {
            return cumulativeCostTrustResult.untrustedReason;
        }
        if (!slotCostDeltaTrustResult.trusted) {
            return slotCostDeltaTrustResult.untrustedReason;
        }
        return null;
    }

    private TrustResult resolveRealtimeCostFromBaseline(
            MtPromotionAdRealtimeSnapshot currentSnapshot,
            BaselineResult baselineResult,
            String baselineMissingReason,
            String baselineCounterMissingReason,
            String rollbackReason
    ) {
        if (currentSnapshot == null || currentSnapshot.getDayDebitCents() == null) {
            return new TrustResult(ZERO, false, "REALTIME_DAY_DEBIT_UNREADABLE");
        }
        if (baselineResult.realtimeSnapshot == null) {
            if (baselineResult.initializationRequired
                    && baselineResult.recoveryWindowStartAt != null
                    && baselineResult.recoveryWindowStartAt.equals(currentSnapshot.getSlotTime())) {
                return new TrustResult(ZERO, true, null);
            }
            return new TrustResult(ZERO, false, baselineMissingReason);
        }
        if (baselineResult.realtimeSnapshot.getDayDebitCents() == null) {
            return new TrustResult(ZERO, false, baselineCounterMissingReason);
        }
        BigDecimal currentDayDebit = centsToYuan(BigDecimal.valueOf(currentSnapshot.getDayDebitCents()));
        if (baselineResult.realtimeSnapshot.getBizDate() != null
                && baselineResult.realtimeSnapshot.getBizDate().isEqual(currentSnapshot.getBizDate())) {
            BigDecimal baselineDayDebit = centsToYuan(BigDecimal.valueOf(baselineResult.realtimeSnapshot.getDayDebitCents()));
            if (currentDayDebit.compareTo(baselineDayDebit) < 0) {
                return new TrustResult(ZERO, false, rollbackReason);
            }
            return new TrustResult(currentDayDebit.subtract(baselineDayDebit).setScale(2, RoundingMode.HALF_UP), true, null);
        }
        if (baselineResult.bridgeRealtimeSnapshot != null && baselineResult.bridgeRealtimeSnapshot.getDayDebitCents() != null) {
            BigDecimal bridgeDayDebit = centsToYuan(BigDecimal.valueOf(baselineResult.bridgeRealtimeSnapshot.getDayDebitCents()));
            BigDecimal baselineDayDebit = centsToYuan(BigDecimal.valueOf(baselineResult.realtimeSnapshot.getDayDebitCents()));
            if (bridgeDayDebit.compareTo(baselineDayDebit) < 0) {
                return new TrustResult(ZERO, false, rollbackReason);
            }
            return new TrustResult(
                    bridgeDayDebit.subtract(baselineDayDebit).add(currentDayDebit).setScale(2, RoundingMode.HALF_UP),
                    true,
                    null
            );
        }
        return new TrustResult(currentDayDebit, true, null);
    }

    private TrustResult resolveRealtimeSlotCostDelta(
            MtPromotionAdRealtimeSnapshot currentSnapshot,
            MtPromotionAdRealtimeSnapshot previousSnapshot,
            BaselineResult baselineResult,
            LocalDateTime slotTime
    ) {
        if (currentSnapshot == null || currentSnapshot.getDayDebitCents() == null) {
            return new TrustResult(ZERO, false, "REALTIME_DAY_DEBIT_UNREADABLE");
        }
        if (previousSnapshot == null) {
            if (isFirstEffectiveSlot(baselineResult, slotTime)) {
                return new TrustResult(ZERO, true, null);
            }
            return new TrustResult(ZERO, false, "PREVIOUS_SLOT_REALTIME_SNAPSHOT_MISSING");
        }
        BigDecimal currentDayDebit = centsToYuan(BigDecimal.valueOf(currentSnapshot.getDayDebitCents()));
        if (previousSnapshot.getDayDebitCents() == null) {
            return new TrustResult(ZERO, false, "PREVIOUS_SLOT_REALTIME_COUNTER_MISSING");
        }
        if (previousSnapshot.getBizDate() != null && previousSnapshot.getBizDate().isEqual(currentSnapshot.getBizDate())) {
            BigDecimal previousDayDebit = centsToYuan(BigDecimal.valueOf(previousSnapshot.getDayDebitCents()));
            if (currentDayDebit.compareTo(previousDayDebit) < 0) {
                return new TrustResult(ZERO, false, "CURRENT_SLOT_REALTIME_COUNTER_ROLLED_BACK");
            }
            return new TrustResult(currentDayDebit.subtract(previousDayDebit).setScale(2, RoundingMode.HALF_UP), true, null);
        }
        return new TrustResult(currentDayDebit, true, null);
    }

    private RealtimeMetricResult buildFallbackMetricResult(
            BigDecimal currentCounterValue,
            BaselineResult baselineResult,
            MtPromotionMetricSnapshot previousMetricSnapshot,
            LocalDateTime slotTime
    ) {
        TrustResult cumulativeCostTrustResult;
        if (baselineResult.initializationRequired
                && baselineResult.recoveryWindowStartAt != null
                && baselineResult.recoveryWindowStartAt.equals(slotTime)) {
            cumulativeCostTrustResult = new TrustResult(ZERO, true, null);
        } else {
            cumulativeCostTrustResult = resolveCostFromBaseline(
                    currentCounterValue,
                    baselineResult.snapshot,
                    "EXECUTION_WINDOW_BASELINE_MISSING",
                    "EXECUTION_WINDOW_BASELINE_COUNTER_MISSING",
                    "EXECUTION_WINDOW_BASELINE_COUNTER_ROLLED_BACK"
            );
        }
        TrustResult slotCostDeltaTrustResult;
        if (previousMetricSnapshot == null && isFirstEffectiveSlot(baselineResult, slotTime)) {
            slotCostDeltaTrustResult = new TrustResult(ZERO, true, null);
        } else {
            slotCostDeltaTrustResult = resolveCostFromBaseline(
                    currentCounterValue,
                    previousMetricSnapshot,
                    "PREVIOUS_SLOT_SNAPSHOT_MISSING",
                    "PREVIOUS_SLOT_COUNTER_MISSING",
                    "CURRENT_SLOT_COST_DELTA_COUNTER_ROLLED_BACK"
            );
        }
        return new RealtimeMetricResult(
                null,
                null,
                previousMetricSnapshot,
                baselineResult,
                cumulativeCostTrustResult,
                slotCostDeltaTrustResult,
                "mt.waimaiad.adBalanceInfo.debit"
        );
    }

    private MtPromotionMetricSnapshot buildSnapshot(
            Long shopId,
            LocalDate bizDate,
            LocalDateTime slotTime,
            BigDecimal primaryBalance,
            BigDecimal commonBalance,
            BigDecimal promotionBalance,
            BigDecimal costCounterValue
    ) {
        MtPromotionMetricSnapshot snapshot = new MtPromotionMetricSnapshot();
        snapshot.setShopId(shopId);
        snapshot.setBizDate(bizDate);
        snapshot.setSlotTime(slotTime);
        snapshot.setPrimaryBalance(primaryBalance);
        snapshot.setCommonBalance(commonBalance);
        snapshot.setPromotionBalance(promotionBalance);
        snapshot.setCostCounterValue(costCounterValue);
        snapshot.setMetricsTrusted(false);
        snapshot.setCreateTime(slotTime);
        snapshot.setUpdateTime(slotTime);
        return snapshot;
    }

    private RecoveryWindowState resolveRecoveryWindowState(Long shopId,
                                                           LocalDateTime businessWindowStartAt,
                                                           LocalDateTime slotTime) {
        if (shopId == null || businessWindowStartAt == null || slotTime == null) {
            return null;
        }
        MtPromotionMetricSnapshot latestSnapshot = mtPromotionMetricSnapshotMapper.selectLatestByShopIdAndSlotTimeRange(
                shopId,
                businessWindowStartAt,
                slotTime
        );
        if (latestSnapshot == null || StringUtils.isBlank(latestSnapshot.getPayloadJson())) {
            return null;
        }
        Map<String, Object> payload = JsonUtil.toMap(latestSnapshot.getPayloadJson());
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        Object startAtValue = payload.get(TEMPORARY_RECOVERY_WINDOW_START_AT_KEY);
        if (!(startAtValue instanceof String) || StringUtils.isBlank((String) startAtValue)) {
            return null;
        }
        try {
            return new RecoveryWindowState(
                    LocalDateTime.parse((String) startAtValue),
                    StringUtils.defaultIfBlank((String) payload.get(TEMPORARY_RECOVERY_WINDOW_SOURCE_KEY), "RECOVERY_WINDOW_REUSED")
            );
        } catch (Exception e) {
            log.warn("解析临时恢复窗口起点失败，shopId={}, slotTime={}, payload={}",
                    shopId, slotTime, latestSnapshot.getPayloadJson());
            return null;
        }
    }

    private LocalDateTime resolveEffectiveWindowStartAt(LocalDateTime businessWindowStartAt, BaselineResult baselineResult) {
        if (baselineResult != null && baselineResult.recoveryWindowStartAt != null) {
            return baselineResult.recoveryWindowStartAt;
        }
        return businessWindowStartAt;
    }

    private boolean isFirstEffectiveSlot(BaselineResult baselineResult, LocalDateTime slotTime) {
        if (baselineResult == null || slotTime == null) {
            return false;
        }
        if (baselineResult.recoveryWindowStartAt != null && baselineResult.recoveryWindowStartAt.equals(slotTime)) {
            return true;
        }
        if (baselineResult.snapshot != null
                && baselineResult.snapshot.getSlotTime() != null
                && baselineResult.snapshot.getSlotTime().equals(slotTime)) {
            return true;
        }
        return baselineResult.realtimeSnapshot != null
                && baselineResult.realtimeSnapshot.getSlotTime() != null
                && baselineResult.realtimeSnapshot.getSlotTime().equals(slotTime);
    }

    private String resolveBaselineType(String payloadSource, BaselineResult baselineResult) {
        boolean realtime = StringUtils.equals(payloadSource, "mt.waimaiad.adDataDetail.realtime");
        if (baselineResult != null && (baselineResult.temporaryRecoveryWindow || baselineResult.initializationRequired)) {
            return realtime ? BASELINE_TYPE_TEMPORARY_RECOVERY_REALTIME : BASELINE_TYPE_TEMPORARY_RECOVERY_FALLBACK;
        }
        return realtime ? BASELINE_TYPE_REALTIME : BASELINE_TYPE_FALLBACK;
    }

    private void validateCandidate(MtPromotionAutoBidCandidate candidate) {
        if (candidate == null || candidate.getShopId() == null) {
            throw new IllegalArgumentException("自动调价缺少门店信息");
        }
        if (StringUtils.isBlank(candidate.getAccessToken())) {
            throw new IllegalArgumentException("自动调价缺少 accessToken");
        }
    }

    private AdBalanceInfoResponse fetchAdBalanceInfo(Long shopId, String accessToken) {
        try {
            MeituanResponse<AdBalanceInfoResponse> response = meituanClient.invokeApi(new AdBalanceInfoRequest(), accessToken);
            if (response == null || !response.isSuccess() || response.getData() == null) {
                log.warn("查询美团广告余额失败，shopId={}, response={}", shopId, response);
                throw new IllegalStateException("查询美团广告余额失败");
            }

            AdBalanceInfoResponse payload = response.getData();
            if (payload.getResult() == null || !Boolean.TRUE.equals(payload.getResult().getSuccess())) {
                log.warn("查询美团广告余额业务失败，shopId={}, result={}", shopId, payload.getResult());
                throw new IllegalStateException("查询美团广告余额业务失败");
            }
            return payload;
        } catch (MtSdkException e) {
            log.error("查询美团广告余额异常，shopId={}", shopId, e);
            throw new IllegalStateException("查询美团广告余额异常", e);
        }
    }

    private BigDecimal decryptMoney(Long shopId, String accessToken, String encryptedValue, Map<String, String> decryptCache) {
        if (StringUtils.isBlank(encryptedValue)) {
            return ZERO;
        }

        try {
            String decryptedValue = mtWaimaiAdDecryptService.decryptValue(
                    shopId,
                    accessToken,
                    encryptedValue,
                    "美团广告金额",
                    decryptCache
            );
            return centsToYuan(new BigDecimal(decryptedValue.trim()));
        } catch (NumberFormatException e) {
            log.error("解析美团广告金额异常，shopId={}, encryptedValue={}", shopId, encryptedValue, e);
            throw new IllegalStateException("解析美团广告金额异常", e);
        }
    }

    private BigDecimal decryptMoneyLenient(Long shopId, String accessToken, String encryptedValue, Map<String, String> decryptCache) {
        try {
            return decryptMoney(shopId, accessToken, encryptedValue, decryptCache);
        } catch (IllegalStateException e) {
            log.warn("推广计数器解密失败，将当前槽位标记为不可信，shopId={}", shopId, e);
            return null;
        }
    }

    private BigDecimal calculateRatio(BigDecimal totalCost, BigDecimal totalIncome) {
        if (totalIncome == null || totalIncome.compareTo(BigDecimal.ZERO) <= 0) {
            if (totalCost == null || totalCost.compareTo(BigDecimal.ZERO) <= 0) {
                return BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP);
            }
            return BigDecimal.ONE.setScale(6, RoundingMode.HALF_UP);
        }
        return totalCost.divide(totalIncome, 6, RoundingMode.HALF_UP);
    }

    private BigDecimal centsToYuan(BigDecimal cents) {
        if (cents == null) {
            return ZERO;
        }
        return cents.divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
    }

    private static class TrustResult {

        private final BigDecimal windowCost;
        private final boolean trusted;
        private final String untrustedReason;

        private TrustResult(BigDecimal windowCost, boolean trusted, String untrustedReason) {
            this.windowCost = windowCost;
            this.trusted = trusted;
            this.untrustedReason = untrustedReason;
        }
    }

    private static class BaselineResult {

        private final MtPromotionMetricSnapshot snapshot;
        private final MtPromotionAdRealtimeSnapshot realtimeSnapshot;
        private final MtPromotionAdRealtimeSnapshot bridgeRealtimeSnapshot;
        private final String source;
        private final boolean temporaryRecoveryWindow;
        private final boolean initializationRequired;
        private final LocalDateTime recoveryWindowStartAt;
        private final String recoveryWindowSource;

        private BaselineResult(
                MtPromotionMetricSnapshot snapshot,
                MtPromotionAdRealtimeSnapshot realtimeSnapshot,
                MtPromotionAdRealtimeSnapshot bridgeRealtimeSnapshot,
                String source
        ) {
            this(snapshot, realtimeSnapshot, bridgeRealtimeSnapshot, source, false, false, null, null);
        }

        private BaselineResult(
                MtPromotionMetricSnapshot snapshot,
                MtPromotionAdRealtimeSnapshot realtimeSnapshot,
                MtPromotionAdRealtimeSnapshot bridgeRealtimeSnapshot,
                String source,
                boolean temporaryRecoveryWindow,
                boolean initializationRequired,
                LocalDateTime recoveryWindowStartAt,
                String recoveryWindowSource
        ) {
            this.snapshot = snapshot;
            this.realtimeSnapshot = realtimeSnapshot;
            this.bridgeRealtimeSnapshot = bridgeRealtimeSnapshot;
            this.source = source;
            this.temporaryRecoveryWindow = temporaryRecoveryWindow;
            this.initializationRequired = initializationRequired;
            this.recoveryWindowStartAt = recoveryWindowStartAt;
            this.recoveryWindowSource = recoveryWindowSource;
        }
    }

    private static class RealtimeMetricResult {

        private final MtPromotionAdRealtimeSnapshot currentRealtimeSnapshot;
        private final MtPromotionAdRealtimeSnapshot previousRealtimeSnapshot;
        private final MtPromotionMetricSnapshot previousMetricSnapshot;
        private final BaselineResult baselineResult;
        private final TrustResult cumulativeCostTrustResult;
        private final TrustResult slotCostDeltaTrustResult;
        private final String payloadSource;

        private RealtimeMetricResult(
                MtPromotionAdRealtimeSnapshot currentRealtimeSnapshot,
                MtPromotionAdRealtimeSnapshot previousRealtimeSnapshot,
                MtPromotionMetricSnapshot previousMetricSnapshot,
                BaselineResult baselineResult,
                TrustResult cumulativeCostTrustResult,
                TrustResult slotCostDeltaTrustResult,
                String payloadSource
        ) {
            this.currentRealtimeSnapshot = currentRealtimeSnapshot;
            this.previousRealtimeSnapshot = previousRealtimeSnapshot;
            this.previousMetricSnapshot = previousMetricSnapshot;
            this.baselineResult = baselineResult;
            this.cumulativeCostTrustResult = cumulativeCostTrustResult;
            this.slotCostDeltaTrustResult = slotCostDeltaTrustResult;
            this.payloadSource = payloadSource;
        }
    }

    private static class RecoveryWindowState {

        private final LocalDateTime startAt;
        private final String source;

        private RecoveryWindowState(LocalDateTime startAt, String source) {
            this.startAt = startAt;
            this.source = source;
        }
    }
}
