package com.gogoga.backend.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 后台有赞订单核销结果
 */
@Data
public class AdminYouzanOrderRedeemVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long recordId;

    private Long orderId;

    private String orderNo;

    private Long mtShopId;

    private String mtShopName;

    private String rightCode;

    private Integer intervalDays;

    private LocalDateTime previousExpireAt;

    private LocalDateTime newExpireAt;

    private LocalDateTime usedAt;
}
