package com.gogoga.backend.service;

import com.gogoga.backend.client.MtPromotionOpenApiClient;
import com.gogoga.backend.entity.mt.MtAccessToken;
import com.gogoga.backend.entity.mt.MtPromotionBidRecord;
import com.gogoga.backend.entity.mt.MtPromotionRightExpiryShutdownCandidate;
import com.gogoga.backend.mapper.MtPromotionBidRecordMapper;
import com.gogoga.backend.mapper.MtPromotionSettingMapper;
import com.gogoga.backend.mapper.MtShopRightMapper;
import com.gogoga.backend.service.promotion.PromotionPlanStatusUpdateResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MtPromotionRightExpiryShutdownServiceTest {

    @Mock
    private MtPromotionSettingMapper mtPromotionSettingMapper;

    @Mock
    private MtPromotionBidRecordMapper mtPromotionBidRecordMapper;

    @Mock
    private MtShopRightMapper mtShopRightMapper;

    @Mock
    private MtPromotionOpenApiClient mtPromotionOpenApiClient;

    @Mock
    private MtOrderPushControlService mtOrderPushControlService;

    @Mock
    private MtPromotionSettingService mtPromotionSettingService;

    @Mock
    private MtMeituanAuthorizationFailureHandler mtMeituanAuthorizationFailureHandler;

    private MtPromotionRightExpiryShutdownService service;

    @BeforeEach
    void setUp() {
        service = new MtPromotionRightExpiryShutdownService(
                mtPromotionSettingMapper,
                mtPromotionBidRecordMapper,
                mtShopRightMapper,
                mtPromotionOpenApiClient,
                mtOrderPushControlService,
                mtPromotionSettingService,
                mtMeituanAuthorizationFailureHandler
        );
    }

    @Test
    void shouldShutdownRemotePushAndLocalSwitchWhenRightExpired() {
        MtPromotionRightExpiryShutdownCandidate candidate = candidate(false);
        when(mtShopRightMapper.existsActivePromotionRight(eq(101L), any(LocalDateTime.class))).thenReturn(false);
        when(mtPromotionOpenApiClient.updatePlanStatus(
                eq(101L),
                eq("poi-101"),
                eq("token-101"),
                eq(MtPromotionOpenApiClient.STATUS_CLOSE),
                eq(null),
                eq(null),
                eq(MtPromotionRightExpiryShutdownService.TRIGGER_PROMOTION_RIGHT_EXPIRED)
        )).thenReturn(successPlanResult());
        when(mtOrderPushControlService.blockOrderPush(any(MtAccessToken.class), eq(MtPromotionRightExpiryShutdownService.TRIGGER_PROMOTION_RIGHT_EXPIRED)))
                .thenReturn(MtOrderPushControlService.OrderPushSyncResult.succeeded());

        boolean result = service.shutdownOne(candidate);

        assertTrue(result);
        ArgumentCaptor<MtAccessToken> tokenCaptor = ArgumentCaptor.forClass(MtAccessToken.class);
        verify(mtOrderPushControlService).blockOrderPush(tokenCaptor.capture(), eq(MtPromotionRightExpiryShutdownService.TRIGGER_PROMOTION_RIGHT_EXPIRED));
        assertEquals(9L, tokenCaptor.getValue().getId());
        assertEquals("op-101", tokenCaptor.getValue().getOpBizCode());
        verify(mtPromotionSettingService).disablePromotionByShopId(101L, MtPromotionRightExpiryShutdownService.CHANGE_REASON_PROMOTION_RIGHT_EXPIRED);
        ArgumentCaptor<MtPromotionBidRecord> recordCaptor = ArgumentCaptor.forClass(MtPromotionBidRecord.class);
        verify(mtPromotionBidRecordMapper).insert(recordCaptor.capture());
        assertEquals(101L, recordCaptor.getValue().getShopId());
        assertEquals("AUTO", recordCaptor.getValue().getRecordSource());
        assertEquals(MtPromotionRightExpiryShutdownService.DECISION_REASON_PROMOTION_RIGHT_EXPIRED,
                recordCaptor.getValue().getDecisionReason());
        assertEquals("READY", recordCaptor.getValue().getLogMarkdownStatus());
    }

    @Test
    void shouldSkipOrderPushCallWhenAlreadyBlocked() {
        MtPromotionRightExpiryShutdownCandidate candidate = candidate(true);
        when(mtShopRightMapper.existsActivePromotionRight(eq(101L), any(LocalDateTime.class))).thenReturn(false);
        when(mtPromotionOpenApiClient.updatePlanStatus(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(successPlanResult());

        boolean result = service.shutdownOne(candidate);

        assertTrue(result);
        verify(mtOrderPushControlService, never()).blockOrderPush(any(), any());
        verify(mtPromotionSettingService).disablePromotionByShopId(101L, MtPromotionRightExpiryShutdownService.CHANGE_REASON_PROMOTION_RIGHT_EXPIRED);
        verify(mtPromotionBidRecordMapper).insert(any(MtPromotionBidRecord.class));
    }

    @Test
    void shouldKeepLocalSwitchWhenPlanCloseFails() {
        MtPromotionRightExpiryShutdownCandidate candidate = candidate(false);
        PromotionPlanStatusUpdateResult failure = new PromotionPlanStatusUpdateResult();
        failure.setErrorMsg("remote failed");
        when(mtShopRightMapper.existsActivePromotionRight(eq(101L), any(LocalDateTime.class))).thenReturn(false);
        when(mtPromotionOpenApiClient.updatePlanStatus(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(failure);

        boolean result = service.shutdownOne(candidate);

        assertFalse(result);
        verify(mtOrderPushControlService, never()).blockOrderPush(any(), any());
        verify(mtPromotionSettingService, never()).disablePromotionByShopId(any(), any());
        verify(mtMeituanAuthorizationFailureHandler, never()).handleConfirmedAuthFailure(any(), any(), any(), any());
        verify(mtPromotionBidRecordMapper, never()).insert(any());
    }

    @Test
    void shouldMarkReauthRequiredWhenPlanCloseFailsByAuthFailure() {
        MtPromotionRightExpiryShutdownCandidate candidate = candidate(false);
        PromotionPlanStatusUpdateResult failure = new PromotionPlanStatusUpdateResult();
        failure.setAuthFailure(true);
        failure.setResponseCode("OP_UNIAUTH_FAILED");
        failure.setResponseMsg("鉴权失败");
        failure.setResponseTraceId("trace-101");
        failure.setErrorMsg("更新美团推广计划状态失败：鉴权失败");
        when(mtShopRightMapper.existsActivePromotionRight(eq(101L), any(LocalDateTime.class))).thenReturn(false);
        when(mtPromotionOpenApiClient.updatePlanStatus(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(failure);

        boolean result = service.shutdownOne(candidate);

        assertTrue(result);
        verify(mtMeituanAuthorizationFailureHandler).handleConfirmedAuthFailure(
                101L,
                MtPromotionRightExpiryShutdownService.TRIGGER_PROMOTION_RIGHT_EXPIRED,
                "OP_UNIAUTH_FAILED",
                "鉴权失败"
        );
        verify(mtOrderPushControlService, never()).blockOrderPush(any(), any());
        verify(mtPromotionSettingService, never()).disablePromotionByShopId(any(), any());
        verify(mtPromotionBidRecordMapper, never()).insert(any());
    }

    @Test
    void shouldKeepLocalSwitchWhenOrderPushStopFails() {
        MtPromotionRightExpiryShutdownCandidate candidate = candidate(false);
        when(mtShopRightMapper.existsActivePromotionRight(eq(101L), any(LocalDateTime.class))).thenReturn(false);
        when(mtPromotionOpenApiClient.updatePlanStatus(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(successPlanResult());
        when(mtOrderPushControlService.blockOrderPush(any(MtAccessToken.class), any()))
                .thenReturn(MtOrderPushControlService.OrderPushSyncResult.failure("push failed"));

        boolean result = service.shutdownOne(candidate);

        assertFalse(result);
        verify(mtPromotionSettingService, never()).disablePromotionByShopId(any(), any());
        verify(mtPromotionBidRecordMapper, never()).insert(any());
    }

    @Test
    void shouldSkipRemoteCallsWhenRightRecoveredAfterSelection() {
        MtPromotionRightExpiryShutdownCandidate candidate = candidate(false);
        when(mtShopRightMapper.existsActivePromotionRight(eq(101L), any(LocalDateTime.class))).thenReturn(true);

        boolean result = service.shutdownOne(candidate);

        assertFalse(result);
        verify(mtPromotionOpenApiClient, never()).updatePlanStatus(any(), any(), any(), any(), any(), any(), any());
        verify(mtOrderPushControlService, never()).blockOrderPush(any(), any());
        verify(mtPromotionSettingService, never()).disablePromotionByShopId(any(), any());
        verify(mtPromotionBidRecordMapper, never()).insert(any());
    }

    @Test
    void shouldProcessCandidatesByCursor() {
        MtPromotionRightExpiryShutdownCandidate first = candidate(true);
        MtPromotionRightExpiryShutdownCandidate second = candidate(true);
        second.setShopId(202L);
        when(mtPromotionSettingMapper.selectRightExpiryShutdownCandidatesByCursor(any(LocalDateTime.class), eq(null), eq(1)))
                .thenReturn(List.of(first));
        when(mtPromotionSettingMapper.selectRightExpiryShutdownCandidatesByCursor(any(LocalDateTime.class), eq(101L), eq(1)))
                .thenReturn(List.of(second));
        when(mtPromotionSettingMapper.selectRightExpiryShutdownCandidatesByCursor(any(LocalDateTime.class), eq(202L), eq(1)))
                .thenReturn(List.of());
        when(mtShopRightMapper.existsActivePromotionRight(any(), any(LocalDateTime.class))).thenReturn(false);
        when(mtPromotionOpenApiClient.updatePlanStatus(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(successPlanResult());

        service.processExpiredPromotionRights(1);

        verify(mtPromotionSettingService).disablePromotionByShopId(101L, MtPromotionRightExpiryShutdownService.CHANGE_REASON_PROMOTION_RIGHT_EXPIRED);
        verify(mtPromotionSettingService).disablePromotionByShopId(202L, MtPromotionRightExpiryShutdownService.CHANGE_REASON_PROMOTION_RIGHT_EXPIRED);
    }

    private MtPromotionRightExpiryShutdownCandidate candidate(Boolean orderPushBlocked) {
        MtPromotionRightExpiryShutdownCandidate candidate = new MtPromotionRightExpiryShutdownCandidate();
        candidate.setSettingId(1L);
        candidate.setShopId(101L);
        candidate.setPoiId("poi-101");
        candidate.setAccessToken("token-101");
        candidate.setAccessTokenId(9L);
        candidate.setOpBizCode("op-101");
        candidate.setOrderPushBlocked(orderPushBlocked);
        return candidate;
    }

    private PromotionPlanStatusUpdateResult successPlanResult() {
        PromotionPlanStatusUpdateResult result = new PromotionPlanStatusUpdateResult();
        result.setSuccess(true);
        return result;
    }
}
