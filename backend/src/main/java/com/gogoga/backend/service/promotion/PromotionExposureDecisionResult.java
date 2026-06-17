package com.gogoga.backend.service.promotion;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 曝光分支决策结果
 */
@Data
public class PromotionExposureDecisionResult {

    public static final String STATUS_NOT_APPLICABLE = "NOT_APPLICABLE";
    public static final String STATUS_SKIPPED_BY_RATE_CEILING = "SKIPPED_BY_RATE_CEILING";
    public static final String STATUS_UNAVAILABLE = "UNAVAILABLE";
    public static final String STATUS_APPLIED = "APPLIED";

    private String status;

    private String reason;

    private String action;

    private Integer previousFollowupExposure;

    private Integer latestFollowupExposure;

    private BigDecimal growthRate;

    public static PromotionExposureDecisionResult notApplicable(String reason) {
        PromotionExposureDecisionResult result = new PromotionExposureDecisionResult();
        result.setStatus(STATUS_NOT_APPLICABLE);
        result.setReason(reason);
        return result;
    }

    public static PromotionExposureDecisionResult skippedByRateCeiling(String reason) {
        PromotionExposureDecisionResult result = new PromotionExposureDecisionResult();
        result.setStatus(STATUS_SKIPPED_BY_RATE_CEILING);
        result.setReason(reason);
        return result;
    }

    public static PromotionExposureDecisionResult unavailable(String reason) {
        PromotionExposureDecisionResult result = new PromotionExposureDecisionResult();
        result.setStatus(STATUS_UNAVAILABLE);
        result.setReason(reason);
        return result;
    }

    public static PromotionExposureDecisionResult applied(
            String action,
            String reason,
            Integer previousFollowupExposure,
            Integer latestFollowupExposure,
            BigDecimal growthRate
    ) {
        PromotionExposureDecisionResult result = new PromotionExposureDecisionResult();
        result.setStatus(STATUS_APPLIED);
        result.setAction(action);
        result.setReason(reason);
        result.setPreviousFollowupExposure(previousFollowupExposure);
        result.setLatestFollowupExposure(latestFollowupExposure);
        result.setGrowthRate(growthRate);
        return result;
    }

    public boolean isApplied() {
        return STATUS_APPLIED.equals(status);
    }
}
