package com.gogoga.backend.entity.mt;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 美团小应用订阅订单实体
 *
 * @author system
 * @since 2026-03-22
 */
@Data
public class MtAppOrder {

    /**
     * 主键 ID
     */
    private Long id;

    /**
     * 美团订单号
     */
    private String orderId;

    /**
     * 商家后台显示订单号
     */
    private String orderViewId;

    /**
     * 应用 ID
     */
    private String appId;

    /**
     * 商家标识
     */
    private String serviceCode;

    /**
     * 支付状态
     */
    private Integer payStatus;

    /**
     * 版本号
     */
    private String version;

    /**
     * 版本名称
     */
    private String versionName;

    /**
     * 购买天数
     */
    private Integer days;

    /**
     * 服务开始时间
     */
    private Long startTime;

    /**
     * 服务结束时间
     */
    private Long endTime;

    /**
     * 权益状态
     */
    private String rightStatus;

    /**
     * 实付金额
     */
    private Integer realPayPrice;

    /**
     * 下单时间
     */
    private Long orderTime;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 服务时长是否已累加
     */
    private Integer type;

    /**
     * 店铺地址
     */
    private String address;

    /**
     * 店铺名称
     */
    private String shopName;

    /**
     * 店铺电话
     */
    private String phone;

    /**
     * 店铺 ID
     */
    private Long shopId;
}
