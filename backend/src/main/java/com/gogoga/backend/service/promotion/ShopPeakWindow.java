package com.gogoga.backend.service.promotion;

import lombok.Data;

import java.time.LocalTime;

/**
 * 高峰时间窗口
 */
@Data
public class ShopPeakWindow {

    private Integer startHour;

    private Integer endHour;

    private String startTime;

    private String endTime;

    private Integer orderCount;

    public boolean matches(LocalTime slotTime) {
        if (slotTime == null || startHour == null || endHour == null) {
            return false;
        }
        int hour = slotTime.getHour();
        return hour >= startHour && hour < endHour;
    }

    public String toDisplayText() {
        return startTime + "-" + endTime;
    }
}
