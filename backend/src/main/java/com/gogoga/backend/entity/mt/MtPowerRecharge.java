package com.gogoga.backend.entity.mt;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 门店算力充值实体
 *
 * @author system
 * @since 2026-03-23
 */
@Data
public class MtPowerRecharge {

    /**
     * 主键 ID
     */
    private Long id;

    /**
     * 充值单号
     */
    private String rechargeNo;

    /**
     * 店铺 ID
     */
    private Long shopId;

    /**
     * 购买算力数量
     */
    private Integer powerAmount;

    /**
     * 价格单位对应算力
     */
    private Integer unitPower;

    /**
     * 价格单位金额（分）
     */
    private Integer unitPriceFen;

    /**
     * 原价金额（分）
     */
    private Integer originalAmountFen;

    /**
     * 冻结折扣上下文类型
     */
    private String discountContextType;

    /**
     * 冻结折扣标签
     */
    private String discountTag;

    /**
     * 冻结折扣比例，10000 表示原价
     */
    private Integer discountRateBasisPoints;

    /**
     * 订单总金额（分）
     */
    private Integer totalAmountFen;

    /**
     * 冻结兑换码ID
     */
    private Long redemptionCodeId;

    /**
     * 冻结兑换码
     */
    private String redemptionCode;

    /**
     * 冻结网销用户ID
     */
    private String salesUserId;

    /**
     * 冻结网销用户手机号
     */
    private String salesUserPhone;

    /**
     * 佣金默认比例窗口起始时间
     */
    private LocalDateTime commissionWindowStartAt;

    /**
     * 冻结默认佣金比例
     */
    private Integer defaultCommissionRatio;

    /**
     * 支付渠道
     */
    private String payChannel;

    /**
     * 支付状态
     */
    private Integer payStatus;

    /**
     * 支付侧订单号
     */
    private String paymentOrderNo;

    /**
     * 支付交易号
     */
    private String transactionId;

    /**
     * 支付下单返回内容
     */
    private String payPayload;

    /**
     * 支付通知内容
     */
    private String notifyPayload;

    /**
     * 支付成功时间
     */
    private LocalDateTime paidAt;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
