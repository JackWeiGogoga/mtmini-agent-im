package com.gogoga.backend.entity.mt;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 门店推广期日统计预聚合。
 */
@Data
public class MtPromotionPeriodMetricDaily {

    private Long id;

    private Long shopId;

    private LocalDate bizDate;

    private Integer showCount;

    private Long debitCents;

    private BigDecimal income;

    private Integer orderCount;

    private String sourceHash;

    private LocalDateTime calculatedAt;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
