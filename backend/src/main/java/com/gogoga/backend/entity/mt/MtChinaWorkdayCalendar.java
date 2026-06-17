package com.gogoga.backend.entity.mt;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 中国工作日历
 */
@Data
public class MtChinaWorkdayCalendar {

    private Long id;

    private LocalDate calendarDate;

    private String dayType;

    private String holidayName;

    private Integer sourceYear;

    private String sourceTitle;

    private String sourceUrl;

    private Boolean enabled;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
