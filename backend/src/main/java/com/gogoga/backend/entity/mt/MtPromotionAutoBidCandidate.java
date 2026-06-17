package com.gogoga.backend.entity.mt;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 自动调价候选门店
 *
 * @author system
 * @since 2026-03-26
 */
@Data
public class MtPromotionAutoBidCandidate {

    /**
     * 推广配置 ID
     */
    private Long settingId;

    /**
     * 门店 ID
     */
    private Long shopId;

    /**
     * 门店名称
     */
    private String shopName;

    /**
     * 门店 poiId
     */
    private String poiId;

    /**
     * 营业时间
     */
    private String shippingTime;

    /**
     * 推广策略 JSON
     */
    private String strategyConfigJson;

    /**
     * 授权 access token
     */
    private String accessToken;

    /**
     * token 过期时间
     */
    private LocalDateTime tokenExpiresAt;
}
