package com.gogoga.backend.entity.youzan;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 有赞订单权益核销记录实体
 */
@Data
public class YzOrderRightUseRecord {

    private Long id;

    private Long yzAppOrderId;

    private String orderNo;

    private Long mtShopId;

    private String rightCode;

    private Integer intervalDays;

    private LocalDateTime previousExpireAt;

    private LocalDateTime newExpireAt;

    private String operatorUserId;

    private String operatorUsername;

    private String remark;

    private String status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
