package com.gogoga.backend.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * H5 手动修改当前推广出价结果
 *
 * @author system
 * @since 2026-05-11
 */
@Data
public class MtH5ManualPromotionBidVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private BigDecimal previousBidAmount;

    private BigDecimal effectiveBidAmount;

    private String windowKey;

    private LocalDateTime windowStartAt;

    private LocalDateTime windowEndAt;

    private LocalDateTime attributedSlotTime;

    private LocalDateTime operatedAt;

    private Boolean remoteSynced;

    private Long recordId;
}
