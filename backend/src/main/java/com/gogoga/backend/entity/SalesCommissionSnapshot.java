package com.gogoga.backend.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SalesCommissionSnapshot {

    private Long id;

    private Long rechargeId;

    private String rechargeNo;

    private Long shopId;

    private String poiId;

    private String shopName;

    private String shopPhone;

    private Integer powerAmount;

    private Integer paidAmountFen;

    private Integer commissionRatio;

    private Integer commissionAmountFen;

    private String salesUserId;

    private String salesUserPhone;

    private String redemptionCode;

    private String settlementStatus;

    private LocalDateTime settledAt;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
