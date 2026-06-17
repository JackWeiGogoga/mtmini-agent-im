package com.gogoga.backend.mapper;

import com.gogoga.backend.entity.mt.MtChinaWorkdayCalendar;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * 中国工作日历 Mapper
 */
@Mapper
public interface MtChinaWorkdayCalendarMapper {

    List<MtChinaWorkdayCalendar> selectEnabledByDateRange(
            @Param("startDate") LocalDate startDate,
            @Param("endDateExclusive") LocalDate endDateExclusive
    );

    int countEnabledBySourceYear(@Param("sourceYear") Integer sourceYear);
}
