package com.gogoga.backend.entity.mt;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 门店推广出价约束
 */
@Data
public class MtPromotionBidConstraint {

    private Long id;

    private Long shopId;

    private String poiId;

    private BigDecimal maxBidAmount;

    private String source;

    private String rawMessage;

    private LocalDateTime observedAt;

    private LocalDateTime expireAt;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
