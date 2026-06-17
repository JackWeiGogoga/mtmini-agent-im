package com.gogoga.backend.service.promotion;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShopPeakWindowDayTypeResolverTest {

    private final ShopPeakWindowDayTypeResolver resolver = new ShopPeakWindowDayTypeResolver();

    @Test
    void shouldResolveWeekday() {
        assertEquals(ShopPeakDayType.WEEKDAY, resolver.resolve(LocalDate.of(2026, 4, 15)));
    }

    @Test
    void shouldResolveWeekend() {
        assertEquals(ShopPeakDayType.WEEKEND, resolver.resolve(LocalDate.of(2026, 4, 18)));
    }

    @Test
    void shouldResolveConfiguredHolidayBeforeNaturalWeekday() {
        ShopPeakWindowDayTypeResolver resolver = new ShopPeakWindowDayTypeResolver(calendarService(
                LocalDate.of(2026, 5, 1),
                ChinaWorkdayCalendarDayType.HOLIDAY
        ));

        assertEquals(ShopPeakDayType.HOLIDAY, resolver.resolve(LocalDate.of(2026, 5, 1)));
    }

    @Test
    void shouldResolveAdjustedWorkdayBeforeNaturalWeekend() {
        ShopPeakWindowDayTypeResolver resolver = new ShopPeakWindowDayTypeResolver(calendarService(
                LocalDate.of(2026, 5, 9),
                ChinaWorkdayCalendarDayType.ADJUSTED_WORKDAY
        ));

        assertEquals(ShopPeakDayType.WEEKDAY, resolver.resolve(LocalDate.of(2026, 5, 9)));
    }

    private ChinaWorkdayCalendarService calendarService(
            LocalDate configuredDate,
            ChinaWorkdayCalendarDayType configuredDayType
    ) {
        return new ChinaWorkdayCalendarService(null) {
            @Override
            public ChinaWorkdayCalendarDay resolveDay(LocalDate date) {
                return configuredDate.equals(date)
                        ? new ChinaWorkdayCalendarDay(configuredDayType, null)
                        : null;
            }
        };
    }
}
