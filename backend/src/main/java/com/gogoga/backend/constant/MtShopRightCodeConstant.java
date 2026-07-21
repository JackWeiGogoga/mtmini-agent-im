package com.gogoga.backend.constant;

/**
 * 门店权益编码常量
 *
 * @author system
 * @since 2026-03-25
 */
public final class MtShopRightCodeConstant {

    /**
     * 会员通用权益
     */
    public static final String VIP_CODE = "VIP_CODE";

    /**
     * H5 推广配置权益
     */
    public static final String H5_PROMOTION = "H5_PROMOTION";

    /**
     * 仅允许打开应用，不授予推广能力的美团规格权益
     */
    public static final String MT_APP_OPEN_ONLY = "MT_APP_OPEN_ONLY";

    /**
     * 只含 IM 自动回复（含知识库配置）、不含消息群发的权益
     *
     * <p>能力边界由 {@code MtShopRightService#hasAutoReplyRight} 与
     * {@code MtShopRightService#hasBroadcastRight} 两个方法界定，新增权益码时改那两处即可。</p>
     */
    public static final String IM_AUTO_REPLY_ONLY = "IM_AUTO_REPLY_ONLY";

    private MtShopRightCodeConstant() {
    }
}
