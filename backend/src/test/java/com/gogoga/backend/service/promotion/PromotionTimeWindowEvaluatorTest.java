package com.gogoga.backend.service.promotion;

import com.gogoga.backend.service.MtPromotionSettingService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 推广时段判断器测试
 *
 * @author system
 * @since 2026-03-26
 */
class PromotionTimeWindowEvaluatorTest {

    private final PromotionTimeWindowEvaluator promotionTimeWindowEvaluator = new PromotionTimeWindowEvaluator();

    @Test
    void shouldAllowExecutionInAutoModeUsingBusinessShippingTime() {
        PromotionTimeWindowResult result = promotionTimeWindowEvaluator.evaluate(
                buildStrategyConfig("auto", List.of()),
                "10:00-21:00",
                LocalDateTime.of(2026, 3, 26, 12, 0)
        );

        assertTrue(result.isExecutable());
        assertEquals(LocalDateTime.of(2026, 3, 26, 10, 0), result.getExecutionWindow().getWindowStartAt());
        assertEquals(LocalDateTime.of(2026, 3, 26, 21, 0), result.getExecutionWindow().getWindowEndAt());
    }

    @Test
    void shouldRejectAutoModeWhenBusinessShippingTimeInvalid() {
        PromotionTimeWindowResult result = promotionTimeWindowEvaluator.evaluate(
                buildStrategyConfig("auto", List.of()),
                null,
                LocalDateTime.of(2026, 3, 26, 12, 0)
        );

        assertFalse(result.isExecutable());
        assertEquals("INVALID_SHIPPING_TIME", result.getReason());
    }

    @Test
    void shouldRejectExecutionOutsideCustomSlots() {
        MtPromotionSettingService.StrategyTimeSlot slot = new MtPromotionSettingService.StrategyTimeSlot();
        slot.setStart("10:00");
        slot.setEnd("11:00");

        PromotionTimeWindowResult result = promotionTimeWindowEvaluator.evaluate(
                buildStrategyConfig("custom", List.of(slot)),
                null,
                LocalDateTime.of(2026, 3, 26, 9, 30)
        );

        assertFalse(result.isExecutable());
    }

    @Test
    void shouldAllowExecutionWithinCrossDayCustomSlot() {
        MtPromotionSettingService.StrategyTimeSlot slot = new MtPromotionSettingService.StrategyTimeSlot();
        slot.setStart("22:00");
        slot.setEnd("02:00");

        PromotionTimeWindowResult result = promotionTimeWindowEvaluator.evaluate(
                buildStrategyConfig("custom", List.of(slot)),
                null,
                LocalDateTime.of(2026, 3, 27, 1, 30)
        );

        assertTrue(result.isExecutable());
        assertEquals(LocalDateTime.of(2026, 3, 26, 22, 0), result.getExecutionWindow().getWindowStartAt());
        assertEquals(LocalDateTime.of(2026, 3, 27, 2, 0), result.getExecutionWindow().getWindowEndAt());
    }

    @Test
    void shouldAllowExecutionWithinBusinessShippingTime() {
        PromotionTimeWindowResult result = promotionTimeWindowEvaluator.evaluate(
                buildStrategyConfig("business", List.of()),
                "10:00-21:00",
                LocalDateTime.of(2026, 3, 26, 12, 0)
        );

        assertTrue(result.isExecutable());
        assertEquals(LocalDateTime.of(2026, 3, 26, 10, 0), result.getExecutionWindow().getWindowStartAt());
        assertEquals(LocalDateTime.of(2026, 3, 26, 21, 0), result.getExecutionWindow().getWindowEndAt());
    }

    @Test
    void shouldAllowExecutionWithinWeeklySemicolonBusinessShippingTime() {
        PromotionTimeWindowResult result = promotionTimeWindowEvaluator.evaluate(
                buildStrategyConfig("business", List.of()),
                "10:00-21:30;10:00-21:30;;10:00-21:30;10:00-21:30;10:00-21:30;10:00-21:30",
                LocalDateTime.of(2026, 3, 26, 12, 0)
        );

        assertTrue(result.isExecutable());
        assertEquals(LocalDateTime.of(2026, 3, 26, 10, 0), result.getExecutionWindow().getWindowStartAt());
        assertEquals(LocalDateTime.of(2026, 3, 26, 21, 30), result.getExecutionWindow().getWindowEndAt());
    }

    @Test
    void shouldTreatBlankWeeklyBusinessDayAsOutsideBusinessWindow() {
        PromotionTimeWindowResult result = promotionTimeWindowEvaluator.evaluate(
                buildStrategyConfig("business", List.of()),
                "10:00-21:30;10:00-21:30;;10:00-21:30;10:00-21:30;10:00-21:30;10:00-21:30",
                LocalDateTime.of(2026, 3, 25, 12, 0)
        );

        assertFalse(result.isExecutable());
        assertEquals("OUT_OF_BUSINESS_WINDOW", result.getReason());
    }

    @Test
    void shouldAllowExecutionWithinWeeklySplitCrossDayBusinessShippingTime() {
        String shippingTime = "00:00-04:00,17:00-24:00;00:00-04:00,17:00-24:00;"
                + "00:00-04:00,17:00-24:00;00:00-04:00,17:00-24:00;"
                + "00:00-04:00,17:00-24:00;00:00-04:00,10:50-24:00;"
                + "00:00-04:00,10:50-24:00";

        PromotionTimeWindowResult weekdayNight = promotionTimeWindowEvaluator.evaluate(
                buildStrategyConfig("business", List.of()),
                shippingTime,
                LocalDateTime.of(2026, 3, 26, 18, 0)
        );
        PromotionTimeWindowResult weekendDaytime = promotionTimeWindowEvaluator.evaluate(
                buildStrategyConfig("business", List.of()),
                shippingTime,
                LocalDateTime.of(2026, 3, 28, 11, 0)
        );
        PromotionTimeWindowResult earlyMorning = promotionTimeWindowEvaluator.evaluate(
                buildStrategyConfig("business", List.of()),
                shippingTime,
                LocalDateTime.of(2026, 3, 26, 1, 0)
        );

        assertTrue(weekdayNight.isExecutable());
        assertEquals(LocalDateTime.of(2026, 3, 26, 17, 0), weekdayNight.getExecutionWindow().getWindowStartAt());
        assertEquals(LocalDateTime.of(2026, 3, 27, 4, 0), weekdayNight.getExecutionWindow().getWindowEndAt());
        assertTrue(weekendDaytime.isExecutable());
        assertEquals(LocalDateTime.of(2026, 3, 28, 10, 50), weekendDaytime.getExecutionWindow().getWindowStartAt());
        assertEquals(LocalDateTime.of(2026, 3, 29, 4, 0), weekendDaytime.getExecutionWindow().getWindowEndAt());
        assertTrue(earlyMorning.isExecutable());
        assertEquals(LocalDateTime.of(2026, 3, 25, 17, 0), earlyMorning.getExecutionWindow().getWindowStartAt());
        assertEquals(LocalDateTime.of(2026, 3, 26, 4, 0), earlyMorning.getExecutionWindow().getWindowEndAt());
    }

    @Test
    void shouldAllowExecutionWithinCrossDayBusinessShippingTime() {
        PromotionTimeWindowResult result = promotionTimeWindowEvaluator.evaluate(
                buildStrategyConfig("business", List.of()),
                "22:00-02:00",
                LocalDateTime.of(2026, 3, 27, 1, 0)
        );

        assertTrue(result.isExecutable());
        assertEquals(LocalDateTime.of(2026, 3, 26, 22, 0), result.getExecutionWindow().getWindowStartAt());
        assertEquals(LocalDateTime.of(2026, 3, 27, 2, 0), result.getExecutionWindow().getWindowEndAt());
    }

    @Test
    void shouldAllowExecutionWithinSplitBusinessShippingTimeAtNight() {
        PromotionTimeWindowResult result = promotionTimeWindowEvaluator.evaluate(
                buildStrategyConfig("business", List.of()),
                "00:00-02:00,10:00-24:00",
                LocalDateTime.of(2026, 3, 27, 1, 0)
        );

        assertTrue(result.isExecutable());
        assertEquals(LocalDateTime.of(2026, 3, 26, 10, 0), result.getExecutionWindow().getWindowStartAt());
        assertEquals(LocalDateTime.of(2026, 3, 27, 2, 0), result.getExecutionWindow().getWindowEndAt());
    }

    @Test
    void shouldAllowExecutionWithinSplitBusinessShippingTimeInDaytime() {
        PromotionTimeWindowResult result = promotionTimeWindowEvaluator.evaluate(
                buildStrategyConfig("business", List.of()),
                "00:00-02:00,10:00-24:00",
                LocalDateTime.of(2026, 3, 27, 23, 50)
        );

        assertTrue(result.isExecutable());
        assertEquals(LocalDateTime.of(2026, 3, 27, 10, 0), result.getExecutionWindow().getWindowStartAt());
        assertEquals(LocalDateTime.of(2026, 3, 28, 2, 0), result.getExecutionWindow().getWindowEndAt());
    }

    @Test
    void shouldNotMergeSplitBusinessShippingTimeWhenCurrentDayHasGap() {
        PromotionTimeWindowResult result = promotionTimeWindowEvaluator.evaluate(
                buildStrategyConfig("business", List.of()),
                "00:00-02:00,05:00-06:00,10:00-24:00",
                LocalDateTime.of(2026, 3, 27, 1, 0)
        );

        assertTrue(result.isExecutable());
        assertEquals(LocalDateTime.of(2026, 3, 27, 0, 0), result.getExecutionWindow().getWindowStartAt());
        assertEquals(LocalDateTime.of(2026, 3, 27, 2, 0), result.getExecutionWindow().getWindowEndAt());
    }

    @Test
    void shouldRejectInvalidBusinessShippingTime() {
        PromotionTimeWindowResult result = promotionTimeWindowEvaluator.evaluate(
                buildStrategyConfig("business", List.of()),
                "10-21",
                LocalDateTime.of(2026, 3, 26, 12, 0)
        );

        assertFalse(result.isExecutable());
    }

    private MtPromotionSettingService.StrategyConfig buildStrategyConfig(
            String timeMode,
            List<MtPromotionSettingService.StrategyTimeSlot> customSlots
    ) {
        MtPromotionSettingService.StrategyConfig strategyConfig = new MtPromotionSettingService.StrategyConfig();
        strategyConfig.setTimeMode(timeMode);
        strategyConfig.setCustomSlots(customSlots);
        return strategyConfig;
    }
}
