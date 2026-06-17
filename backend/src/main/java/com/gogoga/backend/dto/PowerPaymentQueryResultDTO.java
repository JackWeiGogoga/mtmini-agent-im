package com.gogoga.backend.dto;

import lombok.Data;

/**
 * 支付查询结果
 *
 * @author system
 * @since 2026-03-23
 */
@Data
public class PowerPaymentQueryResultDTO {

    /**
     * 充值单号
     */
    private String rechargeNo;

    /**
     * 支付侧订单号
     */
    private String paymentOrderNo;

    /**
     * 支付平台交易号
     */
    private String transactionId;

    /**
     * 微信应用 ID
     */
    private String appId;

    /**
     * 微信商户号
     */
    private String mchId;

    /**
     * 交易类型
     */
    private String tradeType;

    /**
     * 微信交易状态
     */
    private String tradeState;

    /**
     * 订单总金额（分）
     */
    private Integer totalAmountFen;

    /**
     * 用户支付金额（分）
     */
    private Integer payerAmountFen;

    /**
     * 订单币种
     */
    private String currency;

    /**
     * 用户支付币种
     */
    private String payerCurrency;

    /**
     * 支付状态
     */
    private Integer payStatus;

    /**
     * 是否支付成功
     */
    private boolean success;

    /**
     * 是否终态
     */
    private boolean finished;

    /**
     * 原始响应内容
     */
    private String rawPayload;
}
