package com.gogoga.backend.service.promotion;

import com.gogoga.backend.entity.mt.MtPromotionAutoBidCandidate;

import java.time.LocalDateTime;

/**
 * 推广指标提供器
 *
 * @author system
 * @since 2026-03-26
 */
public interface PromotionMetricsProvider {

    /**
     * 查询当前时段累计推广指标
     *
     * @param candidate 候选门店
     * @param executionWindow 当前执行窗口
     * @param slotTime 时段时间
     * @return 指标快照
     */
    PromotionMetricsSnapshot queryMetrics(
            MtPromotionAutoBidCandidate candidate,
            PromotionExecutionWindow executionWindow,
            LocalDateTime slotTime
    );
}
