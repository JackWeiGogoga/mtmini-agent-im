package com.gogoga.backend.service.promotion;

import com.gogoga.backend.entity.mt.MtPromotionBidRecord;
import com.gogoga.backend.service.MtPromotionSettingService;
import com.gogoga.backend.service.PromotionPowerConsumeService;
import com.gogoga.backend.util.JsonUtil;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 推广调价决策编排服务
 */
@Service
@RequiredArgsConstructor
public class PromotionBidDecisionService {

    public static final String DECISION_MODE_RULE = "rule";
    public static final String DECISION_SOURCE_RULE = "rule";

    private final PromotionDecisionEngine promotionDecisionEngine;

    public PromotionDecisionResult decide(
            MtPromotionSettingService.StrategyConfig strategyConfig,
            PromotionMetricsSnapshot metricsSnapshot,
            List<MtPromotionBidRecord> successHistory,
            LocalDateTime slotTime
    ) {
        return decide(strategyConfig, metricsSnapshot, successHistory, slotTime, null);
    }

    public PromotionDecisionResult decide(
            MtPromotionSettingService.StrategyConfig strategyConfig,
            PromotionMetricsSnapshot metricsSnapshot,
            List<MtPromotionBidRecord> successHistory,
            LocalDateTime slotTime,
            java.math.BigDecimal initialBid
    ) {
        return buildRuleResult(strategyConfig, metricsSnapshot, successHistory, DECISION_SOURCE_RULE, null, initialBid);
    }

    private PromotionDecisionResult buildRuleResult(
            MtPromotionSettingService.StrategyConfig strategyConfig,
            PromotionMetricsSnapshot metricsSnapshot,
            List<MtPromotionBidRecord> successHistory,
            String decisionSource,
            String llmFailureSummary,
            java.math.BigDecimal initialBid
    ) {
        PromotionDecisionResult result = promotionDecisionEngine.decide(strategyConfig, metricsSnapshot, successHistory, initialBid);
        result.setDecisionSource(decisionSource);
        result.setPowerConsumeMode(PromotionPowerConsumeService.POWER_MODE_RULE);
        result.setLlmTotalTokens(null);
        result.setLlmPromptTokens(null);
        result.setLlmCompletionTokens(null);
        PromotionDecisionDetail detail = new PromotionDecisionDetail();
        detail.setDecisionMode(DECISION_MODE_RULE);
        detail.setDecisionSource(decisionSource);
        if (StringUtils.isNotBlank(llmFailureSummary)) {
            detail.setLlmFailureSummary(StringUtils.left(llmFailureSummary, 300));
        }
        result.setDecisionDetailJson(JsonUtil.toJson(detail));
        return result;
    }
}
