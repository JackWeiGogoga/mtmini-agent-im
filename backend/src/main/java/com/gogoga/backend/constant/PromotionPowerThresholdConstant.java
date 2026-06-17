package com.gogoga.backend.constant;

/**
 * 推广执行相关算力阈值。
 *
 * @author system
 */
public final class PromotionPowerThresholdConstant {

    /**
     * 单次 AI 推广执行最低算力门槛。
     */
    public static final int MIN_PROMOTION_EXECUTION_POWER = 2000;

    /**
     * AI 推广算力余额提前提醒门槛。
     */
    public static final int PROMOTION_POWER_WARNING_THRESHOLD = 20000;

    private PromotionPowerThresholdConstant() {
    }
}
