package com.gogoga.backend.service.promotion;

import com.gogoga.backend.entity.mt.MtOrderHourlyAggregate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 高峰窗口识别器
 */
@Component
public class ShopPeakWindowDetector {

    private static final BigDecimal CANDIDATE_HOUR_FACTOR = new BigDecimal("1.00");
    private static final int MIN_CANDIDATE_HOUR_ORDERS = 2;

    public List<ShopPeakWindow> detect(List<MtOrderHourlyAggregate> hourlyAggregates) {
        if (hourlyAggregates == null || hourlyAggregates.isEmpty()) {
            return List.of();
        }
        BigDecimal averageOrderCount = BigDecimal.valueOf(
                hourlyAggregates.stream()
                        .map(MtOrderHourlyAggregate::getOrderCount)
                        .filter(value -> value != null && value > 0)
                        .mapToInt(Integer::intValue)
                        .average()
                        .orElse(0D)
        );
        BigDecimal candidateThreshold = averageOrderCount.multiply(CANDIDATE_HOUR_FACTOR)
                .max(BigDecimal.valueOf(MIN_CANDIDATE_HOUR_ORDERS))
                .setScale(2, RoundingMode.HALF_UP);

        List<MtOrderHourlyAggregate> candidates = hourlyAggregates.stream()
                .filter(item -> item.getHourBucket() != null)
                .filter(item -> item.getOrderCount() != null)
                .filter(item -> BigDecimal.valueOf(item.getOrderCount()).compareTo(candidateThreshold) >= 0)
                .sorted(Comparator.comparing(MtOrderHourlyAggregate::getHourBucket))
                .toList();
        if (candidates.isEmpty()) {
            return List.of();
        }

        List<ShopPeakWindow> windows = new ArrayList<>();
        int startHour = candidates.get(0).getHourBucket();
        int lastHour = startHour;
        int orderCount = candidates.get(0).getOrderCount();
        for (int i = 1; i < candidates.size(); i++) {
            MtOrderHourlyAggregate current = candidates.get(i);
            if (current.getHourBucket() == lastHour + 1) {
                lastHour = current.getHourBucket();
                orderCount += current.getOrderCount();
                continue;
            }
            windows.add(buildWindow(startHour, lastHour + 1, orderCount));
            startHour = current.getHourBucket();
            lastHour = startHour;
            orderCount = current.getOrderCount();
        }
        windows.add(buildWindow(startHour, lastHour + 1, orderCount));
        return windows;
    }

    private ShopPeakWindow buildWindow(int startHour, int endHour, int orderCount) {
        ShopPeakWindow window = new ShopPeakWindow();
        window.setStartHour(startHour);
        window.setEndHour(endHour);
        window.setStartTime(formatHour(startHour));
        window.setEndTime(formatHour(endHour));
        window.setOrderCount(orderCount);
        return window;
    }

    private String formatHour(int hour) {
        int normalizedHour = Math.min(Math.max(hour, 0), 24);
        return String.format(Locale.ROOT, "%02d:00", normalizedHour);
    }
}
