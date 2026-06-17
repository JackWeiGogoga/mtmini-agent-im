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

    private PromotionSummary promotion;

    private MarketingStatsSummary marketingStats;

    private PowerStatsSummary powerStats;

    private CurrentBidSummary currentBid;

    private MtH5TabStatusVO tabStatus;

    private GuideSummary guide;

    private MtH5AuthorizationStatusVO authorization;

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
    public static class PromotionSummary implements Serializable {

        private static final long serialVersionUID = 1L;

        private Boolean promotionEnabled;

        private Boolean balanceReminderEnabled;

        private Boolean strategyConfigured;

        private Boolean strategyEntryVisible;

        private String strategyConfigJson;

        private String promotionDisabledReasonText;

        private Boolean autoTimeModeAvailable;

        private String autoTimeModeHintText;
    }

    @Data
    public static class MarketingStatsSummary implements Serializable {

        private static final long serialVersionUID = 1L;

        private Integer currentExposure;

        private Integer previousExposure;

        private BigDecimal currentCost;

        private BigDecimal previousCost;

        private BigDecimal currentCostRatio;

        private BigDecimal previousCostRatio;

        private BigDecimal currentIncome;

        private BigDecimal previousIncome;
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
    public static class CurrentBidSummary implements Serializable {

        private static final long serialVersionUID = 1L;

        private Boolean inPromotionWindow;

        private Boolean editable;

        private String disabledReason;

        private BigDecimal currentBidAmount;

        private BigDecimal previousBidAmount;

        private String latestExecuteStatus;

        private String displayStatusText;

        private BigDecimal minBidAmount;

        private BigDecimal maxBidAmount;

        private String windowKey;

        private LocalDateTime windowStartAt;

        private LocalDateTime windowEndAt;

        private LocalDateTime latestSuccessSlotTime;
    }

    @Data
    public static class GuideSummary implements Serializable {

        private static final long serialVersionUID = 1L;

        private String homeGuideCode;

        private Boolean homeGuideCompleted;
    }
}
