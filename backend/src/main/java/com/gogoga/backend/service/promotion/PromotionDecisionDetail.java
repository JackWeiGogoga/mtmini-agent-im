package com.gogoga.backend.service.promotion;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 调价决策扩展详情
 */
@Data
public class PromotionDecisionDetail {

    private String decisionMode;

    private String decisionSource;

    private List<String> llmPricingReasons;

    private List<String> llmRiskFlags;

    private Double llmConfidence;

    private String llmFailureSummary;

    private String llmRawResponseSummary;

    private Long llmTotalTokens;

    private String logMarkdownSource;

    private String logLlmFailureSummary;

    private String logLlmRawResponseSummary;

    private Long logLlmPromptTokens;

    private Long logLlmCompletionTokens;

    private Long logLlmTotalTokens;

    private String logPowerConsumeFailureSummary;

    private String metricsBaselineType;

    private LocalDateTime metricsEffectiveWindowStartAt;

    private LocalDateTime metricsBusinessWindowStartAt;

    private Boolean temporaryRecoveryWindow;

    private Boolean recoveryWindowInitialization;

    private BigDecimal peerAverageBidAmount;

    private String peerAverageBidPayloadJson;

    private String peerAverageBidErrorMsg;

    private String feeRateBranchAction;

    private String feeRateBranchReason;

    private String exposureBranchStatus;

    private String exposureBranchAction;

    private String exposureBranchReason;

    private Integer exposurePreviousFollowupExposure;

    private Integer exposureLatestFollowupExposure;

    private BigDecimal exposureGrowthRate;

    private String finalAggregationMode;

    private String finalAggregationSource;

    private Boolean exposureWeakGrowthRollbackApplied;

    private Integer exposureWeakGrowthConsecutiveCount;

    private String exposureWeakGrowthRollbackUnavailableReason;

    private LocalDateTime exposureWeakGrowthBestSlotTime;

    private Integer exposureWeakGrowthBestFollowupExposure;

    private BigDecimal exposureWeakGrowthBestPreviousBidAmount;

    private BigDecimal exposureWeakGrowthRollbackBidAmount;

    private String peakWindowDayType;

    private String peakWindowProfileDayType;

    private String peakWindowCalendarDayType;

    private String peakWindowHolidayName;

    private Boolean peakWindowMatched;

    private Boolean peakWindowGatingEnabled;

    private String peakWindowMatchedRange;

    private String peakWindowProfileStatus;

    private Integer peakWindowSampleDayCount;

    private Integer peakWindowSampleOrderCount;

    private String peakWindowUnavailableReason;

    private BigDecimal windowBaselineBidAmount;

    private String pricingBranch;

    private BigDecimal userBidMinAmount;

    private BigDecimal userBidMaxAmount;

    private BigDecimal effectiveMaxBidAmount;

    private BigDecimal bidBeforeUserRangeClamp;

    private BigDecimal bidAfterUserRangeClamp;

    private String strategyConfigHash;

    private String strategyConfigSnapshotJson;

    private String peakBidPhase;

    private String peakWindowKey;

    private String nextPeakWindowKey;

    private BigDecimal lowPeakBaselineBidAmount;

    private String lowPeakBaselineSource;

    private BigDecimal peakEntryBidAmount;

    private String peakEntryBidSource;

    private Integer slotsUntilPeakStart;

    private Integer slotsSincePeakEnd;

    private BigDecimal peakRampTargetBidAmount;

    private String peakRampReason;

    private LocalDate recentOrderStatisticBizDate;

    private LocalDate recentOrderLookbackStartDate;

    private LocalDate recentOrderLookbackEndDate;

    private List<LocalDate> recentOrderSampleDates;

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

    private String manualBidSource;

    private LocalDateTime manualBidOperatedAt;

    private LocalDateTime manualBidAttributedSlotTime;
}
