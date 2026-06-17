package com.gogoga.backend.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 后台定价分析列表行。
 */
@Data
public class AdminPricingAnalysisRowVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long shopId;
    private String poiId;
    private String epoiId;
    private String shopName;
    private LocalDate bizDate;
    private LocalDateTime slotTime;
    private String windowKey;
    private LocalDateTime windowStartAt;
    private LocalDateTime windowEndAt;
    private BigDecimal previousBidAmount;
    private BigDecimal nextBidAmount;
    private String decisionAction;
    private String decisionReason;
    private String decisionReasonText;
    private BigDecimal totalCost;
    private BigDecimal totalIncome;
    private BigDecimal totalCostRatio;
    private BigDecimal costDelta;
    private BigDecimal costCounterValue;
    private String executeStatus;
    private String logMarkdownStatus;
    private Long logTaskId;
    private String errorMsg;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
