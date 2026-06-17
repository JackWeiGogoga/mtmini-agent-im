package com.gogoga.backend.client.result;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 附近商家平均出价查询结果
 */
@Data
public class PeerBidPriceOpenApiResult {

    private boolean success;

    private Integer channel;

    private BigDecimal averageBidAmount;

    private String payloadJson;

    private String errorMsg;
}
