package com.gogoga.backend.client.result;

import lombok.Data;

/**
 * 推广计划出价更新结果
 */
@Data
public class PromotionBidOpenApiResult {

    private boolean success;

    private Integer channel;

    private Integer bid;

    private String payloadJson;

    private String errorMsg;
}
