package com.gogoga.backend.param;

import lombok.Data;

/**
 * H5 推广配置更新参数
 *
 * @author system
 * @since 2026-03-24
 */
@Data
public class MtH5PromotionSettingsUpdateParam {

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
}
