package com.gogoga.backend.service.promotion;

import com.gogoga.backend.entity.mt.MtChinaWorkdayCalendar;
import com.gogoga.backend.mapper.MtChinaWorkdayCalendarMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChinaWorkdayCalendarServiceTest {

    @Mock
    private MtChinaWorkdayCalendarMapper mtChinaWorkdayCalendarMapper;

    @Test
    void shouldResolveHolidayAndAdjustedWorkdayFromCalendar() {
        when(mtChinaWorkdayCalendarMapper.selectEnabledByDateRange(
                eq(LocalDate.of(2026, 1, 1)),
                eq(LocalDate.of(2027, 1, 1))
        )).thenReturn(List.of(
                calendar(LocalDate.of(2026, 5, 1), "HOLIDAY"),
                calendar(LocalDate.of(2026, 5, 9), "ADJUSTED_WORKDAY")
        ));
        ChinaWorkdayCalendarService service = new ChinaWorkdayCalendarService(mtChinaWorkdayCalendarMapper);

        assertEquals(ChinaWorkdayCalendarDayType.HOLIDAY, service.resolve(LocalDate.of(2026, 5, 1)));
        assertEquals(ChinaWorkdayCalendarDayType.ADJUSTED_WORKDAY, service.resolve(LocalDate.of(2026, 5, 9)));
        assertEquals("ADJUSTED_WORKDAY", service.resolveDay(LocalDate.of(2026, 5, 9)).dayType().name());
        assertEquals("劳动节补班", service.resolveDay(LocalDate.of(2026, 5, 9)).holidayName());
    }

    @Test
    void shouldReturnNullForOrdinaryOrNullDate() {
        when(mtChinaWorkdayCalendarMapper.selectEnabledByDateRange(
                eq(LocalDate.of(2026, 1, 1)),
                eq(LocalDate.of(2027, 1, 1))
        )).thenReturn(List.of(calendar(LocalDate.of(2026, 10, 1), "HOLIDAY")));
        ChinaWorkdayCalendarService service = new ChinaWorkdayCalendarService(mtChinaWorkdayCalendarMapper);

        assertNull(service.resolve(null));
        assertNull(service.resolve(LocalDate.of(2026, 4, 15)));
    }

    @Test
    void shouldCacheLoadedYear() {
        when(mtChinaWorkdayCalendarMapper.selectEnabledByDateRange(
                eq(LocalDate.of(2026, 1, 1)),
                eq(LocalDate.of(2027, 1, 1))
        )).thenReturn(List.of(calendar(LocalDate.of(2026, 10, 1), "HOLIDAY")));
        ChinaWorkdayCalendarService service = new ChinaWorkdayCalendarService(mtChinaWorkdayCalendarMapper);

        assertEquals(ChinaWorkdayCalendarDayType.HOLIDAY, service.resolve(LocalDate.of(2026, 10, 1)));
        assertNull(service.resolve(LocalDate.of(2026, 10, 8)));

        verify(mtChinaWorkdayCalendarMapper, times(1)).selectEnabledByDateRange(
                eq(LocalDate.of(2026, 1, 1)),
                eq(LocalDate.of(2027, 1, 1))
        );
    }

    private MtChinaWorkdayCalendar calendar(LocalDate date, String dayType) {
        MtChinaWorkdayCalendar calendar = new MtChinaWorkdayCalendar();
        calendar.setCalendarDate(date);
        calendar.setDayType(dayType);
        if (LocalDate.of(2026, 5, 9).equals(date)) {
            calendar.setHolidayName("劳动节补班");
        }
        calendar.setEnabled(true);
        return calendar;
    }
}
