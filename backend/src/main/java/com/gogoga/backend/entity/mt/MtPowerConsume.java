package com.gogoga.backend.entity.mt;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 门店算力消费实体
 *
 * @author system
 * @since 2026-03-23
 */
@Data
public class MtPowerConsume {

    /**
     * 主键 ID
     */
    private Long id;

    /**
     * 消费流水号
     */
    private String consumeNo;

    /**
     * 店铺 ID
     */
    private Long shopId;

    /**
     * 消费来源类型
     */
    private String sourceType;

    /**
     * 消费来源 ID
     */
    private String sourceId;

    /**
     * 幂等请求号
     */
    private String requestNo;

    /**
     * 消费算力数量
     */
    private Integer powerAmount;

    /**
     * 消费前余额
     */
    private Integer balanceBefore;

    /**
     * 消费后余额
     */
    private Integer balanceAfter;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
