package com.gogoga.backend.entity.mt;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 门店推广配置实体
 *
 * @author system
 * @since 2026-03-24
 */
@Data
public class MtPromotionSetting {

    /**
     * 主键 ID
     */
    private Long id;

    /**
     * 店铺 ID
     */
    private Long shopId;

    /**
     * 是否开启推广
     */
    private Boolean promotionEnabled;

    /**
     * 是否开启余额提醒
     */
    private Boolean balanceReminderEnabled;

    /**
     * 策略配置 JSON
     */
    private String strategyConfigJson;

    /**
     * 是否已配置策略
     */
    private Boolean strategyConfigured;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
