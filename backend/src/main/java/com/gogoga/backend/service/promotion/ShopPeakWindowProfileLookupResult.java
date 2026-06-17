package com.gogoga.backend.service.promotion;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 槽位高峰画像命中结果
 */
@Data
public class ShopPeakWindowProfileLookupResult {

    public static final String STATUS_USABLE = "USABLE";
    public static final String STATUS_UNAVAILABLE = "UNAVAILABLE";

    private ShopPeakDayType dayType;

    private ShopPeakDayType profileDayType;

    private ChinaWorkdayCalendarDayType calendarDayType;

    private String holidayName;

    private String profileStatus;

    private boolean peakMatched;

    private boolean gatingEnabled;

    private String matchedWindow;

    private Integer sampleDayCount;

    private Integer sampleOrderCount;

    private String unavailableReason;

    private LocalDateTime generatedAt;

    private List<ShopPeakWindow> peakWindows;
}
