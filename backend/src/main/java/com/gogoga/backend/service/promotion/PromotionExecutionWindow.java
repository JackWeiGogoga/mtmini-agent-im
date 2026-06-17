package com.gogoga.backend.service.promotion;

import lombok.Data;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 推广执行窗口
 *
 * @author system
 * @since 2026-03-30
 */
@Data
public class PromotionExecutionWindow {

    private static final DateTimeFormatter WINDOW_KEY_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    /**
     * 窗口开始时间
     */
    private LocalDateTime windowStartAt;

    /**
     * 窗口结束时间
     */
    private LocalDateTime windowEndAt;

    /**
     * 窗口唯一标识
     */
    private String windowKey;

    /**
     * 创建执行窗口
     *
     * @param windowStartAt 窗口开始时间
     * @param windowEndAt 窗口结束时间
     * @return 执行窗口
     */
    public static PromotionExecutionWindow of(LocalDateTime windowStartAt, LocalDateTime windowEndAt) {
        PromotionExecutionWindow window = new PromotionExecutionWindow();
        window.setWindowStartAt(windowStartAt);
        window.setWindowEndAt(windowEndAt);
        window.setWindowKey(buildWindowKey(windowStartAt, windowEndAt));
        return window;
    }

    private static String buildWindowKey(LocalDateTime windowStartAt, LocalDateTime windowEndAt) {
        return WINDOW_KEY_FORMATTER.format(windowStartAt) + "-" + WINDOW_KEY_FORMATTER.format(windowEndAt);
    }
}
