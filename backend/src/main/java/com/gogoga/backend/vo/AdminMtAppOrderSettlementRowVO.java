package com.gogoga.backend.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 后台美团订单结算统计行。
 */
@Data
public class AdminMtAppOrderSettlementRowVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long shopId;

    private String poiId;

    private String orderId;

    private LocalDateTime orderTime;

    private String shopName;

    private String phone;

    private String paidSpec;

    private Integer paidAmountFen;

    private BigDecimal paidAmount;

    private Integer currentMonthUsedDays;

    private Integer previousMonthUsedDays;

    private Integer remainingUnusedDays;

    private BigDecimal meituanDeductionAmount;

    private BigDecimal previousMonthActualIncome;

    private BigDecimal currentMonthActualIncome;

    private BigDecimal remainingUnsettledActualIncome;

    private BigDecimal actualIncome;

    private Integer payStatus;

    private String version;

    private String versionName;

    private Integer days;

    private LocalDateTime startTime;

    private LocalDateTime endTime;
}
