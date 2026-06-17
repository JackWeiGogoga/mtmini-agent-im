package com.gogoga.backend.vo;

import com.gogoga.backend.entity.mt.MtAsyncTask;
import com.gogoga.backend.entity.mt.MtPowerConsume;
import com.gogoga.backend.entity.mt.MtPromotionAdRealtimeSnapshot;
import com.gogoga.backend.entity.mt.MtPromotionMetricSnapshot;
import com.gogoga.backend.entity.mt.MtPromotionSettingHistory;
import com.gogoga.backend.entity.mt.MtShopPeakWindowProfile;
import com.gogoga.backend.entity.mt.MtShopRight;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 后台定价分析详情。
 */
@Data
public class AdminPricingAnalysisDetailVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private RecordSummary recordSummary;
    private ShopContext shopContext;
    private StrategyContext strategyContext;
    private MetricsContext metricsContext;
    private PeakProfileContext peakProfileContext;
    private StatusCheckContext statusCheckContext;
    private BidConstraintContext bidConstraintContext;
    private DecisionContext decisionContext;
    private ExecutionContext executionContext;
    private RawPayloads rawPayloads;

    @Data
    public static class RecordSummary implements Serializable {
        private Long id;
        private Long shopId;
        private LocalDateTime slotTime;
        private String recordSource;
        private LocalDateTime attributedSlotTime;
        private LocalDateTime operatedAt;
        private String windowKey;
        private LocalDateTime windowStartAt;
        private LocalDateTime windowEndAt;
        private String executeStatus;
        private String decisionAction;
        private String decisionReason;
        private String decisionReasonText;
        private BigDecimal previousBidAmount;
        private BigDecimal nextBidAmount;
        private BigDecimal totalCost;
        private BigDecimal totalIncome;
        private BigDecimal totalCostRatio;
        private BigDecimal costDelta;
        private BigDecimal costCounterValue;
        private String requestNo;
        private String errorMsg;
        private String markdownMessage;
        private String logMarkdownStatus;
        private Long logTaskId;
        private LocalDateTime createTime;
    }

    @Data
    public static class ShopContext implements Serializable {
        private Long shopId;
        private String poiId;
        private String epoiId;
        private String shopName;
        private String address;
        private String shippingTime;
        private Boolean hasAccessToken;
        private LocalDateTime tokenExpiresAt;
        private Boolean tokenExpired;
        private Integer authStatus;
        private String authStatusText;
        private Boolean reauthRequired;
        private LocalDateTime authStatusChangedAt;
        private LocalDateTime lastAuthFailureAt;
        private List<MtShopRight> rights;
    }

    @Data
    public static class StrategyContext implements Serializable {
        private String executionStrategyConfigJson;
        private String executionStrategyConfigHash;
        private Boolean executionStrategySnapshotMissing;
        private String currentStrategyConfigJson;
        private String currentStrategyConfigHash;
        private Boolean currentPromotionEnabled;
        private Boolean currentStrategyConfigured;
        private Boolean sameAsCurrent;
        private List<MtPromotionSettingHistory> histories;
    }

    @Data
    public static class MetricsContext implements Serializable {
        private MtPromotionMetricSnapshot metricSnapshot;
        private MtPromotionAdRealtimeSnapshot realtimeSnapshot;
        private Integer windowOrderCount;
        private BigDecimal windowIncome;
        private Integer currentHourOrderCount;
        private BigDecimal currentHourIncome;
    }

    @Data
    public static class PeakProfileContext implements Serializable {
        private PeakProfileSnapshot snapshot;
        private List<MtShopPeakWindowProfile> profiles;
    }

    @Data
    public static class PeakProfileSnapshot implements Serializable {
        private String dayType;
        private String profileDayType;
        private String calendarDayType;
        private String holidayName;
        private String profileStatus;
        private Boolean gatingEnabled;
        private Boolean peakMatched;
        private String matchedRange;
        private Integer sampleDayCount;
        private Integer sampleOrderCount;
        private String unavailableReason;
        private String peakBidPhase;
        private String peakWindowKey;
        private String nextPeakWindowKey;
    }

    @Data
    public static class DecisionContext implements Serializable {
        private String decisionMode;
        private String decisionSource;
        private String pricingBranch;
        private String feeRateBranchAction;
        private String feeRateBranchReason;
        private String exposureBranchStatus;
        private String exposureBranchAction;
        private String exposureBranchReason;
        private String finalAggregationMode;
        private String finalAggregationSource;
        private String peakBidPhase;
        private String peakWindowKey;
        private String nextPeakWindowKey;
        private String peakWindowDayType;
        private String peakWindowProfileDayType;
        private Boolean peakWindowMatched;
        private Boolean peakWindowGatingEnabled;
        private String peakWindowMatchedRange;
        private String peakWindowProfileStatus;
        private Integer peakWindowSampleDayCount;
        private Integer peakWindowSampleOrderCount;
        private String peakWindowUnavailableReason;
        private BigDecimal lowPeakBaselineBidAmount;
        private String lowPeakBaselineSource;
        private BigDecimal peakEntryBidAmount;
        private String peakEntryBidSource;
        private Integer slotsUntilPeakStart;
        private Integer slotsSincePeakEnd;
        private BigDecimal peakRampTargetBidAmount;
        private String peakRampReason;
        private BigDecimal peerAverageBidAmount;
        private LocalDateTime metricsEffectiveWindowStartAt;
        private LocalDateTime metricsBusinessWindowStartAt;
        private String metricsBaselineType;
        private Boolean temporaryRecoveryWindow;
        private java.time.LocalDate recentOrderStatisticBizDate;
        private java.time.LocalDate recentOrderLookbackStartDate;
        private java.time.LocalDate recentOrderLookbackEndDate;
        private List<java.time.LocalDate> recentOrderSampleDates;
        private Boolean recentOrderStatisticTrusted;
        private String recentOrderStatisticUntrustedReason;
        private Integer recentOrderTotalOrderCount;
        private BigDecimal recentOrderAverageDailyOrderCount;
        private Boolean highVolumeRampDownEligible;
        private BigDecimal peakRampDecreaseStepAmount;
        private Integer lowPeakOrderCount;
        private BigDecimal lowPeakOrderShare;
        private BigDecimal lowPeakShareBaselineBidAmount;
        private BigDecimal latestHighPeakBidAmount;
        private String latestHighPeakBidSource;
        private Boolean lowPeakShareBaselineApplied;
        private Boolean ratePriorityFastCorrectionApplied;
        private BigDecimal ratePriorityCurrentRate;
        private BigDecimal ratePriorityMaxRate;
        private BigDecimal ratePriorityFastThreshold;
        private BigDecimal ratePriorityFastDecreaseStepAmount;
        private Boolean ratePriorityRecoveredToMainAlgorithm;
        private Boolean exposureWeakGrowthRollbackApplied;
        private Integer exposureWeakGrowthConsecutiveCount;
        private String exposureWeakGrowthRollbackUnavailableReason;
        private LocalDateTime exposureWeakGrowthBestSlotTime;
        private Integer exposureWeakGrowthBestFollowupExposure;
        private BigDecimal exposureWeakGrowthBestPreviousBidAmount;
        private BigDecimal exposureWeakGrowthRollbackBidAmount;
        private BigDecimal userBidMinAmount;
        private BigDecimal userBidMaxAmount;
        private BigDecimal effectiveMaxBidAmount;
        private BigDecimal bidBeforeUserRangeClamp;
        private BigDecimal bidAfterUserRangeClamp;
    }

    @Data
    public static class StatusCheckContext implements Serializable {
        private Integer status;
        private Integer bid;
        private BigDecimal budget;
        private BigDecimal budgetUsed;
        private BigDecimal availableBudget;
        private String responseCode;
        private String responseMsg;
        private String responseDataErrMsg;
    }

    @Data
    public static class BidConstraintContext implements Serializable {
        private BigDecimal globalMaxBidAmount;
        private BigDecimal shopMaxBidAmount;
        private BigDecimal effectiveMaxBidAmount;
        private Boolean active;
        private String source;
        private String rawMessage;
        private LocalDateTime observedAt;
        private LocalDateTime expireAt;
    }

    @Data
    public static class ExecutionContext implements Serializable {
        private MtAsyncTask autoBidTask;
        private MtAsyncTask logTask;
        private List<MtPowerConsume> powerConsumes;
    }

    @Data
    public static class RawPayloads implements Serializable {
        private String metricsPayloadJson;
        private String metricSnapshotPayloadJson;
        private String realtimeSnapshotPayloadJson;
        private String updatePayloadJson;
        private String decisionDetailJson;
    }
}
