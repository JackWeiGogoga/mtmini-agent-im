package com.gogoga.backend.entity.mt;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 门店推广配置历史
 */
@Data
public class MtPromotionSettingHistory {

    private Long id;

    private Long settingId;

    private Long shopId;

    private Integer versionNo;

    private Boolean promotionEnabled;

    private Boolean strategyConfigured;

    private String strategyConfigJson;

    private String strategyConfigHash;

    private String changeSource;

    private String operatorUserId;

    private String operatorName;

    private String operatorDisplayName;

    private String changeReason;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
