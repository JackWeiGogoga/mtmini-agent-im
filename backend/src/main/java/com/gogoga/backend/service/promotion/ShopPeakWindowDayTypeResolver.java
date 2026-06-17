package com.gogoga.backend.service.promotion;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 高峰画像日型判定
 */
@Component
public class ShopPeakWindowDayTypeResolver {

    private final ChinaWorkdayCalendarService chinaWorkdayCalendarService;

    public ShopPeakWindowDayTypeResolver() {
        this(null);
    }

    @Autowired
    public ShopPeakWindowDayTypeResolver(ChinaWorkdayCalendarService chinaWorkdayCalendarService) {
        this.chinaWorkdayCalendarService = chinaWorkdayCalendarService;
    }

    public ShopPeakDayType resolve(LocalDateTime slotTime) {
        return slotTime == null ? ShopPeakDayType.WEEKDAY : resolve(slotTime.toLocalDate());
    }

    public ShopPeakDayType resolve(LocalDate date) {
        if (date == null) {
            return ShopPeakDayType.WEEKDAY;
        }
        ChinaWorkdayCalendarDayType calendarDayType = resolveCalendarDayType(date);
        if (calendarDayType == ChinaWorkdayCalendarDayType.HOLIDAY) {
            return ShopPeakDayType.HOLIDAY;
        }
        if (calendarDayType == ChinaWorkdayCalendarDayType.ADJUSTED_WORKDAY) {
            return ShopPeakDayType.WEEKDAY;
        }
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
            return ShopPeakDayType.WEEKEND;
        }
        return ShopPeakDayType.WEEKDAY;
    }

    public ChinaWorkdayCalendarDayType resolveCalendarDayType(LocalDate date) {
        ChinaWorkdayCalendarService.ChinaWorkdayCalendarDay calendarDay = resolveCalendarDay(date);
        return calendarDay == null ? null : calendarDay.dayType();
    }

    public ChinaWorkdayCalendarService.ChinaWorkdayCalendarDay resolveCalendarDay(LocalDate date) {
        return date == null || chinaWorkdayCalendarService == null
                ? null
                : chinaWorkdayCalendarService.resolveDay(date);
    }
}
