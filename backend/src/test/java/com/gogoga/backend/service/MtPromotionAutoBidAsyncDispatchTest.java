package com.gogoga.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.entity.mt.MtPromotionAutoBidCandidate;
import com.gogoga.backend.enums.MtAsyncTaskTypeEnum;
import com.gogoga.backend.mapper.MtPromotionBidRecordMapper;
import com.gogoga.backend.mapper.MtPromotionSettingMapper;
import com.gogoga.backend.service.async.MtAsyncTaskEnqueueResult;
import com.gogoga.backend.service.async.MtPromotionAutoBidTaskPayload;
import com.gogoga.backend.service.promotion.PromotionBidDecisionService;
import com.gogoga.backend.service.promotion.PromotionDecisionEngine;
import com.gogoga.backend.service.promotion.PromotionExposureDecisionService;
import com.gogoga.backend.service.promotion.PromotionMetricsProvider;
import com.gogoga.backend.service.promotion.PromotionTimeWindowEvaluator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MtPromotionAutoBidAsyncDispatchTest {

    @Mock
    private MtPromotionSettingMapper mtPromotionSettingMapper;

    @Mock
    private MtPromotionBidRecordMapper mtPromotionBidRecordMapper;

    @Mock
    private PromotionTimeWindowEvaluator promotionTimeWindowEvaluator;

    @Mock
    private PromotionMetricsProvider promotionMetricsProvider;

    @Mock
    private com.gogoga.backend.client.MtPromotionOpenApiClient mtPromotionOpenApiClient;

    @Mock
    private PromotionPowerConsumeService promotionPowerConsumeService;

    @Mock
    private MtPromotionSettingService mtPromotionSettingService;

    @Mock
    private MtPowerAccountService mtPowerAccountService;

    @Mock
    private com.gogoga.backend.client.MtShopClient mtShopClient;

    @Mock
    private MtShopService mtShopService;

    @Mock
    private MtPromotionAdRealtimeSnapshotService mtPromotionAdRealtimeSnapshotService;

    @Mock
    private MtPromotionAdDataService mtPromotionAdDataService;

    @Mock
    private com.gogoga.backend.service.promotion.PromotionLogMarkdownService promotionLogMarkdownService;

    @Mock
    private com.gogoga.backend.service.promotion.PromotionLogAsyncTaskService promotionLogAsyncTaskService;

    @Mock
    private com.gogoga.backend.service.promotion.ShopPeakWindowProfileService shopPeakWindowProfileService;

    @Mock
    private MtAsyncTaskService mtAsyncTaskService;

    @Test
    void shouldDispatchAutoBidTasksWithCursorPagination() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        MtProperties mtProperties = new MtProperties();
        mtProperties.getPromotion().getAsyncTask().setEnabled(true);
        mtProperties.getPromotion().getAsyncTask().setDispatchPageSize(2);

        MtPromotionAutoBidCandidate first = buildCandidate(1L);
        MtPromotionAutoBidCandidate second = buildCandidate(2L);
        LocalDateTime slotTime = LocalDateTime.of(2026, 4, 18, 12, 0);

        when(mtPromotionSettingMapper.selectAutoBidCandidatesByCursor(slotTime, null, 2))
                .thenReturn(List.of(first, second));
        when(mtPromotionSettingMapper.selectAutoBidCandidatesByCursor(slotTime, 2L, 2))
                .thenReturn(List.of());
        when(mtAsyncTaskService.enqueueTask(eq(MtAsyncTaskTypeEnum.PROMOTION_AUTO_BID), any(), any(), any(), eq(slotTime), eq(3)))
                .thenReturn(new MtAsyncTaskEnqueueResult(1L, true));

        MtPromotionAutoBidService service = new MtPromotionAutoBidService(
                mtPromotionSettingMapper,
                mtPromotionBidRecordMapper,
                promotionTimeWindowEvaluator,
                promotionMetricsProvider,
                mtPromotionOpenApiClient,
                new PromotionBidDecisionService(new PromotionDecisionEngine()),
                new PromotionExposureDecisionService(mtPromotionAdRealtimeSnapshotService),
                promotionPowerConsumeService,
                mtPromotionSettingService,
                mtPowerAccountService,
                mtShopClient,
                mtShopService,
                mtPromotionAdRealtimeSnapshotService,
                mtPromotionAdDataService,
                promotionLogMarkdownService,
                promotionLogAsyncTaskService,
                new com.gogoga.backend.service.promotion.PromotionLogMessageBuilder(),
                shopPeakWindowProfileService,
                objectMapper,
                mtAsyncTaskService,
                mtProperties
        );

        service.dispatchSlot(slotTime);

        verify(mtPromotionSettingMapper).selectAutoBidCandidatesByCursor(slotTime, null, 2);
        verify(mtPromotionSettingMapper).selectAutoBidCandidatesByCursor(slotTime, 2L, 2);
        ArgumentCaptor<String> bizKeyCaptor = ArgumentCaptor.forClass(String.class);
        verify(mtAsyncTaskService).enqueueTask(
                eq(MtAsyncTaskTypeEnum.PROMOTION_AUTO_BID),
                bizKeyCaptor.capture(),
                eq("1"),
                any(),
                eq(slotTime),
                eq(3)
        );
        assertTrue(bizKeyCaptor.getValue().contains("1#"));
    }

    @Test
    void shouldKeepInlineExecutionWhenAsyncTaskDisabled() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        MtProperties mtProperties = new MtProperties();
        mtProperties.getPromotion().getAsyncTask().setEnabled(false);

        when(mtPromotionSettingMapper.selectAutoBidCandidates(any())).thenReturn(List.of());

        MtPromotionAutoBidService service = new MtPromotionAutoBidService(
                mtPromotionSettingMapper,
                mtPromotionBidRecordMapper,
                promotionTimeWindowEvaluator,
                promotionMetricsProvider,
                mtPromotionOpenApiClient,
                new PromotionBidDecisionService(new PromotionDecisionEngine()),
                new PromotionExposureDecisionService(mtPromotionAdRealtimeSnapshotService),
                promotionPowerConsumeService,
                mtPromotionSettingService,
                mtPowerAccountService,
                mtShopClient,
                mtShopService,
                mtPromotionAdRealtimeSnapshotService,
                mtPromotionAdDataService,
                promotionLogMarkdownService,
                promotionLogAsyncTaskService,
                new com.gogoga.backend.service.promotion.PromotionLogMessageBuilder(),
                shopPeakWindowProfileService,
                objectMapper,
                mtAsyncTaskService,
                mtProperties
        );

        service.executeCurrentSlot();

        verify(mtPromotionSettingMapper).selectAutoBidCandidates(any());
        verify(mtAsyncTaskService, never()).enqueueTask(any(), any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void shouldNotEnqueueMarkdownTaskWhenRecordInsertIsDuplicate() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        MtProperties mtProperties = new MtProperties();
        mtProperties.getPromotion().getAsyncTask().setEnabled(true);
        MtPromotionAutoBidCandidate candidate = buildCandidate(1L);
        MtPromotionAutoBidTaskPayload payload = new MtPromotionAutoBidTaskPayload();
        payload.setShopId(1L);
        payload.setSlotTime(LocalDateTime.of(2026, 4, 18, 12, 0));
        payload.setCandidate(candidate);

        com.gogoga.backend.entity.mt.MtPromotionSetting disabledSetting = new com.gogoga.backend.entity.mt.MtPromotionSetting();
        disabledSetting.setShopId(1L);
        disabledSetting.setPromotionEnabled(false);
        disabledSetting.setStrategyConfigured(true);
        disabledSetting.setStrategyConfigJson(candidate.getStrategyConfigJson());

        when(mtPromotionBidRecordMapper.selectByShopIdAndBizDateAndSlotTime(eq(1L), any(), any())).thenReturn(null);
        when(mtPromotionSettingService.getOrCreateByShopId(1L)).thenReturn(disabledSetting);
        org.mockito.Mockito.doThrow(new org.springframework.dao.DuplicateKeyException("duplicate"))
                .when(mtPromotionBidRecordMapper).insert(any());

        MtPromotionAutoBidService service = new MtPromotionAutoBidService(
                mtPromotionSettingMapper,
                mtPromotionBidRecordMapper,
                promotionTimeWindowEvaluator,
                promotionMetricsProvider,
                mtPromotionOpenApiClient,
                new PromotionBidDecisionService(new PromotionDecisionEngine()),
                new PromotionExposureDecisionService(mtPromotionAdRealtimeSnapshotService),
                promotionPowerConsumeService,
                mtPromotionSettingService,
                mtPowerAccountService,
                mtShopClient,
                mtShopService,
                mtPromotionAdRealtimeSnapshotService,
                mtPromotionAdDataService,
                promotionLogMarkdownService,
                promotionLogAsyncTaskService,
                new com.gogoga.backend.service.promotion.PromotionLogMessageBuilder(),
                shopPeakWindowProfileService,
                objectMapper,
                mtAsyncTaskService,
                mtProperties
        );

        service.executeAutoBidTask(payload);

        verify(mtAsyncTaskService, never()).enqueueTask(eq(MtAsyncTaskTypeEnum.PROMOTION_LOG_MARKDOWN), any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyInt());
    }

    private MtPromotionAutoBidCandidate buildCandidate(Long shopId) {
        MtPromotionAutoBidCandidate candidate = new MtPromotionAutoBidCandidate();
        candidate.setShopId(shopId);
        candidate.setSettingId(shopId);
        candidate.setPoiId("poi-" + shopId);
        candidate.setShopName("shop-" + shopId);
        candidate.setShippingTime("09:00-22:00");
        candidate.setAccessToken("token-" + shopId);
        candidate.setStrategyConfigJson("{\"timeMode\":\"auto\"}");
        return candidate;
    }
}
