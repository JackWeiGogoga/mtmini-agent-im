package com.gogoga.backend.service.promotion;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 推广时段判断结果
 *
 * @author system
 * @since 2026-03-26
 */
@Data
@AllArgsConstructor
public class PromotionTimeWindowResult {

    /**
     * 是否允许执行
     */
    private boolean executable;

    /**
     * 判断原因
     */
    private String reason;

    /**
     * 执行窗口
     */
    private PromotionExecutionWindow executionWindow;
}
