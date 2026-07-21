package com.gogoga.backend.service;

import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.entity.mt.MtAccessToken;
import com.gogoga.backend.enums.MtAccessTokenAuthStatusEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 群发的门店就绪校验。
 *
 * <p>群发与「一键开启AI回复」总开关相互独立，因此这里只校验群发总开关、门店权益与授权态。</p>
 *
 * <p>权益一律走 {@code hasBroadcastRight}：群发的权益范围比自动回复窄，
 * 只含自动回复的权益（如 IM_AUTO_REPLY_ONLY）会在这里被拒。</p>
 */
@Service
@RequiredArgsConstructor
public class MtImBroadcastReadinessService {

    public static final String REASON_GLOBAL_SWITCH_DISABLED = "GLOBAL_SWITCH_DISABLED";
    public static final String REASON_SHOP_RIGHT_MISSING = "SHOP_RIGHT_MISSING";
    public static final String REASON_AUTH_REQUIRED = "AUTH_REQUIRED";

    private final MtProperties mtProperties;
    private final MtShopRightService shopRightService;
    private final MtAccessTokenService accessTokenService;

    /**
     * @return 不就绪的原因码；就绪时返回 null
     */
    public String checkReadiness(Long shopId) {
        if (!mtProperties.getImBroadcast().isEnabled()) {
            return REASON_GLOBAL_SWITCH_DISABLED;
        }
        if (!shopRightService.hasBroadcastRight(shopId)) {
            return REASON_SHOP_RIGHT_MISSING;
        }
        MtAccessToken token = accessTokenService.findByShopId(shopId);
        if (token == null
                || Integer.valueOf(MtAccessTokenAuthStatusEnum.REAUTH_REQUIRED.getCode()).equals(token.getAuthStatus())) {
            return REASON_AUTH_REQUIRED;
        }
        return null;
    }
}
