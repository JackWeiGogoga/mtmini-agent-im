package com.gogoga.backend.service.promotion;

import com.gogoga.backend.entity.mt.MtPromotionBidRecord;
import com.gogoga.backend.service.MtPromotionSettingService;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 推广调价决策引擎
 *
 * @author system
 * @since 2026-03-26
 */
@Component
public class PromotionDecisionEngine {

    public static final String ACTION_INIT = "INIT";
    public static final String ACTION_INCREASE = "INCREASE";
    public static final String ACTION_DECREASE = "DECREASE";
    public static final String ACTION_KEEP = "KEEP";
    public static final String REASON_RATE_PRIORITY_FAST_CORRECTION = "RATE_PRIORITY_FAST_CORRECTION";

    private static final BigDecimal INITIAL_BID = new BigDecimal("0.40");
    private static final BigDecimal MIN_BID = new BigDecimal("0.10");
    private static final BigDecimal BID_STEP = new BigDecimal("0.10");
    private static final BigDecimal FAST_DECREASE_STEP = new BigDecimal("0.20");
    private static final BigDecimal RATE_PRIORITY_FAST_THRESHOLD_DELTA = new BigDecimal("0.050000");
    private static final BigDecimal TEN = BigDecimal.TEN;
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    /**
     * 计算下一时段出价
     *
     * @param strategyConfig 策略配置
     * @param metricsSnapshot 当前指标
     * @param successHistory 当日最近成功记录，按时间倒序
     * @return 决策结果
     */
    public PromotionDecisionResult decide(
            MtPromotionSettingService.StrategyConfig strategyConfig,
            PromotionMetricsSnapshot metricsSnapshot,
            List<MtPromotionBidRecord> successHistory
    ) {
        return decide(strategyConfig, metricsSnapshot, successHistory, INITIAL_BID);
    }

    public PromotionDecisionResult decide(
            MtPromotionSettingService.StrategyConfig strategyConfig,
            PromotionMetricsSnapshot metricsSnapshot,
            List<MtPromotionBidRecord> successHistory,
            BigDecimal initialBid
    ) {
        // Exposure iterations must reuse this fee-rate branch output rather than
        // replacing it with a new multi-round fee-trend algorithm.
        List<MtPromotionBidRecord> normalizedHistory = normalizeSuccessHistory(successHistory);
        PromotionDecisionResult result = new PromotionDecisionResult();
        MtPromotionBidRecord latestSuccess = CollectionUtils.isEmpty(normalizedHistory) ? null : normalizedHistory.get(0);
        BigDecimal previousBid = latestSuccess == null ? null : normalizeMoney(latestSuccess.getNextBidAmount());
        BigDecimal windowIncome = metricsSnapshot == null ? null : normalizeMoney(metricsSnapshot.getWindowIncome());
        BigDecimal currentRatio = metricsSnapshot == null ? null : metricsSnapshot.getWindowCostRatio();
        BigDecimal slotCostDelta = metricsSnapshot == null ? null : normalizeMoney(metricsSnapshot.getSlotCostDelta());
        BigDecimal maxRate = parseRateToDecimal(strategyConfig == null ? null : strategyConfig.getRateMax());

        result.setPreviousBidAmount(previousBid);
        result.setCostDelta(slotCostDelta);

        if (latestSuccess == null) {
            result.setDecisionAction(ACTION_INIT);
            result.setDecisionReason("FIRST_SUCCESS_OF_WINDOW");
            result.setNextBidAmount(clampBid(initialBid == null ? INITIAL_BID : initialBid));
            return result;
        }

        if (windowIncome == null || windowIncome.compareTo(BigDecimal.ZERO) <= 0) {
            result.setDecisionAction(ACTION_KEEP);
            result.setDecisionReason("WINDOW_INCOME_NOT_READY_KEEP_INITIAL_BID");
            result.setNextBidAmount(clampBid(previousBid));
            return result;
        }

        if (currentRatio != null
                && maxRate != null
                && isRatePriority(strategyConfig)
                && currentRatio.compareTo(maxRate.add(RATE_PRIORITY_FAST_THRESHOLD_DELTA)) > 0) {
            result.setDecisionAction(ACTION_DECREASE);
            result.setDecisionReason(REASON_RATE_PRIORITY_FAST_CORRECTION);
            result.setNextBidAmount(clampBid(previousBid.subtract(FAST_DECREASE_STEP)));
            return result;
        }

        if (currentRatio != null && maxRate != null && currentRatio.compareTo(maxRate) > 0) {
            result.setDecisionAction(ACTION_DECREASE);
            result.setDecisionReason("TOTAL_COST_RATIO_ABOVE_MAX");
            result.setNextBidAmount(clampBid(previousBid.subtract(BID_STEP)));
            return result;
        }

        if (slotCostDelta == null || slotCostDelta.compareTo(BigDecimal.ZERO) <= 0) {
            result.setDecisionAction(ACTION_INCREASE);
            result.setDecisionReason("NO_CLICK_COST_DELTA_ZERO");
            result.setNextBidAmount(clampBid(previousBid.add(BID_STEP)));
            return result;
        }

        result.setDecisionAction(ACTION_KEEP);
        result.setDecisionReason("CLICK_DETECTED_KEEP_PREVIOUS_BID");
        result.setNextBidAmount(clampBid(previousBid));
        return result;
    }

    private BigDecimal parseRateToDecimal(String value) {
        if (StringUtils.isBlank(value) || !value.endsWith("%")) {
            return null;
        }
        String number = StringUtils.removeEnd(value.trim(), "%");
        return new BigDecimal(number).divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP);
    }

    private boolean isRatePriority(MtPromotionSettingService.StrategyConfig strategyConfig) {
        return strategyConfig != null && StringUtils.equals(strategyConfig.getPriority(), "rate");
    }

    private BigDecimal normalizeMoney(BigDecimal value) {
        return value == null ? ZERO : value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal clampBid(BigDecimal value) {
        BigDecimal normalized = normalizeBidAmountWithFloor(value);
        return normalized.compareTo(MIN_BID) < 0 ? MIN_BID : normalized;
    }

    private BigDecimal normalizeBidAmountWithFloor(BigDecimal value) {
        BigDecimal normalized = normalizeMoney(value);
        return normalized.multiply(TEN)
                .setScale(0, RoundingMode.DOWN)
                .divide(TEN)
                .setScale(2, RoundingMode.UNNECESSARY);
    }

    private List<MtPromotionBidRecord> normalizeSuccessHistory(List<MtPromotionBidRecord> successHistory) {
        if (CollectionUtils.isEmpty(successHistory)) {
            return Collections.emptyList();
        }
        List<MtPromotionBidRecord> normalized = new ArrayList<>(successHistory);
        normalized.sort(Comparator
                .comparing(MtPromotionBidRecord::getSlotTime, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(MtPromotionBidRecord::getId, Comparator.nullsLast(Comparator.reverseOrder())));
        return normalized;
    }
}
