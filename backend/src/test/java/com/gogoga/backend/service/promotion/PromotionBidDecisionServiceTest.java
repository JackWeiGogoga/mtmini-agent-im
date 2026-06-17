package com.gogoga.backend.service.promotion;

import com.gogoga.backend.entity.mt.MtPromotionBidRecord;
import com.gogoga.backend.service.MtPromotionSettingService;
import com.gogoga.backend.util.JsonUtil;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
class PromotionBidDecisionServiceTest {

    @Test
    void shouldUseRuleDecisionWhenConfiguredRuleMode() {
        PromotionDecisionEngine promotionDecisionEngine = org.mockito.Mockito.mock(PromotionDecisionEngine.class);
        PromotionBidDecisionService promotionBidDecisionService = new PromotionBidDecisionService(promotionDecisionEngine);
        PromotionDecisionResult expected = buildResult("0.40", PromotionDecisionEngine.ACTION_KEEP);
        org.mockito.Mockito.when(promotionDecisionEngine.decide(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(expected);

        PromotionDecisionResult result = promotionBidDecisionService.decide(
                buildStrategyConfig("rule"),
                buildMetrics(),
                List.of(buildHistory("0.40", "3.00")),
                LocalDateTime.of(2026, 4, 8, 12, 30)
        );

        assertEquals(PromotionBidDecisionService.DECISION_SOURCE_RULE, result.getDecisionSource());
        PromotionDecisionDetail detail = JsonUtil.toBean(result.getDecisionDetailJson(), PromotionDecisionDetail.class);
        assertNotNull(detail);
        assertEquals(PromotionBidDecisionService.DECISION_MODE_RULE, detail.getDecisionMode());
    }

    @Test
    void shouldStillUseRuleDecisionWhenStrategyKeepsLegacyLlmMode() {
        PromotionDecisionEngine promotionDecisionEngine = org.mockito.Mockito.mock(PromotionDecisionEngine.class);
        PromotionBidDecisionService promotionBidDecisionService = new PromotionBidDecisionService(promotionDecisionEngine);
        PromotionDecisionResult expected = buildResult("0.50", PromotionDecisionEngine.ACTION_INCREASE);
        org.mockito.Mockito.when(promotionDecisionEngine.decide(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(expected);

        PromotionDecisionResult result = promotionBidDecisionService.decide(
                buildStrategyConfig("llm"),
                buildMetrics(),
                List.of(buildHistory("0.40", "3.00"), buildHistory("0.40", "3.30")),
                LocalDateTime.of(2026, 4, 8, 12, 30)
        );

        assertEquals(PromotionBidDecisionService.DECISION_SOURCE_RULE, result.getDecisionSource());
        PromotionDecisionDetail detail = JsonUtil.toBean(result.getDecisionDetailJson(), PromotionDecisionDetail.class);
        assertNotNull(detail);
        assertEquals(PromotionBidDecisionService.DECISION_MODE_RULE, detail.getDecisionMode());
    }

    @Test
    void shouldDelegateToRuleEngineForAnyInputHistory() {
        PromotionDecisionEngine promotionDecisionEngine = org.mockito.Mockito.mock(PromotionDecisionEngine.class);
        PromotionBidDecisionService promotionBidDecisionService = new PromotionBidDecisionService(promotionDecisionEngine);
        PromotionDecisionResult expected = buildResult("0.30", PromotionDecisionEngine.ACTION_DECREASE);
        expected.setDecisionReason("TOTAL_COST_RATIO_ABOVE_MAX");
        org.mockito.Mockito.when(promotionDecisionEngine.decide(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(expected);

        PromotionDecisionResult result = promotionBidDecisionService.decide(
                buildStrategyConfig("llm"),
                buildMetrics("0.101000"),
                List.of(buildHistory("0.40", "3.00")),
                LocalDateTime.of(2026, 4, 8, 12, 30)
        );

        assertEquals("TOTAL_COST_RATIO_ABOVE_MAX", result.getDecisionReason());
        org.mockito.Mockito.verify(promotionDecisionEngine).decide(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    private MtPromotionSettingService.StrategyConfig buildStrategyConfig(String decisionMode) {
        MtPromotionSettingService.StrategyConfig config = new MtPromotionSettingService.StrategyConfig();
        config.setDecisionMode(decisionMode);
        config.setRateMax("10%");
        config.setTimeMode("auto");
        config.setRateMode("control");
        config.setRateMin("5%");
        config.setPriority("rate");
        return config;
    }

    private PromotionMetricsSnapshot buildMetrics() {
        return buildMetrics("0.041250");
    }

    private PromotionMetricsSnapshot buildMetrics(String totalCostRatio) {
        PromotionMetricsSnapshot snapshot = new PromotionMetricsSnapshot();
        snapshot.setWindowCost(new BigDecimal("3.30"));
        snapshot.setWindowIncome(new BigDecimal("80.00"));
        snapshot.setWindowCostRatio(new BigDecimal(totalCostRatio));
        snapshot.setSlotCostDelta(new BigDecimal("0.30"));
        return snapshot;
    }

    private MtPromotionBidRecord buildHistory(String nextBidAmount, String totalCost) {
        MtPromotionBidRecord record = new MtPromotionBidRecord();
        record.setNextBidAmount(new BigDecimal(nextBidAmount));
        record.setTotalCost(new BigDecimal(totalCost));
        return record;
    }

    private PromotionDecisionResult buildResult(String nextBidAmount, String action) {
        PromotionDecisionResult result = new PromotionDecisionResult();
        result.setDecisionAction(action);
        result.setDecisionReason("TEST_REASON");
        result.setNextBidAmount(new BigDecimal(nextBidAmount));
        return result;
    }
}
