package com.gogoga.backend.entity.mt;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 门店核心定价最近订单统计。
 */
@Data
public class MtPromotionRecentOrderStatistic {

    private Long id;

    private Long shopId;

    private LocalDate bizDate;

    private LocalDate lookbackStartDate;

    private LocalDate lookbackEndDate;

    private String sampleDatesJson;

    private Boolean trusted;

    private String untrustedReason;

    private Integer totalOrderCount;

    private BigDecimal averageDailyOrderCount;

    private Integer lowPeakOrderCount;

    private BigDecimal lowPeakOrderShare;

    private String peakWindowProfileDayType;

    private LocalDateTime peakWindowProfileGeneratedAt;

    private String peakWindowsJson;

    private LocalDateTime generatedAt;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
