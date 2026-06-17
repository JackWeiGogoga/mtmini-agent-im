package com.gogoga.backend.entity.mt;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 门店推广指标快照
 */
@Data
public class MtPromotionMetricSnapshot {

    private Long id;

    private Long shopId;

    private LocalDate bizDate;

    private LocalDateTime slotTime;

    private BigDecimal primaryBalance;

    private BigDecimal commonBalance;

    private BigDecimal promotionBalance;

    private BigDecimal costCounterValue;

    private Boolean metricsTrusted;

    private String untrustedReason;

    private String payloadJson;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
