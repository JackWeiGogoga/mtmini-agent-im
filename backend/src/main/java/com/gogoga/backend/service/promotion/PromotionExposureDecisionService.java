package com.gogoga.backend.service.promotion;

import com.gogoga.backend.entity.mt.MtPromotionAdRealtimeSnapshot;
import com.gogoga.backend.entity.mt.MtPromotionBidRecord;
import com.gogoga.backend.service.MtPromotionAdRealtimeSnapshotService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 推广曝光分支决策服务
 */
@Service
@RequiredArgsConstructor
public class PromotionExposureDecisionService {

    private static final BigDecimal GROWTH_THRESHOLD = new BigDecimal("0.20");

    private final MtPromotionAdRealtimeSnapshotService mtPromotionAdRealtimeSnapshotService;

    public PromotionExposureDecisionResult analyze(Long shopId, List<MtPromotionBidRecord> successHistory) {
        if (shopId == null) {
            return PromotionExposureDecisionResult.unavailable("SHOP_ID_MISSING");
        }
        List<MtPromotionBidRecord> normalizedHistory = normalizeHistory(successHistory);
        if (normalizedHistory.size() < 2) {
            return PromotionExposureDecisionResult.unavailable("SUCCESS_HISTORY_INSUFFICIENT");
        }

        MtPromotionBidRecord latestSuccess = normalizedHistory.get(0);
        MtPromotionBidRecord previousSuccess = normalizedHistory.get(1);
        ExposureObservation previousObservation = resolveObservation(shopId, previousSuccess, "PREVIOUS");
        if (!previousObservation.available()) {
            return PromotionExposureDecisionResult.unavailable(previousObservation.reason());
        }
        ExposureObservation latestObservation = resolveObservation(shopId, latestSuccess, "LATEST");
        if (!latestObservation.available()) {
            return PromotionExposureDecisionResult.unavailable(latestObservation.reason());
        }
        if (previousObservation.exposureCount() <= 0) {
            return PromotionExposureDecisionResult.unavailable("PREVIOUS_FOLLOWUP_EXPOSURE_ZERO");
        }

        BigDecimal growthRate = BigDecimal.valueOf(latestObservation.exposureCount() - previousObservation.exposureCount())
                .divide(BigDecimal.valueOf(previousObservation.exposureCount()), 6, RoundingMode.HALF_UP);
        if (growthRate.compareTo(GROWTH_THRESHOLD) >= 0) {
            return PromotionExposureDecisionResult.applied(
                    PromotionDecisionEngine.ACTION_KEEP,
                    "EXPOSURE_GROWTH_ABOVE_THRESHOLD",
                    previousObservation.exposureCount(),
                    latestObservation.exposureCount(),
                    growthRate
            );
        }
        return PromotionExposureDecisionResult.applied(
                PromotionDecisionEngine.ACTION_INCREASE,
                "EXPOSURE_GROWTH_BELOW_THRESHOLD",
                previousObservation.exposureCount(),
                latestObservation.exposureCount(),
                growthRate
        );
    }

    private List<MtPromotionBidRecord> normalizeHistory(List<MtPromotionBidRecord> successHistory) {
        if (CollectionUtils.isEmpty(successHistory)) {
            return List.of();
        }
        List<MtPromotionBidRecord> normalized = new ArrayList<>(successHistory);
        normalized.sort(Comparator
                .comparing(MtPromotionBidRecord::getSlotTime, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(MtPromotionBidRecord::getId, Comparator.nullsLast(Comparator.reverseOrder())));
        return normalized;
    }

    private ExposureObservation resolveObservation(Long shopId, MtPromotionBidRecord record, String prefix) {
        if (record == null || record.getSlotTime() == null) {
            return ExposureObservation.unavailable(prefix + "_SUCCESS_SLOT_TIME_MISSING");
        }
        LocalDateTime baselineSlotTime = record.getSlotTime();
        LocalDateTime followupSlotTime = baselineSlotTime.plusMinutes(10);
        if (!baselineSlotTime.toLocalDate().equals(followupSlotTime.toLocalDate())) {
            return ExposureObservation.unavailable(prefix + "_FOLLOWUP_CROSS_DAY");
        }

        MtPromotionAdRealtimeSnapshot baselineSnapshot = mtPromotionAdRealtimeSnapshotService.selectByShopIdAndBizDateAndSlotTime(
                shopId,
                baselineSlotTime.toLocalDate(),
                baselineSlotTime
        );
        if (baselineSnapshot == null || baselineSnapshot.getDayShowCount() == null) {
            return ExposureObservation.unavailable(prefix + "_BASELINE_SNAPSHOT_MISSING");
        }

        MtPromotionAdRealtimeSnapshot followupSnapshot = mtPromotionAdRealtimeSnapshotService.selectByShopIdAndBizDateAndSlotTime(
                shopId,
                followupSlotTime.toLocalDate(),
                followupSlotTime
        );
        if (followupSnapshot == null || followupSnapshot.getDayShowCount() == null) {
            return ExposureObservation.unavailable(prefix + "_FOLLOWUP_SNAPSHOT_MISSING");
        }

        int exposureCount = followupSnapshot.getDayShowCount() - baselineSnapshot.getDayShowCount();
        if (exposureCount < 0) {
            return ExposureObservation.unavailable(prefix + "_FOLLOWUP_EXPOSURE_NEGATIVE");
        }
        return ExposureObservation.available(exposureCount);
    }

    public Integer resolveFollowupExposureCount(Long shopId, MtPromotionBidRecord record) {
        ExposureObservation observation = resolveObservation(shopId, record, "HISTORY");
        return observation.available() ? observation.exposureCount() : null;
    }

    private record ExposureObservation(boolean available, String reason, int exposureCount) {

        private static ExposureObservation available(int exposureCount) {
            return new ExposureObservation(true, null, exposureCount);
        }

        private static ExposureObservation unavailable(String reason) {
            return new ExposureObservation(false, reason, 0);
        }
    }
}
