package com.gogoga.backend.enums;

/**
 * 看板统计周期枚举。
 */
public enum DashboardStatsRangeTypeEnum {

    TODAY(1),
    LAST_7_DAYS(7),
    LAST_30_DAYS(30),
    ;

    private final int days;

    DashboardStatsRangeTypeEnum(int days) {
        this.days = days;
    }

    public int getDays() {
        return days;
    }

    public static DashboardStatsRangeTypeEnum fromValue(String value) {
        if (value == null || value.isBlank()) {
            return LAST_7_DAYS;
        }
        for (DashboardStatsRangeTypeEnum item : values()) {
            if (item.name().equalsIgnoreCase(value.trim())) {
                return item;
            }
        }
        return LAST_7_DAYS;
    }
}
