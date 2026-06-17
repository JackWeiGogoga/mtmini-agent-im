package com.gogoga.backend.service.promotion;

import lombok.Data;

/**
 * 推广计划状态更新结果
 */
@Data
public class PromotionPlanStatusUpdateResult {

    private boolean success;

    private Integer channel;

    private Integer status;

    private String payloadJson;

    private String errorMsg;

    private String responseCode;

    private String responseMsg;

    private String responseTraceId;

    private boolean authFailure;
}
