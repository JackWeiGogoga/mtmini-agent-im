package com.gogoga.backend.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * H5 首页聚合看板视图对象
 *
 * @author system
 * @since 2026-04-07
 */
@Data
public class MtH5HomeDashboardVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private ShopSummary shop;

    private MembershipSummary membership;

    private CommentStatsSummary commentStats;

    private PowerStatsSummary powerStats;

    private MtH5TabStatusVO tabStatus;

    private GuideSummary guide;

    private MtH5AuthorizationStatusVO authorization;

    private ShopOperationSettingsVO operationSettings;

    @Data
    public static class ShopSummary implements Serializable {

        private static final long serialVersionUID = 1L;

        private String shopName;

        private String poiId;

        private String epoiId;
    }

    @Data
    public static class MembershipSummary implements Serializable {

        private static final long serialVersionUID = 1L;

        private Boolean active;

        private LocalDateTime expireAt;
    }

    @Data
    public static class CommentStatsSummary implements Serializable {

        private static final long serialVersionUID = 1L;

        private Integer goodCommentCount;

        private Integer neutralCommentCount;

        private Integer badCommentCount;

        private Integer repliedBadCommentCount;

        private BigDecimal badCommentReplyRate;

        private Integer previousGoodCommentCount;

        private Integer previousBadCommentCount;

        private BigDecimal previousBadCommentReplyRate;
    }

    @Data
    public static class PowerStatsSummary implements Serializable {

        private static final long serialVersionUID = 1L;

        private Integer currentConsumedPower;

        private Integer previousConsumedPower;

        private Integer currentEstimatedPower;

        private Integer previousEstimatedPower;

        private Integer balance;

        private Integer totalRecharge;

        private Integer totalConsume;

        private Boolean insufficient;

        private String statusText;
    }

    @Data
    public static class GuideSummary implements Serializable {

        private static final long serialVersionUID = 1L;

        private String homeGuideCode;

        private Boolean homeGuideCompleted;
    }
}
