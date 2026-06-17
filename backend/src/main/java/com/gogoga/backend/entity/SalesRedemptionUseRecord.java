package com.gogoga.backend.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SalesRedemptionUseRecord {

    private Long id;

    private Long rechargeId;

    private String rechargeNo;

    private Long shopId;

    private String poiId;

    private String shopName;

    private String salesUserId;

    private String salesUserPhone;

    private Long redemptionCodeId;

    private String redemptionCode;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
