package com.gogoga.backend.entity.mt;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 门店权益实体
 *
 * @author system
 * @since 2026-03-25
 */
@Data
public class MtShopRight {

    /**
     * 主键 ID
     */
    private Long id;

    /**
     * 门店 ID
     */
    private Long shopId;

    /**
     * 权益编码
     */
    private String serviceCode;

    /**
     * 权益类型
     */
    private Integer type;

    /**
     * 到期时间
     */
    private LocalDateTime expireAt;

    /**
     * 剩余次数
     */
    private Long remainingTimes;

    /**
     * 来源版本
     */
    private String sourceVersion;

    /**
     * 最近一次应用的订单号
     */
    private String lastOrderId;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
