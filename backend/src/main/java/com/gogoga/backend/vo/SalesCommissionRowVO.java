package com.gogoga.backend.vo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SalesCommissionRowVO {

    private Long id;

    private String redemptionCode;

    private String rechargeNo;

    private LocalDateTime paidAt;

    private String shopName;

    private String poiId;

    private String shopPhone;

    private Integer powerAmount;

    private Integer paidAmountFen;

    private Integer commissionRatio;

    private Integer commissionAmountFen;

    private String salesUserId;

    private String salesUserName;

    private String salesUserPhone;

    private String receiptCodeUrl;

    @JsonIgnore
    private String receiptCodeObjectKey;

    private String settlementStatus;

    private LocalDateTime settledAt;

    private LocalDateTime createTime;
}
