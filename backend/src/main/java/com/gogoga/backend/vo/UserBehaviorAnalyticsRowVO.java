package com.gogoga.backend.vo;

import lombok.Data;

@Data
public class UserBehaviorAnalyticsRowVO {

    private Long shopId;

    private String poiId;

    private String shopName;

    private String shopPhone;

    private Long h5AppOpenCount;

    private Long h5HomePageViewCount;

    private Long h5PowerPageViewCount;

    private Long h5KnowledgePageViewCount;

    private Long h5SupportPageViewCount;

    private Long h5SupportEntryClickCount;

    private Long h5ExternalLinkClickCount;

    private Long h5RechargeSubmitClickCount;

    private Long h5RedemptionApplySuccessCount;

    private Long h5KnowledgeActionCount;

    private Long pcKnowledgeViewCount;

    private Long h5AutoReplyChangeCount;

    private Long pcAutoReplyChangeCount;

    private Long systemAutoDisableCount;

}
