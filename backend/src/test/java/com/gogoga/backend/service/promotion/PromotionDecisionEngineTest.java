package com.gogoga.backend.service.promotion;

import com.gogoga.backend.entity.mt.MtPromotionBidRecord;
import com.gogoga.backend.service.MtPromotionSettingService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 推广调价决策引擎测试
 *
 * @author system
 * @since 2026-03-26
 */
class PromotionDecisionEngineTest {

    private final PromotionDecisionEngine promotionDecisionEngine = new PromotionDecisionEngine();

    @Test
    void shouldUseInitialBidWhenNoSuccessHistoryExists() {
        PromotionDecisionResult result = promotionDecisionEngine.decide(
                buildStrategyConfig("10%"),
                buildMetrics("0.00", "100.00", "0.000000"),
                List.of()
        );

        assertEquals(PromotionDecisionEngine.ACTION_INIT, result.getDecisionAction());
        assertEquals("FIRST_SUCCESS_OF_WINDOW", result.getDecisionReason());
        assertEquals(new BigDecimal("0.40"), result.getNextBidAmount());
    }

    @Test
    void shouldFloorProvidedInitialBidToOneDecimalWhenNoSuccessHistoryExists() {
        PromotionDecisionResult result = promotionDecisionEngine.decide(
                buildStrategyConfig("10%"),
                buildMetrics("0.00", "100.00", "0.000000"),
                List.of(),
                new BigDecimal("0.66")
        );

        assertEquals(PromotionDecisionEngine.ACTION_INIT, result.getDecisionAction());
        assertEquals("FIRST_SUCCESS_OF_WINDOW", result.getDecisionReason());
        assertEquals(new BigDecimal("0.60"), result.getNextBidAmount());
    }

    @Test
    void shouldDecreaseBidWhenTotalCostRatioExceedsMaxRate() {
        PromotionDecisionResult result = promotionDecisionEngine.decide(
                buildStrategyConfig("10%"),
                buildMetrics("5.00", "40.00", "0.125000"),
                List.of(buildHistory("0.50", "4.50"))
        );

        assertEquals(PromotionDecisionEngine.ACTION_DECREASE, result.getDecisionAction());
        assertEquals(new BigDecimal("0.40"), result.getNextBidAmount());
    }

    @Test
    void shouldFastDecreaseWhenRatePriorityExceedsMaxRateByFivePercentagePoints() {
        PromotionDecisionResult result = promotionDecisionEngine.decide(
                buildRatePriorityStrategyConfig("10%"),
                buildMetrics("6.04", "40.00", "0.151000"),
                List.of(buildHistory("0.50", "4.50"))
        );

        assertEquals(PromotionDecisionEngine.ACTION_DECREASE, result.getDecisionAction());
        assertEquals(PromotionDecisionEngine.REASON_RATE_PRIORITY_FAST_CORRECTION, result.getDecisionReason());
        assertEquals(new BigDecimal("0.30"), result.getNextBidAmount());
    }

    @Test
    void shouldUseNormalDecreaseWhenRatePriorityIsWithinFivePercentagePointBand() {
        PromotionDecisionResult result = promotionDecisionEngine.decide(
                buildRatePriorityStrategyConfig("10%"),
                buildMetrics("5.60", "40.00", "0.140000"),
                List.of(buildHistory("0.50", "4.50"))
        );

        assertEquals(PromotionDecisionEngine.ACTION_DECREASE, result.getDecisionAction());
        assertEquals("TOTAL_COST_RATIO_ABOVE_MAX", result.getDecisionReason());
        assertEquals(new BigDecimal("0.40"), result.getNextBidAmount());
    }

    @Test
    void shouldKeepInitialBidWhenWindowIncomeIsStillZero() {
        PromotionDecisionResult result = promotionDecisionEngine.decide(
                buildStrategyConfig("10%"),
                buildMetrics("0.00", "0.00", "0.000000"),
                List.of(buildHistory("0.50", "4.50"))
        );

        assertEquals(PromotionDecisionEngine.ACTION_KEEP, result.getDecisionAction());
        assertEquals("WINDOW_INCOME_NOT_READY_KEEP_INITIAL_BID", result.getDecisionReason());
        assertEquals(new BigDecimal("0.50"), result.getNextBidAmount());
    }

    @Test
    void shouldIncreaseBidWhenWindowIncomeIsPositiveAndNoAdditionalCostIsObserved() {
        PromotionDecisionResult result = promotionDecisionEngine.decide(
                buildStrategyConfig("10%"),
                buildMetrics("0.00", "100.00", "0.000000"),
                List.of(buildHistory("0.50", "4.50"))
        );

        assertEquals(PromotionDecisionEngine.ACTION_INCREASE, result.getDecisionAction());
        assertEquals("NO_CLICK_COST_DELTA_ZERO", result.getDecisionReason());
        assertEquals(new BigDecimal("0.60"), result.getNextBidAmount());
    }

    @Test
    void shouldKeepBidWhenAdditionalCostIsObserved() {
        PromotionDecisionResult result = promotionDecisionEngine.decide(
                buildStrategyConfig("10%"),
                buildMetrics("4.80", "100.00", "0.048000"),
                List.of(buildHistory("0.50", "4.50"))
        );

        assertEquals(PromotionDecisionEngine.ACTION_KEEP, result.getDecisionAction());
        assertEquals(new BigDecimal("0.50"), result.getNextBidAmount());
    }

    @Test
    void shouldContinueUsingCurrentSlotCostDeltaInsteadOfComparingTwoHistoricalBidRounds() {
        PromotionDecisionResult result = promotionDecisionEngine.decide(
                buildStrategyConfig("10%"),
                buildMetrics("4.80", "100.00", "0.048000"),
                List.of(
                        buildHistory("0.50", "9.90"),
                        buildHistory("0.40", "1.00")
                )
        );

        assertEquals(PromotionDecisionEngine.ACTION_KEEP, result.getDecisionAction());
        assertEquals("CLICK_DETECTED_KEEP_PREVIOUS_BID", result.getDecisionReason());
        assertEquals(new BigDecimal("0.50"), result.getNextBidAmount());
    }

    @Test
    void shouldEnforceMinimumBidFloorWhenDecreasing() {
        PromotionDecisionResult result = promotionDecisionEngine.decide(
                buildStrategyConfig("10%"),
                buildMetrics("5.00", "40.00", "0.125000"),
                List.of(buildHistory("0.10", "4.50"))
        );

        assertEquals(PromotionDecisionEngine.ACTION_DECREASE, result.getDecisionAction());
        assertEquals(new BigDecimal("0.10"), result.getNextBidAmount());
    }

    private MtPromotionSettingService.StrategyConfig buildStrategyConfig(String rateMax) {
        MtPromotionSettingService.StrategyConfig strategyConfig = new MtPromotionSettingService.StrategyConfig();
        strategyConfig.setRateMax(rateMax);
        strategyConfig.setTimeMode("auto");
        return strategyConfig;
    }

    private MtPromotionSettingService.StrategyConfig buildRatePriorityStrategyConfig(String rateMax) {
        MtPromotionSettingService.StrategyConfig strategyConfig = buildStrategyConfig(rateMax);
        strategyConfig.setPriority("rate");
        return strategyConfig;
    }

    private PromotionMetricsSnapshot buildMetrics(String totalCost, String totalIncome, String totalCostRatio) {
        PromotionMetricsSnapshot snapshot = new PromotionMetricsSnapshot();
        snapshot.setWindowCost(new BigDecimal(totalCost));
        snapshot.setWindowIncome(new BigDecimal(totalIncome));
        snapshot.setWindowCostRatio(new BigDecimal(totalCostRatio));
        snapshot.setSlotCostDelta(new BigDecimal(totalCost));
        return snapshot;
    }

    private MtPromotionBidRecord buildHistory(String nextBidAmount, String totalCost) {
        MtPromotionBidRecord record = new MtPromotionBidRecord();
        record.setNextBidAmount(new BigDecimal(nextBidAmount));
        record.setTotalCost(new BigDecimal(totalCost));
        return record;
    }
}
