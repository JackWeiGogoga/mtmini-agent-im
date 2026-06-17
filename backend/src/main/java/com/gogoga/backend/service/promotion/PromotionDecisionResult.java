package com.gogoga.backend.service.promotion;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 推广调价决策结果
 *
 * @author system
 * @since 2026-03-26
 */
@Data
public class PromotionDecisionResult {

    /**
     * 上一次出价
     */
    private BigDecimal previousBidAmount;

    /**
     * 下一次出价
     */
    private BigDecimal nextBidAmount;

    /**
     * 决策动作
     */
    private String decisionAction;

    /**
     * 决策原因
     */
    private String decisionReason;

    /**
     * 决策来源
     */
    private String decisionSource;

    /**
     * 花费增量
     */
    private BigDecimal costDelta;

    /**
     * 决策扩展详情
     */
    private String decisionDetailJson;

    /**
     * 算力消费模式
     */
    private String powerConsumeMode;

    /**
     * LLM 总 token 用量
     */
    private Long llmTotalTokens;

    /**
     * LLM 输入 token 用量
     */
    private Long llmPromptTokens;

    /**
     * LLM 输出 token 用量
     */
    private Long llmCompletionTokens;
}
