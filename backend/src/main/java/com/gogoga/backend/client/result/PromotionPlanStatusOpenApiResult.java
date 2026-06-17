package com.gogoga.backend.client.result;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 推广计划状态查询结果
 */
@Data
public class PromotionPlanStatusOpenApiResult {

    private boolean success;

    private Integer channel;

    private Integer status;

    private Integer bid;

    private BigDecimal budget;

    private BigDecimal budgetUsed;

    private BigDecimal availableBudget;

    private String payloadJson;

    private String errorMsg;

    private String responseCode;

    private String responseMsg;

    private String responseTraceId;

    private boolean authFailure;
}
