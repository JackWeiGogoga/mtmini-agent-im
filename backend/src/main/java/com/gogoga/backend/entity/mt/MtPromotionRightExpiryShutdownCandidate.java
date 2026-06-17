package com.gogoga.backend.entity.mt;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 功能权益过期关停候选门店。
 */
@Data
public class MtPromotionRightExpiryShutdownCandidate {

    private Long settingId;

    private Long shopId;

    private String shopName;

    private String poiId;

    private String accessToken;

    private LocalDateTime tokenExpiresAt;

    private Long accessTokenId;

    private String opBizCode;

    private Boolean orderPushBlocked;
}
