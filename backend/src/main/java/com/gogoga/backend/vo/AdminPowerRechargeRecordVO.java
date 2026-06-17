package com.gogoga.backend.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 后台算力充值记录视图
 */
@Data
public class AdminPowerRechargeRecordVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String rechargeNo;

    private Long shopId;

    private Integer powerAmount;

    private Integer unitPower;

    private Integer unitPriceFen;

    private Integer totalAmountFen;

    private String payChannel;

    private Integer payStatus;

    private String payStatusDesc;

    private String paymentOrderNo;

    private String transactionId;

    private LocalDateTime paidAt;

    private LocalDateTime createTime;
}
