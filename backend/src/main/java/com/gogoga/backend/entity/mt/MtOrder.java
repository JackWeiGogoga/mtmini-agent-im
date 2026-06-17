package com.gogoga.backend.entity.mt;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 美团外卖订单实体
 *
 * @author system
 * @since 2026-03-22
 */
@Data
public class MtOrder {

    /**
     * 主键 ID
     */
    private Long id;

    /**
     * 订单展示 ID
     */
    private String orderId;

    /**
     * 店铺 ID
     */
    private Long shopId;

    /**
     * ERP 店铺 ID
     */
    private String epoiId;

    /**
     * 门店 ID
     */
    private String poiId;

    /**
     * 活动信息
     */
    private String activity;

    /**
     * 备注
     */
    private String caution;

    /**
     * 流水单号
     */
    private String daySeq;

    /**
     * 用户预计送达时间
     */
    private Long deliveryTime;

    /**
     * 配送方式
     */
    private String logisticsCode;

    /**
     * 商品原价
     */
    private BigDecimal originalPrice;

    /**
     * 支付类型
     */
    private Integer payType;

    /**
     * 取餐类型
     */
    private Integer pickType;

    /**
     * 收货地址
     */
    private String recipientAddress;

    /**
     * 收货人姓名
     */
    private String recipientName;

    /**
     * 收货人手机号
     */
    private String recipientPhone;

    /**
     * 配送员电话
     */
    private String shipperPhone;

    /**
     * 配送费
     */
    private BigDecimal shippingFee;

    /**
     * 订单状态
     */
    private Integer status;

    /**
     * 订单金额
     */
    private BigDecimal total;

    /**
     * 商家对账信息
     */
    private String poiReceiveDetail;

    /**
     * 纬度
     */
    private String latitude;

    /**
     * 经度
     */
    private String longitude;

    /**
     * 用户下单时间
     */
    private Long orderSendTime;

    /**
     * 商户收到时间
     */
    private Long orderReceiveTime;

    /**
     * 商户确认时间
     */
    private Long orderConfirmTime;

    /**
     * 订单取消时间
     */
    private Long orderCancelTime;

    /**
     * 订单完成时间
     */
    private Long orderCompletedTime;

    /**
     * 配送单下单时间
     */
    private Long logisticsSendTime;

    /**
     * 配送单确认时间
     */
    private Long logisticsConfirmTime;

    /**
     * 配送单取消时间
     */
    private Long logisticsCancelTime;

    /**
     * 骑手取单时间
     */
    private Long logisticsFetchTime;

    /**
     * 配送完成时间
     */
    private Long logisticsCompletedTime;

    /**
     * 透传用户标识
     */
    private String userIdView;

    /**
     * 是否黑名单订单
     */
    private Integer isBlack;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 订单收入
     */
    private BigDecimal income;
}
