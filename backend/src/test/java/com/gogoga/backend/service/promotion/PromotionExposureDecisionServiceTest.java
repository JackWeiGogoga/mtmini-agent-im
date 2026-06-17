package com.gogoga.backend.service.promotion;

import com.gogoga.backend.entity.mt.MtPromotionAdRealtimeSnapshot;
import com.gogoga.backend.entity.mt.MtPromotionBidRecord;
import com.gogoga.backend.service.MtPromotionAdRealtimeSnapshotService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PromotionExposureDecisionServiceTest {

    private final MtPromotionAdRealtimeSnapshotService snapshotService = mock(MtPromotionAdRealtimeSnapshotService.class);
    private final PromotionExposureDecisionService service = new PromotionExposureDecisionService(snapshotService);

    @Test
    void shouldReturnUnavailableWhenSuccessHistoryIsInsufficient() {
        PromotionExposureDecisionResult result = service.analyze(1L, List.of(buildHistory(LocalDateTime.of(2026, 4, 8, 12, 20))));

        assertEquals(PromotionExposureDecisionResult.STATUS_UNAVAILABLE, result.getStatus());
        assertEquals("SUCCESS_HISTORY_INSUFFICIENT", result.getReason());
    }

    @Test
    void shouldReturnUnavailableWhenFollowupSnapshotIsMissing() {
        LocalDateTime previousSlot = LocalDateTime.of(2026, 4, 8, 12, 10);
        LocalDateTime latestSlot = LocalDateTime.of(2026, 4, 8, 12, 20);
        when(snapshotService.selectByShopIdAndBizDateAndSlotTime(eq(1L), eq(previousSlot.toLocalDate()), eq(previousSlot)))
                .thenReturn(buildSnapshot(previousSlot, 100));
        when(snapshotService.selectByShopIdAndBizDateAndSlotTime(eq(1L), eq(previousSlot.plusMinutes(10).toLocalDate()), eq(previousSlot.plusMinutes(10))))
                .thenReturn(buildSnapshot(previousSlot.plusMinutes(10), 110));
        when(snapshotService.selectByShopIdAndBizDateAndSlotTime(eq(1L), eq(latestSlot.toLocalDate()), eq(latestSlot)))
                .thenReturn(buildSnapshot(latestSlot, 110));
        when(snapshotService.selectByShopIdAndBizDateAndSlotTime(eq(1L), eq(latestSlot.plusMinutes(10).toLocalDate()), eq(latestSlot.plusMinutes(10))))
                .thenReturn(null);

        PromotionExposureDecisionResult result = service.analyze(1L, List.of(
                buildHistory(latestSlot),
                buildHistory(previousSlot)
        ));

        assertEquals(PromotionExposureDecisionResult.STATUS_UNAVAILABLE, result.getStatus());
        assertEquals("LATEST_FOLLOWUP_SNAPSHOT_MISSING", result.getReason());
    }

    @Test
    void shouldReturnUnavailableWhenPreviousFollowupExposureIsZero() {
        LocalDateTime previousSlot = LocalDateTime.of(2026, 4, 8, 12, 10);
        LocalDateTime latestSlot = LocalDateTime.of(2026, 4, 8, 12, 20);
        stubExposure(previousSlot, 100, 100);
        stubExposure(latestSlot, 100, 120);

        PromotionExposureDecisionResult result = service.analyze(1L, List.of(
                buildHistory(latestSlot),
                buildHistory(previousSlot)
        ));

        assertEquals(PromotionExposureDecisionResult.STATUS_UNAVAILABLE, result.getStatus());
        assertEquals("PREVIOUS_FOLLOWUP_EXPOSURE_ZERO", result.getReason());
    }

    @Test
    void shouldKeepBidWhenExposureGrowthIsAtThreshold() {
        LocalDateTime previousSlot = LocalDateTime.of(2026, 4, 8, 12, 10);
        LocalDateTime latestSlot = LocalDateTime.of(2026, 4, 8, 12, 20);
        stubExposure(previousSlot, 100, 110);
        stubExposure(latestSlot, 110, 122);

        PromotionExposureDecisionResult result = service.analyze(1L, List.of(
                buildHistory(latestSlot),
                buildHistory(previousSlot)
        ));

        assertEquals(PromotionExposureDecisionResult.STATUS_APPLIED, result.getStatus());
        assertEquals(PromotionDecisionEngine.ACTION_KEEP, result.getAction());
        assertEquals("EXPOSURE_GROWTH_ABOVE_THRESHOLD", result.getReason());
        assertEquals(10, result.getPreviousFollowupExposure());
        assertEquals(12, result.getLatestFollowupExposure());
        assertEquals(new BigDecimal("0.200000"), result.getGrowthRate());
    }

    @Test
    void shouldIncreaseBidWhenExposureGrowthIsBelowThreshold() {
        LocalDateTime previousSlot = LocalDateTime.of(2026, 4, 8, 12, 10);
        LocalDateTime latestSlot = LocalDateTime.of(2026, 4, 8, 12, 20);
        stubExposure(previousSlot, 100, 110);
        stubExposure(latestSlot, 110, 121);

        PromotionExposureDecisionResult result = service.analyze(1L, List.of(
                buildHistory(latestSlot),
                buildHistory(previousSlot)
        ));

        assertEquals(PromotionExposureDecisionResult.STATUS_APPLIED, result.getStatus());
        assertEquals(PromotionDecisionEngine.ACTION_INCREASE, result.getAction());
        assertEquals("EXPOSURE_GROWTH_BELOW_THRESHOLD", result.getReason());
        assertEquals(new BigDecimal("0.100000"), result.getGrowthRate());
    }

    @Test
    void shouldReturnUnavailableWhenFollowupCrossesDayBoundary() {
        LocalDateTime previousSlot = LocalDateTime.of(2026, 4, 8, 23, 40);
        LocalDateTime latestSlot = LocalDateTime.of(2026, 4, 8, 23, 50);
        stubExposure(previousSlot, 100, 110);

        PromotionExposureDecisionResult result = service.analyze(1L, List.of(
                buildHistory(latestSlot),
                buildHistory(previousSlot)
        ));

        assertEquals(PromotionExposureDecisionResult.STATUS_UNAVAILABLE, result.getStatus());
        assertEquals("LATEST_FOLLOWUP_CROSS_DAY", result.getReason());
    }

    private void stubExposure(LocalDateTime slotTime, int baselineExposure, int followupExposure) {
        when(snapshotService.selectByShopIdAndBizDateAndSlotTime(eq(1L), eq(slotTime.toLocalDate()), eq(slotTime)))
                .thenReturn(buildSnapshot(slotTime, baselineExposure));
        when(snapshotService.selectByShopIdAndBizDateAndSlotTime(eq(1L), eq(slotTime.plusMinutes(10).toLocalDate()), eq(slotTime.plusMinutes(10))))
                .thenReturn(buildSnapshot(slotTime.plusMinutes(10), followupExposure));
    }

    private MtPromotionBidRecord buildHistory(LocalDateTime slotTime) {
        MtPromotionBidRecord record = new MtPromotionBidRecord();
        record.setSlotTime(slotTime);
        return record;
    }

    private MtPromotionAdRealtimeSnapshot buildSnapshot(LocalDateTime slotTime, int dayShowCount) {
        MtPromotionAdRealtimeSnapshot snapshot = new MtPromotionAdRealtimeSnapshot();
        snapshot.setSlotTime(slotTime);
        snapshot.setBizDate(slotTime.toLocalDate());
        snapshot.setDayShowCount(dayShowCount);
        return snapshot;
    }
}
