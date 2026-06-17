package com.gogoga.backend.enums;

import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
public enum UserBehaviorStatsRangeEnum {

    TODAY("TODAY", 1),
    LAST_7_DAYS("LAST_7_DAYS", 7),
    LAST_30_DAYS("LAST_30_DAYS", 30);

    private final String value;
    private final int days;

    UserBehaviorStatsRangeEnum(String value, int days) {
        this.value = value;
        this.days = days;
    }

    public static UserBehaviorStatsRangeEnum fromValue(String value) {
        for (UserBehaviorStatsRangeEnum item : values()) {
            if (StringUtils.equalsIgnoreCase(item.value, value)) {
                return item;
            }
        }
        return TODAY;
    }

    public LocalDateTime startAt(LocalDate today) {
        return today.minusDays(days - 1L).atStartOfDay();
    }

    public LocalDateTime endAt(LocalDate today) {
        return today.plusDays(1).atStartOfDay();
    }
}
