package com.gogoga.backend.service.promotion;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 推广指标快照
 *
 * @author system
 * @since 2026-03-26
 */
@Data
public class PromotionMetricsSnapshot {

    /**
     * 当前广告余额
     */
    private BigDecimal promotionBalance;

    /**
     * 广告累计花费计数器
     */
    private BigDecimal costCounterValue;

    /**
     * 当前执行窗口累计花费
     */
    private BigDecimal windowCost;

    /**
     * 当前执行窗口累计实收
     */
    private BigDecimal windowIncome;

    /**
     * 当前执行窗口累计费用率
     */
    private BigDecimal windowCostRatio;

    /**
     * 当前槽位花费增量
     */
    private BigDecimal slotCostDelta;

    /**
     * 当前窗口指标是否可信
     */
    private boolean metricsTrusted;

    /**
     * 指标不可信原因
     */
    private String untrustedReason;

    /**
     * 指标原始内容
     */
    private String payloadJson;

    /**
     * 当前累计指标采用的基线类型
     */
    private String baselineType;

    /**
     * 当前累计指标实际采用的窗口开始时间
     */
    private LocalDateTime effectiveWindowStartAt;

    /**
     * 原始业务窗口开始时间
     */
    private LocalDateTime businessWindowStartAt;

    /**
     * 是否使用临时恢复窗口
     */
    private boolean temporaryRecoveryWindow;

    /**
     * 当前槽位是否仅用于初始化临时恢复窗口
     */
    private boolean recoveryWindowInitializationRequired;
}
