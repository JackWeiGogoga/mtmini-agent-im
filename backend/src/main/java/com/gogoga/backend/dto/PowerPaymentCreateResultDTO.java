package com.gogoga.backend.dto;

import lombok.Data;

/**
 * 支付下单结果
 *
 * @author system
 * @since 2026-03-23
 */
@Data
public class PowerPaymentCreateResultDTO {

    /**
     * 是否 mock 支付
     */
    private boolean mockEnabled;

    /**
     * 支付渠道
     */
    private String payChannel;

    /**
     * 支付侧订单号
     */
    private String paymentOrderNo;

    /**
     * 微信 H5 跳转地址
     */
    private String mwebUrl;

    /**
     * 原始返回内容
     */
    private String rawPayload;
}
