package com.gogoga.backend.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class AdminPowerRechargeOrderRowVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String rechargeNo;

    private Long shopId;

    private String poiId;

    private String epoiId;

    private String shopName;

    private Integer powerAmount;

    private Integer unitPower;

    private Integer unitPriceFen;

    private Integer originalAmountFen;

    private Integer totalAmountFen;

    private String discountContextType;

    private String discountTag;

    private Integer discountRateBasisPoints;

    private String redemptionCode;

    private String salesUserId;

    private String salesUserName;

    private String salesUserPhone;

    private LocalDateTime commissionWindowStartAt;

    private Integer defaultCommissionRatio;

    private String payChannel;

    private Integer payStatus;

    private String payStatusDesc;

    private String paymentOrderNo;

    private String transactionId;

    private LocalDateTime paidAt;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
