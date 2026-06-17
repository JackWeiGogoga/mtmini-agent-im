package com.gogoga.backend.service.promotion;

import com.gogoga.backend.entity.mt.MtChinaWorkdayCalendar;
import com.gogoga.backend.mapper.MtChinaWorkdayCalendarMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 中国工作日历服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChinaWorkdayCalendarService {

    private static final EnumSet<ChinaWorkdayCalendarDayType> SUPPORTED_DAY_TYPES =
            EnumSet.allOf(ChinaWorkdayCalendarDayType.class);

    private final MtChinaWorkdayCalendarMapper mtChinaWorkdayCalendarMapper;
    private final Map<Integer, Map<LocalDate, ChinaWorkdayCalendarDay>> cacheByYear = new ConcurrentHashMap<>();

    @PostConstruct
    public void warnIfCurrentYearCalendarMissing() {
        int currentYear = LocalDate.now().getYear();
        try {
            if (mtChinaWorkdayCalendarMapper.countEnabledBySourceYear(currentYear) == 0) {
                log.warn("中国工作日历当前年份无启用记录，year={}", currentYear);
            }
        } catch (Exception e) {
            log.warn("中国工作日历当前年份记录检查失败，year={}, error={}", currentYear, e.getMessage());
        }
    }

    public ChinaWorkdayCalendarDayType resolve(LocalDate date) {
        ChinaWorkdayCalendarDay calendarDay = resolveDay(date);
        return calendarDay == null ? null : calendarDay.dayType();
    }

    public ChinaWorkdayCalendarDay resolveDay(LocalDate date) {
        if (date == null) {
            return null;
        }
        return loadYear(date.getYear()).get(date);
    }

    private Map<LocalDate, ChinaWorkdayCalendarDay> loadYear(int year) {
        return cacheByYear.computeIfAbsent(year, this::loadYearFromDatabase);
    }

    private Map<LocalDate, ChinaWorkdayCalendarDay> loadYearFromDatabase(int year) {
        LocalDate startDate = LocalDate.of(year, 1, 1);
        LocalDate endDateExclusive = startDate.plusYears(1);
        List<MtChinaWorkdayCalendar> records;
        try {
            records = mtChinaWorkdayCalendarMapper.selectEnabledByDateRange(startDate, endDateExclusive);
        } catch (Exception e) {
            log.warn("加载中国工作日历失败，year={}, error={}", year, e.getMessage());
            return Map.of();
        }
        if (CollectionUtils.isEmpty(records)) {
            log.warn("中国工作日历年份无启用例外日记录，year={}", year);
            return Map.of();
        }

        Map<LocalDate, ChinaWorkdayCalendarDay> result = new HashMap<>();
        for (MtChinaWorkdayCalendar record : records) {
            ChinaWorkdayCalendarDayType dayType = parseDayType(record == null ? null : record.getDayType());
            LocalDate calendarDate = record == null ? null : record.getCalendarDate();
            if (calendarDate == null || dayType == null) {
                continue;
            }
            result.put(calendarDate, new ChinaWorkdayCalendarDay(dayType, record.getHolidayName()));
        }
        return Map.copyOf(result);
    }

    private ChinaWorkdayCalendarDayType parseDayType(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        try {
            ChinaWorkdayCalendarDayType dayType = ChinaWorkdayCalendarDayType.valueOf(value);
            return SUPPORTED_DAY_TYPES.contains(dayType) ? dayType : null;
        } catch (IllegalArgumentException e) {
            log.warn("中国工作日历存在不支持的日型，dayType={}", value);
            return null;
        }
    }

    public record ChinaWorkdayCalendarDay(
            ChinaWorkdayCalendarDayType dayType,
            String holidayName
    ) {
    }
}
