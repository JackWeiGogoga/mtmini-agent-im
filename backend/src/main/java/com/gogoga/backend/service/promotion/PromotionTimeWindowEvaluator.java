package com.gogoga.backend.service.promotion;

import com.gogoga.backend.service.MtPromotionSettingService;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;

/**
 * 推广执行时段判断器
 *
 * @author system
 * @since 2026-03-26
 */
@Component
public class PromotionTimeWindowEvaluator {

    /**
     * 判断指定时段是否允许执行
     *
     * @param strategyConfig 策略配置
     * @param shippingTime 营业时间
     * @param slotTime 时段时间
     * @return 判断结果
     */
    public PromotionTimeWindowResult evaluate(
            MtPromotionSettingService.StrategyConfig strategyConfig,
            String shippingTime,
            LocalDateTime slotTime
    ) {
        if (strategyConfig == null || StringUtils.isBlank(strategyConfig.getTimeMode())) {
            return new PromotionTimeWindowResult(false, "INVALID_STRATEGY_TIME_MODE", null);
        }

        String timeMode = strategyConfig.getTimeMode();
        if ("auto".equals(timeMode)) {
            return evaluateBusinessTime(shippingTime, slotTime);
        }
        if ("custom".equals(timeMode)) {
            return evaluateCustomSlots(strategyConfig, slotTime);
        }
        if ("business".equals(timeMode)) {
            return evaluateBusinessTime(shippingTime, slotTime);
        }
        return new PromotionTimeWindowResult(false, "UNSUPPORTED_TIME_MODE", null);
    }

    private PromotionTimeWindowResult evaluateCustomSlots(
            MtPromotionSettingService.StrategyConfig strategyConfig,
            LocalDateTime slotTime
    ) {
        if (CollectionUtils.isEmpty(strategyConfig.getCustomSlots())) {
            return new PromotionTimeWindowResult(false, "EMPTY_CUSTOM_SLOTS", null);
        }

        for (MtPromotionSettingService.StrategyTimeSlot slot : strategyConfig.getCustomSlots()) {
            LocalTime start = parseTime(slot == null ? null : slot.getStart());
            LocalTime end = parseTime(slot == null ? null : slot.getEnd());
            PromotionExecutionWindow executionWindow = resolveWindow(start, end, slotTime);
            if (executionWindow == null) {
                continue;
            }
            return new PromotionTimeWindowResult(true, "CUSTOM_SLOT_MATCHED", executionWindow);
        }
        return new PromotionTimeWindowResult(false, "OUT_OF_CUSTOM_WINDOW", null);
    }

    private PromotionTimeWindowResult evaluateBusinessTime(String shippingTime, LocalDateTime slotTime) {
        if (StringUtils.isBlank(shippingTime)) {
            return new PromotionTimeWindowResult(false, "INVALID_SHIPPING_TIME", null);
        }

        if (isWeeklyClosedDay(shippingTime, slotTime)) {
            return new PromotionTimeWindowResult(false, "OUT_OF_BUSINESS_WINDOW", null);
        }

        List<TimeRange> timeRanges = parseBusinessTimeRanges(shippingTime, slotTime);
        if (timeRanges.isEmpty()) {
            return new PromotionTimeWindowResult(false, "INVALID_SHIPPING_TIME", null);
        }

        for (TimeRange timeRange : timeRanges) {
            PromotionExecutionWindow executionWindow = resolveWindow(timeRange, slotTime);
            if (executionWindow != null) {
                return new PromotionTimeWindowResult(true, "BUSINESS_TIME_MATCHED", executionWindow);
            }
        }
        return new PromotionTimeWindowResult(false, "OUT_OF_BUSINESS_WINDOW", null);
    }

    private List<TimeRange> parseBusinessTimeRanges(String shippingTime, LocalDateTime slotTime) {
        String normalized = StringUtils.deleteWhitespace(shippingTime);
        if (StringUtils.contains(normalized, ';')) {
            return parseWeeklyBusinessTimeRanges(normalized, slotTime);
        }
        return parseBusinessTimeRangesFromSegment(normalized);
    }

    private List<TimeRange> parseWeeklyBusinessTimeRanges(String normalized, LocalDateTime slotTime) {
        String[] daySegments = StringUtils.splitPreserveAllTokens(normalized, ';');
        if (daySegments == null || daySegments.length == 0) {
            return List.of();
        }

        if (daySegments.length == 7 && slotTime != null) {
            int dayIndex = slotTime.getDayOfWeek().getValue() - 1;
            return parseBusinessTimeRangesFromSegment(daySegments[dayIndex]);
        }

        List<TimeRange> parsedRanges = new ArrayList<>();
        for (String daySegment : daySegments) {
            parsedRanges.addAll(parseBusinessTimeRangesFromSegment(daySegment));
        }
        return normalizeBusinessTimeRanges(parsedRanges);
    }

    private boolean isWeeklyClosedDay(String shippingTime, LocalDateTime slotTime) {
        String normalized = StringUtils.deleteWhitespace(shippingTime);
        if (!StringUtils.contains(normalized, ';') || slotTime == null) {
            return false;
        }
        String[] daySegments = StringUtils.splitPreserveAllTokens(normalized, ';');
        if (daySegments == null || daySegments.length != 7) {
            return false;
        }
        int dayIndex = slotTime.getDayOfWeek().getValue() - 1;
        return StringUtils.isBlank(daySegments[dayIndex]);
    }

    private List<TimeRange> parseBusinessTimeRangesFromSegment(String segment) {
        String[] ranges = StringUtils.split(segment, ",，");
        if (ranges == null || ranges.length == 0) {
            return List.of();
        }

        List<TimeRange> parsedRanges = new ArrayList<>();
        for (String range : ranges) {
            TimeRange timeRange = parseBusinessTimeRange(range);
            if (timeRange != null) {
                parsedRanges.add(timeRange);
            }
        }
        if (parsedRanges.isEmpty()) {
            return List.of();
        }
        return normalizeBusinessTimeRanges(parsedRanges);
    }

    private List<TimeRange> normalizeBusinessTimeRanges(List<TimeRange> parsedRanges) {
        if (parsedRanges.size() != 2) {
            return parsedRanges;
        }

        TimeRange first = parsedRanges.get(0);
        TimeRange last = parsedRanges.get(parsedRanges.size() - 1);
        if (!LocalTime.MIDNIGHT.equals(first.start()) || !last.endOfDay()) {
            return parsedRanges;
        }

        List<TimeRange> normalizedRanges = new ArrayList<>(parsedRanges);
        normalizedRanges.set(0, new TimeRange(last.start(), first.end(), false));
        normalizedRanges.remove(normalizedRanges.size() - 1);
        return normalizedRanges;
    }

    private TimeRange parseBusinessTimeRange(String range) {
        String[] parts = StringUtils.split(range, '-');
        if (parts == null || parts.length != 2) {
            return null;
        }

        LocalTime start = parseTime(parts[0]);
        LocalTime end = parseEndTime(parts[1]);
        if (start == null || end == null || (start.equals(end) && !isEndOfDay(parts[1]))) {
            return null;
        }
        return new TimeRange(start, end, isEndOfDay(parts[1]));
    }

    private PromotionExecutionWindow resolveWindow(LocalTime start, LocalTime end, LocalDateTime slotTime) {
        if (start == null || end == null || start.equals(end) || slotTime == null) {
            return null;
        }

        if (start.isBefore(end)) {
            LocalDateTime windowStartAt = slotTime.toLocalDate().atTime(start);
            LocalDateTime windowEndAt = slotTime.toLocalDate().atTime(end);
            if (!slotTime.isBefore(windowStartAt) && slotTime.isBefore(windowEndAt)) {
                return PromotionExecutionWindow.of(windowStartAt, windowEndAt);
            }
            return null;
        }

        LocalTime currentTime = slotTime.toLocalTime();
        if (!currentTime.isBefore(start)) {
            return PromotionExecutionWindow.of(
                    slotTime.toLocalDate().atTime(start),
                    slotTime.toLocalDate().plusDays(1L).atTime(end)
            );
        }
        if (currentTime.isBefore(end)) {
            return PromotionExecutionWindow.of(
                    slotTime.toLocalDate().minusDays(1L).atTime(start),
                    slotTime.toLocalDate().atTime(end)
            );
        }
        return null;
    }

    private PromotionExecutionWindow resolveWindow(TimeRange range, LocalDateTime slotTime) {
        if (range == null || slotTime == null) {
            return null;
        }
        if (range.endOfDay()) {
            LocalDateTime windowStartAt = slotTime.toLocalDate().atTime(range.start());
            LocalDateTime windowEndAt = slotTime.toLocalDate().plusDays(1L).atStartOfDay();
            if (!slotTime.isBefore(windowStartAt) && slotTime.isBefore(windowEndAt)) {
                return PromotionExecutionWindow.of(windowStartAt, windowEndAt);
            }
            return null;
        }
        return resolveWindow(range.start(), range.end(), slotTime);
    }

    private LocalTime parseTime(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        try {
            return LocalTime.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private LocalTime parseEndTime(String value) {
        if (isEndOfDay(value)) {
            return LocalTime.MIDNIGHT;
        }
        return parseTime(value);
    }

    private boolean isEndOfDay(String value) {
        return StringUtils.equals(StringUtils.trimToNull(value), "24:00");
    }

    private record TimeRange(LocalTime start, LocalTime end, boolean endOfDay) {
    }
}
