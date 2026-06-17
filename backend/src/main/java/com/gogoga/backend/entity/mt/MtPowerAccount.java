package com.gogoga.backend.entity.mt;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 门店算力账户实体
 *
 * @author system
 * @since 2026-03-23
 */
@Data
public class MtPowerAccount {

    /**
     * 主键 ID
     */
    private Long id;

    /**
     * 店铺 ID
     */
    private Long shopId;

    /**
     * 可用算力余额
     */
    private Integer balance;

    /**
     * 实充剩余算力
     */
    private Integer rechargeBalance;

    /**
     * 赠送剩余算力
     */
    private Integer grantBalance;

    /**
     * 累计充值算力
     */
    private Integer totalRecharge;

    /**
     * 累计赠送算力
     */
    private Integer totalGrant;

    /**
     * 累计消费算力
     */
    private Integer totalConsume;

    /**
     * 版本号
     */
    private Integer version;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
