package com.gogoga.backend.vo;

import lombok.Data;

@Data
public class UserBehaviorAnalyticsRowVO {

    private Long shopId;

    private String poiId;

    private String shopName;

    private String shopPhone;

    private Long h5AppOpenCount;

    private Long pcMasterConsoleOperationCount;

    private Long h5PromotionSwitchClickCount;

    private Long h5ConfigButtonClickCount;

    private Long h5UnlockAiPeakClickCount;

    private Long h5ConfigModalConfirmClickCount;

    private Long h5ConfigModalCancelClickCount;

    private Long h5ManualBidEditOpenCount;

    private Long h5ManualBidSubmitClickCount;

    private Long h5PowerTabClickCount;

    private Long h5LogTabClickCount;

    private Long h5SupportTabClickCount;

    private Long h5RechargeGifClickCount;

    private Long h5PowerPayClickCount;

    private Long h5RedemptionCodeClickCount;

    private Long adminPromotionSwitchUpdateCount;

    private Long adminPromotionConfigSaveCount;

    private Long adminPromotionConfigCancelCount;

    private Long adminPromotionLogViewCount;
}
