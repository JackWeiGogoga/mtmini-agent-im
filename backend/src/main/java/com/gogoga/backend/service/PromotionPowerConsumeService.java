package com.gogoga.backend.service;

import com.gogoga.backend.config.PowerProperties;
import com.gogoga.backend.dto.PowerConsumeRequestDTO;
import com.gogoga.backend.dto.PowerConsumeResultDTO;
import com.gogoga.backend.service.promotion.PromotionDecisionResult;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 推广计算算力消费服务
 */
@Service
public class PromotionPowerConsumeService {

    public static final String CONSUME_SOURCE_RULE = "PROMOTION_CALC_RULE";
    public static final String CONSUME_SOURCE_LLM = "PROMOTION_CALC_LLM";
    public static final String CONSUME_SOURCE_LOG_LLM = "PROMOTION_LOG_LLM";
    public static final String POWER_MODE_RULE = "RULE";
    public static final String POWER_MODE_LLM = "LLM";
    private static final DateTimeFormatter REQUEST_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    private final MtPowerConsumeService mtPowerConsumeService;
    private final PowerProperties powerProperties;

    public PromotionPowerConsumeService(MtPowerConsumeService mtPowerConsumeService, PowerProperties powerProperties) {
        this.mtPowerConsumeService = mtPowerConsumeService;
        this.powerProperties = powerProperties;
    }

    public PowerConsumeResultDTO consumeForDecision(Long shopId, LocalDateTime slotTime, PromotionDecisionResult decisionResult) {
        if (shopId == null || slotTime == null || decisionResult == null) {
            return null;
        }
        String powerMode = decisionResult.getPowerConsumeMode();
        if (POWER_MODE_LLM.equals(powerMode)) {
            return consume(
                    shopId,
                    slotTime,
                    CONSUME_SOURCE_LLM,
                    calculateLlmPowerAmount(decisionResult.getLlmPromptTokens(), decisionResult.getLlmCompletionTokens()),
                    decisionResult.getLlmPromptTokens(),
                    decisionResult.getLlmCompletionTokens()
            );
        }
        return null;
    }

    public PowerConsumeResultDTO consumeForLogGeneration(Long shopId, LocalDateTime slotTime, Long promptTokens, Long completionTokens) {
        if (shopId == null || slotTime == null) {
            return null;
        }
        if ((promptTokens == null || promptTokens <= 0) && (completionTokens == null || completionTokens <= 0)) {
            return null;
        }
        return consume(
                shopId,
                slotTime.plusSeconds(5),
                CONSUME_SOURCE_LOG_LLM,
                calculateLlmPowerAmount(promptTokens, completionTokens),
                promptTokens,
                completionTokens
        );
    }

    int calculateLlmPowerAmount(Long promptTokens, Long completionTokens) {
        return powerProperties.getBilling().calculateLlmPowerAmount(promptTokens, completionTokens);
    }

    private PowerConsumeResultDTO consume(
            Long shopId,
            LocalDateTime slotTime,
            String sourceType,
            Integer powerAmount,
            Long promptTokens,
            Long completionTokens
    ) {
        PowerConsumeRequestDTO request = new PowerConsumeRequestDTO();
        request.setShopId(shopId);
        request.setSourceType(sourceType);
        request.setSourceId(buildSourceId(shopId, slotTime));
        request.setRequestNo(buildRequestNo(shopId, slotTime, sourceType));
        request.setPowerAmount(powerAmount);
        request.setRemark(resolveRemark(sourceType, promptTokens, completionTokens));
        return mtPowerConsumeService.consume(request);
    }

    private String resolveRemark(String sourceType, Long promptTokens, Long completionTokens) {
        long totalTokens = (promptTokens == null ? 0L : promptTokens) + (completionTokens == null ? 0L : completionTokens);
        if (CONSUME_SOURCE_LOG_LLM.equals(sourceType)) {
            return "推广日志 LLM 生成算力消耗，promptTokens=" + defaultLong(promptTokens)
                    + ", completionTokens=" + defaultLong(completionTokens)
                    + ", totalTokens=" + totalTokens;
        }
        return "推广费用 LLM 计算算力消耗，promptTokens=" + defaultLong(promptTokens)
                + ", completionTokens=" + defaultLong(completionTokens)
                + ", totalTokens=" + totalTokens;
    }

    private String buildSourceId(Long shopId, LocalDateTime slotTime) {
        return shopId + "-" + slotTime.format(REQUEST_TIME_FORMATTER);
    }

    private String buildRequestNo(Long shopId, LocalDateTime slotTime, String sourceType) {
        return "PROMO-POWER-" + sourceType + "-" + shopId + "-" + slotTime.format(REQUEST_TIME_FORMATTER);
    }

    private long defaultLong(Long value) {
        return value == null ? 0L : value;
    }
}
