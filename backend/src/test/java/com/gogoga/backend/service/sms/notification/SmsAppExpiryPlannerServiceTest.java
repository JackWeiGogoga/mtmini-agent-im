package com.gogoga.backend.service.sms.notification;

import com.gogoga.backend.config.SmsProperties;
import com.gogoga.backend.entity.sms.SmsAppExpiryCandidate;
import com.gogoga.backend.mapper.MtShopRightMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SmsAppExpiryPlannerServiceTest {

    @Test
    void shouldPlanFutureActiveRightForNextCalendarDayAtTenInConfiguredZone() {
        MtShopRightMapper mapper = mock(MtShopRightMapper.class);
        MerchantLifecycleSmsService lifecycle = mock(MerchantLifecycleSmsService.class);
        SmsProperties properties = new SmsProperties();
        properties.getBusiness().setPlannerBatchSize(200);
        SmsAppExpiryCandidate candidate = new SmsAppExpiryCandidate();
        candidate.setShopId(10L);
        candidate.setExpectedExpireAt(LocalDateTime.of(2999, 8, 1, 23, 59, 59));
        when(mapper.selectActiveAppExpiryCandidatesByCursor(any(), eq(0L), eq(200)))
                .thenReturn(List.of(candidate));
        SmsNotificationEventEnqueueResult enqueueResult = new SmsNotificationEventEnqueueResult(
                new com.gogoga.backend.entity.sms.SmsNotificationEvent(),
                true
        );
        when(lifecycle.enqueueAppExpired(any(), any(), any())).thenReturn(enqueueResult);
        SmsAppExpiryPlannerService planner = new SmsAppExpiryPlannerService(mapper, lifecycle, properties);

        SmsAppExpiryPlanningResult result = planner.planActiveRights();

        assertEquals(1, result.candidateCount());
        assertEquals(1, result.createdCount());
        assertEquals(0, result.duplicateCount());
        assertEquals(0, result.failedCount());
        verify(lifecycle).enqueueAppExpired(
                10L,
                LocalDateTime.of(2999, 8, 1, 23, 59, 59),
                LocalDateTime.of(2999, 8, 2, 10, 0)
        );
    }

    @Test
    void shouldNotBackfillHistoricalExpiredRightsWhenNoActiveCandidateExists() {
        MtShopRightMapper mapper = mock(MtShopRightMapper.class);
        MerchantLifecycleSmsService lifecycle = mock(MerchantLifecycleSmsService.class);
        when(mapper.selectActiveAppExpiryCandidatesByCursor(any(), eq(0L), any(Integer.class)))
                .thenReturn(List.of());
        SmsAppExpiryPlannerService planner = new SmsAppExpiryPlannerService(
                mapper,
                lifecycle,
                new SmsProperties()
        );

        SmsAppExpiryPlanningResult result = planner.planActiveRights();

        assertEquals(0, result.candidateCount());
        assertEquals(0, result.createdCount());
        assertEquals(0, result.duplicateCount());
        assertEquals(0, result.failedCount());
        verify(lifecycle, never()).enqueueAppExpired(any(), any(), any());
    }

    @Test
    void shouldAggregateCreatedDuplicateAndFailedCandidates() {
        MtShopRightMapper mapper = mock(MtShopRightMapper.class);
        MerchantLifecycleSmsService lifecycle = mock(MerchantLifecycleSmsService.class);
        SmsProperties properties = new SmsProperties();
        properties.getBusiness().setPlannerBatchSize(200);
        List<SmsAppExpiryCandidate> candidates = List.of(
                candidate(10L),
                candidate(11L),
                candidate(12L)
        );
        when(mapper.selectActiveAppExpiryCandidatesByCursor(any(), eq(0L), eq(200)))
                .thenReturn(candidates);
        when(lifecycle.enqueueAppExpired(eq(10L), any(), any()))
                .thenReturn(enqueueResult(true));
        when(lifecycle.enqueueAppExpired(eq(11L), any(), any()))
                .thenReturn(enqueueResult(false));
        when(lifecycle.enqueueAppExpired(eq(12L), any(), any())).thenReturn(null);
        SmsAppExpiryPlannerService planner = new SmsAppExpiryPlannerService(mapper, lifecycle, properties);

        SmsAppExpiryPlanningResult result = planner.planActiveRights();

        assertEquals(3, result.candidateCount());
        assertEquals(1, result.createdCount());
        assertEquals(1, result.duplicateCount());
        assertEquals(1, result.failedCount());
    }

    private SmsAppExpiryCandidate candidate(Long shopId) {
        SmsAppExpiryCandidate candidate = new SmsAppExpiryCandidate();
        candidate.setShopId(shopId);
        candidate.setExpectedExpireAt(LocalDateTime.of(2999, 8, 1, 23, 59, 59));
        return candidate;
    }

    private SmsNotificationEventEnqueueResult enqueueResult(boolean created) {
        return new SmsNotificationEventEnqueueResult(
                new com.gogoga.backend.entity.sms.SmsNotificationEvent(),
                created
        );
    }
}
