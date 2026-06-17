package com.gogoga.backend.entity.mt;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 门店高峰画像
 */
@Data
public class MtShopPeakWindowProfile {

    private Long id;

    private Long shopId;

    private String dayType;

    private String profileStatus;

    private Integer lookbackDays;

    private Integer sampleDayCount;

    private Integer sampleOrderCount;

    private String peakWindowsJson;

    private String unavailableReason;

    private LocalDateTime generatedAt;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
