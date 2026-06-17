package com.gogoga.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.gogoga.backend.client.MtPromotionOpenApiClient;
import com.gogoga.backend.client.MtShopClient;
import com.gogoga.backend.client.result.PeerBidPriceOpenApiResult;
import com.gogoga.backend.client.result.PromotionBidOpenApiResult;
import com.gogoga.backend.client.result.PromotionPlanStatusOpenApiResult;
import com.gogoga.backend.entity.mt.MtAccessToken;
import com.gogoga.backend.entity.mt.MtPromotionAutoBidCandidate;
import com.gogoga.backend.entity.mt.MtPromotionBidRecord;
import com.gogoga.backend.entity.mt.MtPromotionRecentOrderStatistic;
import com.gogoga.backend.entity.mt.MtPowerAccount;
import com.gogoga.backend.entity.mt.MtPromotionSetting;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.mapper.MtPromotionBidRecordMapper;
import com.gogoga.backend.mapper.MtPromotionSettingMapper;
import com.gogoga.backend.service.promotion.PromotionBidDecisionService;
import com.gogoga.backend.service.promotion.PromotionDecisionDetail;
import com.gogoga.backend.service.promotion.PromotionDecisionResult;
import com.gogoga.backend.service.promotion.PromotionExposureDecisionService;
import com.gogoga.backend.service.promotion.PromotionExecutionWindow;
import com.gogoga.backend.service.promotion.PromotionLogMarkdownResult;
import com.gogoga.backend.service.promotion.PromotionLogMarkdownService;
import com.gogoga.backend.service.promotion.PromotionLogMessageBuilder;
import com.gogoga.backend.service.promotion.PromotionMetricsProvider;
import com.gogoga.backend.service.promotion.PromotionMetricsSnapshot;
import com.gogoga.backend.service.promotion.PromotionRecentOrderStatisticService;
import com.gogoga.backend.service.promotion.PromotionTimeWindowEvaluator;
import com.gogoga.backend.service.promotion.PromotionTimeWindowResult;
import com.gogoga.backend.service.promotion.ChinaWorkdayCalendarDayType;
import com.gogoga.backend.service.promotion.ShopPeakDayType;
import com.gogoga.backend.service.promotion.ShopPeakWindow;
import com.gogoga.backend.service.promotion.ShopPeakWindowProfileLookupResult;
import com.gogoga.backend.service.promotion.ShopPeakWindowProfileService;
import com.gogoga.backend.util.JsonUtil;
import com.gogoga.backend.vo.MtH5ManualPromotionBidVO;
import com.meituan.sdk.model.wmoperNg.poi.wmoperngQueryPoiDetail.Data;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MtPromotionAutoBidServiceTest {

    @Mock
    private MtPromotionSettingMapper mtPromotionSettingMapper;

    @Mock
    private MtPromotionBidRecordMapper mtPromotionBidRecordMapper;

    @Mock
    private PromotionTimeWindowEvaluator promotionTimeWindowEvaluator;

    @Mock
    private PromotionMetricsProvider promotionMetricsProvider;

    @Mock
    private MtPromotionOpenApiClient mtPromotionOpenApiClient;

    @Mock
    private MtPromotionSettingService mtPromotionSettingService;

    @Mock
    private MtPowerAccountService mtPowerAccountService;

    @Mock
    private PromotionPowerConsumeService promotionPowerConsumeService;

    @Mock
    private MtShopClient mtShopClient;

    @Mock
    private MtShopService mtShopService;

    @Mock
    private MtPromotionAdRealtimeSnapshotService mtPromotionAdRealtimeSnapshotService;

    @Mock
    private MtPromotionAdDataService mtPromotionAdDataService;

    @Mock
    private PromotionLogMarkdownService promotionLogMarkdownService;

    @Mock
    private ShopPeakWindowProfileService shopPeakWindowProfileService;

    @Mock
    private MtPromotionBidConstraintService mtPromotionBidConstraintService;

    @Mock
    private MtAccessTokenService mtAccessTokenService;

    @Mock
    private MtMeituanAuthorizationFailureHandler mtMeituanAuthorizationFailureHandler;

    @Mock
    private PromotionRecentOrderStatisticService promotionRecentOrderStatisticService;

    @Test
    void shouldPersistSuccessfulRecordWithRuleOnlyDecisionSource() throws Exception {
        PromotionBidDecisionService decisionService = new PromotionBidDecisionService(
                new com.gogoga.backend.service.promotion.PromotionDecisionEngine()
        );
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        MtPromotionAutoBidService service = buildServiceWithBidConstraint(decisionService, objectMapper);

        LocalDateTime slotTime = LocalDateTime.of(2026, 4, 8, 12, 30);
        MtPromotionAutoBidCandidate candidate = buildCandidate();
        PromotionExecutionWindow window = PromotionExecutionWindow.of(
                LocalDateTime.of(2026, 4, 8, 10, 0),
                LocalDateTime.of(2026, 4, 8, 22, 0)
        );
        PromotionMetricsSnapshot snapshot = new PromotionMetricsSnapshot();
        snapshot.setPromotionBalance(new BigDecimal("100.00"));
        snapshot.setCostCounterValue(new BigDecimal("30.30"));
        snapshot.setWindowCost(new BigDecimal("3.30"));
        snapshot.setWindowIncome(new BigDecimal("66.00"));
        snapshot.setWindowCostRatio(new BigDecimal("0.050000"));
        snapshot.setSlotCostDelta(new BigDecimal("0.30"));
        snapshot.setMetricsTrusted(true);
        PromotionBidOpenApiResult updateResult = new PromotionBidOpenApiResult();
        updateResult.setSuccess(true);
        updateResult.setPayloadJson("{\"ok\":true}");
        updateResult.setBid(40);
        PromotionPlanStatusOpenApiResult statusOpenApiResult = new PromotionPlanStatusOpenApiResult();
        statusOpenApiResult.setSuccess(true);
        statusOpenApiResult.setStatus(MtPromotionOpenApiClient.STATUS_OPEN);
        statusOpenApiResult.setPayloadJson("{\"status\":0}");
        MtPromotionSetting currentSetting = buildCurrentSetting(true, """
                {"priority":"rate","timeMode":"auto","rateMode":"control","rateMin":"5%","rateMax":"10%","decisionMode":"llm"}
                """);

        when(mtPromotionSettingMapper.selectAutoBidCandidates(slotTime)).thenReturn(List.of(candidate));
        when(mtPromotionBidRecordMapper.selectByShopIdAndBizDateAndSlotTime(eq(1L), any(), eq(slotTime))).thenReturn(null);
        when(mtPromotionSettingService.normalizeForExecution(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mtPromotionSettingService.getOrCreateByShopId(1L)).thenReturn(currentSetting);
        when(mtPowerAccountService.getOrCreateAccount(1L)).thenReturn(buildPowerAccount(25000));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(slotTime.minusMinutes(10))))
                .thenReturn(new PromotionTimeWindowResult(false, "OUT_OF_BUSINESS_WINDOW", null));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(slotTime)))
                .thenReturn(new PromotionTimeWindowResult(true, "AUTO_TIME_MODE", window));
        when(mtPromotionOpenApiClient.queryPlanStatus(
                eq(candidate),
                eq(slotTime),
                eq(window),
                eq("WINDOW_STATUS_CHECK")
        )).thenReturn(statusOpenApiResult);
        when(mtPromotionOpenApiClient.updatePlanBid(
                eq(candidate),
                eq(40),
                eq(slotTime),
                eq(window),
                eq("SLOT_BID_UPDATE")
        )).thenReturn(updateResult);
        when(promotionMetricsProvider.queryMetrics(eq(candidate), any(PromotionExecutionWindow.class), eq(slotTime))).thenReturn(snapshot);
        when(mtPromotionBidRecordMapper.selectLatestSuccessByShopIdAndWindowKey(eq(1L), eq(window.getWindowKey()), anyInt()))
                .thenReturn(List.of(
                        buildHistory(slotTime.minusMinutes(10), "0.40", "3.10"),
                        buildHistory(slotTime.minusMinutes(20), "0.40", "3.00")
                ));
        ArgumentCaptor<MtPromotionBidRecord> recordCaptor = ArgumentCaptor.forClass(MtPromotionBidRecord.class);
        doAnswer(invocation -> 1).when(mtPromotionBidRecordMapper).insert(recordCaptor.capture());

        service.executeSlot(slotTime);

        MtPromotionBidRecord inserted = recordCaptor.getValue();
        assertNotNull(inserted);
        assertEquals("SUCCESS", inserted.getExecuteStatus());
        assertEquals(new BigDecimal("0.40"), inserted.getNextBidAmount());
        assertEquals("CLICK_DETECTED_KEEP_PREVIOUS_BID", inserted.getDecisionReason());
        assertNotNull(inserted.getDecisionDetailJson());
        java.util.Map<String, Object> detail = JsonUtil.toMap(inserted.getDecisionDetailJson());
        assertEquals("rule", detail.get("decisionSource"));
        assertEquals(currentSetting.getStrategyConfigJson(), detail.get("strategyConfigSnapshotJson"));
        assertEquals(
                MtPromotionSettingHistoryService.strategyConfigHash(currentSetting.getStrategyConfigJson()),
                detail.get("strategyConfigHash")
        );
        java.util.Map<String, Object> updatePayload = JsonUtil.toMap(inserted.getUpdatePayloadJson());
        assertNotNull(updatePayload.get("statusCheck"));
        assertNotNull(updatePayload.get("bidUpdate"));
        InOrder inOrder = inOrder(mtPromotionOpenApiClient);
        inOrder.verify(mtPromotionOpenApiClient).queryPlanStatus(
                eq(candidate),
                eq(slotTime),
                eq(window),
                eq("WINDOW_STATUS_CHECK")
        );
        inOrder.verify(mtPromotionOpenApiClient).updatePlanBid(
                eq(candidate),
                eq(40),
                eq(slotTime),
                eq(window),
                eq("SLOT_BID_UPDATE")
        );
        verify(promotionPowerConsumeService, never()).consumeForDecision(any(), any(), any());
        verify(mtPromotionOpenApiClient, never()).updatePlanStatus(any(), any(), any(), any(), any());
    }

    @Test
    void shouldPersistManualBidRecordForCurrentWindow() {
        PromotionBidDecisionService decisionService = new PromotionBidDecisionService(
                new com.gogoga.backend.service.promotion.PromotionDecisionEngine()
        );
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        MtPromotionAutoBidService service = buildServiceWithBidConstraint(decisionService, objectMapper);
        service.setMtAccessTokenService(mtAccessTokenService);

        MtShop shop = new MtShop();
        shop.setId(1L);
        shop.setPoiId("poi-1");
        shop.setName("手动门店");
        shop.setShippingTime("09:00-22:00");
        MtPromotionSetting setting = buildCurrentSetting(true, buildStrategyConfigJson("business"));
        setting.setId(11L);
        MtAccessToken token = new MtAccessToken();
        token.setAccessToken("token");
        token.setExpiresAt(LocalDateTime.now().plusDays(1));
        PromotionExecutionWindow window = PromotionExecutionWindow.of(
                LocalDateTime.of(2026, 4, 8, 9, 0),
                LocalDateTime.of(2026, 4, 8, 22, 0)
        );
        MtPromotionBidRecord latest = buildHistory(LocalDateTime.of(2026, 4, 8, 12, 30), "0.70", "3.00");
        latest.setWindowKey(window.getWindowKey());
        PromotionBidOpenApiResult updateResult = buildUpdateResult(80);

        when(mtPromotionSettingService.getOrCreateByShopId(1L)).thenReturn(setting);
        when(mtAccessTokenService.findByShopId(1L)).thenReturn(token);
        when(mtPromotionSettingService.normalizeForExecution(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), any()))
                .thenReturn(new PromotionTimeWindowResult(true, "BUSINESS_TIME_MATCHED", window));
        when(mtPromotionBidConstraintService.resolveEffectiveMaxBidAmount(eq(1L), any(), any()))
                .thenReturn(new BigDecimal("4.00"));
        when(mtPromotionBidRecordMapper.selectLatestSuccessByShopIdAndWindowKey(eq(1L), eq(window.getWindowKey()), eq(1)))
                .thenReturn(List.of(latest));
        when(mtPromotionOpenApiClient.updatePlanBid(any(), eq(80), any(), eq(window), eq("MANUAL_BID_UPDATE")))
                .thenReturn(updateResult);
        ArgumentCaptor<MtPromotionBidRecord> recordCaptor = ArgumentCaptor.forClass(MtPromotionBidRecord.class);
        doAnswer(invocation -> {
            MtPromotionBidRecord record = invocation.getArgument(0);
            record.setId(99L);
            return 1;
        }).when(mtPromotionBidRecordMapper).insert(recordCaptor.capture());

        MtH5ManualPromotionBidVO result = service.updateCurrentWindowBidManually(shop, new BigDecimal("0.80"));

        MtPromotionBidRecord inserted = recordCaptor.getValue();
        assertEquals(99L, result.getRecordId());
        assertEquals("MANUAL", inserted.getRecordSource());
        assertEquals("MANUAL_BID_UPDATE", inserted.getDecisionReason());
        assertEquals(new BigDecimal("0.70"), inserted.getPreviousBidAmount());
        assertEquals(new BigDecimal("0.80"), inserted.getNextBidAmount());
        assertEquals(LocalDateTime.of(2026, 4, 8, 12, 30), inserted.getAttributedSlotTime());
        assertNotNull(inserted.getOperatedAt());
        assertEquals("READY", inserted.getLogMarkdownStatus());
        assertNotNull(inserted.getMarkdownMessage());
        assertTrue(inserted.getDecisionDetailJson().contains("MANUAL"));
        assertTrue(inserted.getDecisionDetailJson().contains("manual"));
    }

    @Test
    void shouldUsePeerAverageBidAsInitialBidWhenNoSuccessHistoryExists() throws Exception {
        PromotionBidDecisionService decisionService = new PromotionBidDecisionService(
                new com.gogoga.backend.service.promotion.PromotionDecisionEngine()
        );
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        MtPromotionAutoBidService service = buildService(decisionService, objectMapper);

        LocalDateTime slotTime = LocalDateTime.of(2026, 4, 8, 12, 30);
        MtPromotionAutoBidCandidate candidate = buildCandidate();
        PromotionExecutionWindow window = PromotionExecutionWindow.of(
                LocalDateTime.of(2026, 4, 8, 10, 0),
                LocalDateTime.of(2026, 4, 8, 22, 0)
        );
        PromotionMetricsSnapshot snapshot = new PromotionMetricsSnapshot();
        snapshot.setPromotionBalance(new BigDecimal("100.00"));
        snapshot.setCostCounterValue(new BigDecimal("30.30"));
        snapshot.setWindowCost(BigDecimal.ZERO.setScale(2));
        snapshot.setWindowIncome(new BigDecimal("66.00"));
        snapshot.setWindowCostRatio(BigDecimal.ZERO.setScale(6));
        snapshot.setSlotCostDelta(BigDecimal.ZERO.setScale(2));
        snapshot.setMetricsTrusted(true);
        PromotionPlanStatusOpenApiResult statusOpenApiResult = new PromotionPlanStatusOpenApiResult();
        statusOpenApiResult.setSuccess(true);
        statusOpenApiResult.setStatus(MtPromotionOpenApiClient.STATUS_OPEN);
        statusOpenApiResult.setPayloadJson("{\"status\":0}");
        PeerBidPriceOpenApiResult peerBidResult = new PeerBidPriceOpenApiResult();
        peerBidResult.setSuccess(true);
        peerBidResult.setAverageBidAmount(new BigDecimal("0.66"));
        peerBidResult.setPayloadJson("{\"averageBidAmount\":0.66}");
        PromotionBidOpenApiResult updateResult = new PromotionBidOpenApiResult();
        updateResult.setSuccess(true);
        updateResult.setPayloadJson("{\"ok\":true}");
        updateResult.setBid(10);
        MtPromotionSetting currentSetting = buildCurrentSetting(true, candidate.getStrategyConfigJson());

        when(mtPromotionSettingMapper.selectAutoBidCandidates(slotTime)).thenReturn(List.of(candidate));
        when(mtPromotionBidRecordMapper.selectByShopIdAndBizDateAndSlotTime(eq(1L), any(), eq(slotTime))).thenReturn(null);
        when(mtPromotionSettingService.normalizeForExecution(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mtPromotionSettingService.getOrCreateByShopId(1L)).thenReturn(currentSetting);
        when(mtPowerAccountService.getOrCreateAccount(1L)).thenReturn(buildPowerAccount(25000));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(slotTime.minusMinutes(10))))
                .thenReturn(new PromotionTimeWindowResult(false, "OUT_OF_BUSINESS_WINDOW", null));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(slotTime)))
                .thenReturn(new PromotionTimeWindowResult(true, "AUTO_TIME_MODE", window));
        when(mtPromotionOpenApiClient.queryPlanStatus(eq(candidate), eq(slotTime), eq(window), eq("WINDOW_STATUS_CHECK")))
                .thenReturn(statusOpenApiResult);
        when(promotionMetricsProvider.queryMetrics(eq(candidate), any(PromotionExecutionWindow.class), eq(slotTime))).thenReturn(snapshot);
        when(mtPromotionBidRecordMapper.selectLatestSuccessByShopIdAndWindowKey(eq(1L), eq(window.getWindowKey()), anyInt()))
                .thenReturn(List.of());
        when(mtPromotionOpenApiClient.queryPeerAverageBid(
                eq(candidate),
                eq(slotTime),
                eq(window),
                eq("INITIAL_BID_FROM_PEER_AVG")
        )).thenReturn(peerBidResult);
        when(mtPromotionOpenApiClient.updatePlanBid(
                eq(candidate),
                eq(10),
                eq(slotTime),
                eq(window),
                eq("SLOT_BID_UPDATE")
        )).thenReturn(updateResult);

        ArgumentCaptor<MtPromotionBidRecord> recordCaptor = ArgumentCaptor.forClass(MtPromotionBidRecord.class);
        doAnswer(invocation -> 1).when(mtPromotionBidRecordMapper).insert(recordCaptor.capture());

        service.executeSlot(slotTime);

        MtPromotionBidRecord inserted = recordCaptor.getValue();
        assertNotNull(inserted);
        assertEquals("SUCCESS", inserted.getExecuteStatus());
        assertEquals("FIRST_SUCCESS_OF_WINDOW", inserted.getDecisionReason());
        assertEquals(new BigDecimal("0.10"), inserted.getNextBidAmount());
        java.util.Map<String, Object> detail = JsonUtil.toMap(inserted.getDecisionDetailJson());
        assertEquals(0.66d, ((Number) detail.get("peerAverageBidAmount")).doubleValue());
        assertEquals("{\"averageBidAmount\":0.66}", detail.get("peerAverageBidPayloadJson"));
        assertEquals("PEAK_RULE", detail.get("pricingBranch"));
        verify(mtPromotionOpenApiClient).queryPeerAverageBid(
                eq(candidate),
                eq(slotTime),
                eq(window),
                eq("INITIAL_BID_FROM_PEER_AVG")
        );
        verify(mtPromotionOpenApiClient).updatePlanBid(
                eq(candidate),
                eq(10),
                eq(slotTime),
                eq(window),
                eq("SLOT_BID_UPDATE")
        );
    }

    @Test
    void shouldFloorInitialBidToMinWhenPeerAverageFormulaReturnsZero() throws Exception {
        PromotionBidDecisionService decisionService = new PromotionBidDecisionService(
                new com.gogoga.backend.service.promotion.PromotionDecisionEngine()
        );
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        MtPromotionAutoBidService service = buildService(decisionService, objectMapper);

        LocalDateTime slotTime = LocalDateTime.of(2026, 4, 8, 12, 30);
        MtPromotionAutoBidCandidate candidate = buildCandidate();
        PromotionExecutionWindow window = PromotionExecutionWindow.of(
                LocalDateTime.of(2026, 4, 8, 10, 0),
                LocalDateTime.of(2026, 4, 8, 22, 0)
        );
        PromotionMetricsSnapshot snapshot = new PromotionMetricsSnapshot();
        snapshot.setPromotionBalance(new BigDecimal("100.00"));
        snapshot.setCostCounterValue(new BigDecimal("30.30"));
        snapshot.setWindowCost(BigDecimal.ZERO.setScale(2));
        snapshot.setWindowIncome(new BigDecimal("66.00"));
        snapshot.setWindowCostRatio(BigDecimal.ZERO.setScale(6));
        snapshot.setSlotCostDelta(BigDecimal.ZERO.setScale(2));
        snapshot.setMetricsTrusted(true);
        PromotionPlanStatusOpenApiResult statusOpenApiResult = new PromotionPlanStatusOpenApiResult();
        statusOpenApiResult.setSuccess(true);
        statusOpenApiResult.setStatus(MtPromotionOpenApiClient.STATUS_OPEN);
        statusOpenApiResult.setPayloadJson("{\"status\":0}");
        PeerBidPriceOpenApiResult peerBidResult = new PeerBidPriceOpenApiResult();
        peerBidResult.setSuccess(true);
        peerBidResult.setAverageBidAmount(new BigDecimal("0.70"));
        peerBidResult.setPayloadJson("{\"averageBidAmount\":0.70}");
        PromotionBidOpenApiResult updateResult = new PromotionBidOpenApiResult();
        updateResult.setSuccess(true);
        updateResult.setPayloadJson("{\"ok\":true}");
        updateResult.setBid(10);
        MtPromotionSetting currentSetting = buildCurrentSetting(true, candidate.getStrategyConfigJson());

        when(mtPromotionSettingMapper.selectAutoBidCandidates(slotTime)).thenReturn(List.of(candidate));
        when(mtPromotionBidRecordMapper.selectByShopIdAndBizDateAndSlotTime(eq(1L), any(), eq(slotTime))).thenReturn(null);
        when(mtPromotionSettingService.normalizeForExecution(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mtPromotionSettingService.getOrCreateByShopId(1L)).thenReturn(currentSetting);
        when(mtPowerAccountService.getOrCreateAccount(1L)).thenReturn(buildPowerAccount(25000));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(slotTime.minusMinutes(10))))
                .thenReturn(new PromotionTimeWindowResult(false, "OUT_OF_BUSINESS_WINDOW", null));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(slotTime)))
                .thenReturn(new PromotionTimeWindowResult(true, "AUTO_TIME_MODE", window));
        when(mtPromotionOpenApiClient.queryPlanStatus(eq(candidate), eq(slotTime), eq(window), eq("WINDOW_STATUS_CHECK")))
                .thenReturn(statusOpenApiResult);
        when(promotionMetricsProvider.queryMetrics(eq(candidate), any(PromotionExecutionWindow.class), eq(slotTime))).thenReturn(snapshot);
        when(mtPromotionBidRecordMapper.selectLatestSuccessByShopIdAndWindowKey(eq(1L), eq(window.getWindowKey()), anyInt()))
                .thenReturn(List.of());
        when(mtPromotionOpenApiClient.queryPeerAverageBid(
                eq(candidate),
                eq(slotTime),
                eq(window),
                eq("INITIAL_BID_FROM_PEER_AVG")
        )).thenReturn(peerBidResult);
        when(mtPromotionOpenApiClient.updatePlanBid(
                eq(candidate),
                eq(10),
                eq(slotTime),
                eq(window),
                eq("SLOT_BID_UPDATE")
        )).thenReturn(updateResult);

        ArgumentCaptor<MtPromotionBidRecord> recordCaptor = ArgumentCaptor.forClass(MtPromotionBidRecord.class);
        doAnswer(invocation -> 1).when(mtPromotionBidRecordMapper).insert(recordCaptor.capture());

        service.executeSlot(slotTime);

        MtPromotionBidRecord inserted = recordCaptor.getValue();
        assertNotNull(inserted);
        assertEquals("SUCCESS", inserted.getExecuteStatus());
        assertEquals("FIRST_SUCCESS_OF_WINDOW", inserted.getDecisionReason());
        assertEquals(new BigDecimal("0.10"), inserted.getNextBidAmount());
        verify(mtPromotionOpenApiClient).updatePlanBid(
                eq(candidate),
                eq(10),
                eq(slotTime),
                eq(window),
                eq("SLOT_BID_UPDATE")
        );
    }

    @Test
    void shouldUseCurrentPlanBidAsInitialBidWhenPromotionEnabledMidWindow() throws Exception {
        PromotionBidDecisionService decisionService = new PromotionBidDecisionService(
                new com.gogoga.backend.service.promotion.PromotionDecisionEngine()
        );
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        MtPromotionAutoBidService service = buildService(decisionService, objectMapper);

        LocalDateTime slotTime = LocalDateTime.of(2026, 4, 8, 12, 30);
        MtPromotionAutoBidCandidate candidate = buildCandidate();
        PromotionExecutionWindow window = PromotionExecutionWindow.of(
                LocalDateTime.of(2026, 4, 8, 10, 0),
                LocalDateTime.of(2026, 4, 8, 22, 0)
        );
        PromotionMetricsSnapshot snapshot = new PromotionMetricsSnapshot();
        snapshot.setPromotionBalance(new BigDecimal("100.00"));
        snapshot.setCostCounterValue(new BigDecimal("30.30"));
        snapshot.setWindowCost(BigDecimal.ZERO.setScale(2));
        snapshot.setWindowIncome(new BigDecimal("66.00"));
        snapshot.setWindowCostRatio(BigDecimal.ZERO.setScale(6));
        snapshot.setSlotCostDelta(BigDecimal.ZERO.setScale(2));
        snapshot.setMetricsTrusted(true);
        PromotionPlanStatusOpenApiResult statusOpenApiResult = new PromotionPlanStatusOpenApiResult();
        statusOpenApiResult.setSuccess(true);
        statusOpenApiResult.setStatus(MtPromotionOpenApiClient.STATUS_OPEN);
        statusOpenApiResult.setBid(70);
        statusOpenApiResult.setPayloadJson("{\"status\":0,\"bid\":70}");
        PromotionBidOpenApiResult updateResult = new PromotionBidOpenApiResult();
        updateResult.setSuccess(true);
        updateResult.setPayloadJson("{\"ok\":true}");
        updateResult.setBid(70);
        MtPromotionSetting currentSetting = buildCurrentSetting(true, candidate.getStrategyConfigJson());

        when(mtPromotionSettingMapper.selectAutoBidCandidates(slotTime)).thenReturn(List.of(candidate));
        when(mtPromotionBidRecordMapper.selectByShopIdAndBizDateAndSlotTime(eq(1L), any(), eq(slotTime))).thenReturn(null);
        when(mtPromotionSettingService.normalizeForExecution(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mtPromotionSettingService.getOrCreateByShopId(1L)).thenReturn(currentSetting);
        when(mtPowerAccountService.getOrCreateAccount(1L)).thenReturn(buildPowerAccount(25000));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(slotTime.minusMinutes(10))))
                .thenReturn(new PromotionTimeWindowResult(true, "AUTO_TIME_MODE", window));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(slotTime)))
                .thenReturn(new PromotionTimeWindowResult(true, "AUTO_TIME_MODE", window));
        when(mtPromotionOpenApiClient.queryPlanStatus(eq(candidate), eq(slotTime), eq(window), eq("WINDOW_STATUS_CHECK")))
                .thenReturn(statusOpenApiResult);
        when(promotionMetricsProvider.queryMetrics(eq(candidate), any(PromotionExecutionWindow.class), eq(slotTime))).thenReturn(snapshot);
        when(mtPromotionBidRecordMapper.selectLatestSuccessByShopIdAndWindowKey(eq(1L), eq(window.getWindowKey()), anyInt()))
                .thenReturn(List.of());
        when(mtPromotionOpenApiClient.updatePlanBid(
                eq(candidate),
                eq(70),
                eq(slotTime),
                eq(window),
                eq("SLOT_BID_UPDATE")
        )).thenReturn(updateResult);

        ArgumentCaptor<MtPromotionBidRecord> recordCaptor = ArgumentCaptor.forClass(MtPromotionBidRecord.class);
        doAnswer(invocation -> 1).when(mtPromotionBidRecordMapper).insert(recordCaptor.capture());

        service.executeSlot(slotTime);

        MtPromotionBidRecord inserted = recordCaptor.getValue();
        assertNotNull(inserted);
        assertEquals("SUCCESS", inserted.getExecuteStatus());
        assertEquals("FIRST_SUCCESS_OF_WINDOW", inserted.getDecisionReason());
        assertEquals(new BigDecimal("0.70"), inserted.getNextBidAmount());
        verify(mtPromotionOpenApiClient, never()).queryPeerAverageBid(any(), any(), any(), any());
        verify(mtPromotionOpenApiClient).updatePlanBid(
                eq(candidate),
                eq(70),
                eq(slotTime),
                eq(window),
                eq("SLOT_BID_UPDATE")
        );
    }

    @Test
    void shouldFloorCurrentPlanBidAsInitialBidWhenPromotionEnabledMidWindow() throws Exception {
        PromotionBidDecisionService decisionService = new PromotionBidDecisionService(
                new com.gogoga.backend.service.promotion.PromotionDecisionEngine()
        );
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        MtPromotionAutoBidService service = buildService(decisionService, objectMapper);

        LocalDateTime slotTime = LocalDateTime.of(2026, 4, 8, 12, 30);
        MtPromotionAutoBidCandidate candidate = buildCandidate();
        PromotionExecutionWindow window = PromotionExecutionWindow.of(
                LocalDateTime.of(2026, 4, 8, 10, 0),
                LocalDateTime.of(2026, 4, 8, 22, 0)
        );
        PromotionMetricsSnapshot snapshot = new PromotionMetricsSnapshot();
        snapshot.setPromotionBalance(new BigDecimal("100.00"));
        snapshot.setCostCounterValue(new BigDecimal("30.30"));
        snapshot.setWindowCost(BigDecimal.ZERO.setScale(2));
        snapshot.setWindowIncome(new BigDecimal("66.00"));
        snapshot.setWindowCostRatio(BigDecimal.ZERO.setScale(6));
        snapshot.setSlotCostDelta(BigDecimal.ZERO.setScale(2));
        snapshot.setMetricsTrusted(true);
        PromotionPlanStatusOpenApiResult statusOpenApiResult = new PromotionPlanStatusOpenApiResult();
        statusOpenApiResult.setSuccess(true);
        statusOpenApiResult.setStatus(MtPromotionOpenApiClient.STATUS_OPEN);
        statusOpenApiResult.setBid(5);
        statusOpenApiResult.setPayloadJson("{\"status\":0,\"bid\":5}");
        PromotionBidOpenApiResult updateResult = new PromotionBidOpenApiResult();
        updateResult.setSuccess(true);
        updateResult.setPayloadJson("{\"ok\":true}");
        updateResult.setBid(10);
        MtPromotionSetting currentSetting = buildCurrentSetting(true, candidate.getStrategyConfigJson());

        when(mtPromotionSettingMapper.selectAutoBidCandidates(slotTime)).thenReturn(List.of(candidate));
        when(mtPromotionBidRecordMapper.selectByShopIdAndBizDateAndSlotTime(eq(1L), any(), eq(slotTime))).thenReturn(null);
        when(mtPromotionSettingService.normalizeForExecution(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mtPromotionSettingService.getOrCreateByShopId(1L)).thenReturn(currentSetting);
        when(mtPowerAccountService.getOrCreateAccount(1L)).thenReturn(buildPowerAccount(25000));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(slotTime.minusMinutes(10))))
                .thenReturn(new PromotionTimeWindowResult(true, "AUTO_TIME_MODE", window));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(slotTime)))
                .thenReturn(new PromotionTimeWindowResult(true, "AUTO_TIME_MODE", window));
        when(mtPromotionOpenApiClient.queryPlanStatus(eq(candidate), eq(slotTime), eq(window), eq("WINDOW_STATUS_CHECK")))
                .thenReturn(statusOpenApiResult);
        when(promotionMetricsProvider.queryMetrics(eq(candidate), any(PromotionExecutionWindow.class), eq(slotTime))).thenReturn(snapshot);
        when(mtPromotionBidRecordMapper.selectLatestSuccessByShopIdAndWindowKey(eq(1L), eq(window.getWindowKey()), anyInt()))
                .thenReturn(List.of());
        when(mtPromotionOpenApiClient.updatePlanBid(
                eq(candidate),
                eq(10),
                eq(slotTime),
                eq(window),
                eq("SLOT_BID_UPDATE")
        )).thenReturn(updateResult);

        ArgumentCaptor<MtPromotionBidRecord> recordCaptor = ArgumentCaptor.forClass(MtPromotionBidRecord.class);
        doAnswer(invocation -> 1).when(mtPromotionBidRecordMapper).insert(recordCaptor.capture());

        service.executeSlot(slotTime);

        MtPromotionBidRecord inserted = recordCaptor.getValue();
        assertNotNull(inserted);
        assertEquals("SUCCESS", inserted.getExecuteStatus());
        assertEquals("FIRST_SUCCESS_OF_WINDOW", inserted.getDecisionReason());
        assertEquals(new BigDecimal("0.10"), inserted.getNextBidAmount());
        verify(mtPromotionOpenApiClient, never()).queryPeerAverageBid(any(), any(), any(), any());
        verify(mtPromotionOpenApiClient).updatePlanBid(
                eq(candidate),
                eq(10),
                eq(slotTime),
                eq(window),
                eq("SLOT_BID_UPDATE")
        );
    }

    @Test
    void shouldFallbackToDefaultInitialBidWhenPeerAverageBidBelowMin() throws Exception {
        PromotionBidDecisionService decisionService = new PromotionBidDecisionService(
                new com.gogoga.backend.service.promotion.PromotionDecisionEngine()
        );
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        MtPromotionAutoBidService service = buildService(decisionService, objectMapper);

        LocalDateTime slotTime = LocalDateTime.of(2026, 4, 8, 12, 30);
        MtPromotionAutoBidCandidate candidate = buildCandidate();
        PromotionExecutionWindow window = PromotionExecutionWindow.of(
                LocalDateTime.of(2026, 4, 8, 12, 0),
                LocalDateTime.of(2026, 4, 8, 22, 0)
        );
        PromotionMetricsSnapshot snapshot = new PromotionMetricsSnapshot();
        snapshot.setPromotionBalance(new BigDecimal("100.00"));
        snapshot.setCostCounterValue(new BigDecimal("30.30"));
        snapshot.setWindowCost(BigDecimal.ZERO.setScale(2));
        snapshot.setWindowIncome(new BigDecimal("66.00"));
        snapshot.setWindowCostRatio(BigDecimal.ZERO.setScale(6));
        snapshot.setSlotCostDelta(BigDecimal.ZERO.setScale(2));
        snapshot.setMetricsTrusted(true);
        PromotionPlanStatusOpenApiResult statusOpenApiResult = new PromotionPlanStatusOpenApiResult();
        statusOpenApiResult.setSuccess(true);
        statusOpenApiResult.setStatus(MtPromotionOpenApiClient.STATUS_OPEN);
        statusOpenApiResult.setPayloadJson("{\"status\":0}");
        PeerBidPriceOpenApiResult peerBidResult = new PeerBidPriceOpenApiResult();
        peerBidResult.setSuccess(true);
        peerBidResult.setAverageBidAmount(new BigDecimal("0.09"));
        PromotionBidOpenApiResult updateResult = new PromotionBidOpenApiResult();
        updateResult.setSuccess(true);
        updateResult.setPayloadJson("{\"ok\":true}");
        updateResult.setBid(40);
        MtPromotionSetting currentSetting = buildCurrentSetting(true, candidate.getStrategyConfigJson());

        when(mtPromotionSettingMapper.selectAutoBidCandidates(slotTime)).thenReturn(List.of(candidate));
        when(mtPromotionBidRecordMapper.selectByShopIdAndBizDateAndSlotTime(eq(1L), any(), eq(slotTime))).thenReturn(null);
        when(mtPromotionSettingService.normalizeForExecution(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mtPromotionSettingService.getOrCreateByShopId(1L)).thenReturn(currentSetting);
        when(mtPowerAccountService.getOrCreateAccount(1L)).thenReturn(buildPowerAccount(25000));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(slotTime.minusMinutes(10))))
                .thenReturn(new PromotionTimeWindowResult(false, "OUT_OF_BUSINESS_WINDOW", null));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(slotTime)))
                .thenReturn(new PromotionTimeWindowResult(true, "AUTO_TIME_MODE", window));
        when(mtPromotionOpenApiClient.queryPlanStatus(eq(candidate), eq(slotTime), eq(window), eq("WINDOW_STATUS_CHECK")))
                .thenReturn(statusOpenApiResult);
        when(promotionMetricsProvider.queryMetrics(eq(candidate), any(PromotionExecutionWindow.class), eq(slotTime))).thenReturn(snapshot);
        when(mtPromotionBidRecordMapper.selectLatestSuccessByShopIdAndWindowKey(eq(1L), eq(window.getWindowKey()), anyInt()))
                .thenReturn(List.of());
        when(mtPromotionOpenApiClient.queryPeerAverageBid(eq(candidate), eq(slotTime), eq(window), eq("INITIAL_BID_FROM_PEER_AVG")))
                .thenReturn(peerBidResult);
        when(mtPromotionOpenApiClient.updatePlanBid(eq(candidate), eq(40), eq(slotTime), eq(window), eq("SLOT_BID_UPDATE")))
                .thenReturn(updateResult);

        ArgumentCaptor<MtPromotionBidRecord> recordCaptor = ArgumentCaptor.forClass(MtPromotionBidRecord.class);
        doAnswer(invocation -> 1).when(mtPromotionBidRecordMapper).insert(recordCaptor.capture());

        service.executeSlot(slotTime);

        MtPromotionBidRecord inserted = recordCaptor.getValue();
        assertNotNull(inserted);
        assertEquals(new BigDecimal("0.40"), inserted.getNextBidAmount());
        verify(mtPromotionOpenApiClient).updatePlanBid(eq(candidate), eq(40), eq(slotTime), eq(window), eq("SLOT_BID_UPDATE"));
    }

    @Test
    void shouldFallbackToDefaultInitialBidWhenPeerAverageBidAboveMax() throws Exception {
        PromotionBidDecisionService decisionService = new PromotionBidDecisionService(
                new com.gogoga.backend.service.promotion.PromotionDecisionEngine()
        );
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        MtPromotionAutoBidService service = buildService(decisionService, objectMapper);

        LocalDateTime slotTime = LocalDateTime.of(2026, 4, 8, 12, 30);
        MtPromotionAutoBidCandidate candidate = buildCandidate();
        PromotionExecutionWindow window = PromotionExecutionWindow.of(
                LocalDateTime.of(2026, 4, 8, 12, 0),
                LocalDateTime.of(2026, 4, 8, 22, 0)
        );
        PromotionMetricsSnapshot snapshot = new PromotionMetricsSnapshot();
        snapshot.setPromotionBalance(new BigDecimal("100.00"));
        snapshot.setCostCounterValue(new BigDecimal("30.30"));
        snapshot.setWindowCost(BigDecimal.ZERO.setScale(2));
        snapshot.setWindowIncome(new BigDecimal("66.00"));
        snapshot.setWindowCostRatio(BigDecimal.ZERO.setScale(6));
        snapshot.setSlotCostDelta(BigDecimal.ZERO.setScale(2));
        snapshot.setMetricsTrusted(true);
        PromotionPlanStatusOpenApiResult statusOpenApiResult = new PromotionPlanStatusOpenApiResult();
        statusOpenApiResult.setSuccess(true);
        statusOpenApiResult.setStatus(MtPromotionOpenApiClient.STATUS_OPEN);
        statusOpenApiResult.setPayloadJson("{\"status\":0}");
        PeerBidPriceOpenApiResult peerBidResult = new PeerBidPriceOpenApiResult();
        peerBidResult.setSuccess(true);
        peerBidResult.setAverageBidAmount(new BigDecimal("1.51"));
        PromotionBidOpenApiResult updateResult = new PromotionBidOpenApiResult();
        updateResult.setSuccess(true);
        updateResult.setPayloadJson("{\"ok\":true}");
        updateResult.setBid(40);
        MtPromotionSetting currentSetting = buildCurrentSetting(true, candidate.getStrategyConfigJson());

        when(mtPromotionSettingMapper.selectAutoBidCandidates(slotTime)).thenReturn(List.of(candidate));
        when(mtPromotionBidRecordMapper.selectByShopIdAndBizDateAndSlotTime(eq(1L), any(), eq(slotTime))).thenReturn(null);
        when(mtPromotionSettingService.normalizeForExecution(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mtPromotionSettingService.getOrCreateByShopId(1L)).thenReturn(currentSetting);
        when(mtPowerAccountService.getOrCreateAccount(1L)).thenReturn(buildPowerAccount(25000));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(slotTime.minusMinutes(10))))
                .thenReturn(new PromotionTimeWindowResult(false, "OUT_OF_BUSINESS_WINDOW", null));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(slotTime)))
                .thenReturn(new PromotionTimeWindowResult(true, "AUTO_TIME_MODE", window));
        when(mtPromotionOpenApiClient.queryPlanStatus(eq(candidate), eq(slotTime), eq(window), eq("WINDOW_STATUS_CHECK")))
                .thenReturn(statusOpenApiResult);
        when(promotionMetricsProvider.queryMetrics(eq(candidate), any(PromotionExecutionWindow.class), eq(slotTime))).thenReturn(snapshot);
        when(mtPromotionBidRecordMapper.selectLatestSuccessByShopIdAndWindowKey(eq(1L), eq(window.getWindowKey()), anyInt()))
                .thenReturn(List.of());
        when(mtPromotionOpenApiClient.queryPeerAverageBid(eq(candidate), eq(slotTime), eq(window), eq("INITIAL_BID_FROM_PEER_AVG")))
                .thenReturn(peerBidResult);
        when(mtPromotionOpenApiClient.updatePlanBid(eq(candidate), eq(40), eq(slotTime), eq(window), eq("SLOT_BID_UPDATE")))
                .thenReturn(updateResult);

        ArgumentCaptor<MtPromotionBidRecord> recordCaptor = ArgumentCaptor.forClass(MtPromotionBidRecord.class);
        doAnswer(invocation -> 1).when(mtPromotionBidRecordMapper).insert(recordCaptor.capture());

        service.executeSlot(slotTime);

        MtPromotionBidRecord inserted = recordCaptor.getValue();
        assertNotNull(inserted);
        assertEquals(new BigDecimal("0.40"), inserted.getNextBidAmount());
        verify(mtPromotionOpenApiClient).updatePlanBid(eq(candidate), eq(40), eq(slotTime), eq(window), eq("SLOT_BID_UPDATE"));
    }

    @Test
    void shouldIncreaseBidWhenExposureBranchSuggestsIncreaseAfterFeeRateKeep() throws Exception {
        PromotionBidDecisionService decisionService = new PromotionBidDecisionService(
                new com.gogoga.backend.service.promotion.PromotionDecisionEngine()
        );
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        MtPromotionAutoBidService service = buildService(decisionService, objectMapper);

        LocalDateTime slotTime = LocalDateTime.of(2026, 4, 8, 12, 30);
        MtPromotionAutoBidCandidate candidate = buildCandidate();
        PromotionExecutionWindow window = PromotionExecutionWindow.of(
                LocalDateTime.of(2026, 4, 8, 10, 0),
                LocalDateTime.of(2026, 4, 8, 22, 0)
        );
        PromotionMetricsSnapshot snapshot = new PromotionMetricsSnapshot();
        snapshot.setPromotionBalance(new BigDecimal("100.00"));
        snapshot.setCostCounterValue(new BigDecimal("30.30"));
        snapshot.setWindowCost(new BigDecimal("3.30"));
        snapshot.setWindowIncome(new BigDecimal("66.00"));
        snapshot.setWindowCostRatio(new BigDecimal("0.050000"));
        snapshot.setSlotCostDelta(new BigDecimal("0.30"));
        snapshot.setMetricsTrusted(true);
        PromotionPlanStatusOpenApiResult statusOpenApiResult = new PromotionPlanStatusOpenApiResult();
        statusOpenApiResult.setSuccess(true);
        statusOpenApiResult.setStatus(MtPromotionOpenApiClient.STATUS_OPEN);
        statusOpenApiResult.setPayloadJson("{\"status\":0}");
        PromotionBidOpenApiResult updateResult = new PromotionBidOpenApiResult();
        updateResult.setSuccess(true);
        updateResult.setPayloadJson("{\"ok\":true}");
        updateResult.setBid(50);
        MtPromotionSetting currentSetting = buildCurrentSetting(true, candidate.getStrategyConfigJson());

        when(mtPromotionSettingMapper.selectAutoBidCandidates(slotTime)).thenReturn(List.of(candidate));
        when(mtPromotionBidRecordMapper.selectByShopIdAndBizDateAndSlotTime(eq(1L), any(), eq(slotTime))).thenReturn(null);
        when(mtPromotionSettingService.normalizeForExecution(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mtPromotionSettingService.getOrCreateByShopId(1L)).thenReturn(currentSetting);
        when(mtPowerAccountService.getOrCreateAccount(1L)).thenReturn(buildPowerAccount(25000));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(slotTime.minusMinutes(10))))
                .thenReturn(new PromotionTimeWindowResult(true, "AUTO_TIME_MODE", window));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(slotTime)))
                .thenReturn(new PromotionTimeWindowResult(true, "AUTO_TIME_MODE", window));
        when(mtPromotionOpenApiClient.queryPlanStatus(eq(candidate), eq(slotTime), eq(window), eq("WINDOW_STATUS_CHECK")))
                .thenReturn(statusOpenApiResult);
        when(promotionMetricsProvider.queryMetrics(eq(candidate), any(PromotionExecutionWindow.class), eq(slotTime))).thenReturn(snapshot);
        when(mtPromotionBidRecordMapper.selectLatestSuccessByShopIdAndWindowKey(eq(1L), eq(window.getWindowKey()), anyInt()))
                .thenReturn(List.of(
                        buildHistory(slotTime.minusMinutes(10), "0.40", "3.10"),
                        buildHistory(slotTime.minusMinutes(20), "0.30", "3.00")
                ));
        when(mtPromotionAdRealtimeSnapshotService.selectByShopIdAndBizDateAndSlotTime(eq(1L), eq(slotTime.toLocalDate()), eq(slotTime.minusMinutes(20))))
                .thenReturn(buildRealtimeSnapshot(slotTime.minusMinutes(20), 100));
        when(mtPromotionAdRealtimeSnapshotService.selectByShopIdAndBizDateAndSlotTime(eq(1L), eq(slotTime.toLocalDate()), eq(slotTime.minusMinutes(10))))
                .thenReturn(buildRealtimeSnapshot(slotTime.minusMinutes(10), 110));
        when(mtPromotionAdRealtimeSnapshotService.selectByShopIdAndBizDateAndSlotTime(eq(1L), eq(slotTime.toLocalDate()), eq(slotTime)))
                .thenReturn(buildRealtimeSnapshot(slotTime, 120));
        when(mtPromotionOpenApiClient.updatePlanBid(eq(candidate), eq(50), eq(slotTime), eq(window), eq("SLOT_BID_UPDATE")))
                .thenReturn(updateResult);

        ArgumentCaptor<MtPromotionBidRecord> recordCaptor = ArgumentCaptor.forClass(MtPromotionBidRecord.class);
        doAnswer(invocation -> 1).when(mtPromotionBidRecordMapper).insert(recordCaptor.capture());

        service.executeSlot(slotTime);

        MtPromotionBidRecord inserted = recordCaptor.getValue();
        assertEquals(new BigDecimal("0.50"), inserted.getNextBidAmount());
        assertEquals("EXPOSURE_GROWTH_WEAK_INCREASE_BID", inserted.getDecisionReason());
        com.gogoga.backend.service.promotion.PromotionDecisionDetail detail =
                JsonUtil.toBean(inserted.getDecisionDetailJson(), com.gogoga.backend.service.promotion.PromotionDecisionDetail.class);
        assertEquals("KEEP", detail.getFeeRateBranchAction());
        assertEquals("APPLIED", detail.getExposureBranchStatus());
        assertEquals("INCREASE", detail.getExposureBranchAction());
        assertEquals(Integer.valueOf(10), detail.getExposurePreviousFollowupExposure());
        assertEquals(Integer.valueOf(10), detail.getExposureLatestFollowupExposure());
        assertEquals("RATE_WITH_EXPOSURE", detail.getFinalAggregationMode());
        verify(mtPromotionOpenApiClient).updatePlanBid(eq(candidate), eq(50), eq(slotTime), eq(window), eq("SLOT_BID_UPDATE"));
    }

    @Test
    void shouldNotDoubleIncreaseWhenBothFeeRateAndExposureSuggestIncrease() throws Exception {
        PromotionBidDecisionService decisionService = org.mockito.Mockito.mock(PromotionBidDecisionService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        MtPromotionAutoBidService service = buildService(decisionService, objectMapper);

        LocalDateTime slotTime = LocalDateTime.of(2026, 4, 8, 12, 30);
        MtPromotionAutoBidCandidate candidate = buildCandidate();
        PromotionExecutionWindow window = PromotionExecutionWindow.of(
                LocalDateTime.of(2026, 4, 8, 10, 0),
                LocalDateTime.of(2026, 4, 8, 22, 0)
        );
        PromotionMetricsSnapshot snapshot = new PromotionMetricsSnapshot();
        snapshot.setPromotionBalance(new BigDecimal("100.00"));
        snapshot.setWindowIncome(new BigDecimal("66.00"));
        snapshot.setWindowCostRatio(new BigDecimal("0.050000"));
        snapshot.setSlotCostDelta(BigDecimal.ZERO.setScale(2));
        snapshot.setMetricsTrusted(true);
        PromotionPlanStatusOpenApiResult statusOpenApiResult = new PromotionPlanStatusOpenApiResult();
        statusOpenApiResult.setSuccess(true);
        statusOpenApiResult.setStatus(MtPromotionOpenApiClient.STATUS_OPEN);
        statusOpenApiResult.setPayloadJson("{\"status\":0}");
        PromotionDecisionResult decisionResult = new PromotionDecisionResult();
        decisionResult.setPreviousBidAmount(new BigDecimal("0.40"));
        decisionResult.setNextBidAmount(new BigDecimal("0.50"));
        decisionResult.setDecisionAction("INCREASE");
        decisionResult.setDecisionReason("NO_CLICK_COST_DELTA_ZERO");
        decisionResult.setDecisionDetailJson("{\"decisionSource\":\"rule\"}");
        PromotionBidOpenApiResult updateResult = new PromotionBidOpenApiResult();
        updateResult.setSuccess(true);
        updateResult.setPayloadJson("{\"ok\":true}");
        updateResult.setBid(50);
        MtPromotionSetting currentSetting = buildCurrentSetting(true, candidate.getStrategyConfigJson());

        when(mtPromotionSettingMapper.selectAutoBidCandidates(slotTime)).thenReturn(List.of(candidate));
        when(mtPromotionBidRecordMapper.selectByShopIdAndBizDateAndSlotTime(eq(1L), any(), eq(slotTime))).thenReturn(null);
        when(mtPromotionSettingService.normalizeForExecution(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mtPromotionSettingService.getOrCreateByShopId(1L)).thenReturn(currentSetting);
        when(mtPowerAccountService.getOrCreateAccount(1L)).thenReturn(buildPowerAccount(25000));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(slotTime.minusMinutes(10))))
                .thenReturn(new PromotionTimeWindowResult(true, "AUTO_TIME_MODE", window));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(slotTime)))
                .thenReturn(new PromotionTimeWindowResult(true, "AUTO_TIME_MODE", window));
        when(mtPromotionOpenApiClient.queryPlanStatus(eq(candidate), eq(slotTime), eq(window), eq("WINDOW_STATUS_CHECK")))
                .thenReturn(statusOpenApiResult);
        when(promotionMetricsProvider.queryMetrics(eq(candidate), any(PromotionExecutionWindow.class), eq(slotTime))).thenReturn(snapshot);
        when(mtPromotionBidRecordMapper.selectLatestSuccessByShopIdAndWindowKey(eq(1L), eq(window.getWindowKey()), anyInt()))
                .thenReturn(List.of(
                        buildHistory(slotTime.minusMinutes(10), "0.40", "3.10"),
                        buildHistory(slotTime.minusMinutes(20), "0.30", "3.00")
                ));
        when(decisionService.decide(any(), eq(snapshot), any(), eq(slotTime), any())).thenReturn(decisionResult);
        when(mtPromotionAdRealtimeSnapshotService.selectByShopIdAndBizDateAndSlotTime(eq(1L), eq(slotTime.toLocalDate()), eq(slotTime.minusMinutes(20))))
                .thenReturn(buildRealtimeSnapshot(slotTime.minusMinutes(20), 100));
        when(mtPromotionAdRealtimeSnapshotService.selectByShopIdAndBizDateAndSlotTime(eq(1L), eq(slotTime.toLocalDate()), eq(slotTime.minusMinutes(10))))
                .thenReturn(buildRealtimeSnapshot(slotTime.minusMinutes(10), 110));
        when(mtPromotionAdRealtimeSnapshotService.selectByShopIdAndBizDateAndSlotTime(eq(1L), eq(slotTime.toLocalDate()), eq(slotTime)))
                .thenReturn(buildRealtimeSnapshot(slotTime, 120));
        when(mtPromotionOpenApiClient.updatePlanBid(eq(candidate), eq(50), eq(slotTime), eq(window), eq("SLOT_BID_UPDATE")))
                .thenReturn(updateResult);

        ArgumentCaptor<MtPromotionBidRecord> recordCaptor = ArgumentCaptor.forClass(MtPromotionBidRecord.class);
        doAnswer(invocation -> 1).when(mtPromotionBidRecordMapper).insert(recordCaptor.capture());

        service.executeSlot(slotTime);

        MtPromotionBidRecord inserted = recordCaptor.getValue();
        assertEquals(new BigDecimal("0.50"), inserted.getNextBidAmount());
        assertEquals("EXPOSURE_GROWTH_WEAK_INCREASE_BID", inserted.getDecisionReason());
        com.gogoga.backend.service.promotion.PromotionDecisionDetail detail =
                JsonUtil.toBean(inserted.getDecisionDetailJson(), com.gogoga.backend.service.promotion.PromotionDecisionDetail.class);
        assertEquals("INCREASE", detail.getFeeRateBranchAction());
        assertEquals("EXPOSURE_INCREASE", detail.getFinalAggregationSource());
        verify(mtPromotionOpenApiClient).updatePlanBid(eq(candidate), eq(50), eq(slotTime), eq(window), eq("SLOT_BID_UPDATE"));
    }

    @Test
    void shouldKeepBidWhenFeeRateAndExposureSignalsConflict() throws Exception {
        PromotionBidDecisionService decisionService = org.mockito.Mockito.mock(PromotionBidDecisionService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        MtPromotionAutoBidService service = buildService(decisionService, objectMapper);

        LocalDateTime slotTime = LocalDateTime.of(2026, 4, 8, 12, 30);
        MtPromotionAutoBidCandidate candidate = buildCandidate();
        PromotionExecutionWindow window = PromotionExecutionWindow.of(
                LocalDateTime.of(2026, 4, 8, 10, 0),
                LocalDateTime.of(2026, 4, 8, 22, 0)
        );
        PromotionMetricsSnapshot snapshot = new PromotionMetricsSnapshot();
        snapshot.setPromotionBalance(new BigDecimal("100.00"));
        snapshot.setWindowIncome(new BigDecimal("66.00"));
        snapshot.setWindowCostRatio(new BigDecimal("0.050000"));
        snapshot.setSlotCostDelta(new BigDecimal("0.10"));
        snapshot.setMetricsTrusted(true);
        PromotionPlanStatusOpenApiResult statusOpenApiResult = new PromotionPlanStatusOpenApiResult();
        statusOpenApiResult.setSuccess(true);
        statusOpenApiResult.setStatus(MtPromotionOpenApiClient.STATUS_OPEN);
        statusOpenApiResult.setPayloadJson("{\"status\":0}");
        PromotionDecisionResult decisionResult = new PromotionDecisionResult();
        decisionResult.setPreviousBidAmount(new BigDecimal("0.40"));
        decisionResult.setNextBidAmount(new BigDecimal("0.30"));
        decisionResult.setDecisionAction("DECREASE");
        decisionResult.setDecisionReason("TOTAL_COST_RATIO_ABOVE_MAX");
        decisionResult.setDecisionDetailJson("{\"decisionSource\":\"rule\"}");
        PromotionBidOpenApiResult updateResult = new PromotionBidOpenApiResult();
        updateResult.setSuccess(true);
        updateResult.setPayloadJson("{\"ok\":true}");
        updateResult.setBid(40);
        MtPromotionSetting currentSetting = buildCurrentSetting(true, candidate.getStrategyConfigJson());

        when(mtPromotionSettingMapper.selectAutoBidCandidates(slotTime)).thenReturn(List.of(candidate));
        when(mtPromotionBidRecordMapper.selectByShopIdAndBizDateAndSlotTime(eq(1L), any(), eq(slotTime))).thenReturn(null);
        when(mtPromotionSettingService.normalizeForExecution(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mtPromotionSettingService.getOrCreateByShopId(1L)).thenReturn(currentSetting);
        when(mtPowerAccountService.getOrCreateAccount(1L)).thenReturn(buildPowerAccount(25000));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(slotTime.minusMinutes(10))))
                .thenReturn(new PromotionTimeWindowResult(true, "AUTO_TIME_MODE", window));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(slotTime)))
                .thenReturn(new PromotionTimeWindowResult(true, "AUTO_TIME_MODE", window));
        when(mtPromotionOpenApiClient.queryPlanStatus(eq(candidate), eq(slotTime), eq(window), eq("WINDOW_STATUS_CHECK")))
                .thenReturn(statusOpenApiResult);
        when(promotionMetricsProvider.queryMetrics(eq(candidate), any(PromotionExecutionWindow.class), eq(slotTime))).thenReturn(snapshot);
        when(mtPromotionBidRecordMapper.selectLatestSuccessByShopIdAndWindowKey(eq(1L), eq(window.getWindowKey()), anyInt()))
                .thenReturn(List.of(
                        buildHistory(slotTime.minusMinutes(10), "0.40", "3.10"),
                        buildHistory(slotTime.minusMinutes(20), "0.30", "3.00")
                ));
        when(decisionService.decide(any(), eq(snapshot), any(), eq(slotTime), any())).thenReturn(decisionResult);
        when(mtPromotionAdRealtimeSnapshotService.selectByShopIdAndBizDateAndSlotTime(eq(1L), eq(slotTime.toLocalDate()), eq(slotTime.minusMinutes(20))))
                .thenReturn(buildRealtimeSnapshot(slotTime.minusMinutes(20), 100));
        when(mtPromotionAdRealtimeSnapshotService.selectByShopIdAndBizDateAndSlotTime(eq(1L), eq(slotTime.toLocalDate()), eq(slotTime.minusMinutes(10))))
                .thenReturn(buildRealtimeSnapshot(slotTime.minusMinutes(10), 110));
        when(mtPromotionAdRealtimeSnapshotService.selectByShopIdAndBizDateAndSlotTime(eq(1L), eq(slotTime.toLocalDate()), eq(slotTime)))
                .thenReturn(buildRealtimeSnapshot(slotTime, 120));
        when(mtPromotionOpenApiClient.updatePlanBid(eq(candidate), eq(40), eq(slotTime), eq(window), eq("SLOT_BID_UPDATE")))
                .thenReturn(updateResult);

        ArgumentCaptor<MtPromotionBidRecord> recordCaptor = ArgumentCaptor.forClass(MtPromotionBidRecord.class);
        doAnswer(invocation -> 1).when(mtPromotionBidRecordMapper).insert(recordCaptor.capture());

        service.executeSlot(slotTime);

        MtPromotionBidRecord inserted = recordCaptor.getValue();
        assertEquals(new BigDecimal("0.40"), inserted.getNextBidAmount());
        assertEquals("RATE_EXPOSURE_CONFLICT_KEEP_BID", inserted.getDecisionReason());
        com.gogoga.backend.service.promotion.PromotionDecisionDetail detail =
                JsonUtil.toBean(inserted.getDecisionDetailJson(), com.gogoga.backend.service.promotion.PromotionDecisionDetail.class);
        assertEquals("RATE_EXPOSURE_CONFLICT", detail.getFinalAggregationSource());
        verify(mtPromotionOpenApiClient).updatePlanBid(eq(candidate), eq(40), eq(slotTime), eq(window), eq("SLOT_BID_UPDATE"));
    }

    @Test
    void shouldFallbackToFeeRateOnlyWhenCurrentRateExceedsCeiling() throws Exception {
        PromotionBidDecisionService decisionService = org.mockito.Mockito.mock(PromotionBidDecisionService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        MtPromotionAutoBidService service = buildService(decisionService, objectMapper);

        LocalDateTime slotTime = LocalDateTime.of(2026, 4, 8, 12, 30);
        MtPromotionAutoBidCandidate candidate = buildCandidate();
        PromotionExecutionWindow window = PromotionExecutionWindow.of(
                LocalDateTime.of(2026, 4, 8, 10, 0),
                LocalDateTime.of(2026, 4, 8, 22, 0)
        );
        PromotionMetricsSnapshot snapshot = new PromotionMetricsSnapshot();
        snapshot.setPromotionBalance(new BigDecimal("100.00"));
        snapshot.setWindowIncome(new BigDecimal("66.00"));
        snapshot.setWindowCostRatio(new BigDecimal("0.120000"));
        snapshot.setSlotCostDelta(new BigDecimal("0.10"));
        snapshot.setMetricsTrusted(true);
        PromotionPlanStatusOpenApiResult statusOpenApiResult = new PromotionPlanStatusOpenApiResult();
        statusOpenApiResult.setSuccess(true);
        statusOpenApiResult.setStatus(MtPromotionOpenApiClient.STATUS_OPEN);
        statusOpenApiResult.setPayloadJson("{\"status\":0}");
        PromotionDecisionResult decisionResult = new PromotionDecisionResult();
        decisionResult.setPreviousBidAmount(new BigDecimal("0.40"));
        decisionResult.setNextBidAmount(new BigDecimal("0.30"));
        decisionResult.setDecisionAction("DECREASE");
        decisionResult.setDecisionReason("TOTAL_COST_RATIO_ABOVE_MAX");
        decisionResult.setDecisionDetailJson("{\"decisionSource\":\"rule\"}");
        PromotionBidOpenApiResult updateResult = new PromotionBidOpenApiResult();
        updateResult.setSuccess(true);
        updateResult.setPayloadJson("{\"ok\":true}");
        updateResult.setBid(30);
        MtPromotionSetting currentSetting = buildCurrentSetting(true, candidate.getStrategyConfigJson());

        when(mtPromotionSettingMapper.selectAutoBidCandidates(slotTime)).thenReturn(List.of(candidate));
        when(mtPromotionBidRecordMapper.selectByShopIdAndBizDateAndSlotTime(eq(1L), any(), eq(slotTime))).thenReturn(null);
        when(mtPromotionSettingService.normalizeForExecution(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mtPromotionSettingService.getOrCreateByShopId(1L)).thenReturn(currentSetting);
        when(mtPowerAccountService.getOrCreateAccount(1L)).thenReturn(buildPowerAccount(25000));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(slotTime.minusMinutes(10))))
                .thenReturn(new PromotionTimeWindowResult(true, "AUTO_TIME_MODE", window));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(slotTime)))
                .thenReturn(new PromotionTimeWindowResult(true, "AUTO_TIME_MODE", window));
        when(mtPromotionOpenApiClient.queryPlanStatus(eq(candidate), eq(slotTime), eq(window), eq("WINDOW_STATUS_CHECK")))
                .thenReturn(statusOpenApiResult);
        when(promotionMetricsProvider.queryMetrics(eq(candidate), any(PromotionExecutionWindow.class), eq(slotTime))).thenReturn(snapshot);
        when(mtPromotionBidRecordMapper.selectLatestSuccessByShopIdAndWindowKey(eq(1L), eq(window.getWindowKey()), anyInt()))
                .thenReturn(List.of(
                        buildHistory(slotTime.minusMinutes(10), "0.40", "3.10"),
                        buildHistory(slotTime.minusMinutes(20), "0.30", "3.00")
                ));
        when(decisionService.decide(any(), eq(snapshot), any(), eq(slotTime), any())).thenReturn(decisionResult);
        when(mtPromotionOpenApiClient.updatePlanBid(eq(candidate), eq(30), eq(slotTime), eq(window), eq("SLOT_BID_UPDATE")))
                .thenReturn(updateResult);

        ArgumentCaptor<MtPromotionBidRecord> recordCaptor = ArgumentCaptor.forClass(MtPromotionBidRecord.class);
        doAnswer(invocation -> 1).when(mtPromotionBidRecordMapper).insert(recordCaptor.capture());

        service.executeSlot(slotTime);

        MtPromotionBidRecord inserted = recordCaptor.getValue();
        assertEquals(new BigDecimal("0.30"), inserted.getNextBidAmount());
        assertEquals("TOTAL_COST_RATIO_ABOVE_MAX", inserted.getDecisionReason());
        com.gogoga.backend.service.promotion.PromotionDecisionDetail detail =
                JsonUtil.toBean(inserted.getDecisionDetailJson(), com.gogoga.backend.service.promotion.PromotionDecisionDetail.class);
        assertEquals("SKIPPED_BY_RATE_CEILING", detail.getExposureBranchStatus());
        assertEquals("RATE_ONLY", detail.getFinalAggregationMode());
        verify(mtPromotionOpenApiClient).updatePlanBid(eq(candidate), eq(30), eq(slotTime), eq(window), eq("SLOT_BID_UPDATE"));
    }

    @Test
    void shouldRollbackToPreviousBidOfFirstExposureRecordAfterThreeWeakExposureIncreases() throws Exception {
        PromotionBidDecisionService decisionService = org.mockito.Mockito.mock(PromotionBidDecisionService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        MtPromotionAutoBidService service = buildService(decisionService, objectMapper);

        LocalDateTime slotTime = LocalDateTime.of(2026, 4, 8, 12, 30);
        MtPromotionAutoBidCandidate candidate = buildCandidate();
        PromotionExecutionWindow window = PromotionExecutionWindow.of(
                LocalDateTime.of(2026, 4, 8, 10, 0),
                LocalDateTime.of(2026, 4, 8, 22, 0)
        );
        PromotionMetricsSnapshot snapshot = buildTrustedSnapshot("3.30", "0.30");
        PromotionDecisionResult decisionResult = buildMockDecisionResult("0.80", "0.80", "KEEP", "CLICK_DETECTED_KEEP_PREVIOUS_BID");
        MtPromotionSetting currentSetting = buildCurrentSetting(true, candidate.getStrategyConfigJson());
        List<MtPromotionBidRecord> history = buildExposureWeakIncreaseHistory(slotTime);

        stubCommonAutoBidPrerequisites(slotTime, candidate, window, snapshot, currentSetting);
        when(mtPromotionBidRecordMapper.selectLatestSuccessByShopIdAndWindowKey(eq(1L), eq(window.getWindowKey()), anyInt()))
                .thenReturn(history);
        when(decisionService.decide(any(), eq(snapshot), any(), eq(slotTime), any())).thenReturn(decisionResult);
        stubExposureSeries(slotTime, 100, 108, 123, 132, 142, 153);
        when(mtPromotionOpenApiClient.updatePlanBid(eq(candidate), eq(50), eq(slotTime), eq(window), eq("SLOT_BID_UPDATE")))
                .thenReturn(buildUpdateResult(50));

        ArgumentCaptor<MtPromotionBidRecord> recordCaptor = ArgumentCaptor.forClass(MtPromotionBidRecord.class);
        doAnswer(invocation -> 1).when(mtPromotionBidRecordMapper).insert(recordCaptor.capture());

        service.executeSlot(slotTime);

        MtPromotionBidRecord inserted = recordCaptor.getValue();
        assertEquals(new BigDecimal("0.50"), inserted.getNextBidAmount());
        assertEquals("EXPOSURE_WEAK_GROWTH_ROLLBACK_BID", inserted.getDecisionReason());
        PromotionDecisionDetail detail = JsonUtil.toBean(inserted.getDecisionDetailJson(), PromotionDecisionDetail.class);
        assertEquals(Boolean.TRUE, detail.getExposureWeakGrowthRollbackApplied());
        assertEquals(Integer.valueOf(3), detail.getExposureWeakGrowthConsecutiveCount());
        assertEquals(slotTime.minusMinutes(30), detail.getExposureWeakGrowthBestSlotTime());
        assertEquals(new BigDecimal("0.50"), detail.getExposureWeakGrowthBestPreviousBidAmount());
        assertEquals(new BigDecimal("0.50"), detail.getExposureWeakGrowthRollbackBidAmount());
        assertEquals("EXPOSURE_WEAK_GROWTH_ROLLBACK", detail.getFinalAggregationSource());
        verify(mtPromotionOpenApiClient).updatePlanBid(eq(candidate), eq(50), eq(slotTime), eq(window), eq("SLOT_BID_UPDATE"));
    }

    @Test
    void shouldNotRollbackWhenExposureIncreaseStreakIsInterrupted() throws Exception {
        PromotionBidDecisionService decisionService = org.mockito.Mockito.mock(PromotionBidDecisionService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        MtPromotionAutoBidService service = buildService(decisionService, objectMapper);

        LocalDateTime slotTime = LocalDateTime.of(2026, 4, 8, 12, 30);
        MtPromotionAutoBidCandidate candidate = buildCandidate();
        PromotionExecutionWindow window = PromotionExecutionWindow.of(
                LocalDateTime.of(2026, 4, 8, 10, 0),
                LocalDateTime.of(2026, 4, 8, 22, 0)
        );
        PromotionMetricsSnapshot snapshot = buildTrustedSnapshot("3.30", "0.30");
        PromotionDecisionResult decisionResult = buildMockDecisionResult("0.80", "0.80", "KEEP", "CLICK_DETECTED_KEEP_PREVIOUS_BID");
        MtPromotionSetting currentSetting = buildCurrentSetting(true, candidate.getStrategyConfigJson());
        List<MtPromotionBidRecord> history = buildExposureWeakIncreaseHistory(slotTime);
        history.get(2).setDecisionDetailJson("{}");
        history.get(2).setDecisionReason("CLICK_DETECTED_KEEP_PREVIOUS_BID");

        stubCommonAutoBidPrerequisites(slotTime, candidate, window, snapshot, currentSetting);
        when(mtPromotionBidRecordMapper.selectLatestSuccessByShopIdAndWindowKey(eq(1L), eq(window.getWindowKey()), anyInt()))
                .thenReturn(history);
        when(decisionService.decide(any(), eq(snapshot), any(), eq(slotTime), any())).thenReturn(decisionResult);
        stubExposureSeries(slotTime, 100, 108, 123, 132, 142, 153);
        when(mtPromotionOpenApiClient.updatePlanBid(eq(candidate), eq(90), eq(slotTime), eq(window), eq("SLOT_BID_UPDATE")))
                .thenReturn(buildUpdateResult(90));

        ArgumentCaptor<MtPromotionBidRecord> recordCaptor = ArgumentCaptor.forClass(MtPromotionBidRecord.class);
        doAnswer(invocation -> 1).when(mtPromotionBidRecordMapper).insert(recordCaptor.capture());

        service.executeSlot(slotTime);

        MtPromotionBidRecord inserted = recordCaptor.getValue();
        assertEquals(new BigDecimal("0.90"), inserted.getNextBidAmount());
        assertEquals("EXPOSURE_GROWTH_WEAK_INCREASE_BID", inserted.getDecisionReason());
        PromotionDecisionDetail detail = JsonUtil.toBean(inserted.getDecisionDetailJson(), PromotionDecisionDetail.class);
        assertEquals(Boolean.FALSE, detail.getExposureWeakGrowthRollbackApplied());
        assertEquals("EXPOSURE_INCREASE_STREAK_INTERRUPTED", detail.getExposureWeakGrowthRollbackUnavailableReason());
        assertEquals("EXPOSURE_INCREASE", detail.getFinalAggregationSource());
    }

    @Test
    void shouldNotRollbackWhenThreeRecordExposureStreakIsInsufficient() throws Exception {
        PromotionBidDecisionService decisionService = org.mockito.Mockito.mock(PromotionBidDecisionService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        MtPromotionAutoBidService service = buildService(decisionService, objectMapper);

        LocalDateTime slotTime = LocalDateTime.of(2026, 4, 8, 12, 30);
        MtPromotionAutoBidCandidate candidate = buildCandidate();
        PromotionExecutionWindow window = PromotionExecutionWindow.of(
                LocalDateTime.of(2026, 4, 8, 10, 0),
                LocalDateTime.of(2026, 4, 8, 22, 0)
        );
        PromotionMetricsSnapshot snapshot = buildTrustedSnapshot("3.30", "0.30");
        PromotionDecisionResult decisionResult = buildMockDecisionResult("0.80", "0.80", "KEEP", "CLICK_DETECTED_KEEP_PREVIOUS_BID");
        MtPromotionSetting currentSetting = buildCurrentSetting(true, candidate.getStrategyConfigJson());

        stubCommonAutoBidPrerequisites(slotTime, candidate, window, snapshot, currentSetting);
        when(mtPromotionBidRecordMapper.selectLatestSuccessByShopIdAndWindowKey(eq(1L), eq(window.getWindowKey()), anyInt()))
                .thenReturn(buildExposureWeakIncreaseHistory(slotTime).subList(0, 2));
        when(decisionService.decide(any(), eq(snapshot), any(), eq(slotTime), any())).thenReturn(decisionResult);
        stubExposureSeries(slotTime, 100, 108, 123, 132, 142, 153);
        when(mtPromotionOpenApiClient.updatePlanBid(eq(candidate), eq(90), eq(slotTime), eq(window), eq("SLOT_BID_UPDATE")))
                .thenReturn(buildUpdateResult(90));

        ArgumentCaptor<MtPromotionBidRecord> recordCaptor = ArgumentCaptor.forClass(MtPromotionBidRecord.class);
        doAnswer(invocation -> 1).when(mtPromotionBidRecordMapper).insert(recordCaptor.capture());

        service.executeSlot(slotTime);

        PromotionDecisionDetail detail = JsonUtil.toBean(recordCaptor.getValue().getDecisionDetailJson(), PromotionDecisionDetail.class);
        assertEquals(Boolean.FALSE, detail.getExposureWeakGrowthRollbackApplied());
        assertEquals("SUCCESS_HISTORY_STREAK_INSUFFICIENT", detail.getExposureWeakGrowthRollbackUnavailableReason());
        assertEquals(new BigDecimal("0.90"), recordCaptor.getValue().getNextBidAmount());
    }

    @Test
    void shouldCountCombinedRateAndExposureIncreaseForRollbackStreak() throws Exception {
        PromotionBidDecisionService decisionService = org.mockito.Mockito.mock(PromotionBidDecisionService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        MtPromotionAutoBidService service = buildService(decisionService, objectMapper);

        LocalDateTime slotTime = LocalDateTime.of(2026, 4, 8, 12, 30);
        MtPromotionAutoBidCandidate candidate = buildCandidate();
        PromotionExecutionWindow window = PromotionExecutionWindow.of(
                LocalDateTime.of(2026, 4, 8, 10, 0),
                LocalDateTime.of(2026, 4, 8, 22, 0)
        );
        PromotionMetricsSnapshot snapshot = buildTrustedSnapshot("3.30", "0.00");
        PromotionDecisionResult decisionResult = buildMockDecisionResult("0.80", "0.90", "INCREASE", "NO_CLICK_COST_DELTA_ZERO");
        MtPromotionSetting currentSetting = buildCurrentSetting(true, candidate.getStrategyConfigJson());

        stubCommonAutoBidPrerequisites(slotTime, candidate, window, snapshot, currentSetting);
        when(mtPromotionBidRecordMapper.selectLatestSuccessByShopIdAndWindowKey(eq(1L), eq(window.getWindowKey()), anyInt()))
                .thenReturn(buildExposureWeakIncreaseHistory(slotTime));
        when(decisionService.decide(any(), eq(snapshot), any(), eq(slotTime), any())).thenReturn(decisionResult);
        stubExposureSeries(slotTime, 100, 108, 123, 132, 142, 153);
        when(mtPromotionOpenApiClient.updatePlanBid(eq(candidate), eq(50), eq(slotTime), eq(window), eq("SLOT_BID_UPDATE")))
                .thenReturn(buildUpdateResult(50));

        ArgumentCaptor<MtPromotionBidRecord> recordCaptor = ArgumentCaptor.forClass(MtPromotionBidRecord.class);
        doAnswer(invocation -> 1).when(mtPromotionBidRecordMapper).insert(recordCaptor.capture());

        service.executeSlot(slotTime);

        MtPromotionBidRecord inserted = recordCaptor.getValue();
        PromotionDecisionDetail detail = JsonUtil.toBean(inserted.getDecisionDetailJson(), PromotionDecisionDetail.class);
        assertEquals("INCREASE", detail.getFeeRateBranchAction());
        assertEquals(Boolean.TRUE, detail.getExposureWeakGrowthRollbackApplied());
        assertEquals(new BigDecimal("0.50"), inserted.getNextBidAmount());
    }

    @Test
    void shouldClampBidToFourYuanBeforeSyncingToMeituan() throws Exception {
        PromotionBidDecisionService decisionService = org.mockito.Mockito.mock(PromotionBidDecisionService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        MtPromotionAutoBidService service = buildService(decisionService, objectMapper);

        LocalDateTime slotTime = LocalDateTime.of(2026, 4, 8, 12, 30);
        MtPromotionAutoBidCandidate candidate = buildCandidate();
        PromotionExecutionWindow window = PromotionExecutionWindow.of(
                LocalDateTime.of(2026, 4, 8, 10, 0),
                LocalDateTime.of(2026, 4, 8, 22, 0)
        );
        PromotionMetricsSnapshot snapshot = new PromotionMetricsSnapshot();
        snapshot.setPromotionBalance(new BigDecimal("100.00"));
        snapshot.setMetricsTrusted(true);
        PromotionPlanStatusOpenApiResult statusOpenApiResult = new PromotionPlanStatusOpenApiResult();
        statusOpenApiResult.setSuccess(true);
        statusOpenApiResult.setStatus(MtPromotionOpenApiClient.STATUS_OPEN);
        statusOpenApiResult.setPayloadJson("{\"status\":0}");
        PromotionDecisionResult decisionResult = new PromotionDecisionResult();
        decisionResult.setPreviousBidAmount(new BigDecimal("0.40"));
        decisionResult.setNextBidAmount(new BigDecimal("6.66"));
        decisionResult.setDecisionAction("INCREASE");
        decisionResult.setDecisionReason("RULE_RAISE");
        decisionResult.setDecisionDetailJson("{\"decisionSource\":\"rule\"}");
        PromotionBidOpenApiResult updateResult = new PromotionBidOpenApiResult();
        updateResult.setSuccess(true);
        updateResult.setPayloadJson("{\"ok\":true}");
        updateResult.setBid(400);
        MtPromotionSetting currentSetting = buildCurrentSetting(true, candidate.getStrategyConfigJson());

        when(mtPromotionSettingMapper.selectAutoBidCandidates(slotTime)).thenReturn(List.of(candidate));
        when(mtPromotionBidRecordMapper.selectByShopIdAndBizDateAndSlotTime(eq(1L), any(), eq(slotTime))).thenReturn(null);
        when(mtPromotionSettingService.normalizeForExecution(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mtPromotionSettingService.getOrCreateByShopId(1L)).thenReturn(currentSetting);
        when(mtPowerAccountService.getOrCreateAccount(1L)).thenReturn(buildPowerAccount(25000));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(slotTime.minusMinutes(10))))
                .thenReturn(new PromotionTimeWindowResult(true, "BUSINESS_TIME_MATCHED", window));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(slotTime)))
                .thenReturn(new PromotionTimeWindowResult(true, "BUSINESS_TIME_MATCHED", window));
        when(mtPromotionOpenApiClient.queryPlanStatus(eq(candidate), eq(slotTime), eq(window), eq("WINDOW_STATUS_CHECK")))
                .thenReturn(statusOpenApiResult);
        when(promotionMetricsProvider.queryMetrics(eq(candidate), eq(window), eq(slotTime))).thenReturn(snapshot);
        when(mtPromotionBidRecordMapper.selectLatestSuccessByShopIdAndWindowKey(eq(1L), eq(window.getWindowKey()), anyInt()))
                .thenReturn(List.of(buildHistory(slotTime.minusMinutes(10), "0.40", "3.10")));
        when(decisionService.decide(any(), eq(snapshot), any(), eq(slotTime), any())).thenReturn(decisionResult);
        when(mtPromotionOpenApiClient.updatePlanBid(eq(candidate), eq(400), eq(slotTime), eq(window), eq("SLOT_BID_UPDATE")))
                .thenReturn(updateResult);

        ArgumentCaptor<MtPromotionBidRecord> recordCaptor = ArgumentCaptor.forClass(MtPromotionBidRecord.class);
        doAnswer(invocation -> 1).when(mtPromotionBidRecordMapper).insert(recordCaptor.capture());

        service.executeSlot(slotTime);

        MtPromotionBidRecord inserted = recordCaptor.getValue();
        assertNotNull(inserted);
        assertEquals(new BigDecimal("4.00"), inserted.getNextBidAmount());
        verify(mtPromotionOpenApiClient).updatePlanBid(eq(candidate), eq(400), eq(slotTime), eq(window), eq("SLOT_BID_UPDATE"));
    }

    @Test
    void shouldKeepBidWhenIncreaseWouldExceedMaxBid() throws Exception {
        PromotionBidDecisionService decisionService = org.mockito.Mockito.mock(PromotionBidDecisionService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        MtPromotionAutoBidService service = buildService(decisionService, objectMapper);

        LocalDateTime slotTime = LocalDateTime.of(2026, 4, 8, 12, 40);
        MtPromotionAutoBidCandidate candidate = buildCandidate();
        PromotionExecutionWindow window = PromotionExecutionWindow.of(
                LocalDateTime.of(2026, 4, 8, 10, 0),
                LocalDateTime.of(2026, 4, 8, 22, 0)
        );
        PromotionMetricsSnapshot snapshot = new PromotionMetricsSnapshot();
        snapshot.setPromotionBalance(new BigDecimal("100.00"));
        snapshot.setMetricsTrusted(true);
        PromotionPlanStatusOpenApiResult statusOpenApiResult = new PromotionPlanStatusOpenApiResult();
        statusOpenApiResult.setSuccess(true);
        statusOpenApiResult.setStatus(MtPromotionOpenApiClient.STATUS_OPEN);
        statusOpenApiResult.setPayloadJson("{\"status\":0}");
        PromotionDecisionResult decisionResult = new PromotionDecisionResult();
        decisionResult.setPreviousBidAmount(new BigDecimal("4.00"));
        decisionResult.setNextBidAmount(new BigDecimal("4.10"));
        decisionResult.setDecisionAction("INCREASE");
        decisionResult.setDecisionReason("NO_CLICK_COST_DELTA_ZERO");
        decisionResult.setDecisionDetailJson("{\"decisionSource\":\"rule\"}");
        PromotionBidOpenApiResult updateResult = new PromotionBidOpenApiResult();
        updateResult.setSuccess(true);
        updateResult.setPayloadJson("{\"ok\":true}");
        updateResult.setBid(400);
        MtPromotionSetting currentSetting = buildCurrentSetting(true, candidate.getStrategyConfigJson());

        when(mtPromotionSettingMapper.selectAutoBidCandidates(slotTime)).thenReturn(List.of(candidate));
        when(mtPromotionBidRecordMapper.selectByShopIdAndBizDateAndSlotTime(eq(1L), any(), eq(slotTime))).thenReturn(null);
        when(mtPromotionSettingService.normalizeForExecution(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mtPromotionSettingService.getOrCreateByShopId(1L)).thenReturn(currentSetting);
        when(mtPowerAccountService.getOrCreateAccount(1L)).thenReturn(buildPowerAccount(25000));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(slotTime.minusMinutes(10))))
                .thenReturn(new PromotionTimeWindowResult(true, "BUSINESS_TIME_MATCHED", window));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(slotTime)))
                .thenReturn(new PromotionTimeWindowResult(true, "BUSINESS_TIME_MATCHED", window));
        when(mtPromotionOpenApiClient.queryPlanStatus(eq(candidate), eq(slotTime), eq(window), eq("WINDOW_STATUS_CHECK")))
                .thenReturn(statusOpenApiResult);
        when(promotionMetricsProvider.queryMetrics(eq(candidate), eq(window), eq(slotTime))).thenReturn(snapshot);
        when(mtPromotionBidRecordMapper.selectLatestSuccessByShopIdAndWindowKey(eq(1L), eq(window.getWindowKey()), anyInt()))
                .thenReturn(List.of(buildHistory(slotTime.minusMinutes(10), "4.00", "3.10")));
        when(decisionService.decide(any(), eq(snapshot), any(), eq(slotTime), any())).thenReturn(decisionResult);
        when(mtPromotionOpenApiClient.updatePlanBid(eq(candidate), eq(400), eq(slotTime), eq(window), eq("SLOT_BID_UPDATE")))
                .thenReturn(updateResult);

        ArgumentCaptor<MtPromotionBidRecord> recordCaptor = ArgumentCaptor.forClass(MtPromotionBidRecord.class);
        doAnswer(invocation -> 1).when(mtPromotionBidRecordMapper).insert(recordCaptor.capture());

        service.executeSlot(slotTime);

        MtPromotionBidRecord inserted = recordCaptor.getValue();
        assertNotNull(inserted);
        assertEquals(new BigDecimal("4.00"), inserted.getPreviousBidAmount());
        assertEquals(new BigDecimal("4.00"), inserted.getNextBidAmount());
        assertEquals("KEEP", inserted.getDecisionAction());
        assertEquals("MAX_BID_REACHED_KEEP", inserted.getDecisionReason());
        verify(mtPromotionOpenApiClient).updatePlanBid(eq(candidate), eq(400), eq(slotTime), eq(window), eq("SLOT_BID_UPDATE"));
    }

    @Test
    void shouldUseCachedShopMaxBidInDecision() throws Exception {
        PromotionBidDecisionService decisionService = org.mockito.Mockito.mock(PromotionBidDecisionService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        MtPromotionAutoBidService service = buildServiceWithBidConstraint(decisionService, objectMapper);

        LocalDateTime slotTime = LocalDateTime.of(2026, 4, 8, 12, 50);
        MtPromotionAutoBidCandidate candidate = buildCandidate();
        PromotionExecutionWindow window = PromotionExecutionWindow.of(
                LocalDateTime.of(2026, 4, 8, 10, 0),
                LocalDateTime.of(2026, 4, 8, 22, 0)
        );
        PromotionMetricsSnapshot snapshot = new PromotionMetricsSnapshot();
        snapshot.setPromotionBalance(new BigDecimal("100.00"));
        snapshot.setMetricsTrusted(true);
        PromotionPlanStatusOpenApiResult statusOpenApiResult = buildStatusResult();
        PromotionDecisionResult decisionResult = new PromotionDecisionResult();
        decisionResult.setPreviousBidAmount(new BigDecimal("3.00"));
        decisionResult.setNextBidAmount(new BigDecimal("3.10"));
        decisionResult.setDecisionAction("INCREASE");
        decisionResult.setDecisionReason("NO_CLICK_COST_DELTA_ZERO");
        decisionResult.setDecisionDetailJson("{\"decisionSource\":\"rule\"}");
        PromotionBidOpenApiResult updateResult = buildUpdateResult(300);
        MtPromotionSetting currentSetting = buildCurrentSetting(true, candidate.getStrategyConfigJson());

        when(mtPromotionSettingMapper.selectAutoBidCandidates(slotTime)).thenReturn(List.of(candidate));
        when(mtPromotionBidRecordMapper.selectByShopIdAndBizDateAndSlotTime(eq(1L), any(), eq(slotTime))).thenReturn(null);
        when(mtPromotionSettingService.normalizeForExecution(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mtPromotionSettingService.getOrCreateByShopId(1L)).thenReturn(currentSetting);
        when(mtPowerAccountService.getOrCreateAccount(1L)).thenReturn(buildPowerAccount(25000));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(slotTime.minusMinutes(10))))
                .thenReturn(new PromotionTimeWindowResult(true, "BUSINESS_TIME_MATCHED", window));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(slotTime)))
                .thenReturn(new PromotionTimeWindowResult(true, "BUSINESS_TIME_MATCHED", window));
        when(mtPromotionOpenApiClient.queryPlanStatus(eq(candidate), eq(slotTime), eq(window), eq("WINDOW_STATUS_CHECK")))
                .thenReturn(statusOpenApiResult);
        when(promotionMetricsProvider.queryMetrics(eq(candidate), eq(window), eq(slotTime))).thenReturn(snapshot);
        when(mtPromotionBidRecordMapper.selectLatestSuccessByShopIdAndWindowKey(eq(1L), eq(window.getWindowKey()), anyInt()))
                .thenReturn(List.of(buildHistory(slotTime.minusMinutes(10), "3.00", "3.00")));
        when(mtPromotionBidConstraintService.resolveEffectiveMaxBidAmount(eq(1L), eq(new BigDecimal("4.00")), eq(slotTime)))
                .thenReturn(new BigDecimal("3.00"));
        when(decisionService.decide(any(), eq(snapshot), any(), eq(slotTime), any())).thenReturn(decisionResult);
        when(mtPromotionOpenApiClient.updatePlanBid(eq(candidate), eq(300), eq(slotTime), eq(window), eq("SLOT_BID_UPDATE")))
                .thenReturn(updateResult);

        ArgumentCaptor<MtPromotionBidRecord> recordCaptor = ArgumentCaptor.forClass(MtPromotionBidRecord.class);
        doAnswer(invocation -> 1).when(mtPromotionBidRecordMapper).insert(recordCaptor.capture());

        service.executeSlot(slotTime);

        MtPromotionBidRecord inserted = recordCaptor.getValue();
        assertNotNull(inserted);
        assertEquals(new BigDecimal("3.00"), inserted.getPreviousBidAmount());
        assertEquals(new BigDecimal("3.00"), inserted.getNextBidAmount());
        assertEquals("KEEP", inserted.getDecisionAction());
        assertEquals("MAX_BID_REACHED_KEEP", inserted.getDecisionReason());
        verify(mtPromotionOpenApiClient).updatePlanBid(eq(candidate), eq(300), eq(slotTime), eq(window), eq("SLOT_BID_UPDATE"));
    }

    @ParameterizedTest
    @CsvSource({
            "0.3,0.9,0.10,30",
            "0.3,0.9,1.50,90",
            "0.3,0.9,0.60,60",
            ",,0.10,10"
    })
    void shouldClampAutoBidDecisionToUserConfiguredBidRange(
            String bidMin,
            String bidMax,
            String decisionBid,
            int expectedBidCents
    ) throws Exception {
        PromotionBidDecisionService decisionService = org.mockito.Mockito.mock(PromotionBidDecisionService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        MtPromotionAutoBidService service = buildServiceWithBidConstraint(decisionService, objectMapper);

        LocalDateTime slotTime = LocalDateTime.of(2026, 4, 8, 13, 10);
        String strategyConfigJson = bidMin == null
                ? buildStrategyConfigJson("business")
                : buildStrategyConfigJsonWithBidRange("business", bidMin, bidMax);
        MtPromotionAutoBidCandidate candidate = buildCandidateWithStrategy(strategyConfigJson);
        PromotionExecutionWindow window = PromotionExecutionWindow.of(
                LocalDateTime.of(2026, 4, 8, 10, 0),
                LocalDateTime.of(2026, 4, 8, 22, 0)
        );
        PromotionMetricsSnapshot snapshot = new PromotionMetricsSnapshot();
        snapshot.setPromotionBalance(new BigDecimal("100.00"));
        snapshot.setMetricsTrusted(true);
        MtPromotionSetting currentSetting = buildCurrentSetting(true, strategyConfigJson);
        PromotionDecisionResult decisionResult = new PromotionDecisionResult();
        decisionResult.setPreviousBidAmount(new BigDecimal("0.50"));
        decisionResult.setNextBidAmount(new BigDecimal(decisionBid));
        decisionResult.setDecisionAction("KEEP");
        decisionResult.setDecisionReason("TEST_DECISION");
        decisionResult.setDecisionDetailJson("{\"decisionSource\":\"rule\"}");

        stubCommonAutoBidPrerequisites(slotTime, candidate, window, snapshot, currentSetting);
        when(mtPromotionBidRecordMapper.selectLatestSuccessByShopIdAndWindowKey(eq(1L), eq(window.getWindowKey()), anyInt()))
                .thenReturn(List.of(buildHistory(slotTime.minusMinutes(10), "0.50", "1.00")));
        when(mtPromotionBidConstraintService.resolveEffectiveMaxBidAmount(eq(1L), eq(new BigDecimal("4.00")), eq(slotTime)))
                .thenReturn(new BigDecimal("4.00"));
        when(decisionService.decide(any(), eq(snapshot), any(), eq(slotTime), any())).thenReturn(decisionResult);
        when(mtPromotionOpenApiClient.updatePlanBid(eq(candidate), eq(expectedBidCents), eq(slotTime), eq(window), eq("SLOT_BID_UPDATE")))
                .thenReturn(buildUpdateResult(expectedBidCents));

        ArgumentCaptor<MtPromotionBidRecord> recordCaptor = ArgumentCaptor.forClass(MtPromotionBidRecord.class);
        doAnswer(invocation -> 1).when(mtPromotionBidRecordMapper).insert(recordCaptor.capture());

        service.executeSlot(slotTime);

        MtPromotionBidRecord inserted = recordCaptor.getValue();
        assertEquals(BigDecimal.valueOf(expectedBidCents, 2), inserted.getNextBidAmount());
        PromotionDecisionDetail detail = JsonUtil.toBean(inserted.getDecisionDetailJson(), PromotionDecisionDetail.class);
        assertNotNull(detail);
        assertEquals(new BigDecimal(bidMin == null ? "0.10" : bidMin).setScale(2), detail.getUserBidMinAmount());
        assertEquals(new BigDecimal(bidMax == null ? "4.00" : bidMax).setScale(2), detail.getUserBidMaxAmount());
        assertEquals(new BigDecimal(decisionBid).setScale(2), detail.getBidBeforeUserRangeClamp());
        assertEquals(BigDecimal.valueOf(expectedBidCents, 2), detail.getBidAfterUserRangeClamp());
    }

    @Test
    void shouldRecordObservedShopMaxBidAndRetryOnce() throws Exception {
        PromotionBidDecisionService decisionService = org.mockito.Mockito.mock(PromotionBidDecisionService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        MtPromotionAutoBidService service = buildServiceWithBidConstraint(decisionService, objectMapper);

        LocalDateTime slotTime = LocalDateTime.of(2026, 4, 8, 13, 0);
        MtPromotionAutoBidCandidate candidate = buildCandidate();
        PromotionExecutionWindow window = PromotionExecutionWindow.of(
                LocalDateTime.of(2026, 4, 8, 10, 0),
                LocalDateTime.of(2026, 4, 8, 22, 0)
        );
        PromotionMetricsSnapshot snapshot = new PromotionMetricsSnapshot();
        snapshot.setPromotionBalance(new BigDecimal("100.00"));
        snapshot.setMetricsTrusted(true);
        PromotionPlanStatusOpenApiResult statusOpenApiResult = buildStatusResult();
        PromotionDecisionResult decisionResult = new PromotionDecisionResult();
        decisionResult.setPreviousBidAmount(new BigDecimal("4.00"));
        decisionResult.setNextBidAmount(new BigDecimal("4.10"));
        decisionResult.setDecisionAction("INCREASE");
        decisionResult.setDecisionReason("NO_CLICK_COST_DELTA_ZERO");
        decisionResult.setDecisionDetailJson("{\"decisionSource\":\"rule\"}");
        PromotionBidOpenApiResult firstUpdateResult = new PromotionBidOpenApiResult();
        firstUpdateResult.setSuccess(false);
        firstUpdateResult.setBid(400);
        firstUpdateResult.setErrorMsg("出价不能高于3.0元");
        firstUpdateResult.setPayloadJson("{\"responseMsg\":\"出价不能高于3.0元\"}");
        PromotionBidOpenApiResult retryUpdateResult = buildUpdateResult(300);
        MtPromotionSetting currentSetting = buildCurrentSetting(true, candidate.getStrategyConfigJson());

        when(mtPromotionSettingMapper.selectAutoBidCandidates(slotTime)).thenReturn(List.of(candidate));
        when(mtPromotionBidRecordMapper.selectByShopIdAndBizDateAndSlotTime(eq(1L), any(), eq(slotTime))).thenReturn(null);
        when(mtPromotionSettingService.normalizeForExecution(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mtPromotionSettingService.getOrCreateByShopId(1L)).thenReturn(currentSetting);
        when(mtPowerAccountService.getOrCreateAccount(1L)).thenReturn(buildPowerAccount(25000));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(slotTime.minusMinutes(10))))
                .thenReturn(new PromotionTimeWindowResult(true, "BUSINESS_TIME_MATCHED", window));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(slotTime)))
                .thenReturn(new PromotionTimeWindowResult(true, "BUSINESS_TIME_MATCHED", window));
        when(mtPromotionOpenApiClient.queryPlanStatus(eq(candidate), eq(slotTime), eq(window), eq("WINDOW_STATUS_CHECK")))
                .thenReturn(statusOpenApiResult);
        when(promotionMetricsProvider.queryMetrics(eq(candidate), eq(window), eq(slotTime))).thenReturn(snapshot);
        when(mtPromotionBidRecordMapper.selectLatestSuccessByShopIdAndWindowKey(eq(1L), eq(window.getWindowKey()), anyInt()))
                .thenReturn(List.of(buildHistory(slotTime.minusMinutes(10), "4.00", "4.00")));
        when(mtPromotionBidConstraintService.resolveEffectiveMaxBidAmount(eq(1L), eq(new BigDecimal("4.00")), eq(slotTime)))
                .thenReturn(new BigDecimal("4.00"));
        when(mtPromotionBidConstraintService.observeMaxBidLimitFromFailure(eq(candidate), eq(firstUpdateResult), eq(slotTime)))
                .thenReturn(Optional.of(new BigDecimal("3.00")));
        when(decisionService.decide(any(), eq(snapshot), any(), eq(slotTime), any())).thenReturn(decisionResult);
        when(mtPromotionOpenApiClient.updatePlanBid(eq(candidate), eq(400), eq(slotTime), eq(window), eq("SLOT_BID_UPDATE")))
                .thenReturn(firstUpdateResult);
        when(mtPromotionOpenApiClient.updatePlanBid(eq(candidate), eq(300), eq(slotTime), eq(window), eq("SLOT_BID_UPDATE_RETRY_WITH_OBSERVED_LIMIT")))
                .thenReturn(retryUpdateResult);

        ArgumentCaptor<MtPromotionBidRecord> recordCaptor = ArgumentCaptor.forClass(MtPromotionBidRecord.class);
        doAnswer(invocation -> 1).when(mtPromotionBidRecordMapper).insert(recordCaptor.capture());

        service.executeSlot(slotTime);

        MtPromotionBidRecord inserted = recordCaptor.getValue();
        assertNotNull(inserted);
        assertEquals(new BigDecimal("4.00"), inserted.getPreviousBidAmount());
        assertEquals(new BigDecimal("3.00"), inserted.getNextBidAmount());
        assertEquals("DECREASE", inserted.getDecisionAction());
        assertEquals("MAX_BID_REACHED_KEEP", inserted.getDecisionReason());
        InOrder inOrder = inOrder(mtPromotionOpenApiClient);
        inOrder.verify(mtPromotionOpenApiClient).updatePlanBid(eq(candidate), eq(400), eq(slotTime), eq(window), eq("SLOT_BID_UPDATE"));
        inOrder.verify(mtPromotionOpenApiClient).updatePlanBid(eq(candidate), eq(300), eq(slotTime), eq(window), eq("SLOT_BID_UPDATE_RETRY_WITH_OBSERVED_LIMIT"));
    }

    @Test
    void shouldClosePromotionWhenWindowEnds() throws Exception {
        PromotionBidDecisionService decisionService = new PromotionBidDecisionService(
                new com.gogoga.backend.service.promotion.PromotionDecisionEngine()
        );
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        MtPromotionAutoBidService service = buildService(decisionService, objectMapper);

        LocalDateTime slotTime = LocalDateTime.of(2026, 4, 8, 22, 0);
        MtPromotionAutoBidCandidate candidate = buildCandidate();
        PromotionExecutionWindow previousWindow = PromotionExecutionWindow.of(
                LocalDateTime.of(2026, 4, 8, 10, 0),
                LocalDateTime.of(2026, 4, 8, 22, 0)
        );
        MtPromotionSetting currentSetting = buildCurrentSetting(true, """
                {"priority":"rate","timeMode":"auto","rateMode":"control","rateMin":"5%","rateMax":"10%","decisionMode":"llm","bidMin":0.3,"bidMax":0.9}
                """);

        when(mtPromotionSettingMapper.selectAutoBidCandidates(slotTime)).thenReturn(List.of(candidate));
        when(mtPromotionBidRecordMapper.selectByShopIdAndBizDateAndSlotTime(eq(1L), any(), eq(slotTime))).thenReturn(null);
        when(mtPromotionSettingService.normalizeForExecution(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mtPromotionSettingService.getOrCreateByShopId(1L)).thenReturn(currentSetting);
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(slotTime.minusMinutes(10))))
                .thenReturn(new PromotionTimeWindowResult(true, "BUSINESS_TIME_MATCHED", previousWindow));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(slotTime)))
                .thenReturn(new PromotionTimeWindowResult(false, "OUT_OF_BUSINESS_WINDOW", null));
        PromotionBidOpenApiResult updateResult = new PromotionBidOpenApiResult();
        updateResult.setSuccess(true);
        updateResult.setPayloadJson("{\"bid\":10}");
        updateResult.setBid(10);
        when(mtPromotionOpenApiClient.updatePlanBid(
                eq(candidate),
                eq(10),
                eq(slotTime),
                eq(previousWindow),
                eq("WINDOW_END_BID_RESET")
        )).thenReturn(updateResult);

        ArgumentCaptor<MtPromotionBidRecord> recordCaptor = ArgumentCaptor.forClass(MtPromotionBidRecord.class);
        doAnswer(invocation -> 1).when(mtPromotionBidRecordMapper).insert(recordCaptor.capture());

        service.executeSlot(slotTime);

        MtPromotionBidRecord inserted = recordCaptor.getValue();
        assertNotNull(inserted);
        assertEquals("SUCCESS", inserted.getExecuteStatus());
        assertEquals("WINDOW_END_BID_RESET_TO_FLOOR", inserted.getDecisionReason());
        assertEquals(previousWindow.getWindowKey(), inserted.getWindowKey());
        assertEquals(new BigDecimal("0.10"), inserted.getNextBidAmount());
        assertNotNull(JsonUtil.toMap(inserted.getUpdatePayloadJson()).get("bidUpdate"));
    }

    @Test
    void shouldResetBidToFloorWhenSavedSettingsAreOutOfPromotionWindow() {
        PromotionBidDecisionService decisionService = new PromotionBidDecisionService(
                new com.gogoga.backend.service.promotion.PromotionDecisionEngine()
        );
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        MtPromotionAutoBidService service = buildServiceWithLogMarkdown(decisionService, objectMapper);
        service.setMtAccessTokenService(mtAccessTokenService);

        MtShop shop = new MtShop();
        shop.setId(1L);
        shop.setPoiId("32049853");
        shop.setPoiName("测试门店");
        shop.setShippingTime("09:00-22:00");
        MtPromotionSetting currentSetting = buildCurrentSetting(true, """
                {"priority":"rate","timeMode":"custom","customSlots":[{"start":"11:00","end":"13:00"}],"rateMode":"control","rateMin":"5%","rateMax":"10%","decisionMode":"rule","bidMin":0.1,"bidMax":3.0}
                """);
        MtAccessToken token = new MtAccessToken();
        token.setAccessToken("access-token");
        MtPromotionBidRecord latestSuccess = new MtPromotionBidRecord();
        latestSuccess.setNextBidAmount(new BigDecimal("1.00"));

        when(mtPromotionSettingService.getOrCreateByShopId(1L)).thenReturn(currentSetting);
        when(mtPromotionSettingService.normalizeForExecution(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mtAccessTokenService.findByShopId(1L)).thenReturn(token);
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), any(LocalDateTime.class)))
                .thenReturn(new PromotionTimeWindowResult(false, "OUT_OF_CUSTOM_WINDOW", null));
        when(mtPromotionBidRecordMapper.selectLatestSuccessByShopId(1L)).thenReturn(latestSuccess);
        PromotionBidOpenApiResult updateResult = new PromotionBidOpenApiResult();
        updateResult.setSuccess(true);
        updateResult.setPayloadJson("{\"bid\":10}");
        updateResult.setBid(10);
        when(mtPromotionOpenApiClient.updatePlanBid(
                any(MtPromotionAutoBidCandidate.class),
                eq(10),
                any(LocalDateTime.class),
                eq(null),
                eq("SETTINGS_SAVE_OUT_OF_WINDOW_BID_RESET")
        )).thenReturn(updateResult);

        ArgumentCaptor<MtPromotionBidRecord> recordCaptor = ArgumentCaptor.forClass(MtPromotionBidRecord.class);
        doAnswer(invocation -> 1).when(mtPromotionBidRecordMapper).insert(recordCaptor.capture());

        service.resetBidToFloorIfOutOfPromotionWindowAfterSettingsSave(shop);

        MtPromotionBidRecord inserted = recordCaptor.getValue();
        assertNotNull(inserted);
        assertEquals("SUCCESS", inserted.getExecuteStatus());
        assertEquals("SETTINGS_SAVE_OUT_OF_WINDOW_BID_RESET", inserted.getDecisionReason());
        assertEquals(new BigDecimal("1.00"), inserted.getPreviousBidAmount());
        assertEquals(new BigDecimal("0.10"), inserted.getNextBidAmount());
        assertEquals("READY", inserted.getLogMarkdownStatus());
        assertTrue(inserted.getMarkdownMessage().contains("策略保存后当前不在推广时段内"));
        assertNotNull(JsonUtil.toMap(inserted.getUpdatePayloadJson()).get("bidUpdate"));
        verify(mtPromotionAdDataService, never()).ensureRecentAdDataBackfillBeforeLogGeneration(any(), any());
        verify(promotionLogMarkdownService, never()).generate(any());
    }

    @Test
    void shouldSkipSettingsSaveBidResetWhenLatestSuccessfulBidAlreadyAtFloor() {
        PromotionBidDecisionService decisionService = new PromotionBidDecisionService(
                new com.gogoga.backend.service.promotion.PromotionDecisionEngine()
        );
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        MtPromotionAutoBidService service = buildServiceWithLogMarkdown(decisionService, objectMapper);
        service.setMtAccessTokenService(mtAccessTokenService);

        MtShop shop = new MtShop();
        shop.setId(1L);
        shop.setPoiId("32049853");
        shop.setPoiName("测试门店");
        shop.setShippingTime("09:00-22:00");
        MtPromotionSetting currentSetting = buildCurrentSetting(true, """
                {"priority":"rate","timeMode":"custom","customSlots":[{"start":"11:00","end":"13:00"}],"rateMode":"control","rateMin":"5%","rateMax":"10%","decisionMode":"rule","bidMin":0.1,"bidMax":3.0}
                """);
        MtAccessToken token = new MtAccessToken();
        token.setAccessToken("access-token");
        MtPromotionBidRecord latestSuccess = new MtPromotionBidRecord();
        latestSuccess.setNextBidAmount(new BigDecimal("0.10"));

        when(mtPromotionSettingService.getOrCreateByShopId(1L)).thenReturn(currentSetting);
        when(mtPromotionSettingService.normalizeForExecution(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mtAccessTokenService.findByShopId(1L)).thenReturn(token);
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), any(LocalDateTime.class)))
                .thenReturn(new PromotionTimeWindowResult(false, "OUT_OF_CUSTOM_WINDOW", null));
        when(mtPromotionBidRecordMapper.selectLatestSuccessByShopId(1L)).thenReturn(latestSuccess);

        service.resetBidToFloorIfOutOfPromotionWindowAfterSettingsSave(shop);

        verify(mtPromotionOpenApiClient, never()).updatePlanBid(any(), anyInt(), any(), any(), any());
        verify(mtPromotionBidRecordMapper, never()).insert(any());
        verify(mtPromotionAdDataService, never()).ensureRecentAdDataBackfillBeforeLogGeneration(any(), any());
        verify(promotionLogMarkdownService, never()).generate(any());
    }

    @Test
    void shouldNotPersistSkippedRecordWhenOrdinarilyOutsidePromotionWindow() throws Exception {
        PromotionBidDecisionService decisionService = new PromotionBidDecisionService(
                new com.gogoga.backend.service.promotion.PromotionDecisionEngine()
        );
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        MtPromotionAutoBidService service = buildServiceWithLogMarkdown(decisionService, objectMapper);

        LocalDateTime slotTime = LocalDateTime.of(2026, 4, 8, 23, 0);
        MtPromotionAutoBidCandidate candidate = buildCandidate();
        MtPromotionSetting currentSetting = buildCurrentSetting(true, candidate.getStrategyConfigJson());

        when(mtPromotionSettingMapper.selectAutoBidCandidates(slotTime)).thenReturn(List.of(candidate));
        when(mtPromotionBidRecordMapper.selectByShopIdAndBizDateAndSlotTime(eq(1L), any(), eq(slotTime))).thenReturn(null);
        when(mtPromotionSettingService.normalizeForExecution(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mtPromotionSettingService.getOrCreateByShopId(1L)).thenReturn(currentSetting);
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(slotTime.minusMinutes(10))))
                .thenReturn(new PromotionTimeWindowResult(false, "OUT_OF_BUSINESS_WINDOW", null));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(slotTime)))
                .thenReturn(new PromotionTimeWindowResult(false, "OUT_OF_BUSINESS_WINDOW", null));

        service.executeSlot(slotTime);

        verify(mtPromotionBidRecordMapper, never()).insert(any());
        verify(promotionLogMarkdownService, never()).generate(any());
        verify(mtPromotionOpenApiClient, never()).queryPlanStatus(any(), any(), any(), any());
        verify(mtPromotionOpenApiClient, never()).updatePlanBid(any(), any(), any(), any(), any());
        verify(promotionMetricsProvider, never()).queryMetrics(any(), any(), any());
        verify(promotionPowerConsumeService, never()).consumeForDecision(any(), any(), any());
    }

    @Test
    void shouldDisableLocalPromotionWhenRemotePlanStatusClosedInWindow() throws Exception {
        PromotionBidDecisionService decisionService = new PromotionBidDecisionService(
                new com.gogoga.backend.service.promotion.PromotionDecisionEngine()
        );
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        MtPromotionAutoBidService service = buildService(decisionService, objectMapper);

        LocalDateTime slotTime = LocalDateTime.of(2026, 4, 8, 12, 30);
        MtPromotionAutoBidCandidate candidate = buildCandidate();
        PromotionExecutionWindow window = PromotionExecutionWindow.of(
                LocalDateTime.of(2026, 4, 8, 10, 0),
                LocalDateTime.of(2026, 4, 8, 22, 0)
        );
        MtPromotionSetting currentSetting = buildCurrentSetting(true, """
                {"priority":"rate","timeMode":"auto","rateMode":"control","rateMin":"5%","rateMax":"10%","decisionMode":"llm"}
                """);
        PromotionPlanStatusOpenApiResult statusOpenApiResult = new PromotionPlanStatusOpenApiResult();
        statusOpenApiResult.setSuccess(true);
        statusOpenApiResult.setStatus(MtPromotionOpenApiClient.STATUS_CLOSE);
        statusOpenApiResult.setPayloadJson("{\"status\":1}");

        when(mtPromotionSettingMapper.selectAutoBidCandidates(slotTime)).thenReturn(List.of(candidate));
        when(mtPromotionBidRecordMapper.selectByShopIdAndBizDateAndSlotTime(eq(1L), any(), eq(slotTime))).thenReturn(null);
        when(mtPromotionSettingService.normalizeForExecution(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mtPromotionSettingService.getOrCreateByShopId(1L)).thenReturn(currentSetting);
        when(mtPowerAccountService.getOrCreateAccount(1L)).thenReturn(buildPowerAccount(25000));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(slotTime.minusMinutes(10))))
                .thenReturn(new PromotionTimeWindowResult(true, "BUSINESS_TIME_MATCHED", window));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(slotTime)))
                .thenReturn(new PromotionTimeWindowResult(true, "BUSINESS_TIME_MATCHED", window));
        when(mtPromotionOpenApiClient.queryPlanStatus(
                eq(candidate),
                eq(slotTime),
                eq(window),
                eq("WINDOW_STATUS_CHECK")
        )).thenReturn(statusOpenApiResult);
        when(mtPromotionAdRealtimeSnapshotService.selectByShopIdAndBizDateAndSlotTime(1L, slotTime.toLocalDate(), slotTime))
                .thenReturn(null);

        ArgumentCaptor<MtPromotionBidRecord> recordCaptor = ArgumentCaptor.forClass(MtPromotionBidRecord.class);
        doAnswer(invocation -> 1).when(mtPromotionBidRecordMapper).insert(recordCaptor.capture());

        service.executeSlot(slotTime);

        MtPromotionBidRecord inserted = recordCaptor.getValue();
        assertNotNull(inserted);
        assertEquals("SKIPPED", inserted.getExecuteStatus());
        assertEquals("REMOTE_PLAN_STATUS_CLOSED", inserted.getDecisionReason());
        PromotionDecisionDetail skippedDetail = JsonUtil.toBean(inserted.getDecisionDetailJson(), PromotionDecisionDetail.class);
        assertEquals(null, skippedDetail == null ? null : skippedDetail.getStrategyConfigSnapshotJson());
        verify(mtPromotionSettingService).disablePromotionByShopId(1L);
        verify(mtPromotionOpenApiClient, never()).updatePlanBid(any(), any(), any(), any(), any());
        verify(mtPromotionOpenApiClient, never()).updatePlanStatus(any(), any(), any(), any(), any());
        verify(promotionPowerConsumeService, never()).consumeForDecision(any(), any(), any());
    }

    @Test
    void shouldMarkReauthRequiredAndDisablePromotionWhenStatusCheckStillAuthFailsAfterRefresh() throws Exception {
        PromotionBidDecisionService decisionService = new PromotionBidDecisionService(
                new com.gogoga.backend.service.promotion.PromotionDecisionEngine()
        );
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        MtPromotionAutoBidService service = buildService(decisionService, objectMapper);
        service.setMtAccessTokenService(mtAccessTokenService);
        service.setMtMeituanAuthorizationFailureHandler(mtMeituanAuthorizationFailureHandler);

        LocalDateTime slotTime = LocalDateTime.of(2026, 4, 8, 12, 30);
        MtPromotionAutoBidCandidate candidate = buildCandidate();
        PromotionExecutionWindow window = PromotionExecutionWindow.of(
                LocalDateTime.of(2026, 4, 8, 10, 0),
                LocalDateTime.of(2026, 4, 8, 22, 0)
        );
        MtPromotionSetting currentSetting = buildCurrentSetting(true, candidate.getStrategyConfigJson());
        PromotionPlanStatusOpenApiResult firstFailure = new PromotionPlanStatusOpenApiResult();
        firstFailure.setAuthFailure(true);
        firstFailure.setErrorMsg("OP_UNIAUTH_FAILED 鉴权失败");
        firstFailure.setPayloadJson("{\"authFailure\":true}");
        PromotionPlanStatusOpenApiResult retryFailure = new PromotionPlanStatusOpenApiResult();
        retryFailure.setAuthFailure(true);
        retryFailure.setErrorMsg("OP_UNIAUTH_FAILED 鉴权失败");
        retryFailure.setPayloadJson("{\"authFailure\":true,\"retry\":true}");
        MtAccessToken refreshedToken = new MtAccessToken();
        refreshedToken.setAccessToken("token-new");
        refreshedToken.setExpiresAt(LocalDateTime.of(2026, 4, 8, 18, 0));

        when(mtPromotionSettingMapper.selectAutoBidCandidates(slotTime)).thenReturn(List.of(candidate));
        when(mtPromotionBidRecordMapper.selectByShopIdAndBizDateAndSlotTime(eq(1L), any(), eq(slotTime))).thenReturn(null);
        when(mtPromotionSettingService.normalizeForExecution(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mtPromotionSettingService.getOrCreateByShopId(1L)).thenReturn(currentSetting);
        when(mtPowerAccountService.getOrCreateAccount(1L)).thenReturn(buildPowerAccount(25000));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(slotTime.minusMinutes(10))))
                .thenReturn(new PromotionTimeWindowResult(true, "BUSINESS_TIME_MATCHED", window));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(slotTime)))
                .thenReturn(new PromotionTimeWindowResult(true, "BUSINESS_TIME_MATCHED", window));
        when(mtPromotionOpenApiClient.queryPlanStatus(eq(candidate), eq(slotTime), eq(window), eq("WINDOW_STATUS_CHECK")))
                .thenReturn(firstFailure);
        when(mtAccessTokenService.refreshTokenForShopWithResult(1L))
                .thenReturn(MtAccessTokenService.TokenRefreshResult.success(refreshedToken, 0, null));
        when(mtPromotionOpenApiClient.queryPlanStatus(eq(candidate), eq(slotTime), eq(window), eq("WINDOW_STATUS_CHECK_AFTER_TOKEN_REFRESH")))
                .thenReturn(retryFailure);
        when(mtPromotionAdRealtimeSnapshotService.selectByShopIdAndBizDateAndSlotTime(1L, slotTime.toLocalDate(), slotTime))
                .thenReturn(null);
        ArgumentCaptor<MtPromotionBidRecord> recordCaptor = ArgumentCaptor.forClass(MtPromotionBidRecord.class);
        doAnswer(invocation -> 1).when(mtPromotionBidRecordMapper).insert(recordCaptor.capture());

        service.executeSlot(slotTime);

        MtPromotionBidRecord inserted = recordCaptor.getValue();
        assertEquals("FAILED", inserted.getExecuteStatus());
        assertEquals("OP_UNIAUTH_FAILED 鉴权失败", inserted.getErrorMsg());
        verify(mtMeituanAuthorizationFailureHandler).handleConfirmedAuthFailure(
                1L,
                "MT_PROMOTION_AUTO_BID_STATUS_CHECK",
                null,
                "OP_UNIAUTH_FAILED 鉴权失败"
        );
        verify(mtAccessTokenService, never()).markReauthRequired(1L);
        verify(mtPromotionSettingService, never()).disablePromotionByShopId(eq(1L), any());
        verify(mtPromotionOpenApiClient, never()).updatePlanBid(any(), any(), any(), any(), any());
    }

    @Test
    void shouldUseFallbackMarkdownWithoutCallingLlmForSkippedRecord() throws Exception {
        PromotionBidDecisionService decisionService = new PromotionBidDecisionService(
                new com.gogoga.backend.service.promotion.PromotionDecisionEngine()
        );
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        MtPromotionAutoBidService service = buildServiceWithLogMarkdown(decisionService, objectMapper);

        LocalDateTime slotTime = LocalDateTime.of(2026, 4, 8, 12, 30);
        MtPromotionAutoBidCandidate candidate = buildCandidate();
        PromotionExecutionWindow window = PromotionExecutionWindow.of(
                LocalDateTime.of(2026, 4, 8, 10, 0),
                LocalDateTime.of(2026, 4, 8, 22, 0)
        );
        MtPromotionSetting currentSetting = buildCurrentSetting(true, candidate.getStrategyConfigJson());
        PromotionPlanStatusOpenApiResult statusOpenApiResult = new PromotionPlanStatusOpenApiResult();
        statusOpenApiResult.setSuccess(true);
        statusOpenApiResult.setStatus(MtPromotionOpenApiClient.STATUS_CLOSE);
        statusOpenApiResult.setPayloadJson("{\"status\":1}");

        when(mtPromotionSettingMapper.selectAutoBidCandidates(slotTime)).thenReturn(List.of(candidate));
        when(mtPromotionBidRecordMapper.selectByShopIdAndBizDateAndSlotTime(eq(1L), any(), eq(slotTime))).thenReturn(null);
        when(mtPromotionSettingService.normalizeForExecution(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mtPromotionSettingService.getOrCreateByShopId(1L)).thenReturn(currentSetting);
        when(mtPowerAccountService.getOrCreateAccount(1L)).thenReturn(buildPowerAccount(25000));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(slotTime.minusMinutes(10))))
                .thenReturn(new PromotionTimeWindowResult(true, "BUSINESS_TIME_MATCHED", window));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(slotTime)))
                .thenReturn(new PromotionTimeWindowResult(true, "BUSINESS_TIME_MATCHED", window));
        when(mtPromotionOpenApiClient.queryPlanStatus(
                eq(candidate),
                eq(slotTime),
                eq(window),
                eq("WINDOW_STATUS_CHECK")
        )).thenReturn(statusOpenApiResult);
        when(mtPromotionAdRealtimeSnapshotService.selectByShopIdAndBizDateAndSlotTime(1L, slotTime.toLocalDate(), slotTime))
                .thenReturn(null);

        ArgumentCaptor<MtPromotionBidRecord> recordCaptor = ArgumentCaptor.forClass(MtPromotionBidRecord.class);
        doAnswer(invocation -> 1).when(mtPromotionBidRecordMapper).insert(recordCaptor.capture());

        service.executeSlot(slotTime);

        MtPromotionBidRecord inserted = recordCaptor.getValue();
        assertEquals("SKIPPED", inserted.getExecuteStatus());
        assertTrue(inserted.getMarkdownMessage().contains("当前时段未执行自动调价"));
        assertEquals("fallback",
                JsonUtil.toBean(inserted.getDecisionDetailJson(), com.gogoga.backend.service.promotion.PromotionDecisionDetail.class)
                        .getLogMarkdownSource());
        verify(promotionLogMarkdownService, never()).generate(any());
        verify(promotionPowerConsumeService, never()).consumeForLogGeneration(any(), any(), any(), any());
    }

    @Test
    void shouldUseFallbackMarkdownWithoutCallingLlmForFailedRecord() throws Exception {
        PromotionBidDecisionService decisionService = new PromotionBidDecisionService(
                new com.gogoga.backend.service.promotion.PromotionDecisionEngine()
        );
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        MtPromotionAutoBidService service = buildServiceWithLogMarkdown(decisionService, objectMapper);

        LocalDateTime slotTime = LocalDateTime.of(2026, 4, 8, 12, 30);
        MtPromotionAutoBidCandidate candidate = buildCandidate();
        PromotionExecutionWindow window = PromotionExecutionWindow.of(
                LocalDateTime.of(2026, 4, 8, 10, 0),
                LocalDateTime.of(2026, 4, 8, 22, 0)
        );
        PromotionMetricsSnapshot snapshot = new PromotionMetricsSnapshot();
        snapshot.setPromotionBalance(new BigDecimal("100.00"));
        snapshot.setCostCounterValue(new BigDecimal("30.30"));
        snapshot.setWindowCost(new BigDecimal("3.30"));
        snapshot.setWindowIncome(new BigDecimal("66.00"));
        snapshot.setWindowCostRatio(new BigDecimal("0.050000"));
        snapshot.setSlotCostDelta(new BigDecimal("0.30"));
        snapshot.setMetricsTrusted(true);
        PromotionPlanStatusOpenApiResult statusOpenApiResult = new PromotionPlanStatusOpenApiResult();
        statusOpenApiResult.setSuccess(true);
        statusOpenApiResult.setStatus(MtPromotionOpenApiClient.STATUS_OPEN);
        statusOpenApiResult.setPayloadJson("{\"status\":0}");
        PromotionBidOpenApiResult updateResult = new PromotionBidOpenApiResult();
        updateResult.setSuccess(false);
        updateResult.setErrorMsg("REMOTE_TIMEOUT");
        updateResult.setPayloadJson("{\"ok\":false}");
        MtPromotionSetting currentSetting = buildCurrentSetting(true, candidate.getStrategyConfigJson());

        when(mtPromotionSettingMapper.selectAutoBidCandidates(slotTime)).thenReturn(List.of(candidate));
        when(mtPromotionBidRecordMapper.selectByShopIdAndBizDateAndSlotTime(eq(1L), any(), eq(slotTime))).thenReturn(null);
        when(mtPromotionSettingService.normalizeForExecution(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mtPromotionSettingService.getOrCreateByShopId(1L)).thenReturn(currentSetting);
        when(mtPowerAccountService.getOrCreateAccount(1L)).thenReturn(buildPowerAccount(25000));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(slotTime.minusMinutes(10))))
                .thenReturn(new PromotionTimeWindowResult(false, "OUT_OF_BUSINESS_WINDOW", null));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(slotTime)))
                .thenReturn(new PromotionTimeWindowResult(true, "AUTO_TIME_MODE", window));
        when(mtPromotionOpenApiClient.queryPlanStatus(eq(candidate), eq(slotTime), eq(window), eq("WINDOW_STATUS_CHECK")))
                .thenReturn(statusOpenApiResult);
        when(promotionMetricsProvider.queryMetrics(eq(candidate), any(PromotionExecutionWindow.class), eq(slotTime))).thenReturn(snapshot);
        when(mtPromotionBidRecordMapper.selectLatestSuccessByShopIdAndWindowKey(eq(1L), eq(window.getWindowKey()), anyInt()))
                .thenReturn(List.of(
                        buildHistory(slotTime.minusMinutes(10), "0.40", "3.10"),
                        buildHistory(slotTime.minusMinutes(20), "0.40", "3.00")
                ));
        when(mtPromotionOpenApiClient.updatePlanBid(eq(candidate), eq(40), eq(slotTime), eq(window), eq("SLOT_BID_UPDATE")))
                .thenReturn(updateResult);

        ArgumentCaptor<MtPromotionBidRecord> recordCaptor = ArgumentCaptor.forClass(MtPromotionBidRecord.class);
        doAnswer(invocation -> 1).when(mtPromotionBidRecordMapper).insert(recordCaptor.capture());

        service.executeSlot(slotTime);

        MtPromotionBidRecord inserted = recordCaptor.getValue();
        assertEquals("FAILED", inserted.getExecuteStatus());
        PromotionDecisionDetail failedDetail = JsonUtil.toBean(inserted.getDecisionDetailJson(), PromotionDecisionDetail.class);
        assertEquals(currentSetting.getStrategyConfigJson(), failedDetail.getStrategyConfigSnapshotJson());
        assertEquals(
                MtPromotionSettingHistoryService.strategyConfigHash(currentSetting.getStrategyConfigJson()),
                failedDetail.getStrategyConfigHash()
        );
        assertTrue(inserted.getMarkdownMessage().contains("自动调价执行失败"));
        assertEquals("fallback",
                failedDetail.getLogMarkdownSource());
        verify(promotionLogMarkdownService, never()).generate(any());
        verify(promotionPowerConsumeService, never()).consumeForLogGeneration(any(), any(), any(), any());
    }

    @Test
    void shouldSkipAutoBidWhenDailyBudgetExhausted() throws Exception {
        PromotionBidDecisionService decisionService = new PromotionBidDecisionService(
                new com.gogoga.backend.service.promotion.PromotionDecisionEngine()
        );
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        MtPromotionAutoBidService service = buildService(decisionService, objectMapper);

        LocalDateTime slotTime = LocalDateTime.of(2026, 4, 8, 12, 30);
        MtPromotionAutoBidCandidate candidate = buildCandidate();
        PromotionExecutionWindow window = PromotionExecutionWindow.of(
                LocalDateTime.of(2026, 4, 8, 10, 0),
                LocalDateTime.of(2026, 4, 8, 22, 0)
        );
        MtPromotionSetting currentSetting = buildCurrentSetting(true, """
                {"priority":"rate","timeMode":"auto","rateMode":"control","rateMin":"5%","rateMax":"10%","decisionMode":"llm"}
                """);
        PromotionPlanStatusOpenApiResult statusOpenApiResult = new PromotionPlanStatusOpenApiResult();
        statusOpenApiResult.setSuccess(true);
        statusOpenApiResult.setStatus(MtPromotionOpenApiClient.STATUS_OPEN);
        statusOpenApiResult.setBudget(new BigDecimal("100.00"));
        statusOpenApiResult.setBudgetUsed(new BigDecimal("100.00"));
        statusOpenApiResult.setAvailableBudget(BigDecimal.ZERO);
        statusOpenApiResult.setPayloadJson("{\"status\":0,\"budget\":100.00,\"budgetUsed\":100.00,\"availableBudget\":0.00}");

        when(mtPromotionSettingMapper.selectAutoBidCandidates(slotTime)).thenReturn(List.of(candidate));
        when(mtPromotionBidRecordMapper.selectByShopIdAndBizDateAndSlotTime(eq(1L), any(), eq(slotTime))).thenReturn(null);
        when(mtPromotionSettingService.normalizeForExecution(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mtPromotionSettingService.getOrCreateByShopId(1L)).thenReturn(currentSetting);
        when(mtPowerAccountService.getOrCreateAccount(1L)).thenReturn(buildPowerAccount(25000));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(slotTime.minusMinutes(10))))
                .thenReturn(new PromotionTimeWindowResult(true, "BUSINESS_TIME_MATCHED", window));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(slotTime)))
                .thenReturn(new PromotionTimeWindowResult(true, "BUSINESS_TIME_MATCHED", window));
        when(mtPromotionOpenApiClient.queryPlanStatus(
                eq(candidate),
                eq(slotTime),
                eq(window),
                eq("WINDOW_STATUS_CHECK")
        )).thenReturn(statusOpenApiResult);

        ArgumentCaptor<MtPromotionBidRecord> recordCaptor = ArgumentCaptor.forClass(MtPromotionBidRecord.class);
        doAnswer(invocation -> 1).when(mtPromotionBidRecordMapper).insert(recordCaptor.capture());

        service.executeSlot(slotTime);

        List<MtPromotionBidRecord> insertedRecords = recordCaptor.getAllValues();
        assertEquals(1, insertedRecords.size());
        MtPromotionBidRecord inserted = insertedRecords.get(0);
        assertNotNull(inserted);
        assertEquals("SKIPPED", inserted.getExecuteStatus());
        assertEquals("PROMOTION_DAILY_BUDGET_EXHAUSTED", inserted.getDecisionReason());
        verify(mtPromotionAdRealtimeSnapshotService).captureCurrentSlotSnapshot(1L, "token", slotTime);
        verify(promotionMetricsProvider, never()).queryMetrics(any(), any(), any());
        verify(mtPromotionOpenApiClient, never()).updatePlanBid(any(), any(), any(), any(), any());
        verify(mtPromotionSettingService, never()).disablePromotionByShopId(any());
        verify(promotionPowerConsumeService, never()).consumeForDecision(any(), any(), any());
    }

    @Test
    void shouldSuppressRepeatedDailyBudgetRecordWithinSameBizDate() throws Exception {
        PromotionBidDecisionService decisionService = new PromotionBidDecisionService(
                new com.gogoga.backend.service.promotion.PromotionDecisionEngine()
        );
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        MtPromotionAutoBidService service = buildService(decisionService, objectMapper);

        LocalDateTime firstSlotTime = LocalDateTime.of(2026, 4, 8, 12, 30);
        LocalDateTime secondSlotTime = firstSlotTime.plusMinutes(10);
        MtPromotionAutoBidCandidate candidate = buildCandidate();
        PromotionExecutionWindow window = PromotionExecutionWindow.of(
                LocalDateTime.of(2026, 4, 8, 10, 0),
                LocalDateTime.of(2026, 4, 8, 22, 0)
        );
        MtPromotionSetting currentSetting = buildCurrentSetting(true, candidate.getStrategyConfigJson());
        PromotionPlanStatusOpenApiResult exhaustedStatus = new PromotionPlanStatusOpenApiResult();
        exhaustedStatus.setSuccess(true);
        exhaustedStatus.setStatus(MtPromotionOpenApiClient.STATUS_OPEN);
        exhaustedStatus.setBudget(new BigDecimal("100.00"));
        exhaustedStatus.setBudgetUsed(new BigDecimal("100.00"));
        exhaustedStatus.setAvailableBudget(BigDecimal.ZERO);
        exhaustedStatus.setPayloadJson("{\"status\":0,\"availableBudget\":0.00}");
        MtPromotionBidRecord firstNotice = new MtPromotionBidRecord();
        firstNotice.setShopId(1L);
        firstNotice.setBizDate(firstSlotTime.toLocalDate());
        firstNotice.setSlotTime(firstSlotTime);
        firstNotice.setDecisionReason("PROMOTION_DAILY_BUDGET_EXHAUSTED");

        when(mtPromotionSettingMapper.selectAutoBidCandidates(firstSlotTime)).thenReturn(List.of(candidate));
        when(mtPromotionSettingMapper.selectAutoBidCandidates(secondSlotTime)).thenReturn(List.of(candidate));
        when(mtPromotionBidRecordMapper.selectByShopIdAndBizDateAndSlotTime(eq(1L), any(), eq(firstSlotTime))).thenReturn(null);
        when(mtPromotionBidRecordMapper.selectByShopIdAndBizDateAndSlotTime(eq(1L), any(), eq(secondSlotTime))).thenReturn(null);
        when(mtPromotionSettingService.normalizeForExecution(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mtPromotionSettingService.getOrCreateByShopId(1L)).thenReturn(currentSetting);
        when(mtPowerAccountService.getOrCreateAccount(1L)).thenReturn(buildPowerAccount(25000));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), any(LocalDateTime.class)))
                .thenReturn(new PromotionTimeWindowResult(true, "BUSINESS_TIME_MATCHED", window));
        when(mtPromotionOpenApiClient.queryPlanStatus(eq(candidate), eq(firstSlotTime), eq(window), eq("WINDOW_STATUS_CHECK")))
                .thenReturn(exhaustedStatus);
        when(mtPromotionOpenApiClient.queryPlanStatus(eq(candidate), eq(secondSlotTime), eq(window), eq("WINDOW_STATUS_CHECK")))
                .thenReturn(exhaustedStatus);
        when(mtPromotionBidRecordMapper.selectLatestByShopIdAndBizDateAndDecisionReason(
                eq(1L),
                eq(firstSlotTime.toLocalDate()),
                eq("PROMOTION_DAILY_BUDGET_EXHAUSTED")
        )).thenReturn(null, firstNotice);

        ArgumentCaptor<MtPromotionBidRecord> recordCaptor = ArgumentCaptor.forClass(MtPromotionBidRecord.class);
        doAnswer(invocation -> 1).when(mtPromotionBidRecordMapper).insert(recordCaptor.capture());

        service.executeSlot(firstSlotTime);
        service.executeSlot(secondSlotTime);

        List<MtPromotionBidRecord> insertedRecords = recordCaptor.getAllValues();
        assertEquals(1, insertedRecords.size());
        assertEquals("PROMOTION_DAILY_BUDGET_EXHAUSTED", insertedRecords.get(0).getDecisionReason());
        verify(mtPromotionOpenApiClient, never()).updatePlanBid(any(), any(), any(), any(), any());
    }

    @Test
    void shouldResumeExecutionOnNextBizDateAfterBudgetSuppression() throws Exception {
        PromotionBidDecisionService decisionService = new PromotionBidDecisionService(
                new com.gogoga.backend.service.promotion.PromotionDecisionEngine()
        );
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        MtPromotionAutoBidService service = buildService(decisionService, objectMapper);

        LocalDateTime budgetExhaustedSlotTime = LocalDateTime.of(2026, 4, 8, 12, 30);
        LocalDateTime resumedSlotTime = LocalDateTime.of(2026, 4, 9, 12, 30);
        MtPromotionAutoBidCandidate candidate = buildCandidate();
        PromotionExecutionWindow exhaustedWindow = PromotionExecutionWindow.of(
                LocalDateTime.of(2026, 4, 8, 10, 0),
                LocalDateTime.of(2026, 4, 8, 22, 0)
        );
        PromotionExecutionWindow resumedWindow = PromotionExecutionWindow.of(
                LocalDateTime.of(2026, 4, 9, 10, 0),
                LocalDateTime.of(2026, 4, 9, 22, 0)
        );
        MtPromotionSetting currentSetting = buildCurrentSetting(true, candidate.getStrategyConfigJson());
        PromotionPlanStatusOpenApiResult exhaustedStatus = new PromotionPlanStatusOpenApiResult();
        exhaustedStatus.setSuccess(true);
        exhaustedStatus.setStatus(MtPromotionOpenApiClient.STATUS_OPEN);
        exhaustedStatus.setAvailableBudget(BigDecimal.ZERO);
        exhaustedStatus.setPayloadJson("{\"status\":0,\"availableBudget\":0.00}");
        PromotionPlanStatusOpenApiResult resumedStatus = new PromotionPlanStatusOpenApiResult();
        resumedStatus.setSuccess(true);
        resumedStatus.setStatus(MtPromotionOpenApiClient.STATUS_OPEN);
        resumedStatus.setAvailableBudget(new BigDecimal("20.00"));
        resumedStatus.setPayloadJson("{\"status\":0,\"availableBudget\":20.00}");
        PromotionMetricsSnapshot resumedSnapshot = new PromotionMetricsSnapshot();
        resumedSnapshot.setPromotionBalance(new BigDecimal("100.00"));
        resumedSnapshot.setCostCounterValue(new BigDecimal("30.30"));
        resumedSnapshot.setWindowCost(BigDecimal.ZERO.setScale(2));
        resumedSnapshot.setWindowIncome(new BigDecimal("66.00"));
        resumedSnapshot.setWindowCostRatio(BigDecimal.ZERO.setScale(6));
        resumedSnapshot.setSlotCostDelta(BigDecimal.ZERO.setScale(2));
        resumedSnapshot.setMetricsTrusted(true);
        PeerBidPriceOpenApiResult peerBidResult = new PeerBidPriceOpenApiResult();
        peerBidResult.setSuccess(true);
        peerBidResult.setAverageBidAmount(new BigDecimal("0.66"));
        peerBidResult.setPayloadJson("{\"averageBidAmount\":0.66}");
        PromotionBidOpenApiResult updateResult = new PromotionBidOpenApiResult();
        updateResult.setSuccess(true);
        updateResult.setPayloadJson("{\"ok\":true}");
        updateResult.setBid(10);

        when(mtPromotionSettingMapper.selectAutoBidCandidates(budgetExhaustedSlotTime)).thenReturn(List.of(candidate));
        when(mtPromotionSettingMapper.selectAutoBidCandidates(resumedSlotTime)).thenReturn(List.of(candidate));
        when(mtPromotionBidRecordMapper.selectByShopIdAndBizDateAndSlotTime(eq(1L), any(), eq(budgetExhaustedSlotTime))).thenReturn(null);
        when(mtPromotionBidRecordMapper.selectByShopIdAndBizDateAndSlotTime(eq(1L), any(), eq(resumedSlotTime))).thenReturn(null);
        when(mtPromotionSettingService.normalizeForExecution(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mtPromotionSettingService.getOrCreateByShopId(1L)).thenReturn(currentSetting);
        when(mtPowerAccountService.getOrCreateAccount(1L)).thenReturn(buildPowerAccount(25000));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(budgetExhaustedSlotTime.minusMinutes(10))))
                .thenReturn(new PromotionTimeWindowResult(true, "BUSINESS_TIME_MATCHED", exhaustedWindow));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(budgetExhaustedSlotTime)))
                .thenReturn(new PromotionTimeWindowResult(true, "BUSINESS_TIME_MATCHED", exhaustedWindow));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(resumedSlotTime.minusMinutes(10))))
                .thenReturn(new PromotionTimeWindowResult(false, "OUT_OF_BUSINESS_WINDOW", null));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(resumedSlotTime)))
                .thenReturn(new PromotionTimeWindowResult(true, "BUSINESS_TIME_MATCHED", resumedWindow));
        when(mtPromotionOpenApiClient.queryPlanStatus(eq(candidate), eq(budgetExhaustedSlotTime), eq(exhaustedWindow), eq("WINDOW_STATUS_CHECK")))
                .thenReturn(exhaustedStatus);
        when(mtPromotionOpenApiClient.queryPlanStatus(eq(candidate), eq(resumedSlotTime), eq(resumedWindow), eq("WINDOW_STATUS_CHECK")))
                .thenReturn(resumedStatus);
        when(mtPromotionBidRecordMapper.selectLatestByShopIdAndBizDateAndDecisionReason(
                eq(1L),
                eq(budgetExhaustedSlotTime.toLocalDate()),
                eq("PROMOTION_DAILY_BUDGET_EXHAUSTED")
        )).thenReturn(null);
        when(promotionMetricsProvider.queryMetrics(eq(candidate), any(PromotionExecutionWindow.class), eq(resumedSlotTime)))
                .thenReturn(resumedSnapshot);
        when(mtPromotionBidRecordMapper.selectLatestSuccessByShopIdAndWindowKey(eq(1L), eq(resumedWindow.getWindowKey()), anyInt()))
                .thenReturn(List.of());
        when(mtPromotionOpenApiClient.queryPeerAverageBid(eq(candidate), eq(resumedSlotTime), eq(resumedWindow), eq("INITIAL_BID_FROM_PEER_AVG")))
                .thenReturn(peerBidResult);
        when(mtPromotionOpenApiClient.updatePlanBid(eq(candidate), eq(10), eq(resumedSlotTime), eq(resumedWindow), eq("SLOT_BID_UPDATE")))
                .thenReturn(updateResult);

        ArgumentCaptor<MtPromotionBidRecord> recordCaptor = ArgumentCaptor.forClass(MtPromotionBidRecord.class);
        doAnswer(invocation -> 1).when(mtPromotionBidRecordMapper).insert(recordCaptor.capture());

        service.executeSlot(budgetExhaustedSlotTime);
        service.executeSlot(resumedSlotTime);

        List<MtPromotionBidRecord> insertedRecords = recordCaptor.getAllValues();
        assertEquals(2, insertedRecords.size());
        assertEquals("PROMOTION_DAILY_BUDGET_EXHAUSTED", insertedRecords.get(0).getDecisionReason());
        assertEquals("SUCCESS", insertedRecords.get(1).getExecuteStatus());
        assertEquals("FIRST_SUCCESS_OF_WINDOW", insertedRecords.get(1).getDecisionReason());
        assertEquals(new BigDecimal("0.10"), insertedRecords.get(1).getNextBidAmount());
    }

    @Test
    void shouldSkipAutoBidWhenMetricsAreUntrusted() throws Exception {
        PromotionBidDecisionService decisionService = new PromotionBidDecisionService(
                new com.gogoga.backend.service.promotion.PromotionDecisionEngine()
        );
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        MtPromotionAutoBidService service = buildService(decisionService, objectMapper);

        LocalDateTime slotTime = LocalDateTime.of(2026, 4, 8, 12, 30);
        MtPromotionAutoBidCandidate candidate = buildCandidate();
        PromotionExecutionWindow window = PromotionExecutionWindow.of(
                LocalDateTime.of(2026, 4, 8, 10, 0),
                LocalDateTime.of(2026, 4, 8, 22, 0)
        );
        MtPromotionSetting currentSetting = buildCurrentSetting(true, """
                {"priority":"rate","timeMode":"auto","rateMode":"control","rateMin":"5%","rateMax":"10%","decisionMode":"llm"}
                """);
        PromotionPlanStatusOpenApiResult statusOpenApiResult = new PromotionPlanStatusOpenApiResult();
        statusOpenApiResult.setSuccess(true);
        statusOpenApiResult.setStatus(MtPromotionOpenApiClient.STATUS_OPEN);
        statusOpenApiResult.setPayloadJson("{\"status\":0}");
        PromotionMetricsSnapshot snapshot = new PromotionMetricsSnapshot();
        snapshot.setPromotionBalance(new BigDecimal("100.00"));
        snapshot.setCostCounterValue(new BigDecimal("30.30"));
        snapshot.setWindowCost(new BigDecimal("0.00"));
        snapshot.setWindowIncome(new BigDecimal("66.00"));
        snapshot.setWindowCostRatio(BigDecimal.ZERO.setScale(6));
        snapshot.setSlotCostDelta(BigDecimal.ZERO.setScale(2));
        snapshot.setMetricsTrusted(false);
        snapshot.setUntrustedReason("PREVIOUS_SLOT_SNAPSHOT_MISSING");
        snapshot.setPayloadJson("{\"metricsTrusted\":false}");

        when(mtPromotionSettingMapper.selectAutoBidCandidates(slotTime)).thenReturn(List.of(candidate));
        when(mtPromotionBidRecordMapper.selectByShopIdAndBizDateAndSlotTime(eq(1L), any(), eq(slotTime))).thenReturn(null);
        when(mtPromotionSettingService.normalizeForExecution(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mtPromotionSettingService.getOrCreateByShopId(1L)).thenReturn(currentSetting);
        when(mtPowerAccountService.getOrCreateAccount(1L)).thenReturn(buildPowerAccount(25000));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(slotTime.minusMinutes(10))))
                .thenReturn(new PromotionTimeWindowResult(true, "BUSINESS_TIME_MATCHED", window));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(slotTime)))
                .thenReturn(new PromotionTimeWindowResult(true, "BUSINESS_TIME_MATCHED", window));
        when(mtPromotionOpenApiClient.queryPlanStatus(
                eq(candidate),
                eq(slotTime),
                eq(window),
                eq("WINDOW_STATUS_CHECK")
        )).thenReturn(statusOpenApiResult);
        when(promotionMetricsProvider.queryMetrics(eq(candidate), any(PromotionExecutionWindow.class), eq(slotTime))).thenReturn(snapshot);

        ArgumentCaptor<MtPromotionBidRecord> recordCaptor = ArgumentCaptor.forClass(MtPromotionBidRecord.class);
        doAnswer(invocation -> 1).when(mtPromotionBidRecordMapper).insert(recordCaptor.capture());

        service.executeSlot(slotTime);

        MtPromotionBidRecord inserted = recordCaptor.getValue();
        assertNotNull(inserted);
        assertEquals("SKIPPED", inserted.getExecuteStatus());
        assertEquals("UNTRUSTED_PROMOTION_METRICS:PREVIOUS_SLOT_SNAPSHOT_MISSING", inserted.getDecisionReason());
        assertEquals("{\"metricsTrusted\":false}", inserted.getMetricsPayloadJson());
        verify(mtPromotionOpenApiClient, never()).updatePlanBid(any(), any(), any(), any(), any());
        verify(promotionPowerConsumeService, never()).consumeForDecision(any(), any(), any());
    }

    @Test
    void shouldSkipCurrentSlotWhenTemporaryRecoveryWindowIsInitialized() throws Exception {
        PromotionBidDecisionService decisionService = new PromotionBidDecisionService(
                new com.gogoga.backend.service.promotion.PromotionDecisionEngine()
        );
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        MtPromotionAutoBidService service = buildService(decisionService, objectMapper);

        LocalDateTime slotTime = LocalDateTime.of(2026, 4, 8, 12, 30);
        MtPromotionAutoBidCandidate candidate = buildCandidate();
        PromotionExecutionWindow window = PromotionExecutionWindow.of(
                LocalDateTime.of(2026, 4, 8, 10, 0),
                LocalDateTime.of(2026, 4, 8, 22, 0)
        );
        MtPromotionSetting currentSetting = buildCurrentSetting(true, candidate.getStrategyConfigJson());
        PromotionPlanStatusOpenApiResult statusOpenApiResult = new PromotionPlanStatusOpenApiResult();
        statusOpenApiResult.setSuccess(true);
        statusOpenApiResult.setStatus(MtPromotionOpenApiClient.STATUS_OPEN);
        statusOpenApiResult.setPayloadJson("{\"status\":0}");
        PromotionMetricsSnapshot snapshot = new PromotionMetricsSnapshot();
        snapshot.setPromotionBalance(new BigDecimal("100.00"));
        snapshot.setCostCounterValue(new BigDecimal("30.30"));
        snapshot.setWindowCost(BigDecimal.ZERO.setScale(2));
        snapshot.setWindowIncome(BigDecimal.ZERO.setScale(2));
        snapshot.setWindowCostRatio(BigDecimal.ZERO.setScale(6));
        snapshot.setSlotCostDelta(BigDecimal.ZERO.setScale(2));
        snapshot.setMetricsTrusted(true);
        snapshot.setUntrustedReason(null);
        snapshot.setBaselineType("TEMPORARY_RECOVERY_REALTIME");
        snapshot.setBusinessWindowStartAt(window.getWindowStartAt());
        snapshot.setEffectiveWindowStartAt(slotTime);
        snapshot.setRecoveryWindowInitializationRequired(true);
        snapshot.setPayloadJson("{\"temporaryRecoveryWindowStartAt\":\"2026-04-08T12:30\"}");
        PeerBidPriceOpenApiResult peerBidResult = new PeerBidPriceOpenApiResult();
        peerBidResult.setSuccess(true);
        peerBidResult.setAverageBidAmount(new BigDecimal("0.66"));
        PromotionBidOpenApiResult updateResult = new PromotionBidOpenApiResult();
        updateResult.setSuccess(true);
        updateResult.setPayloadJson("{\"ok\":true}");
        updateResult.setBid(10);

        when(mtPromotionSettingMapper.selectAutoBidCandidates(slotTime)).thenReturn(List.of(candidate));
        when(mtPromotionBidRecordMapper.selectByShopIdAndBizDateAndSlotTime(eq(1L), any(), eq(slotTime))).thenReturn(null);
        when(mtPromotionSettingService.normalizeForExecution(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mtPromotionSettingService.getOrCreateByShopId(1L)).thenReturn(currentSetting);
        when(mtPowerAccountService.getOrCreateAccount(1L)).thenReturn(buildPowerAccount(25000));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(slotTime.minusMinutes(10))))
                .thenReturn(new PromotionTimeWindowResult(true, "BUSINESS_TIME_MATCHED", window));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(slotTime)))
                .thenReturn(new PromotionTimeWindowResult(true, "BUSINESS_TIME_MATCHED", window));
        when(mtPromotionOpenApiClient.queryPlanStatus(
                eq(candidate),
                eq(slotTime),
                eq(window),
                eq("WINDOW_STATUS_CHECK")
        )).thenReturn(statusOpenApiResult);
        when(promotionMetricsProvider.queryMetrics(eq(candidate), any(PromotionExecutionWindow.class), eq(slotTime))).thenReturn(snapshot);
        when(mtPromotionBidRecordMapper.selectLatestSuccessByShopIdAndWindowKey(eq(1L), eq(window.getWindowKey()), anyInt()))
                .thenReturn(List.of());
        when(mtPromotionOpenApiClient.queryPeerAverageBid(
                eq(candidate),
                eq(slotTime),
                eq(window),
                eq("INITIAL_BID_FROM_PEER_AVG")
        )).thenReturn(peerBidResult);
        when(mtPromotionOpenApiClient.updatePlanBid(
                eq(candidate),
                eq(10),
                eq(slotTime),
                eq(window),
                eq("SLOT_BID_UPDATE")
        )).thenReturn(updateResult);

        ArgumentCaptor<MtPromotionBidRecord> recordCaptor = ArgumentCaptor.forClass(MtPromotionBidRecord.class);
        doAnswer(invocation -> 1).when(mtPromotionBidRecordMapper).insert(recordCaptor.capture());

        service.executeSlot(slotTime);

        MtPromotionBidRecord inserted = recordCaptor.getValue();
        assertEquals("SUCCESS", inserted.getExecuteStatus());
        assertEquals("FIRST_SUCCESS_OF_WINDOW", inserted.getDecisionReason());
        assertEquals(new BigDecimal("0.10"), inserted.getNextBidAmount());
        assertEquals(slotTime, inserted.getWindowStartAt());
        com.gogoga.backend.service.promotion.PromotionDecisionDetail detail =
                JsonUtil.toBean(inserted.getDecisionDetailJson(), com.gogoga.backend.service.promotion.PromotionDecisionDetail.class);
        assertEquals("TEMPORARY_RECOVERY_REALTIME", detail.getMetricsBaselineType());
        assertEquals(slotTime, detail.getMetricsEffectiveWindowStartAt());
        assertEquals(Boolean.TRUE, detail.getRecoveryWindowInitialization());
        assertEquals(new BigDecimal("0.10"), detail.getWindowBaselineBidAmount());
        verify(mtPromotionOpenApiClient).updatePlanBid(eq(candidate), eq(10), eq(slotTime), eq(window), eq("SLOT_BID_UPDATE"));
        verify(promotionPowerConsumeService, never()).consumeForDecision(any(), any(), any());
    }

    @Test
    void shouldFloorInitialBidToJiaoWhenDerivedBidHasFen() throws Exception {
        PromotionBidDecisionService decisionService = new PromotionBidDecisionService(
                new com.gogoga.backend.service.promotion.PromotionDecisionEngine()
        );
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        MtPromotionAutoBidService service = buildService(decisionService, objectMapper);

        LocalDateTime slotTime = LocalDateTime.of(2026, 4, 8, 12, 30);
        MtPromotionAutoBidCandidate candidate = buildCandidate();
        PromotionExecutionWindow window = PromotionExecutionWindow.of(
                LocalDateTime.of(2026, 4, 8, 10, 0),
                LocalDateTime.of(2026, 4, 8, 22, 0)
        );
        PromotionMetricsSnapshot snapshot = new PromotionMetricsSnapshot();
        snapshot.setPromotionBalance(new BigDecimal("100.00"));
        snapshot.setCostCounterValue(new BigDecimal("30.30"));
        snapshot.setWindowCost(BigDecimal.ZERO.setScale(2));
        snapshot.setWindowIncome(new BigDecimal("66.00"));
        snapshot.setWindowCostRatio(BigDecimal.ZERO.setScale(6));
        snapshot.setSlotCostDelta(BigDecimal.ZERO.setScale(2));
        snapshot.setMetricsTrusted(true);
        PromotionPlanStatusOpenApiResult statusOpenApiResult = new PromotionPlanStatusOpenApiResult();
        statusOpenApiResult.setSuccess(true);
        statusOpenApiResult.setStatus(MtPromotionOpenApiClient.STATUS_OPEN);
        statusOpenApiResult.setPayloadJson("{\"status\":0}");
        PeerBidPriceOpenApiResult peerBidResult = new PeerBidPriceOpenApiResult();
        peerBidResult.setSuccess(true);
        peerBidResult.setAverageBidAmount(new BigDecimal("0.79"));
        PromotionBidOpenApiResult updateResult = new PromotionBidOpenApiResult();
        updateResult.setSuccess(true);
        updateResult.setPayloadJson("{\"ok\":true}");
        updateResult.setBid(10);
        MtPromotionSetting currentSetting = buildCurrentSetting(true, candidate.getStrategyConfigJson());

        when(mtPromotionSettingMapper.selectAutoBidCandidates(slotTime)).thenReturn(List.of(candidate));
        when(mtPromotionBidRecordMapper.selectByShopIdAndBizDateAndSlotTime(eq(1L), any(), eq(slotTime))).thenReturn(null);
        when(mtPromotionSettingService.normalizeForExecution(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mtPromotionSettingService.getOrCreateByShopId(1L)).thenReturn(currentSetting);
        when(mtPowerAccountService.getOrCreateAccount(1L)).thenReturn(buildPowerAccount(25000));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(slotTime.minusMinutes(10))))
                .thenReturn(new PromotionTimeWindowResult(false, "OUT_OF_BUSINESS_WINDOW", null));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(slotTime)))
                .thenReturn(new PromotionTimeWindowResult(true, "AUTO_TIME_MODE", window));
        when(mtPromotionOpenApiClient.queryPlanStatus(eq(candidate), eq(slotTime), eq(window), eq("WINDOW_STATUS_CHECK")))
                .thenReturn(statusOpenApiResult);
        when(promotionMetricsProvider.queryMetrics(eq(candidate), any(PromotionExecutionWindow.class), eq(slotTime))).thenReturn(snapshot);
        when(mtPromotionBidRecordMapper.selectLatestSuccessByShopIdAndWindowKey(eq(1L), eq(window.getWindowKey()), anyInt()))
                .thenReturn(List.of());
        when(mtPromotionOpenApiClient.queryPeerAverageBid(
                eq(candidate),
                eq(slotTime),
                eq(window),
                eq("INITIAL_BID_FROM_PEER_AVG")
        )).thenReturn(peerBidResult);
        when(mtPromotionOpenApiClient.updatePlanBid(
                eq(candidate),
                eq(10),
                eq(slotTime),
                eq(window),
                eq("SLOT_BID_UPDATE")
        )).thenReturn(updateResult);

        ArgumentCaptor<MtPromotionBidRecord> recordCaptor = ArgumentCaptor.forClass(MtPromotionBidRecord.class);
        doAnswer(invocation -> 1).when(mtPromotionBidRecordMapper).insert(recordCaptor.capture());

        service.executeSlot(slotTime);

        MtPromotionBidRecord inserted = recordCaptor.getValue();
        assertNotNull(inserted);
        assertEquals(new BigDecimal("0.10"), inserted.getNextBidAmount());
        verify(mtPromotionOpenApiClient).updatePlanBid(eq(candidate), eq(10), eq(slotTime), eq(window), eq("SLOT_BID_UPDATE"));
    }

    @Test
    void shouldRefreshShippingTimeBeforeEvaluatingWhenMissing() throws Exception {
        PromotionBidDecisionService decisionService = new PromotionBidDecisionService(
                new com.gogoga.backend.service.promotion.PromotionDecisionEngine()
        );
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        MtPromotionAutoBidService service = buildService(decisionService, objectMapper);

        LocalDateTime slotTime = LocalDateTime.of(2026, 4, 8, 12, 30);
        MtPromotionAutoBidCandidate candidate = buildCandidate();
        candidate.setShippingTime(null);
        PromotionExecutionWindow window = PromotionExecutionWindow.of(
                LocalDateTime.of(2026, 4, 8, 10, 0),
                LocalDateTime.of(2026, 4, 8, 22, 0)
        );
        MtPromotionSetting currentSetting = buildCurrentSetting(true, """
                {"priority":"rate","timeMode":"auto","rateMode":"control","rateMin":"5%","rateMax":"10%","decisionMode":"llm"}
                """);
        MtShop shop = new MtShop();
        shop.setId(1L);
        Data detail = new Data();
        detail.setShippingTime("00:00-02:00,10:00-24:00");

        when(mtPromotionSettingMapper.selectAutoBidCandidates(slotTime)).thenReturn(List.of(candidate));
        when(mtPromotionBidRecordMapper.selectByShopIdAndBizDateAndSlotTime(eq(1L), any(), eq(slotTime))).thenReturn(null);
        when(mtPromotionSettingService.normalizeForExecution(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mtPromotionSettingService.getOrCreateByShopId(1L)).thenReturn(currentSetting);
        when(mtShopService.findById(1L)).thenReturn(shop);
        when(mtShopClient.queryAuthorizedShop(1L, "token", "AUTO_BID_FILL_SHIPPING_TIME")).thenReturn(Optional.of(detail));
        when(mtShopService.updateShippingTimeFromAuthorizedDetail(shop, detail)).thenAnswer(invocation -> {
            shop.setShippingTime("00:00-02:00,10:00-24:00");
            return shop;
        });
        PromotionBidOpenApiResult updateResult = new PromotionBidOpenApiResult();
        updateResult.setSuccess(true);
        updateResult.setPayloadJson("{\"bid\":10}");
        updateResult.setBid(10);
        when(mtPromotionOpenApiClient.updatePlanBid(eq(candidate), eq(10), eq(slotTime), eq(window), eq("WINDOW_END_BID_RESET")))
                .thenReturn(updateResult);
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("00:00-02:00,10:00-24:00"), eq(slotTime.minusMinutes(10))))
                .thenReturn(new PromotionTimeWindowResult(true, "BUSINESS_TIME_MATCHED", window));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("00:00-02:00,10:00-24:00"), eq(slotTime)))
                .thenReturn(new PromotionTimeWindowResult(false, "OUT_OF_BUSINESS_WINDOW", null));

        ArgumentCaptor<MtPromotionBidRecord> recordCaptor = ArgumentCaptor.forClass(MtPromotionBidRecord.class);
        doAnswer(invocation -> 1).when(mtPromotionBidRecordMapper).insert(recordCaptor.capture());

        service.executeSlot(slotTime);

        verify(mtShopClient).queryAuthorizedShop(1L, "token", "AUTO_BID_FILL_SHIPPING_TIME");
        verify(mtShopService).updateShippingTimeFromAuthorizedDetail(shop, detail);
        verify(promotionTimeWindowEvaluator).evaluate(any(), eq("00:00-02:00,10:00-24:00"), eq(slotTime));
    }

    @Test
    void shouldDisablePromotionBeforeExecutionWhenPowerBelowExecutionThreshold() throws Exception {
        PromotionBidDecisionService decisionService = new PromotionBidDecisionService(
                new com.gogoga.backend.service.promotion.PromotionDecisionEngine()
        );
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        MtPromotionAutoBidService service = buildService(decisionService, objectMapper);

        LocalDateTime slotTime = LocalDateTime.of(2026, 4, 8, 12, 30);
        MtPromotionAutoBidCandidate candidate = buildCandidate();
        MtPromotionSetting currentSetting = buildCurrentSetting(true, candidate.getStrategyConfigJson());

        when(mtPromotionSettingMapper.selectAutoBidCandidates(slotTime)).thenReturn(List.of(candidate));
        when(mtPromotionBidRecordMapper.selectByShopIdAndBizDateAndSlotTime(eq(1L), any(), eq(slotTime))).thenReturn(null);
        when(mtPromotionSettingService.normalizeForExecution(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mtPromotionSettingService.getOrCreateByShopId(1L)).thenReturn(currentSetting);
        when(mtPowerAccountService.getOrCreateAccount(1L)).thenReturn(buildPowerAccount(1999));
        PromotionExecutionWindow window = PromotionExecutionWindow.of(
                LocalDateTime.of(2026, 4, 8, 10, 0),
                LocalDateTime.of(2026, 4, 8, 22, 0)
        );
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(slotTime.minusMinutes(10))))
                .thenReturn(new PromotionTimeWindowResult(true, "BUSINESS_TIME_MATCHED", window));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(slotTime)))
                .thenReturn(new PromotionTimeWindowResult(true, "BUSINESS_TIME_MATCHED", window));

        ArgumentCaptor<MtPromotionBidRecord> recordCaptor = ArgumentCaptor.forClass(MtPromotionBidRecord.class);
        doAnswer(invocation -> 1).when(mtPromotionBidRecordMapper).insert(recordCaptor.capture());

        service.executeSlot(slotTime);

        MtPromotionBidRecord inserted = recordCaptor.getValue();
        assertEquals("SKIPPED", inserted.getExecuteStatus());
        assertEquals("POWER_BELOW_EXECUTION_THRESHOLD_AUTO_DISABLED", inserted.getDecisionReason());
        verify(mtPromotionSettingService).disablePromotionByShopId(1L);
        verify(promotionTimeWindowEvaluator, org.mockito.Mockito.times(2)).evaluate(any(), any(), any());
        verify(promotionPowerConsumeService, never()).consumeForDecision(any(), any(), any());
    }

    @Test
    void shouldContinueBeforeExecutionWhenPowerEqualsExecutionThresholdAndWarns() throws Exception {
        PromotionBidDecisionService decisionService = new PromotionBidDecisionService(
                new com.gogoga.backend.service.promotion.PromotionDecisionEngine()
        );
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        MtPromotionAutoBidService service = buildService(decisionService, objectMapper);

        LocalDateTime slotTime = LocalDateTime.of(2026, 4, 8, 12, 30);
        MtPromotionAutoBidCandidate candidate = buildCandidate();
        PromotionExecutionWindow window = PromotionExecutionWindow.of(
                LocalDateTime.of(2026, 4, 8, 10, 0),
                LocalDateTime.of(2026, 4, 8, 22, 0)
        );
        PromotionMetricsSnapshot snapshot = new PromotionMetricsSnapshot();
        snapshot.setPromotionBalance(new BigDecimal("100.00"));
        snapshot.setCostCounterValue(new BigDecimal("30.30"));
        snapshot.setWindowCost(new BigDecimal("3.30"));
        snapshot.setWindowIncome(new BigDecimal("66.00"));
        snapshot.setWindowCostRatio(new BigDecimal("0.050000"));
        snapshot.setSlotCostDelta(new BigDecimal("0.30"));
        snapshot.setMetricsTrusted(true);
        PromotionBidOpenApiResult updateResult = new PromotionBidOpenApiResult();
        updateResult.setSuccess(true);
        updateResult.setPayloadJson("{\"ok\":true}");
        updateResult.setBid(40);
        MtPromotionSetting currentSetting = buildCurrentSetting(true, candidate.getStrategyConfigJson());

        when(mtPromotionSettingMapper.selectAutoBidCandidates(slotTime)).thenReturn(List.of(candidate));
        when(mtPromotionBidRecordMapper.selectByShopIdAndBizDateAndSlotTime(eq(1L), any(), eq(slotTime))).thenReturn(null);
        when(mtPromotionSettingService.normalizeForExecution(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mtPromotionSettingService.getOrCreateByShopId(1L)).thenReturn(currentSetting);
        when(mtPowerAccountService.getOrCreateAccount(1L)).thenReturn(buildPowerAccount(2000));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(slotTime.minusMinutes(10))))
                .thenReturn(new PromotionTimeWindowResult(true, "BUSINESS_TIME_MATCHED", window));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(slotTime)))
                .thenReturn(new PromotionTimeWindowResult(true, "BUSINESS_TIME_MATCHED", window));
        PromotionPlanStatusOpenApiResult statusOpenApiResult = new PromotionPlanStatusOpenApiResult();
        statusOpenApiResult.setSuccess(true);
        statusOpenApiResult.setStatus(MtPromotionOpenApiClient.STATUS_OPEN);
        statusOpenApiResult.setPayloadJson("{\"status\":0}");
        when(mtPromotionOpenApiClient.queryPlanStatus(eq(candidate), eq(slotTime), eq(window), eq("WINDOW_STATUS_CHECK")))
                .thenReturn(statusOpenApiResult);
        when(promotionMetricsProvider.queryMetrics(eq(candidate), any(PromotionExecutionWindow.class), eq(slotTime))).thenReturn(snapshot);
        when(mtPromotionBidRecordMapper.selectLatestSuccessByShopIdAndWindowKey(eq(1L), eq(window.getWindowKey()), anyInt()))
                .thenReturn(List.of(
                        buildHistory(slotTime.minusMinutes(10), "0.40", "3.10"),
                        buildHistory(slotTime.minusMinutes(20), "0.40", "3.00")
                ));
        when(mtPromotionOpenApiClient.updatePlanBid(eq(candidate), eq(40), eq(slotTime), eq(window), eq("SLOT_BID_UPDATE")))
                .thenReturn(updateResult);

        ArgumentCaptor<MtPromotionBidRecord> recordCaptor = ArgumentCaptor.forClass(MtPromotionBidRecord.class);
        doAnswer(invocation -> 1).when(mtPromotionBidRecordMapper).insert(recordCaptor.capture());

        service.executeSlot(slotTime);

        List<MtPromotionBidRecord> insertedRecords = recordCaptor.getAllValues();
        assertEquals(2, insertedRecords.size());
        assertEquals("POWER_BELOW_WARNING_THRESHOLD_REMINDER", insertedRecords.get(0).getDecisionReason());
        assertEquals("SUCCESS", insertedRecords.get(1).getExecuteStatus());
        verify(mtPromotionSettingService, never()).disablePromotionByShopId(1L);
    }

    @Test
    void shouldContinueBeforeExecutionWhenPowerAboveExecutionThreshold() throws Exception {
        PromotionBidDecisionService decisionService = new PromotionBidDecisionService(
                new com.gogoga.backend.service.promotion.PromotionDecisionEngine()
        );
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        MtPromotionAutoBidService service = buildService(decisionService, objectMapper);

        LocalDateTime slotTime = LocalDateTime.of(2026, 4, 8, 12, 30);
        MtPromotionAutoBidCandidate candidate = buildCandidate();
        PromotionExecutionWindow previousWindow = PromotionExecutionWindow.of(
                LocalDateTime.of(2026, 4, 8, 10, 0),
                LocalDateTime.of(2026, 4, 8, 22, 0)
        );
        MtPromotionSetting currentSetting = buildCurrentSetting(true, candidate.getStrategyConfigJson());

        when(mtPromotionSettingMapper.selectAutoBidCandidates(slotTime)).thenReturn(List.of(candidate));
        when(mtPromotionBidRecordMapper.selectByShopIdAndBizDateAndSlotTime(eq(1L), any(), eq(slotTime))).thenReturn(null);
        when(mtPromotionSettingService.normalizeForExecution(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mtPromotionSettingService.getOrCreateByShopId(1L)).thenReturn(currentSetting);
        when(mtPowerAccountService.getOrCreateAccount(1L)).thenReturn(buildPowerAccount(25000));
        PromotionExecutionWindow currentWindow = PromotionExecutionWindow.of(
                LocalDateTime.of(2026, 4, 8, 10, 0),
                LocalDateTime.of(2026, 4, 8, 22, 0)
        );
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(slotTime.minusMinutes(10))))
                .thenReturn(new PromotionTimeWindowResult(true, "BUSINESS_TIME_MATCHED", previousWindow));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(slotTime)))
                .thenReturn(new PromotionTimeWindowResult(true, "BUSINESS_TIME_MATCHED", currentWindow));
        PromotionPlanStatusOpenApiResult statusOpenApiResult = new PromotionPlanStatusOpenApiResult();
        statusOpenApiResult.setSuccess(true);
        statusOpenApiResult.setStatus(MtPromotionOpenApiClient.STATUS_OPEN);
        when(mtPromotionOpenApiClient.queryPlanStatus(eq(candidate), eq(slotTime), eq(currentWindow), eq("WINDOW_STATUS_CHECK")))
                .thenReturn(statusOpenApiResult);
        PromotionMetricsSnapshot snapshot = new PromotionMetricsSnapshot();
        snapshot.setPromotionBalance(new BigDecimal("100.00"));
        snapshot.setCostCounterValue(new BigDecimal("20.00"));
        snapshot.setWindowCost(new BigDecimal("1.00"));
        snapshot.setWindowIncome(new BigDecimal("20.00"));
        snapshot.setWindowCostRatio(new BigDecimal("0.050000"));
        snapshot.setSlotCostDelta(new BigDecimal("0.10"));
        snapshot.setMetricsTrusted(true);
        when(promotionMetricsProvider.queryMetrics(eq(candidate), any(PromotionExecutionWindow.class), eq(slotTime))).thenReturn(snapshot);
        when(mtPromotionBidRecordMapper.selectLatestSuccessByShopIdAndWindowKey(eq(1L), eq(currentWindow.getWindowKey()), anyInt()))
                .thenReturn(List.of(
                        buildHistory(slotTime.minusMinutes(10), "0.40", "3.10"),
                        buildHistory(slotTime.minusMinutes(20), "0.40", "3.00")
                ));
        PromotionBidOpenApiResult updateResult = new PromotionBidOpenApiResult();
        updateResult.setSuccess(true);
        updateResult.setPayloadJson("{\"bid\":40}");
        updateResult.setBid(40);
        when(mtPromotionOpenApiClient.updatePlanBid(eq(candidate), eq(40), eq(slotTime), eq(currentWindow), eq("SLOT_BID_UPDATE")))
                .thenReturn(updateResult);
        ArgumentCaptor<MtPromotionBidRecord> recordCaptor = ArgumentCaptor.forClass(MtPromotionBidRecord.class);
        doAnswer(invocation -> 1).when(mtPromotionBidRecordMapper).insert(recordCaptor.capture());

        service.executeSlot(slotTime);

        List<MtPromotionBidRecord> insertedRecords = recordCaptor.getAllValues();
        assertEquals(1, insertedRecords.size());
        assertEquals("SUCCESS", insertedRecords.get(0).getExecuteStatus());
        verify(mtPromotionSettingService, never()).disablePromotionByShopId(any());
    }

    @Test
    void shouldDisablePromotionWhenPromotionBalanceBelowExecutionThreshold() throws Exception {
        PromotionBidDecisionService decisionService = new PromotionBidDecisionService(
                new com.gogoga.backend.service.promotion.PromotionDecisionEngine()
        );
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        MtPromotionAutoBidService service = buildService(decisionService, objectMapper);

        LocalDateTime slotTime = LocalDateTime.of(2026, 4, 8, 12, 30);
        MtPromotionAutoBidCandidate candidate = buildCandidate();
        PromotionExecutionWindow window = PromotionExecutionWindow.of(
                LocalDateTime.of(2026, 4, 8, 10, 0),
                LocalDateTime.of(2026, 4, 8, 22, 0)
        );
        MtPromotionSetting currentSetting = buildCurrentSetting(true, candidate.getStrategyConfigJson());
        PromotionPlanStatusOpenApiResult statusOpenApiResult = new PromotionPlanStatusOpenApiResult();
        statusOpenApiResult.setSuccess(true);
        statusOpenApiResult.setStatus(MtPromotionOpenApiClient.STATUS_OPEN);
        statusOpenApiResult.setPayloadJson("{\"status\":0}");
        PromotionMetricsSnapshot snapshot = new PromotionMetricsSnapshot();
        snapshot.setPromotionBalance(new BigDecimal("0.50"));
        snapshot.setCostCounterValue(new BigDecimal("30.30"));
        snapshot.setWindowCost(new BigDecimal("0.00"));
        snapshot.setWindowIncome(BigDecimal.ZERO.setScale(2));
        snapshot.setWindowCostRatio(BigDecimal.ZERO.setScale(6));
        snapshot.setSlotCostDelta(BigDecimal.ZERO.setScale(2));
        snapshot.setMetricsTrusted(true);
        snapshot.setPayloadJson("{\"promotionBalance\":0.50}");

        when(mtPromotionSettingMapper.selectAutoBidCandidates(slotTime)).thenReturn(List.of(candidate));
        when(mtPromotionBidRecordMapper.selectByShopIdAndBizDateAndSlotTime(eq(1L), any(), eq(slotTime))).thenReturn(null);
        when(mtPromotionSettingService.normalizeForExecution(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mtPromotionSettingService.getOrCreateByShopId(1L)).thenReturn(currentSetting);
        when(mtPowerAccountService.getOrCreateAccount(1L)).thenReturn(buildPowerAccount(25000));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(slotTime.minusMinutes(10))))
                .thenReturn(new PromotionTimeWindowResult(true, "BUSINESS_TIME_MATCHED", window));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(slotTime)))
                .thenReturn(new PromotionTimeWindowResult(true, "BUSINESS_TIME_MATCHED", window));
        when(mtPromotionOpenApiClient.queryPlanStatus(eq(candidate), eq(slotTime), eq(window), eq("WINDOW_STATUS_CHECK")))
                .thenReturn(statusOpenApiResult);
        when(promotionMetricsProvider.queryMetrics(eq(candidate), any(PromotionExecutionWindow.class), eq(slotTime))).thenReturn(snapshot);

        ArgumentCaptor<MtPromotionBidRecord> recordCaptor = ArgumentCaptor.forClass(MtPromotionBidRecord.class);
        doAnswer(invocation -> 1).when(mtPromotionBidRecordMapper).insert(recordCaptor.capture());

        service.executeSlot(slotTime);

        MtPromotionBidRecord inserted = recordCaptor.getValue();
        assertEquals("SKIPPED", inserted.getExecuteStatus());
        assertEquals("PROMOTION_BALANCE_BELOW_EXECUTION_THRESHOLD_AUTO_DISABLED", inserted.getDecisionReason());
        verify(mtPromotionSettingService).disablePromotionByShopId(1L);
        verify(mtPromotionOpenApiClient, never()).updatePlanBid(any(), any(), any(), any(), any());
        verify(promotionPowerConsumeService, never()).consumeForDecision(any(), any(), any());
    }

    @Test
    void shouldInsertPromotionBalanceWarningBeforeContinuingExecution() throws Exception {
        PromotionBidDecisionService decisionService = new PromotionBidDecisionService(
                new com.gogoga.backend.service.promotion.PromotionDecisionEngine()
        );
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        MtPromotionAutoBidService service = buildService(decisionService, objectMapper);

        LocalDateTime slotTime = LocalDateTime.of(2026, 4, 8, 12, 30);
        MtPromotionAutoBidCandidate candidate = buildCandidate();
        PromotionExecutionWindow window = PromotionExecutionWindow.of(
                LocalDateTime.of(2026, 4, 8, 10, 0),
                LocalDateTime.of(2026, 4, 8, 22, 0)
        );
        MtPromotionSetting currentSetting = buildCurrentSetting(true, candidate.getStrategyConfigJson());
        PromotionPlanStatusOpenApiResult statusOpenApiResult = new PromotionPlanStatusOpenApiResult();
        statusOpenApiResult.setSuccess(true);
        statusOpenApiResult.setStatus(MtPromotionOpenApiClient.STATUS_OPEN);
        statusOpenApiResult.setPayloadJson("{\"status\":0}");
        PromotionMetricsSnapshot snapshot = new PromotionMetricsSnapshot();
        snapshot.setPromotionBalance(new BigDecimal("4.00"));
        snapshot.setCostCounterValue(new BigDecimal("30.30"));
        snapshot.setWindowCost(new BigDecimal("0.00"));
        snapshot.setWindowIncome(BigDecimal.ZERO.setScale(2));
        snapshot.setWindowCostRatio(BigDecimal.ZERO.setScale(6));
        snapshot.setSlotCostDelta(BigDecimal.ZERO.setScale(2));
        snapshot.setMetricsTrusted(false);
        snapshot.setUntrustedReason("PREVIOUS_SLOT_SNAPSHOT_MISSING");
        snapshot.setPayloadJson("{\"promotionBalance\":4.00,\"metricsTrusted\":false}");

        when(mtPromotionSettingMapper.selectAutoBidCandidates(slotTime)).thenReturn(List.of(candidate));
        when(mtPromotionBidRecordMapper.selectByShopIdAndBizDateAndSlotTime(eq(1L), any(), eq(slotTime))).thenReturn(null);
        when(mtPromotionSettingService.normalizeForExecution(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mtPromotionSettingService.getOrCreateByShopId(1L)).thenReturn(currentSetting);
        when(mtPowerAccountService.getOrCreateAccount(1L)).thenReturn(buildPowerAccount(25000));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(slotTime.minusMinutes(10))))
                .thenReturn(new PromotionTimeWindowResult(true, "BUSINESS_TIME_MATCHED", window));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(slotTime)))
                .thenReturn(new PromotionTimeWindowResult(true, "BUSINESS_TIME_MATCHED", window));
        when(mtPromotionOpenApiClient.queryPlanStatus(eq(candidate), eq(slotTime), eq(window), eq("WINDOW_STATUS_CHECK")))
                .thenReturn(statusOpenApiResult);
        when(promotionMetricsProvider.queryMetrics(eq(candidate), any(PromotionExecutionWindow.class), eq(slotTime))).thenReturn(snapshot);

        ArgumentCaptor<MtPromotionBidRecord> recordCaptor = ArgumentCaptor.forClass(MtPromotionBidRecord.class);
        doAnswer(invocation -> 1).when(mtPromotionBidRecordMapper).insert(recordCaptor.capture());

        service.executeSlot(slotTime);

        List<MtPromotionBidRecord> insertedRecords = recordCaptor.getAllValues();
        assertEquals(2, insertedRecords.size());
        assertEquals("PROMOTION_BALANCE_BELOW_WARNING_THRESHOLD_REMINDER", insertedRecords.get(0).getDecisionReason());
        assertEquals("UNTRUSTED_PROMOTION_METRICS:PREVIOUS_SLOT_SNAPSHOT_MISSING", insertedRecords.get(1).getDecisionReason());
        verify(mtPromotionSettingService, never()).disablePromotionByShopId(any());
    }

    @Test
    void shouldPersistMarkdownAndConsumeLogPowerForSuccessRecord() throws Exception {
        PromotionBidDecisionService decisionService = new PromotionBidDecisionService(
                new com.gogoga.backend.service.promotion.PromotionDecisionEngine()
        );
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        MtPromotionAutoBidService service = buildServiceWithLogMarkdown(decisionService, objectMapper);

        LocalDateTime slotTime = LocalDateTime.of(2026, 4, 8, 12, 30);
        MtPromotionAutoBidCandidate candidate = buildCandidate();
        PromotionExecutionWindow window = PromotionExecutionWindow.of(
                LocalDateTime.of(2026, 4, 8, 10, 0),
                LocalDateTime.of(2026, 4, 8, 22, 0)
        );
        PromotionMetricsSnapshot snapshot = new PromotionMetricsSnapshot();
        snapshot.setPromotionBalance(new BigDecimal("100.00"));
        snapshot.setCostCounterValue(new BigDecimal("30.30"));
        snapshot.setWindowCost(new BigDecimal("3.30"));
        snapshot.setWindowIncome(new BigDecimal("66.00"));
        snapshot.setWindowCostRatio(new BigDecimal("0.050000"));
        snapshot.setSlotCostDelta(new BigDecimal("0.30"));
        snapshot.setMetricsTrusted(true);
        PromotionBidOpenApiResult updateResult = new PromotionBidOpenApiResult();
        updateResult.setSuccess(true);
        updateResult.setPayloadJson("{\"ok\":true}");
        updateResult.setBid(40);
        PromotionPlanStatusOpenApiResult statusOpenApiResult = new PromotionPlanStatusOpenApiResult();
        statusOpenApiResult.setSuccess(true);
        statusOpenApiResult.setStatus(MtPromotionOpenApiClient.STATUS_OPEN);
        statusOpenApiResult.setPayloadJson("{\"status\":0}");
        MtPromotionSetting currentSetting = buildCurrentSetting(true, candidate.getStrategyConfigJson());
        PromotionLogMarkdownResult markdownResult = new PromotionLogMarkdownResult();
        markdownResult.setMarkdownMessage("**自动调价执行成功**\n- 指标：累计花费 3.3 元，累计实收 66 元");
        markdownResult.setPromptTokens(1000L);
        markdownResult.setCompletionTokens(234L);
        markdownResult.setTotalTokens(1234L);

        when(mtPromotionSettingMapper.selectAutoBidCandidates(slotTime)).thenReturn(List.of(candidate));
        when(mtPromotionBidRecordMapper.selectByShopIdAndBizDateAndSlotTime(eq(1L), any(), eq(slotTime))).thenReturn(null);
        when(mtPromotionSettingService.normalizeForExecution(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mtPromotionSettingService.getOrCreateByShopId(1L)).thenReturn(currentSetting);
        when(mtPowerAccountService.getOrCreateAccount(1L)).thenReturn(buildPowerAccount(25000));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(slotTime.minusMinutes(10))))
                .thenReturn(new PromotionTimeWindowResult(false, "OUT_OF_BUSINESS_WINDOW", null));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(slotTime)))
                .thenReturn(new PromotionTimeWindowResult(true, "AUTO_TIME_MODE", window));
        when(mtPromotionOpenApiClient.queryPlanStatus(eq(candidate), eq(slotTime), eq(window), eq("WINDOW_STATUS_CHECK")))
                .thenReturn(statusOpenApiResult);
        when(promotionMetricsProvider.queryMetrics(eq(candidate), any(PromotionExecutionWindow.class), eq(slotTime))).thenReturn(snapshot);
        when(mtPromotionBidRecordMapper.selectLatestSuccessByShopIdAndWindowKey(eq(1L), eq(window.getWindowKey()), anyInt()))
                .thenReturn(List.of(
                        buildHistory(slotTime.minusMinutes(10), "0.40", "3.10"),
                        buildHistory(slotTime.minusMinutes(20), "0.40", "3.00")
                ));
        when(mtPromotionOpenApiClient.updatePlanBid(eq(candidate), eq(40), eq(slotTime), eq(window), eq("SLOT_BID_UPDATE")))
                .thenReturn(updateResult);
        when(promotionLogMarkdownService.generate(any())).thenReturn(markdownResult);

        ArgumentCaptor<MtPromotionBidRecord> recordCaptor = ArgumentCaptor.forClass(MtPromotionBidRecord.class);
        doAnswer(invocation -> 1).when(mtPromotionBidRecordMapper).insert(recordCaptor.capture());

        service.executeSlot(slotTime);

        MtPromotionBidRecord inserted = recordCaptor.getValue();
        assertEquals(markdownResult.getMarkdownMessage(), inserted.getMarkdownMessage());
        assertEquals(1234L, JsonUtil.toBean(inserted.getDecisionDetailJson(), com.gogoga.backend.service.promotion.PromotionDecisionDetail.class).getLogLlmTotalTokens());
        verify(mtPromotionAdDataService).ensureRecentAdDataBackfillBeforeLogGeneration(1L, "token");
        verify(promotionPowerConsumeService).consumeForLogGeneration(1L, slotTime, 1000L, 234L);
    }

    @Test
    void shouldRefreshLowPeakBaselineWhenOutsidePeakWindowAfterWindowInitialized() throws Exception {
        PromotionBidDecisionService decisionService = new PromotionBidDecisionService(
                new com.gogoga.backend.service.promotion.PromotionDecisionEngine()
        );
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        MtPromotionAutoBidService service = buildService(decisionService, objectMapper);

        LocalDateTime slotTime = LocalDateTime.of(2026, 4, 8, 12, 30);
        MtPromotionAutoBidCandidate candidate = buildCandidate();
        PromotionExecutionWindow window = PromotionExecutionWindow.of(
                LocalDateTime.of(2026, 4, 8, 10, 0),
                LocalDateTime.of(2026, 4, 8, 22, 0)
        );
        PromotionMetricsSnapshot snapshot = new PromotionMetricsSnapshot();
        snapshot.setPromotionBalance(new BigDecimal("100.00"));
        snapshot.setCostCounterValue(new BigDecimal("30.30"));
        snapshot.setWindowCost(BigDecimal.ZERO.setScale(2));
        snapshot.setWindowIncome(new BigDecimal("66.00"));
        snapshot.setWindowCostRatio(BigDecimal.ZERO.setScale(6));
        snapshot.setSlotCostDelta(BigDecimal.ZERO.setScale(2));
        snapshot.setMetricsTrusted(true);
        PromotionBidOpenApiResult updateResult = new PromotionBidOpenApiResult();
        updateResult.setSuccess(true);
        updateResult.setPayloadJson("{\"ok\":true}");
        updateResult.setBid(40);
        PromotionPlanStatusOpenApiResult statusOpenApiResult = new PromotionPlanStatusOpenApiResult();
        statusOpenApiResult.setSuccess(true);
        statusOpenApiResult.setStatus(MtPromotionOpenApiClient.STATUS_OPEN);
        statusOpenApiResult.setPayloadJson("{\"status\":0}");
        PeerBidPriceOpenApiResult peerBidResult = new PeerBidPriceOpenApiResult();
        peerBidResult.setSuccess(true);
        peerBidResult.setAverageBidAmount(new BigDecimal("1.30"));
        peerBidResult.setPayloadJson("{\"averageBidAmount\":1.30}");
        MtPromotionSetting currentSetting = buildCurrentSetting(true, candidate.getStrategyConfigJson());
        ShopPeakWindowProfileLookupResult peakLookupResult = new ShopPeakWindowProfileLookupResult();
        peakLookupResult.setDayType(ShopPeakDayType.HOLIDAY);
        peakLookupResult.setProfileStatus(ShopPeakWindowProfileLookupResult.STATUS_USABLE);
        peakLookupResult.setGatingEnabled(true);
        peakLookupResult.setPeakMatched(false);
        peakLookupResult.setMatchedWindow(null);
        peakLookupResult.setSampleDayCount(8);
        peakLookupResult.setSampleOrderCount(40);
        peakLookupResult.setProfileDayType(ShopPeakDayType.WEEKEND);
        peakLookupResult.setGeneratedAt(LocalDateTime.of(2026, 4, 8, 4, 10));
        peakLookupResult.setPeakWindows(List.of(buildPeakWindow(14, 17)));
        MtPromotionRecentOrderStatistic statistic = buildRecentOrderStatistic(
                slotTime.toLocalDate(),
                true,
                "210",
                "30.00",
                "0.500000"
        );

        when(mtPromotionSettingMapper.selectAutoBidCandidates(slotTime)).thenReturn(List.of(candidate));
        when(mtPromotionBidRecordMapper.selectByShopIdAndBizDateAndSlotTime(eq(1L), any(), eq(slotTime))).thenReturn(null);
        when(mtPromotionSettingService.normalizeForExecution(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mtPromotionSettingService.getOrCreateByShopId(1L)).thenReturn(currentSetting);
        when(mtPowerAccountService.getOrCreateAccount(1L)).thenReturn(buildPowerAccount(25000));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(slotTime.minusMinutes(10))))
                .thenReturn(new PromotionTimeWindowResult(false, "OUT_OF_BUSINESS_WINDOW", null));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(slotTime)))
                .thenReturn(new PromotionTimeWindowResult(true, "AUTO_TIME_MODE", window));
        when(mtPromotionOpenApiClient.queryPlanStatus(eq(candidate), eq(slotTime), eq(window), eq("WINDOW_STATUS_CHECK")))
                .thenReturn(statusOpenApiResult);
        when(promotionMetricsProvider.queryMetrics(eq(candidate), any(PromotionExecutionWindow.class), eq(slotTime))).thenReturn(snapshot);
        when(mtPromotionBidRecordMapper.selectLatestSuccessByShopIdAndWindowKey(eq(1L), eq(window.getWindowKey()), anyInt()))
                .thenReturn(List.of(buildHistory(slotTime.minusMinutes(10), "0.40", "0.00")));
        when(mtPromotionOpenApiClient.queryPeerAverageBid(eq(candidate), eq(slotTime), eq(window), eq("LOW_PEAK_BASELINE_FROM_PEER_AVG")))
                .thenReturn(peerBidResult);
        when(mtPromotionOpenApiClient.updatePlanBid(eq(candidate), eq(20), eq(slotTime), eq(window), eq("SLOT_BID_UPDATE")))
                .thenReturn(updateResult);
        when(shopPeakWindowProfileService.lookup(1L, slotTime)).thenReturn(peakLookupResult);
        when(promotionRecentOrderStatisticService.resolve(1L, slotTime.toLocalDate(), peakLookupResult)).thenReturn(statistic);

        ArgumentCaptor<MtPromotionBidRecord> recordCaptor = ArgumentCaptor.forClass(MtPromotionBidRecord.class);
        doAnswer(invocation -> 1).when(mtPromotionBidRecordMapper).insert(recordCaptor.capture());

        service.executeSlot(slotTime);

        MtPromotionBidRecord inserted = recordCaptor.getValue();
        assertEquals("LOW_PEAK_BASELINE_BID", inserted.getDecisionReason());
        assertEquals(new BigDecimal("0.20"), inserted.getNextBidAmount());
        com.gogoga.backend.service.promotion.PromotionDecisionDetail detail =
                JsonUtil.toBean(inserted.getDecisionDetailJson(), com.gogoga.backend.service.promotion.PromotionDecisionDetail.class);
        assertEquals("HOLIDAY", detail.getPeakWindowDayType());
        assertEquals("WEEKEND", detail.getPeakWindowProfileDayType());
        assertEquals(Boolean.FALSE, detail.getPeakWindowMatched());
        assertEquals(Boolean.TRUE, detail.getPeakWindowGatingEnabled());
        assertEquals("LOW_PEAK_BASELINE", detail.getPricingBranch());
        assertEquals(new BigDecimal("0.20"), detail.getWindowBaselineBidAmount());
        assertEquals(null, detail.getLatestHighPeakBidAmount());
        assertEquals(Boolean.FALSE, detail.getLowPeakShareBaselineApplied());
        verify(mtPromotionOpenApiClient).queryPeerAverageBid(eq(candidate), eq(slotTime), eq(window), eq("LOW_PEAK_BASELINE_FROM_PEER_AVG"));
    }

    @Test
    void shouldUseLowPeakOrderShareBaselineWhenTrustedPeakProfileHasLatestHighPeakBid() throws Exception {
        PromotionBidDecisionService decisionService = new PromotionBidDecisionService(
                new com.gogoga.backend.service.promotion.PromotionDecisionEngine()
        );
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        MtPromotionAutoBidService service = buildService(decisionService, objectMapper);

        LocalDateTime slotTime = LocalDateTime.of(2026, 4, 8, 12, 30);
        MtPromotionAutoBidCandidate candidate = buildCandidate();
        PromotionExecutionWindow window = PromotionExecutionWindow.of(
                LocalDateTime.of(2026, 4, 8, 10, 0),
                LocalDateTime.of(2026, 4, 8, 22, 0)
        );
        PromotionMetricsSnapshot snapshot = new PromotionMetricsSnapshot();
        snapshot.setPromotionBalance(new BigDecimal("100.00"));
        snapshot.setCostCounterValue(new BigDecimal("30.30"));
        snapshot.setWindowCost(BigDecimal.ZERO.setScale(2));
        snapshot.setWindowIncome(new BigDecimal("66.00"));
        snapshot.setWindowCostRatio(BigDecimal.ZERO.setScale(6));
        snapshot.setSlotCostDelta(BigDecimal.ZERO.setScale(2));
        snapshot.setMetricsTrusted(true);
        PromotionPlanStatusOpenApiResult statusOpenApiResult = buildStatusResult();
        PeerBidPriceOpenApiResult peerBidResult = new PeerBidPriceOpenApiResult();
        peerBidResult.setSuccess(true);
        peerBidResult.setAverageBidAmount(new BigDecimal("1.30"));
        peerBidResult.setPayloadJson("{\"averageBidAmount\":1.30}");
        MtPromotionSetting currentSetting = buildCurrentSetting(true, candidate.getStrategyConfigJson());
        ShopPeakWindowProfileLookupResult peakLookupResult = new ShopPeakWindowProfileLookupResult();
        peakLookupResult.setDayType(ShopPeakDayType.HOLIDAY);
        peakLookupResult.setProfileStatus(ShopPeakWindowProfileLookupResult.STATUS_USABLE);
        peakLookupResult.setGatingEnabled(true);
        peakLookupResult.setPeakMatched(false);
        peakLookupResult.setMatchedWindow(null);
        peakLookupResult.setSampleDayCount(8);
        peakLookupResult.setSampleOrderCount(40);
        peakLookupResult.setProfileDayType(ShopPeakDayType.WEEKEND);
        peakLookupResult.setGeneratedAt(LocalDateTime.of(2026, 4, 8, 4, 10));
        peakLookupResult.setPeakWindows(List.of(buildPeakWindow(14, 17)));
        MtPromotionRecentOrderStatistic statistic = buildRecentOrderStatistic(
                slotTime.toLocalDate(),
                true,
                "210",
                "30.00",
                "0.500000"
        );

        MtPromotionBidRecord highPeakRecord = buildHistory(slotTime.minusMinutes(20), "1.00", "3.00");
        PromotionDecisionDetail highPeakDetail = new PromotionDecisionDetail();
        highPeakDetail.setPricingBranch("PEAK_RULE");
        highPeakDetail.setPeakBidPhase("PEAK");
        highPeakRecord.setDecisionDetailJson(JsonUtil.toJson(highPeakDetail));

        when(mtPromotionSettingMapper.selectAutoBidCandidates(slotTime)).thenReturn(List.of(candidate));
        when(mtPromotionBidRecordMapper.selectByShopIdAndBizDateAndSlotTime(eq(1L), any(), eq(slotTime))).thenReturn(null);
        when(mtPromotionSettingService.normalizeForExecution(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mtPromotionSettingService.getOrCreateByShopId(1L)).thenReturn(currentSetting);
        when(mtPowerAccountService.getOrCreateAccount(1L)).thenReturn(buildPowerAccount(25000));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(slotTime.minusMinutes(10))))
                .thenReturn(new PromotionTimeWindowResult(false, "OUT_OF_BUSINESS_WINDOW", null));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(slotTime)))
                .thenReturn(new PromotionTimeWindowResult(true, "AUTO_TIME_MODE", window));
        when(mtPromotionOpenApiClient.queryPlanStatus(eq(candidate), eq(slotTime), eq(window), eq("WINDOW_STATUS_CHECK")))
                .thenReturn(statusOpenApiResult);
        when(promotionMetricsProvider.queryMetrics(eq(candidate), any(PromotionExecutionWindow.class), eq(slotTime))).thenReturn(snapshot);
        when(mtPromotionBidRecordMapper.selectLatestSuccessByShopIdAndWindowKey(eq(1L), eq(window.getWindowKey()), anyInt()))
                .thenReturn(List.of(buildHistory(slotTime.minusMinutes(10), "0.40", "0.00")));
        when(mtPromotionOpenApiClient.queryPeerAverageBid(eq(candidate), eq(slotTime), eq(window), eq("LOW_PEAK_BASELINE_FROM_PEER_AVG")))
                .thenReturn(peerBidResult);
        when(mtPromotionOpenApiClient.updatePlanBid(eq(candidate), eq(50), eq(slotTime), eq(window), eq("SLOT_BID_UPDATE")))
                .thenReturn(buildUpdateResult(50));
        when(shopPeakWindowProfileService.lookup(1L, slotTime)).thenReturn(peakLookupResult);
        when(promotionRecentOrderStatisticService.resolve(1L, slotTime.toLocalDate(), peakLookupResult)).thenReturn(statistic);
        when(mtPromotionBidRecordMapper.selectLatestSuccessByShopIdSince(eq(1L), eq(slotTime.toLocalDate().minusDays(15)), eq(50)))
                .thenReturn(List.of(highPeakRecord));

        ArgumentCaptor<MtPromotionBidRecord> recordCaptor = ArgumentCaptor.forClass(MtPromotionBidRecord.class);
        doAnswer(invocation -> 1).when(mtPromotionBidRecordMapper).insert(recordCaptor.capture());

        service.executeSlot(slotTime);

        MtPromotionBidRecord inserted = recordCaptor.getValue();
        assertEquals("LOW_PEAK_BASELINE_BID", inserted.getDecisionReason());
        assertEquals(new BigDecimal("0.50"), inserted.getNextBidAmount());
        PromotionDecisionDetail detail = JsonUtil.toBean(inserted.getDecisionDetailJson(), PromotionDecisionDetail.class);
        assertEquals("LOW_PEAK_BASELINE", detail.getPricingBranch());
        assertEquals(new BigDecimal("0.50"), detail.getWindowBaselineBidAmount());
        assertEquals(new BigDecimal("1.00"), detail.getLatestHighPeakBidAmount());
        assertEquals(new BigDecimal("0.50"), detail.getLowPeakShareBaselineBidAmount());
        assertEquals(Boolean.TRUE, detail.getLowPeakShareBaselineApplied());
    }

    @Test
    void shouldRecalculateLowPeakBaselineWhenReturningToLowPeak() throws Exception {
        PromotionBidDecisionService decisionService = new PromotionBidDecisionService(
                new com.gogoga.backend.service.promotion.PromotionDecisionEngine()
        );
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        MtPromotionAutoBidService service = buildService(decisionService, objectMapper);

        LocalDateTime slotTime = LocalDateTime.of(2026, 4, 8, 15, 30);
        MtPromotionAutoBidCandidate candidate = buildCandidate();
        PromotionExecutionWindow window = PromotionExecutionWindow.of(
                LocalDateTime.of(2026, 4, 8, 10, 0),
                LocalDateTime.of(2026, 4, 8, 22, 0)
        );
        PromotionMetricsSnapshot snapshot = new PromotionMetricsSnapshot();
        snapshot.setPromotionBalance(new BigDecimal("100.00"));
        snapshot.setCostCounterValue(new BigDecimal("30.30"));
        snapshot.setWindowCost(new BigDecimal("3.30"));
        snapshot.setWindowIncome(new BigDecimal("66.00"));
        snapshot.setWindowCostRatio(new BigDecimal("0.050000"));
        snapshot.setSlotCostDelta(new BigDecimal("0.30"));
        snapshot.setMetricsTrusted(true);
        PromotionBidOpenApiResult updateResult = new PromotionBidOpenApiResult();
        updateResult.setSuccess(true);
        updateResult.setPayloadJson("{\"ok\":true}");
        updateResult.setBid(40);
        PromotionPlanStatusOpenApiResult statusOpenApiResult = new PromotionPlanStatusOpenApiResult();
        statusOpenApiResult.setSuccess(true);
        statusOpenApiResult.setStatus(MtPromotionOpenApiClient.STATUS_OPEN);
        statusOpenApiResult.setPayloadJson("{\"status\":0}");
        PeerBidPriceOpenApiResult peerBidResult = new PeerBidPriceOpenApiResult();
        peerBidResult.setSuccess(true);
        peerBidResult.setAverageBidAmount(new BigDecimal("1.50"));
        peerBidResult.setPayloadJson("{\"averageBidAmount\":1.50}");
        MtPromotionSetting currentSetting = buildCurrentSetting(true, candidate.getStrategyConfigJson());
        ShopPeakWindowProfileLookupResult peakLookupResult = new ShopPeakWindowProfileLookupResult();
        peakLookupResult.setDayType(ShopPeakDayType.WEEKDAY);
        peakLookupResult.setProfileStatus(ShopPeakWindowProfileLookupResult.STATUS_USABLE);
        peakLookupResult.setGatingEnabled(true);
        peakLookupResult.setPeakMatched(false);

        when(mtPromotionSettingMapper.selectAutoBidCandidates(slotTime)).thenReturn(List.of(candidate));
        when(mtPromotionBidRecordMapper.selectByShopIdAndBizDateAndSlotTime(eq(1L), any(), eq(slotTime))).thenReturn(null);
        when(mtPromotionSettingService.normalizeForExecution(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mtPromotionSettingService.getOrCreateByShopId(1L)).thenReturn(currentSetting);
        when(mtPowerAccountService.getOrCreateAccount(1L)).thenReturn(buildPowerAccount(25000));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(slotTime.minusMinutes(10))))
                .thenReturn(new PromotionTimeWindowResult(true, "BUSINESS_TIME_MATCHED", window));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(slotTime)))
                .thenReturn(new PromotionTimeWindowResult(true, "BUSINESS_TIME_MATCHED", window));
        when(mtPromotionOpenApiClient.queryPlanStatus(eq(candidate), eq(slotTime), eq(window), eq("WINDOW_STATUS_CHECK")))
                .thenReturn(statusOpenApiResult);
        when(promotionMetricsProvider.queryMetrics(eq(candidate), any(PromotionExecutionWindow.class), eq(slotTime))).thenReturn(snapshot);
        when(mtPromotionBidRecordMapper.selectLatestSuccessByShopIdAndWindowKey(eq(1L), eq(window.getWindowKey()), anyInt()))
                .thenReturn(List.of(buildHistoryWithBaseline(
                        slotTime.minusMinutes(10),
                        "0.50",
                        "3.10",
                        "0.40",
                        "PEAK_RULE"
                )));
        when(mtPromotionOpenApiClient.queryPeerAverageBid(eq(candidate), eq(slotTime), eq(window), eq("LOW_PEAK_BASELINE_FROM_PEER_AVG")))
                .thenReturn(peerBidResult);
        when(mtPromotionOpenApiClient.updatePlanBid(eq(candidate), eq(30), eq(slotTime), eq(window), eq("SLOT_BID_UPDATE")))
                .thenReturn(updateResult);
        when(shopPeakWindowProfileService.lookup(1L, slotTime)).thenReturn(peakLookupResult);

        ArgumentCaptor<MtPromotionBidRecord> recordCaptor = ArgumentCaptor.forClass(MtPromotionBidRecord.class);
        doAnswer(invocation -> 1).when(mtPromotionBidRecordMapper).insert(recordCaptor.capture());

        service.executeSlot(slotTime);

        MtPromotionBidRecord inserted = recordCaptor.getValue();
        assertEquals("LOW_PEAK_BASELINE_BID", inserted.getDecisionReason());
        assertEquals(new BigDecimal("0.50"), inserted.getPreviousBidAmount());
        assertEquals(new BigDecimal("0.30"), inserted.getNextBidAmount());
        PromotionDecisionDetail detail = JsonUtil.toBean(inserted.getDecisionDetailJson(), PromotionDecisionDetail.class);
        assertEquals(new BigDecimal("0.30"), detail.getWindowBaselineBidAmount());
        assertEquals("LOW_PEAK_BASELINE", detail.getPricingBranch());
        verify(mtPromotionOpenApiClient).queryPeerAverageBid(eq(candidate), eq(slotTime), eq(window), eq("LOW_PEAK_BASELINE_FROM_PEER_AVG"));
    }

    @Test
    void shouldFallbackToHistoryBaselineWhenLowPeakPeerAverageQueryFails() throws Exception {
        PromotionBidDecisionService decisionService = new PromotionBidDecisionService(
                new com.gogoga.backend.service.promotion.PromotionDecisionEngine()
        );
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        MtPromotionAutoBidService service = buildService(decisionService, objectMapper);

        LocalDateTime slotTime = LocalDateTime.of(2026, 4, 8, 16, 30);
        MtPromotionAutoBidCandidate candidate = buildCandidate();
        PromotionExecutionWindow window = PromotionExecutionWindow.of(
                LocalDateTime.of(2026, 4, 8, 10, 0),
                LocalDateTime.of(2026, 4, 8, 22, 0)
        );
        PromotionMetricsSnapshot snapshot = new PromotionMetricsSnapshot();
        snapshot.setPromotionBalance(new BigDecimal("100.00"));
        snapshot.setCostCounterValue(new BigDecimal("30.30"));
        snapshot.setWindowCost(new BigDecimal("3.30"));
        snapshot.setWindowIncome(new BigDecimal("66.00"));
        snapshot.setWindowCostRatio(new BigDecimal("0.050000"));
        snapshot.setSlotCostDelta(new BigDecimal("0.30"));
        snapshot.setMetricsTrusted(true);
        PromotionBidOpenApiResult updateResult = new PromotionBidOpenApiResult();
        updateResult.setSuccess(true);
        updateResult.setPayloadJson("{\"ok\":true}");
        updateResult.setBid(40);
        PromotionPlanStatusOpenApiResult statusOpenApiResult = new PromotionPlanStatusOpenApiResult();
        statusOpenApiResult.setSuccess(true);
        statusOpenApiResult.setStatus(MtPromotionOpenApiClient.STATUS_OPEN);
        statusOpenApiResult.setPayloadJson("{\"status\":0}");
        PeerBidPriceOpenApiResult peerBidResult = new PeerBidPriceOpenApiResult();
        peerBidResult.setSuccess(false);
        peerBidResult.setErrorMsg("mock failure");
        MtPromotionSetting currentSetting = buildCurrentSetting(true, candidate.getStrategyConfigJson());
        ShopPeakWindowProfileLookupResult peakLookupResult = new ShopPeakWindowProfileLookupResult();
        peakLookupResult.setDayType(ShopPeakDayType.WEEKDAY);
        peakLookupResult.setProfileStatus(ShopPeakWindowProfileLookupResult.STATUS_USABLE);
        peakLookupResult.setGatingEnabled(true);
        peakLookupResult.setPeakMatched(false);

        when(mtPromotionSettingMapper.selectAutoBidCandidates(slotTime)).thenReturn(List.of(candidate));
        when(mtPromotionBidRecordMapper.selectByShopIdAndBizDateAndSlotTime(eq(1L), any(), eq(slotTime))).thenReturn(null);
        when(mtPromotionSettingService.normalizeForExecution(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mtPromotionSettingService.getOrCreateByShopId(1L)).thenReturn(currentSetting);
        when(mtPowerAccountService.getOrCreateAccount(1L)).thenReturn(buildPowerAccount(25000));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(slotTime.minusMinutes(10))))
                .thenReturn(new PromotionTimeWindowResult(true, "BUSINESS_TIME_MATCHED", window));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(slotTime)))
                .thenReturn(new PromotionTimeWindowResult(true, "BUSINESS_TIME_MATCHED", window));
        when(mtPromotionOpenApiClient.queryPlanStatus(eq(candidate), eq(slotTime), eq(window), eq("WINDOW_STATUS_CHECK")))
                .thenReturn(statusOpenApiResult);
        when(promotionMetricsProvider.queryMetrics(eq(candidate), any(PromotionExecutionWindow.class), eq(slotTime))).thenReturn(snapshot);
        when(mtPromotionBidRecordMapper.selectLatestSuccessByShopIdAndWindowKey(eq(1L), eq(window.getWindowKey()), anyInt()))
                .thenReturn(List.of(buildHistoryWithBaseline(
                        slotTime.minusMinutes(10),
                        "0.50",
                        "3.10",
                        "0.40",
                        "PEAK_RULE"
                )));
        when(mtPromotionOpenApiClient.queryPeerAverageBid(eq(candidate), eq(slotTime), eq(window), eq("LOW_PEAK_BASELINE_FROM_PEER_AVG")))
                .thenReturn(peerBidResult);
        when(mtPromotionOpenApiClient.updatePlanBid(eq(candidate), eq(40), eq(slotTime), eq(window), eq("SLOT_BID_UPDATE")))
                .thenReturn(updateResult);
        when(shopPeakWindowProfileService.lookup(1L, slotTime)).thenReturn(peakLookupResult);

        ArgumentCaptor<MtPromotionBidRecord> recordCaptor = ArgumentCaptor.forClass(MtPromotionBidRecord.class);
        doAnswer(invocation -> 1).when(mtPromotionBidRecordMapper).insert(recordCaptor.capture());

        service.executeSlot(slotTime);

        MtPromotionBidRecord inserted = recordCaptor.getValue();
        assertEquals("LOW_PEAK_BASELINE_BID", inserted.getDecisionReason());
        assertEquals(new BigDecimal("0.40"), inserted.getNextBidAmount());
        PromotionDecisionDetail detail = JsonUtil.toBean(inserted.getDecisionDetailJson(), PromotionDecisionDetail.class);
        assertEquals(new BigDecimal("0.40"), detail.getWindowBaselineBidAmount());
        verify(mtPromotionOpenApiClient).queryPeerAverageBid(eq(candidate), eq(slotTime), eq(window), eq("LOW_PEAK_BASELINE_FROM_PEER_AVG"));
    }

    @Test
    void shouldAllowInitialBidWhenOutsidePeakWindowAndNoSuccessHistoryExists() throws Exception {
        PromotionBidDecisionService decisionService = new PromotionBidDecisionService(
                new com.gogoga.backend.service.promotion.PromotionDecisionEngine()
        );
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        MtPromotionAutoBidService service = buildService(decisionService, objectMapper);

        LocalDateTime slotTime = LocalDateTime.of(2026, 4, 8, 12, 30);
        MtPromotionAutoBidCandidate candidate = buildCandidate();
        PromotionExecutionWindow window = PromotionExecutionWindow.of(
                LocalDateTime.of(2026, 4, 8, 10, 0),
                LocalDateTime.of(2026, 4, 8, 22, 0)
        );
        PromotionMetricsSnapshot snapshot = new PromotionMetricsSnapshot();
        snapshot.setPromotionBalance(new BigDecimal("100.00"));
        snapshot.setCostCounterValue(new BigDecimal("30.30"));
        snapshot.setWindowCost(BigDecimal.ZERO.setScale(2));
        snapshot.setWindowIncome(new BigDecimal("66.00"));
        snapshot.setWindowCostRatio(BigDecimal.ZERO.setScale(6));
        snapshot.setSlotCostDelta(BigDecimal.ZERO.setScale(2));
        snapshot.setMetricsTrusted(true);
        PromotionBidOpenApiResult updateResult = new PromotionBidOpenApiResult();
        updateResult.setSuccess(true);
        updateResult.setPayloadJson("{\"ok\":true}");
        updateResult.setBid(10);
        PromotionPlanStatusOpenApiResult statusOpenApiResult = new PromotionPlanStatusOpenApiResult();
        statusOpenApiResult.setSuccess(true);
        statusOpenApiResult.setStatus(MtPromotionOpenApiClient.STATUS_OPEN);
        statusOpenApiResult.setPayloadJson("{\"status\":0}");
        PeerBidPriceOpenApiResult peerBidResult = new PeerBidPriceOpenApiResult();
        peerBidResult.setSuccess(true);
        peerBidResult.setAverageBidAmount(new BigDecimal("0.66"));
        peerBidResult.setPayloadJson("{\"averageBidAmount\":0.66}");
        MtPromotionSetting currentSetting = buildCurrentSetting(true, candidate.getStrategyConfigJson());
        ShopPeakWindowProfileLookupResult peakLookupResult = new ShopPeakWindowProfileLookupResult();
        peakLookupResult.setDayType(ShopPeakDayType.WEEKDAY);
        peakLookupResult.setProfileStatus(ShopPeakWindowProfileLookupResult.STATUS_USABLE);
        peakLookupResult.setGatingEnabled(true);
        peakLookupResult.setPeakMatched(false);

        when(mtPromotionSettingMapper.selectAutoBidCandidates(slotTime)).thenReturn(List.of(candidate));
        when(mtPromotionBidRecordMapper.selectByShopIdAndBizDateAndSlotTime(eq(1L), any(), eq(slotTime))).thenReturn(null);
        when(mtPromotionSettingService.normalizeForExecution(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mtPromotionSettingService.getOrCreateByShopId(1L)).thenReturn(currentSetting);
        when(mtPowerAccountService.getOrCreateAccount(1L)).thenReturn(buildPowerAccount(25000));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(slotTime.minusMinutes(10))))
                .thenReturn(new PromotionTimeWindowResult(false, "OUT_OF_BUSINESS_WINDOW", null));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(slotTime)))
                .thenReturn(new PromotionTimeWindowResult(true, "AUTO_TIME_MODE", window));
        when(mtPromotionOpenApiClient.queryPlanStatus(eq(candidate), eq(slotTime), eq(window), eq("WINDOW_STATUS_CHECK")))
                .thenReturn(statusOpenApiResult);
        when(promotionMetricsProvider.queryMetrics(eq(candidate), any(PromotionExecutionWindow.class), eq(slotTime))).thenReturn(snapshot);
        when(mtPromotionBidRecordMapper.selectLatestSuccessByShopIdAndWindowKey(eq(1L), eq(window.getWindowKey()), anyInt()))
                .thenReturn(List.of());
        when(mtPromotionOpenApiClient.queryPeerAverageBid(eq(candidate), eq(slotTime), eq(window), eq("LOW_PEAK_BASELINE_FROM_PEER_AVG")))
                .thenReturn(peerBidResult);
        when(mtPromotionOpenApiClient.updatePlanBid(eq(candidate), eq(10), eq(slotTime), eq(window), eq("SLOT_BID_UPDATE")))
                .thenReturn(updateResult);
        when(shopPeakWindowProfileService.lookup(1L, slotTime)).thenReturn(peakLookupResult);

        ArgumentCaptor<MtPromotionBidRecord> recordCaptor = ArgumentCaptor.forClass(MtPromotionBidRecord.class);
        doAnswer(invocation -> 1).when(mtPromotionBidRecordMapper).insert(recordCaptor.capture());

        service.executeSlot(slotTime);

        MtPromotionBidRecord inserted = recordCaptor.getValue();
        assertEquals("LOW_PEAK_BASELINE_BID", inserted.getDecisionReason());
        assertEquals(new BigDecimal("0.10"), inserted.getNextBidAmount());
    }

    @Test
    void shouldBypassPeakGatingWhenProfileUnavailable() throws Exception {
        PromotionBidDecisionService decisionService = new PromotionBidDecisionService(
                new com.gogoga.backend.service.promotion.PromotionDecisionEngine()
        );
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        MtPromotionAutoBidService service = buildService(decisionService, objectMapper);

        LocalDateTime slotTime = LocalDateTime.of(2026, 4, 8, 12, 30);
        MtPromotionAutoBidCandidate candidate = buildCandidate();
        PromotionExecutionWindow window = PromotionExecutionWindow.of(
                LocalDateTime.of(2026, 4, 8, 10, 0),
                LocalDateTime.of(2026, 4, 8, 22, 0)
        );
        PromotionMetricsSnapshot snapshot = new PromotionMetricsSnapshot();
        snapshot.setPromotionBalance(new BigDecimal("100.00"));
        snapshot.setCostCounterValue(new BigDecimal("30.30"));
        snapshot.setWindowCost(BigDecimal.ZERO.setScale(2));
        snapshot.setWindowIncome(new BigDecimal("66.00"));
        snapshot.setWindowCostRatio(BigDecimal.ZERO.setScale(6));
        snapshot.setSlotCostDelta(BigDecimal.ZERO.setScale(2));
        snapshot.setMetricsTrusted(true);
        PromotionBidOpenApiResult updateResult = new PromotionBidOpenApiResult();
        updateResult.setSuccess(true);
        updateResult.setPayloadJson("{\"ok\":true}");
        updateResult.setBid(50);
        PromotionPlanStatusOpenApiResult statusOpenApiResult = new PromotionPlanStatusOpenApiResult();
        statusOpenApiResult.setSuccess(true);
        statusOpenApiResult.setStatus(MtPromotionOpenApiClient.STATUS_OPEN);
        statusOpenApiResult.setPayloadJson("{\"status\":0}");
        MtPromotionSetting currentSetting = buildCurrentSetting(true, candidate.getStrategyConfigJson());
        ShopPeakWindowProfileLookupResult peakLookupResult = new ShopPeakWindowProfileLookupResult();
        peakLookupResult.setDayType(ShopPeakDayType.WEEKDAY);
        peakLookupResult.setProfileStatus(ShopPeakWindowProfileLookupResult.STATUS_UNAVAILABLE);
        peakLookupResult.setGatingEnabled(false);
        peakLookupResult.setPeakMatched(false);
        peakLookupResult.setUnavailableReason("SAMPLE_INSUFFICIENT");
        peakLookupResult.setSampleDayCount(1);
        peakLookupResult.setSampleOrderCount(3);

        when(mtPromotionSettingMapper.selectAutoBidCandidates(slotTime)).thenReturn(List.of(candidate));
        when(mtPromotionBidRecordMapper.selectByShopIdAndBizDateAndSlotTime(eq(1L), any(), eq(slotTime))).thenReturn(null);
        when(mtPromotionSettingService.normalizeForExecution(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mtPromotionSettingService.getOrCreateByShopId(1L)).thenReturn(currentSetting);
        when(mtPowerAccountService.getOrCreateAccount(1L)).thenReturn(buildPowerAccount(25000));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(slotTime.minusMinutes(10))))
                .thenReturn(new PromotionTimeWindowResult(false, "OUT_OF_BUSINESS_WINDOW", null));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(slotTime)))
                .thenReturn(new PromotionTimeWindowResult(true, "AUTO_TIME_MODE", window));
        when(mtPromotionOpenApiClient.queryPlanStatus(eq(candidate), eq(slotTime), eq(window), eq("WINDOW_STATUS_CHECK")))
                .thenReturn(statusOpenApiResult);
        when(promotionMetricsProvider.queryMetrics(eq(candidate), any(PromotionExecutionWindow.class), eq(slotTime))).thenReturn(snapshot);
        when(mtPromotionBidRecordMapper.selectLatestSuccessByShopIdAndWindowKey(eq(1L), eq(window.getWindowKey()), anyInt()))
                .thenReturn(List.of(buildHistory(slotTime.minusMinutes(10), "0.40", "0.00")));
        when(mtPromotionOpenApiClient.updatePlanBid(eq(candidate), eq(50), eq(slotTime), eq(window), eq("SLOT_BID_UPDATE")))
                .thenReturn(updateResult);
        when(shopPeakWindowProfileService.lookup(1L, slotTime)).thenReturn(peakLookupResult);

        ArgumentCaptor<MtPromotionBidRecord> recordCaptor = ArgumentCaptor.forClass(MtPromotionBidRecord.class);
        doAnswer(invocation -> 1).when(mtPromotionBidRecordMapper).insert(recordCaptor.capture());

        service.executeSlot(slotTime);

        MtPromotionBidRecord inserted = recordCaptor.getValue();
        assertEquals("NO_CLICK_COST_DELTA_ZERO", inserted.getDecisionReason());
        assertEquals(new BigDecimal("0.50"), inserted.getNextBidAmount());
        com.gogoga.backend.service.promotion.PromotionDecisionDetail detail =
                JsonUtil.toBean(inserted.getDecisionDetailJson(), com.gogoga.backend.service.promotion.PromotionDecisionDetail.class);
        assertEquals("SAMPLE_INSUFFICIENT", detail.getPeakWindowUnavailableReason());
        assertEquals(Boolean.FALSE, detail.getPeakWindowGatingEnabled());
    }

    @Test
    void shouldUseNormalPricingRuleWhenTimeModeIsBusinessEvenIfPeakProfileWouldBeLowPeak() throws Exception {
        PromotionBidDecisionService decisionService = new PromotionBidDecisionService(
                new com.gogoga.backend.service.promotion.PromotionDecisionEngine()
        );
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        MtPromotionAutoBidService service = buildService(decisionService, objectMapper);

        LocalDateTime slotTime = LocalDateTime.of(2026, 4, 8, 15, 30);
        MtPromotionAutoBidCandidate candidate = buildCandidateWithStrategy(buildStrategyConfigJson("business"));
        MtPromotionSetting currentSetting = buildCurrentSetting(true, candidate.getStrategyConfigJson());
        PromotionExecutionWindow window = PromotionExecutionWindow.of(
                LocalDateTime.of(2026, 4, 8, 10, 0),
                LocalDateTime.of(2026, 4, 8, 22, 0)
        );
        PromotionMetricsSnapshot snapshot = new PromotionMetricsSnapshot();
        snapshot.setPromotionBalance(new BigDecimal("100.00"));
        snapshot.setCostCounterValue(new BigDecimal("30.30"));
        snapshot.setWindowCost(BigDecimal.ZERO.setScale(2));
        snapshot.setWindowIncome(new BigDecimal("66.00"));
        snapshot.setWindowCostRatio(BigDecimal.ZERO.setScale(6));
        snapshot.setSlotCostDelta(BigDecimal.ZERO.setScale(2));
        snapshot.setMetricsTrusted(true);
        PromotionBidOpenApiResult updateResult = new PromotionBidOpenApiResult();
        updateResult.setSuccess(true);
        updateResult.setPayloadJson("{\"ok\":true}");
        updateResult.setBid(50);
        PromotionPlanStatusOpenApiResult statusOpenApiResult = new PromotionPlanStatusOpenApiResult();
        statusOpenApiResult.setSuccess(true);
        statusOpenApiResult.setStatus(MtPromotionOpenApiClient.STATUS_OPEN);
        statusOpenApiResult.setPayloadJson("{\"status\":0}");

        when(mtPromotionSettingMapper.selectAutoBidCandidates(slotTime)).thenReturn(List.of(candidate));
        when(mtPromotionBidRecordMapper.selectByShopIdAndBizDateAndSlotTime(eq(1L), any(), eq(slotTime))).thenReturn(null);
        when(mtPromotionSettingService.normalizeForExecution(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mtPromotionSettingService.getOrCreateByShopId(1L)).thenReturn(currentSetting);
        when(mtPowerAccountService.getOrCreateAccount(1L)).thenReturn(buildPowerAccount(25000));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(slotTime.minusMinutes(10))))
                .thenReturn(new PromotionTimeWindowResult(true, "BUSINESS_TIME_MATCHED", window));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(slotTime)))
                .thenReturn(new PromotionTimeWindowResult(true, "BUSINESS_TIME_MATCHED", window));
        when(mtPromotionOpenApiClient.queryPlanStatus(eq(candidate), eq(slotTime), eq(window), eq("WINDOW_STATUS_CHECK")))
                .thenReturn(statusOpenApiResult);
        when(promotionMetricsProvider.queryMetrics(eq(candidate), any(PromotionExecutionWindow.class), eq(slotTime))).thenReturn(snapshot);
        when(mtPromotionBidRecordMapper.selectLatestSuccessByShopIdAndWindowKey(eq(1L), eq(window.getWindowKey()), anyInt()))
                .thenReturn(List.of(buildHistory(slotTime.minusMinutes(10), "0.40", "0.00")));
        when(mtPromotionOpenApiClient.updatePlanBid(eq(candidate), eq(50), eq(slotTime), eq(window), eq("SLOT_BID_UPDATE")))
                .thenReturn(updateResult);

        ArgumentCaptor<MtPromotionBidRecord> recordCaptor = ArgumentCaptor.forClass(MtPromotionBidRecord.class);
        doAnswer(invocation -> 1).when(mtPromotionBidRecordMapper).insert(recordCaptor.capture());

        service.executeSlot(slotTime);

        MtPromotionBidRecord inserted = recordCaptor.getValue();
        assertEquals("NO_CLICK_COST_DELTA_ZERO", inserted.getDecisionReason());
        assertEquals(new BigDecimal("0.50"), inserted.getNextBidAmount());
        PromotionDecisionDetail detail = JsonUtil.toBean(inserted.getDecisionDetailJson(), PromotionDecisionDetail.class);
        assertEquals("PEAK_RULE", detail.getPricingBranch());
        assertEquals(new BigDecimal("0.40"), detail.getWindowBaselineBidAmount());
        assertEquals(null, detail.getPeakWindowGatingEnabled());
        verify(shopPeakWindowProfileService, never()).lookup(any(), any());
        verify(mtPromotionOpenApiClient, never()).queryPeerAverageBid(eq(candidate), eq(slotTime), eq(window), eq("LOW_PEAK_BASELINE_FROM_PEER_AVG"));
    }

    @Test
    void shouldUseNormalPricingRuleWhenTimeModeIsCustomEvenIfPeakProfileWouldBeLowPeak() throws Exception {
        PromotionBidDecisionService decisionService = new PromotionBidDecisionService(
                new com.gogoga.backend.service.promotion.PromotionDecisionEngine()
        );
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        MtPromotionAutoBidService service = buildService(decisionService, objectMapper);

        LocalDateTime slotTime = LocalDateTime.of(2026, 4, 8, 15, 30);
        MtPromotionAutoBidCandidate candidate = buildCandidateWithStrategy("""
                {"priority":"rate","timeMode":"custom","rateMode":"control","customSlots":[{"start":"10:00","end":"22:00"}],"rateMin":"5%","rateMax":"10%","decisionMode":"llm"}
                """);
        MtPromotionSetting currentSetting = buildCurrentSetting(true, candidate.getStrategyConfigJson());
        PromotionExecutionWindow window = PromotionExecutionWindow.of(
                LocalDateTime.of(2026, 4, 8, 10, 0),
                LocalDateTime.of(2026, 4, 8, 22, 0)
        );
        PromotionMetricsSnapshot snapshot = new PromotionMetricsSnapshot();
        snapshot.setPromotionBalance(new BigDecimal("100.00"));
        snapshot.setCostCounterValue(new BigDecimal("30.30"));
        snapshot.setWindowCost(BigDecimal.ZERO.setScale(2));
        snapshot.setWindowIncome(new BigDecimal("66.00"));
        snapshot.setWindowCostRatio(BigDecimal.ZERO.setScale(6));
        snapshot.setSlotCostDelta(BigDecimal.ZERO.setScale(2));
        snapshot.setMetricsTrusted(true);
        PromotionBidOpenApiResult updateResult = new PromotionBidOpenApiResult();
        updateResult.setSuccess(true);
        updateResult.setPayloadJson("{\"ok\":true}");
        updateResult.setBid(50);
        PromotionPlanStatusOpenApiResult statusOpenApiResult = new PromotionPlanStatusOpenApiResult();
        statusOpenApiResult.setSuccess(true);
        statusOpenApiResult.setStatus(MtPromotionOpenApiClient.STATUS_OPEN);
        statusOpenApiResult.setPayloadJson("{\"status\":0}");

        when(mtPromotionSettingMapper.selectAutoBidCandidates(slotTime)).thenReturn(List.of(candidate));
        when(mtPromotionBidRecordMapper.selectByShopIdAndBizDateAndSlotTime(eq(1L), any(), eq(slotTime))).thenReturn(null);
        when(mtPromotionSettingService.normalizeForExecution(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mtPromotionSettingService.getOrCreateByShopId(1L)).thenReturn(currentSetting);
        when(mtPowerAccountService.getOrCreateAccount(1L)).thenReturn(buildPowerAccount(25000));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(slotTime.minusMinutes(10))))
                .thenReturn(new PromotionTimeWindowResult(true, "CUSTOM_SLOT_MATCHED", window));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(slotTime)))
                .thenReturn(new PromotionTimeWindowResult(true, "CUSTOM_SLOT_MATCHED", window));
        when(mtPromotionOpenApiClient.queryPlanStatus(eq(candidate), eq(slotTime), eq(window), eq("WINDOW_STATUS_CHECK")))
                .thenReturn(statusOpenApiResult);
        when(promotionMetricsProvider.queryMetrics(eq(candidate), any(PromotionExecutionWindow.class), eq(slotTime))).thenReturn(snapshot);
        when(mtPromotionBidRecordMapper.selectLatestSuccessByShopIdAndWindowKey(eq(1L), eq(window.getWindowKey()), anyInt()))
                .thenReturn(List.of(buildHistory(slotTime.minusMinutes(10), "0.40", "0.00")));
        when(mtPromotionOpenApiClient.updatePlanBid(eq(candidate), eq(50), eq(slotTime), eq(window), eq("SLOT_BID_UPDATE")))
                .thenReturn(updateResult);

        ArgumentCaptor<MtPromotionBidRecord> recordCaptor = ArgumentCaptor.forClass(MtPromotionBidRecord.class);
        doAnswer(invocation -> 1).when(mtPromotionBidRecordMapper).insert(recordCaptor.capture());

        service.executeSlot(slotTime);

        MtPromotionBidRecord inserted = recordCaptor.getValue();
        assertEquals("NO_CLICK_COST_DELTA_ZERO", inserted.getDecisionReason());
        assertEquals(new BigDecimal("0.50"), inserted.getNextBidAmount());
        PromotionDecisionDetail detail = JsonUtil.toBean(inserted.getDecisionDetailJson(), PromotionDecisionDetail.class);
        assertEquals("PEAK_RULE", detail.getPricingBranch());
        assertEquals(new BigDecimal("0.40"), detail.getWindowBaselineBidAmount());
        assertEquals(null, detail.getPeakWindowGatingEnabled());
        verify(shopPeakWindowProfileService, never()).lookup(any(), any());
        verify(mtPromotionOpenApiClient, never()).queryPeerAverageBid(eq(candidate), eq(slotTime), eq(window), eq("LOW_PEAK_BASELINE_FROM_PEER_AVG"));
    }

    @Test
    void shouldWarmUpBidBeforePeakWithFixedTrack() throws Exception {
        PromotionBidDecisionService decisionService = new PromotionBidDecisionService(
                new com.gogoga.backend.service.promotion.PromotionDecisionEngine()
        );
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        MtPromotionAutoBidService service = buildService(decisionService, objectMapper);

        LocalDateTime slotTime = LocalDateTime.of(2026, 4, 8, 10, 20);
        LocalDateTime generatedAt = LocalDateTime.of(2026, 4, 8, 4, 10);
        MtPromotionAutoBidCandidate candidate = buildCandidate();
        PromotionExecutionWindow window = PromotionExecutionWindow.of(
                LocalDateTime.of(2026, 4, 8, 9, 30),
                LocalDateTime.of(2026, 4, 8, 23, 0)
        );
        PromotionMetricsSnapshot snapshot = buildTrustedSnapshot("3.30", "0.30");
        PromotionBidOpenApiResult updateResult = buildUpdateResult(70);
        PromotionPlanStatusOpenApiResult statusOpenApiResult = buildStatusResult();
        MtPromotionSetting currentSetting = buildCurrentSetting(true, candidate.getStrategyConfigJson());
        ShopPeakWindowProfileLookupResult peakLookupResult = buildUsablePeakLookup(
                generatedAt,
                false,
                null,
                List.of(buildPeakWindow(11, 14))
        );
        String peakWindowKey = "WEEKDAY:11:00-14:00:2026-04-08T04:10";

        when(mtPromotionSettingMapper.selectAutoBidCandidates(slotTime)).thenReturn(List.of(candidate));
        when(mtPromotionBidRecordMapper.selectByShopIdAndBizDateAndSlotTime(eq(1L), any(), eq(slotTime))).thenReturn(null);
        when(mtPromotionSettingService.normalizeForExecution(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mtPromotionSettingService.getOrCreateByShopId(1L)).thenReturn(currentSetting);
        when(mtPowerAccountService.getOrCreateAccount(1L)).thenReturn(buildPowerAccount(25000));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(slotTime.minusMinutes(10))))
                .thenReturn(new PromotionTimeWindowResult(true, "BUSINESS_TIME_MATCHED", window));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(slotTime)))
                .thenReturn(new PromotionTimeWindowResult(true, "BUSINESS_TIME_MATCHED", window));
        when(mtPromotionOpenApiClient.queryPlanStatus(eq(candidate), eq(slotTime), eq(window), eq("WINDOW_STATUS_CHECK")))
                .thenReturn(statusOpenApiResult);
        when(promotionMetricsProvider.queryMetrics(eq(candidate), any(PromotionExecutionWindow.class), eq(slotTime))).thenReturn(snapshot);
        when(mtPromotionBidRecordMapper.selectLatestSuccessByShopIdAndWindowKey(eq(1L), eq(window.getWindowKey()), anyInt()))
                .thenReturn(List.of(buildHistoryWithRampContext(
                        slotTime.minusMinutes(10),
                        "0.60",
                        "3.10",
                        window,
                        "0.60",
                        peakWindowKey,
                        "1.20"
                )));
        when(mtPromotionOpenApiClient.updatePlanBid(eq(candidate), eq(70), eq(slotTime), eq(window), eq("SLOT_BID_UPDATE")))
                .thenReturn(updateResult);
        when(shopPeakWindowProfileService.lookup(1L, slotTime)).thenReturn(peakLookupResult);

        ArgumentCaptor<MtPromotionBidRecord> recordCaptor = ArgumentCaptor.forClass(MtPromotionBidRecord.class);
        doAnswer(invocation -> 1).when(mtPromotionBidRecordMapper).insert(recordCaptor.capture());

        service.executeSlot(slotTime);

        MtPromotionBidRecord inserted = recordCaptor.getValue();
        assertEquals("PRE_PEAK_RAMP_UP", inserted.getDecisionReason());
        assertEquals(new BigDecimal("0.60"), inserted.getPreviousBidAmount());
        assertEquals(new BigDecimal("0.70"), inserted.getNextBidAmount());
        PromotionDecisionDetail detail = JsonUtil.toBean(inserted.getDecisionDetailJson(), PromotionDecisionDetail.class);
        assertEquals("SMOOTH_PEAK_RAMP", detail.getPricingBranch());
        assertEquals("PRE_PEAK_RAMP_UP", detail.getPeakBidPhase());
        assertEquals(new BigDecimal("0.60"), detail.getLowPeakBaselineBidAmount());
        assertEquals("INHERITED", detail.getLowPeakBaselineSource());
        assertEquals(null, detail.getPeakEntryBidAmount());
        assertEquals(null, detail.getPeakEntryBidSource());
        assertEquals(4, detail.getSlotsUntilPeakStart());
        assertEquals(new BigDecimal("0.70"), detail.getPeakRampTargetBidAmount());
        assertEquals(peakWindowKey, detail.getNextPeakWindowKey());
        verify(mtPromotionOpenApiClient, never()).queryPeerAverageBid(any(), any(), any(), any());
    }

    @ParameterizedTest
    @CsvSource({
            "10:20,4,0.60,0.70,70",
            "10:30,3,0.70,0.80,80",
            "10:40,2,0.80,0.90,90",
            "10:50,1,0.90,1.00,100"
    })
    void shouldApplyFixedWarmupBidForEachContinuousWarmupSlot(
            String slotText,
            int expectedSlotsUntilPeakStart,
            String previousBidAmount,
            String expectedBidAmount,
            int expectedBidCents
    ) throws Exception {
        PromotionBidDecisionService decisionService = new PromotionBidDecisionService(
                new com.gogoga.backend.service.promotion.PromotionDecisionEngine()
        );
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        MtPromotionAutoBidService service = buildService(decisionService, objectMapper);

        LocalTime localTime = LocalTime.parse(slotText);
        LocalDateTime slotTime = LocalDateTime.of(2026, 4, 8, localTime.getHour(), localTime.getMinute());
        LocalDateTime generatedAt = LocalDateTime.of(2026, 4, 8, 4, 10);
        MtPromotionAutoBidCandidate candidate = buildCandidate();
        PromotionExecutionWindow window = PromotionExecutionWindow.of(
                LocalDateTime.of(2026, 4, 8, 9, 30),
                LocalDateTime.of(2026, 4, 8, 23, 0)
        );
        PromotionMetricsSnapshot snapshot = buildTrustedSnapshot("3.30", "0.30");
        PromotionBidOpenApiResult updateResult = buildUpdateResult(expectedBidCents);
        PromotionPlanStatusOpenApiResult statusOpenApiResult = buildStatusResult();
        MtPromotionSetting currentSetting = buildCurrentSetting(true, candidate.getStrategyConfigJson());
        ShopPeakWindowProfileLookupResult peakLookupResult = buildUsablePeakLookup(
                generatedAt,
                false,
                null,
                List.of(buildPeakWindow(11, 14))
        );
        String peakWindowKey = "WEEKDAY:11:00-14:00:2026-04-08T04:10";

        when(mtPromotionSettingMapper.selectAutoBidCandidates(slotTime)).thenReturn(List.of(candidate));
        when(mtPromotionBidRecordMapper.selectByShopIdAndBizDateAndSlotTime(eq(1L), any(), eq(slotTime))).thenReturn(null);
        when(mtPromotionSettingService.normalizeForExecution(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mtPromotionSettingService.getOrCreateByShopId(1L)).thenReturn(currentSetting);
        when(mtPowerAccountService.getOrCreateAccount(1L)).thenReturn(buildPowerAccount(25000));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(slotTime.minusMinutes(10))))
                .thenReturn(new PromotionTimeWindowResult(true, "BUSINESS_TIME_MATCHED", window));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(slotTime)))
                .thenReturn(new PromotionTimeWindowResult(true, "BUSINESS_TIME_MATCHED", window));
        when(mtPromotionOpenApiClient.queryPlanStatus(eq(candidate), eq(slotTime), eq(window), eq("WINDOW_STATUS_CHECK")))
                .thenReturn(statusOpenApiResult);
        when(promotionMetricsProvider.queryMetrics(eq(candidate), any(PromotionExecutionWindow.class), eq(slotTime))).thenReturn(snapshot);
        when(mtPromotionBidRecordMapper.selectLatestSuccessByShopIdAndWindowKey(eq(1L), eq(window.getWindowKey()), anyInt()))
                .thenReturn(List.of(expectedSlotsUntilPeakStart == 4
                        ? buildHistoryWithRampContext(
                        slotTime.minusMinutes(10),
                        previousBidAmount,
                        "3.10",
                        window,
                        "0.60",
                        peakWindowKey,
                        "1.20"
                )
                        : buildHistoryWithPrePeakWarmupContext(
                        slotTime.minusMinutes(10),
                        previousBidAmount,
                        "3.10",
                        window,
                        "0.60",
                        peakWindowKey,
                        previousBidAmount
                )));
        when(mtPromotionOpenApiClient.updatePlanBid(eq(candidate), eq(expectedBidCents), eq(slotTime), eq(window), eq("SLOT_BID_UPDATE")))
                .thenReturn(updateResult);
        when(shopPeakWindowProfileService.lookup(1L, slotTime)).thenReturn(peakLookupResult);

        ArgumentCaptor<MtPromotionBidRecord> recordCaptor = ArgumentCaptor.forClass(MtPromotionBidRecord.class);
        doAnswer(invocation -> 1).when(mtPromotionBidRecordMapper).insert(recordCaptor.capture());

        service.executeSlot(slotTime);

        MtPromotionBidRecord inserted = recordCaptor.getValue();
        assertEquals("PRE_PEAK_RAMP_UP", inserted.getDecisionReason());
        assertEquals(new BigDecimal(expectedBidAmount), inserted.getNextBidAmount());
        PromotionDecisionDetail detail = JsonUtil.toBean(inserted.getDecisionDetailJson(), PromotionDecisionDetail.class);
        assertEquals("PRE_PEAK_RAMP_UP", detail.getPeakBidPhase());
        assertEquals(expectedSlotsUntilPeakStart, detail.getSlotsUntilPeakStart());
        assertEquals(new BigDecimal(expectedBidAmount), detail.getPeakRampTargetBidAmount());
        assertEquals(peakWindowKey, detail.getNextPeakWindowKey());
        verify(mtPromotionOpenApiClient, never()).queryPeerAverageBid(any(), any(), any(), any());
    }

    @Test
    void shouldStartWarmUpFromOpeningSlotWhenOpeningIsCloseToPeak() throws Exception {
        PromotionBidDecisionService decisionService = new PromotionBidDecisionService(
                new com.gogoga.backend.service.promotion.PromotionDecisionEngine()
        );
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        MtPromotionAutoBidService service = buildService(decisionService, objectMapper);

        LocalDateTime slotTime = LocalDateTime.of(2026, 4, 8, 10, 40);
        LocalDateTime generatedAt = LocalDateTime.of(2026, 4, 8, 4, 10);
        MtPromotionAutoBidCandidate candidate = buildCandidate();
        PromotionExecutionWindow window = PromotionExecutionWindow.of(
                LocalDateTime.of(2026, 4, 8, 10, 40),
                LocalDateTime.of(2026, 4, 8, 23, 0)
        );
        PromotionMetricsSnapshot snapshot = buildTrustedSnapshot("3.30", "0.30");
        PromotionBidOpenApiResult updateResult = buildUpdateResult(70);
        PromotionPlanStatusOpenApiResult statusOpenApiResult = buildStatusResult();
        MtPromotionSetting currentSetting = buildCurrentSetting(true, candidate.getStrategyConfigJson());
        ShopPeakWindowProfileLookupResult peakLookupResult = buildUsablePeakLookup(
                generatedAt,
                false,
                null,
                List.of(buildPeakWindow(11, 14))
        );
        String peakWindowKey = "WEEKDAY:11:00-14:00:2026-04-08T04:10";

        when(mtPromotionSettingMapper.selectAutoBidCandidates(slotTime)).thenReturn(List.of(candidate));
        when(mtPromotionBidRecordMapper.selectByShopIdAndBizDateAndSlotTime(eq(1L), any(), eq(slotTime))).thenReturn(null);
        when(mtPromotionSettingService.normalizeForExecution(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mtPromotionSettingService.getOrCreateByShopId(1L)).thenReturn(currentSetting);
        when(mtPowerAccountService.getOrCreateAccount(1L)).thenReturn(buildPowerAccount(25000));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(slotTime.minusMinutes(10))))
                .thenReturn(new PromotionTimeWindowResult(false, "OUT_OF_BUSINESS_WINDOW", null));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(slotTime)))
                .thenReturn(new PromotionTimeWindowResult(true, "AUTO_TIME_MODE", window));
        when(mtPromotionOpenApiClient.queryPlanStatus(eq(candidate), eq(slotTime), eq(window), eq("WINDOW_STATUS_CHECK")))
                .thenReturn(statusOpenApiResult);
        when(promotionMetricsProvider.queryMetrics(eq(candidate), any(PromotionExecutionWindow.class), eq(slotTime))).thenReturn(snapshot);
        when(mtPromotionBidRecordMapper.selectLatestSuccessByShopIdAndWindowKey(eq(1L), eq(window.getWindowKey()), anyInt()))
                .thenReturn(List.of(buildHistoryWithRampContext(
                        slotTime.minusMinutes(10),
                        "0.60",
                        "3.10",
                        window,
                        "0.60",
                        peakWindowKey,
                        "1.20"
                )));
        when(mtPromotionOpenApiClient.updatePlanBid(eq(candidate), eq(70), eq(slotTime), eq(window), eq("SLOT_BID_UPDATE")))
                .thenReturn(updateResult);
        when(shopPeakWindowProfileService.lookup(1L, slotTime)).thenReturn(peakLookupResult);

        ArgumentCaptor<MtPromotionBidRecord> recordCaptor = ArgumentCaptor.forClass(MtPromotionBidRecord.class);
        doAnswer(invocation -> 1).when(mtPromotionBidRecordMapper).insert(recordCaptor.capture());

        service.executeSlot(slotTime);

        MtPromotionBidRecord inserted = recordCaptor.getValue();
        assertEquals("PRE_PEAK_RAMP_UP", inserted.getDecisionReason());
        assertEquals(new BigDecimal("0.70"), inserted.getNextBidAmount());
        PromotionDecisionDetail detail = JsonUtil.toBean(inserted.getDecisionDetailJson(), PromotionDecisionDetail.class);
        assertEquals("PRE_PEAK_RAMP_UP", detail.getPeakBidPhase());
        assertEquals(2, detail.getSlotsUntilPeakStart());
        assertEquals(new BigDecimal("0.70"), detail.getPeakRampTargetBidAmount());
    }

    @Test
    void shouldDecayAfterPeakUntilLowBaselineWhenNoNearbyNextPeakExists() throws Exception {
        PromotionBidDecisionService decisionService = new PromotionBidDecisionService(
                new com.gogoga.backend.service.promotion.PromotionDecisionEngine()
        );
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        MtPromotionAutoBidService service = buildService(decisionService, objectMapper);

        LocalDateTime slotTime = LocalDateTime.of(2026, 4, 8, 15, 30);
        LocalDateTime generatedAt = LocalDateTime.of(2026, 4, 8, 4, 10);
        MtPromotionAutoBidCandidate candidate = buildCandidate();
        PromotionExecutionWindow window = PromotionExecutionWindow.of(
                LocalDateTime.of(2026, 4, 8, 9, 30),
                LocalDateTime.of(2026, 4, 8, 23, 0)
        );
        PromotionMetricsSnapshot snapshot = buildTrustedSnapshot("3.30", "0.30");
        PromotionBidOpenApiResult updateResult = buildUpdateResult(60);
        PromotionPlanStatusOpenApiResult statusOpenApiResult = buildStatusResult();
        MtPromotionSetting currentSetting = buildCurrentSetting(true, candidate.getStrategyConfigJson());
        ShopPeakWindowProfileLookupResult peakLookupResult = buildUsablePeakLookup(
                generatedAt,
                false,
                null,
                List.of(buildPeakWindow(11, 14))
        );
        String peakWindowKey = "WEEKDAY:11:00-14:00:2026-04-08T04:10";

        when(mtPromotionSettingMapper.selectAutoBidCandidates(slotTime)).thenReturn(List.of(candidate));
        when(mtPromotionBidRecordMapper.selectByShopIdAndBizDateAndSlotTime(eq(1L), any(), eq(slotTime))).thenReturn(null);
        when(mtPromotionSettingService.normalizeForExecution(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mtPromotionSettingService.getOrCreateByShopId(1L)).thenReturn(currentSetting);
        when(mtPowerAccountService.getOrCreateAccount(1L)).thenReturn(buildPowerAccount(25000));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(slotTime.minusMinutes(10))))
                .thenReturn(new PromotionTimeWindowResult(true, "BUSINESS_TIME_MATCHED", window));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(slotTime)))
                .thenReturn(new PromotionTimeWindowResult(true, "BUSINESS_TIME_MATCHED", window));
        when(mtPromotionOpenApiClient.queryPlanStatus(eq(candidate), eq(slotTime), eq(window), eq("WINDOW_STATUS_CHECK")))
                .thenReturn(statusOpenApiResult);
        when(promotionMetricsProvider.queryMetrics(eq(candidate), any(PromotionExecutionWindow.class), eq(slotTime))).thenReturn(snapshot);
        when(mtPromotionBidRecordMapper.selectLatestSuccessByShopIdAndWindowKey(eq(1L), eq(window.getWindowKey()), anyInt()))
                .thenReturn(List.of(buildHistoryWithRampContext(
                        slotTime.minusMinutes(10),
                        "0.70",
                        "3.10",
                        window,
                        "0.60",
                        peakWindowKey,
                        "1.20"
                )));
        when(mtPromotionOpenApiClient.updatePlanBid(eq(candidate), eq(60), eq(slotTime), eq(window), eq("SLOT_BID_UPDATE")))
                .thenReturn(updateResult);
        when(shopPeakWindowProfileService.lookup(1L, slotTime)).thenReturn(peakLookupResult);

        ArgumentCaptor<MtPromotionBidRecord> recordCaptor = ArgumentCaptor.forClass(MtPromotionBidRecord.class);
        doAnswer(invocation -> 1).when(mtPromotionBidRecordMapper).insert(recordCaptor.capture());

        service.executeSlot(slotTime);

        MtPromotionBidRecord inserted = recordCaptor.getValue();
        assertEquals("POST_PEAK_RAMP_DOWN", inserted.getDecisionReason());
        assertEquals(new BigDecimal("0.70"), inserted.getPreviousBidAmount());
        assertEquals(new BigDecimal("0.60"), inserted.getNextBidAmount());
        PromotionDecisionDetail detail = JsonUtil.toBean(inserted.getDecisionDetailJson(), PromotionDecisionDetail.class);
        assertEquals("POST_PEAK_RAMP_DOWN", detail.getPeakBidPhase());
        assertEquals(9, detail.getSlotsSincePeakEnd());
        assertEquals(new BigDecimal("0.60"), detail.getLowPeakBaselineBidAmount());
        assertEquals(new BigDecimal("0.60"), detail.getPeakRampTargetBidAmount());
    }

    @Test
    void shouldUseFastPostPeakDecreaseWhenRecentAverageOrdersReachThirty() throws Exception {
        MtPromotionRecentOrderStatistic statistic = buildRecentOrderStatistic(
                LocalDate.of(2026, 4, 8),
                true,
                "210",
                "30.00",
                "0.400000"
        );
        MtPromotionBidRecord inserted = executePostPeakRampDownScenario(statistic, "0.90", 70);

        assertEquals("POST_PEAK_RAMP_DOWN", inserted.getDecisionReason());
        assertEquals(new BigDecimal("0.90"), inserted.getPreviousBidAmount());
        assertEquals(new BigDecimal("0.70"), inserted.getNextBidAmount());
        PromotionDecisionDetail detail = JsonUtil.toBean(inserted.getDecisionDetailJson(), PromotionDecisionDetail.class);
        assertEquals(Boolean.TRUE, detail.getRecentOrderStatisticTrusted());
        assertEquals(Boolean.TRUE, detail.getHighVolumeRampDownEligible());
        assertEquals(new BigDecimal("0.20"), detail.getPeakRampDecreaseStepAmount());
        assertEquals(new BigDecimal("30.00"), detail.getRecentOrderAverageDailyOrderCount());
    }

    @Test
    void shouldUseNormalPostPeakDecreaseWhenRecentAverageOrdersAreBelowThirty() throws Exception {
        MtPromotionRecentOrderStatistic statistic = buildRecentOrderStatistic(
                LocalDate.of(2026, 4, 8),
                true,
                "209",
                "29.86",
                "0.400000"
        );
        MtPromotionBidRecord inserted = executePostPeakRampDownScenario(statistic, "0.90", 80);

        assertEquals("POST_PEAK_RAMP_DOWN", inserted.getDecisionReason());
        assertEquals(new BigDecimal("0.80"), inserted.getNextBidAmount());
        PromotionDecisionDetail detail = JsonUtil.toBean(inserted.getDecisionDetailJson(), PromotionDecisionDetail.class);
        assertEquals(Boolean.FALSE, detail.getHighVolumeRampDownEligible());
        assertEquals(new BigDecimal("0.10"), detail.getPeakRampDecreaseStepAmount());
    }

    @Test
    void shouldFloorPostPeakBaselineToOneDecimalBeforeSyncingBid() throws Exception {
        MtPromotionRecentOrderStatistic statistic = buildRecentOrderStatistic(
                LocalDate.of(2026, 4, 8),
                true,
                "209",
                "29.86",
                "0.400000"
        );
        MtPromotionBidRecord inserted = executePostPeakRampDownScenario(statistic, "0.20", "0.11", 10);

        assertEquals("POST_PEAK_RAMP_DOWN", inserted.getDecisionReason());
        assertEquals(new BigDecimal("0.20"), inserted.getPreviousBidAmount());
        assertEquals(new BigDecimal("0.10"), inserted.getNextBidAmount());
        PromotionDecisionDetail detail = JsonUtil.toBean(inserted.getDecisionDetailJson(), PromotionDecisionDetail.class);
        assertEquals(new BigDecimal("0.10"), detail.getLowPeakBaselineBidAmount());
        assertEquals(new BigDecimal("0.10"), detail.getPeakRampTargetBidAmount());
    }

    @Test
    void shouldUseNormalPostPeakDecreaseWhenRecentOrderStatisticIsUntrusted() throws Exception {
        MtPromotionRecentOrderStatistic statistic = buildRecentOrderStatistic(
                LocalDate.of(2026, 4, 8),
                false,
                "210",
                "30.00",
                "0.400000"
        );
        statistic.setUntrustedReason("INSUFFICIENT_ORDER_DAYS");
        MtPromotionBidRecord inserted = executePostPeakRampDownScenario(statistic, "0.90", 80);

        assertEquals("POST_PEAK_RAMP_DOWN", inserted.getDecisionReason());
        assertEquals(new BigDecimal("0.80"), inserted.getNextBidAmount());
        PromotionDecisionDetail detail = JsonUtil.toBean(inserted.getDecisionDetailJson(), PromotionDecisionDetail.class);
        assertEquals(Boolean.FALSE, detail.getHighVolumeRampDownEligible());
        assertEquals("INSUFFICIENT_ORDER_DAYS", detail.getRecentOrderStatisticUntrustedReason());
        assertEquals(new BigDecimal("0.10"), detail.getPeakRampDecreaseStepAmount());
    }

    @Test
    void shouldKeepPostPeakDecayWhenNextPeakWarmupHasNoContinuousPreviousSlot() throws Exception {
        PromotionBidDecisionService decisionService = new PromotionBidDecisionService(
                new com.gogoga.backend.service.promotion.PromotionDecisionEngine()
        );
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        MtPromotionAutoBidService service = buildService(decisionService, objectMapper);

        LocalDateTime slotTime = LocalDateTime.of(2026, 4, 8, 18, 40);
        LocalDateTime generatedAt = LocalDateTime.of(2026, 4, 8, 4, 10);
        MtPromotionAutoBidCandidate candidate = buildCandidate();
        PromotionExecutionWindow window = PromotionExecutionWindow.of(
                LocalDateTime.of(2026, 4, 8, 9, 30),
                LocalDateTime.of(2026, 4, 8, 23, 0)
        );
        PromotionMetricsSnapshot snapshot = buildTrustedSnapshot("3.30", "0.30");
        PromotionBidOpenApiResult updateResult = buildUpdateResult(80);
        PromotionPlanStatusOpenApiResult statusOpenApiResult = buildStatusResult();
        MtPromotionSetting currentSetting = buildCurrentSetting(true, candidate.getStrategyConfigJson());
        ShopPeakWindowProfileLookupResult peakLookupResult = buildUsablePeakLookup(
                generatedAt,
                false,
                null,
                List.of(buildPeakWindow(17, 18), buildPeakWindow(19, 23))
        );
        String nextPeakWindowKey = "WEEKDAY:19:00-23:00:2026-04-08T04:10";

        when(mtPromotionSettingMapper.selectAutoBidCandidates(slotTime)).thenReturn(List.of(candidate));
        when(mtPromotionBidRecordMapper.selectByShopIdAndBizDateAndSlotTime(eq(1L), any(), eq(slotTime))).thenReturn(null);
        when(mtPromotionSettingService.normalizeForExecution(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mtPromotionSettingService.getOrCreateByShopId(1L)).thenReturn(currentSetting);
        when(mtPowerAccountService.getOrCreateAccount(1L)).thenReturn(buildPowerAccount(25000));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(slotTime.minusMinutes(10))))
                .thenReturn(new PromotionTimeWindowResult(true, "BUSINESS_TIME_MATCHED", window));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(slotTime)))
                .thenReturn(new PromotionTimeWindowResult(true, "BUSINESS_TIME_MATCHED", window));
        when(mtPromotionOpenApiClient.queryPlanStatus(eq(candidate), eq(slotTime), eq(window), eq("WINDOW_STATUS_CHECK")))
                .thenReturn(statusOpenApiResult);
        when(promotionMetricsProvider.queryMetrics(eq(candidate), any(PromotionExecutionWindow.class), eq(slotTime))).thenReturn(snapshot);
        when(mtPromotionBidRecordMapper.selectLatestSuccessByShopIdAndWindowKey(eq(1L), eq(window.getWindowKey()), anyInt()))
                .thenReturn(List.of(buildHistoryWithRampContext(
                        slotTime.minusMinutes(10),
                        "0.90",
                        "3.10",
                        window,
                        "0.60",
                        nextPeakWindowKey,
                        "1.20"
                )));
        when(mtPromotionOpenApiClient.updatePlanBid(eq(candidate), eq(80), eq(slotTime), eq(window), eq("SLOT_BID_UPDATE")))
                .thenReturn(updateResult);
        when(shopPeakWindowProfileService.lookup(1L, slotTime)).thenReturn(peakLookupResult);

        ArgumentCaptor<MtPromotionBidRecord> recordCaptor = ArgumentCaptor.forClass(MtPromotionBidRecord.class);
        doAnswer(invocation -> 1).when(mtPromotionBidRecordMapper).insert(recordCaptor.capture());

        service.executeSlot(slotTime);

        MtPromotionBidRecord inserted = recordCaptor.getValue();
        assertEquals("POST_PEAK_RAMP_DOWN", inserted.getDecisionReason());
        assertEquals(new BigDecimal("0.90"), inserted.getPreviousBidAmount());
        assertEquals(new BigDecimal("0.80"), inserted.getNextBidAmount());
        PromotionDecisionDetail detail = JsonUtil.toBean(inserted.getDecisionDetailJson(), PromotionDecisionDetail.class);
        assertEquals("POST_PEAK_RAMP_DOWN", detail.getPeakBidPhase());
        assertEquals(2, detail.getSlotsUntilPeakStart());
        assertEquals(4, detail.getSlotsSincePeakEnd());
        assertEquals(new BigDecimal("0.80"), detail.getPeakRampTargetBidAmount());
        assertEquals(nextPeakWindowKey, detail.getNextPeakWindowKey());
    }

    @Test
    void shouldExitWarmupAtPeakStartAndUseLatestBidContext() throws Exception {
        PromotionBidDecisionService decisionService = new PromotionBidDecisionService(
                new com.gogoga.backend.service.promotion.PromotionDecisionEngine()
        );
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        MtPromotionAutoBidService service = buildService(decisionService, objectMapper);

        LocalDateTime slotTime = LocalDateTime.of(2026, 4, 8, 11, 0);
        LocalDateTime generatedAt = LocalDateTime.of(2026, 4, 8, 4, 10);
        MtPromotionAutoBidCandidate candidate = buildCandidate();
        PromotionExecutionWindow window = PromotionExecutionWindow.of(
                LocalDateTime.of(2026, 4, 8, 9, 30),
                LocalDateTime.of(2026, 4, 8, 23, 0)
        );
        PromotionMetricsSnapshot snapshot = buildTrustedSnapshot("3.30", "0.30");
        PromotionBidOpenApiResult updateResult = buildUpdateResult(100);
        PromotionPlanStatusOpenApiResult statusOpenApiResult = buildStatusResult();
        MtPromotionSetting currentSetting = buildCurrentSetting(true, candidate.getStrategyConfigJson());
        ShopPeakWindowProfileLookupResult peakLookupResult = buildUsablePeakLookup(
                generatedAt,
                true,
                "11:00-14:00",
                List.of(buildPeakWindow(11, 14))
        );
        String peakWindowKey = "WEEKDAY:11:00-14:00:2026-04-08T04:10";

        when(mtPromotionSettingMapper.selectAutoBidCandidates(slotTime)).thenReturn(List.of(candidate));
        when(mtPromotionBidRecordMapper.selectByShopIdAndBizDateAndSlotTime(eq(1L), any(), eq(slotTime))).thenReturn(null);
        when(mtPromotionSettingService.normalizeForExecution(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mtPromotionSettingService.getOrCreateByShopId(1L)).thenReturn(currentSetting);
        when(mtPowerAccountService.getOrCreateAccount(1L)).thenReturn(buildPowerAccount(25000));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(slotTime.minusMinutes(10))))
                .thenReturn(new PromotionTimeWindowResult(true, "BUSINESS_TIME_MATCHED", window));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(slotTime)))
                .thenReturn(new PromotionTimeWindowResult(true, "BUSINESS_TIME_MATCHED", window));
        when(mtPromotionOpenApiClient.queryPlanStatus(eq(candidate), eq(slotTime), eq(window), eq("WINDOW_STATUS_CHECK")))
                .thenReturn(statusOpenApiResult);
        when(promotionMetricsProvider.queryMetrics(eq(candidate), any(PromotionExecutionWindow.class), eq(slotTime))).thenReturn(snapshot);
        when(mtPromotionBidRecordMapper.selectLatestSuccessByShopIdAndWindowKey(eq(1L), eq(window.getWindowKey()), anyInt()))
                .thenReturn(List.of(buildHistoryWithRampContext(
                        slotTime.minusMinutes(10),
                        "1.00",
                        "3.10",
                        window,
                        "0.60",
                        peakWindowKey,
                        "1.20"
                )));
        when(mtPromotionOpenApiClient.updatePlanBid(eq(candidate), eq(100), eq(slotTime), eq(window), eq("SLOT_BID_UPDATE")))
                .thenReturn(updateResult);
        when(shopPeakWindowProfileService.lookup(1L, slotTime)).thenReturn(peakLookupResult);

        ArgumentCaptor<MtPromotionBidRecord> recordCaptor = ArgumentCaptor.forClass(MtPromotionBidRecord.class);
        doAnswer(invocation -> 1).when(mtPromotionBidRecordMapper).insert(recordCaptor.capture());

        service.executeSlot(slotTime);

        MtPromotionBidRecord inserted = recordCaptor.getValue();
        assertEquals("CLICK_DETECTED_KEEP_PREVIOUS_BID", inserted.getDecisionReason());
        assertEquals(new BigDecimal("1.00"), inserted.getPreviousBidAmount());
        assertEquals(new BigDecimal("1.00"), inserted.getNextBidAmount());
        PromotionDecisionDetail detail = JsonUtil.toBean(inserted.getDecisionDetailJson(), PromotionDecisionDetail.class);
        assertEquals("PEAK_RULE", detail.getPricingBranch());
        assertEquals("PEAK", detail.getPeakBidPhase());
        assertEquals(0, detail.getSlotsUntilPeakStart());
        assertEquals(peakWindowKey, detail.getPeakWindowKey());
        assertEquals("ADJUSTED_WORKDAY", detail.getPeakWindowCalendarDayType());
        assertEquals("劳动节补班", detail.getPeakWindowHolidayName());
        verify(mtPromotionOpenApiClient, never()).queryPeerAverageBid(any(), any(), any(), any());
    }

    private MtPromotionAutoBidCandidate buildCandidate() {
        return buildCandidateWithStrategy(buildStrategyConfigJson("auto"));
    }

    private void stubCommonAutoBidPrerequisites(
            LocalDateTime slotTime,
            MtPromotionAutoBidCandidate candidate,
            PromotionExecutionWindow window,
            PromotionMetricsSnapshot snapshot,
            MtPromotionSetting currentSetting
    ) {
        when(mtPromotionSettingMapper.selectAutoBidCandidates(slotTime)).thenReturn(List.of(candidate));
        when(mtPromotionBidRecordMapper.selectByShopIdAndBizDateAndSlotTime(eq(1L), any(), eq(slotTime))).thenReturn(null);
        when(mtPromotionSettingService.normalizeForExecution(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mtPromotionSettingService.getOrCreateByShopId(1L)).thenReturn(currentSetting);
        when(mtPowerAccountService.getOrCreateAccount(1L)).thenReturn(buildPowerAccount(25000));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(slotTime.minusMinutes(10))))
                .thenReturn(new PromotionTimeWindowResult(true, "AUTO_TIME_MODE", window));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(slotTime)))
                .thenReturn(new PromotionTimeWindowResult(true, "AUTO_TIME_MODE", window));
        when(mtPromotionOpenApiClient.queryPlanStatus(eq(candidate), eq(slotTime), eq(window), eq("WINDOW_STATUS_CHECK")))
                .thenReturn(buildStatusResult());
        when(promotionMetricsProvider.queryMetrics(eq(candidate), any(PromotionExecutionWindow.class), eq(slotTime))).thenReturn(snapshot);
    }

    private MtPromotionAutoBidCandidate buildCandidateWithStrategy(String strategyConfigJson) {
        MtPromotionAutoBidCandidate candidate = new MtPromotionAutoBidCandidate();
        candidate.setShopId(1L);
        candidate.setShippingTime("09:00-22:00");
        candidate.setAccessToken("token");
        candidate.setStrategyConfigJson(strategyConfigJson);
        return candidate;
    }

    private String buildStrategyConfigJson(String timeMode) {
        return """
                {"priority":"rate","timeMode":"%s","rateMode":"control","rateMin":"5%%","rateMax":"10%%","decisionMode":"llm"}
                """.formatted(timeMode);
    }

    private String buildStrategyConfigJsonWithBidRange(String timeMode, String bidMin, String bidMax) {
        return """
                {"priority":"rate","timeMode":"%s","rateMode":"control","rateMin":"5%%","rateMax":"10%%","decisionMode":"llm","bidMin":%s,"bidMax":%s}
                """.formatted(timeMode, bidMin, bidMax);
    }

    private MtPromotionBidRecord buildHistory(LocalDateTime slotTime, String nextBidAmount, String totalCost) {
        MtPromotionBidRecord record = new MtPromotionBidRecord();
        record.setSlotTime(slotTime);
        record.setNextBidAmount(new BigDecimal(nextBidAmount));
        record.setTotalCost(new BigDecimal(totalCost));
        record.setTotalIncome(new BigDecimal("66.00"));
        record.setTotalCostRatio(new BigDecimal("0.050000"));
        record.setCostDelta(new BigDecimal("0.10"));
        return record;
    }

    private List<MtPromotionBidRecord> buildExposureWeakIncreaseHistory(LocalDateTime slotTime) {
        return List.of(
                buildExposureWeakIncreaseHistory(slotTime.minusMinutes(10), "0.70", "0.80", "3.50"),
                buildExposureWeakIncreaseHistory(slotTime.minusMinutes(20), "0.60", "0.70", "3.40"),
                buildExposureWeakIncreaseHistory(slotTime.minusMinutes(30), "0.50", "0.60", "3.30"),
                buildExposureWeakIncreaseHistory(slotTime.minusMinutes(40), "0.40", "0.50", "3.20"),
                buildExposureWeakIncreaseHistory(slotTime.minusMinutes(50), "0.30", "0.40", "3.10")
        );
    }

    private MtPromotionBidRecord buildExposureWeakIncreaseHistory(
            LocalDateTime slotTime,
            String previousBidAmount,
            String nextBidAmount,
            String totalCost
    ) {
        MtPromotionBidRecord record = buildHistory(slotTime, nextBidAmount, totalCost);
        record.setPreviousBidAmount(new BigDecimal(previousBidAmount));
        record.setDecisionReason("EXPOSURE_GROWTH_WEAK_INCREASE_BID");
        PromotionDecisionDetail detail = new PromotionDecisionDetail();
        detail.setFinalAggregationMode("RATE_WITH_EXPOSURE");
        detail.setFinalAggregationSource("EXPOSURE_INCREASE");
        record.setDecisionDetailJson(JsonUtil.toJson(detail));
        return record;
    }

    private PromotionDecisionResult buildMockDecisionResult(
            String previousBidAmount,
            String nextBidAmount,
            String action,
            String reason
    ) {
        PromotionDecisionResult decisionResult = new PromotionDecisionResult();
        decisionResult.setPreviousBidAmount(new BigDecimal(previousBidAmount));
        decisionResult.setNextBidAmount(new BigDecimal(nextBidAmount));
        decisionResult.setDecisionAction(action);
        decisionResult.setDecisionReason(reason);
        decisionResult.setDecisionDetailJson("{\"decisionSource\":\"rule\"}");
        return decisionResult;
    }

    private void stubExposureSeries(LocalDateTime slotTime, int minus50, int minus40, int minus30, int minus20, int minus10, int current) {
        lenient().when(mtPromotionAdRealtimeSnapshotService.selectByShopIdAndBizDateAndSlotTime(eq(1L), eq(slotTime.toLocalDate()), eq(slotTime.minusMinutes(50))))
                .thenReturn(buildRealtimeSnapshot(slotTime.minusMinutes(50), minus50));
        lenient().when(mtPromotionAdRealtimeSnapshotService.selectByShopIdAndBizDateAndSlotTime(eq(1L), eq(slotTime.toLocalDate()), eq(slotTime.minusMinutes(40))))
                .thenReturn(buildRealtimeSnapshot(slotTime.minusMinutes(40), minus40));
        lenient().when(mtPromotionAdRealtimeSnapshotService.selectByShopIdAndBizDateAndSlotTime(eq(1L), eq(slotTime.toLocalDate()), eq(slotTime.minusMinutes(30))))
                .thenReturn(buildRealtimeSnapshot(slotTime.minusMinutes(30), minus30));
        lenient().when(mtPromotionAdRealtimeSnapshotService.selectByShopIdAndBizDateAndSlotTime(eq(1L), eq(slotTime.toLocalDate()), eq(slotTime.minusMinutes(20))))
                .thenReturn(buildRealtimeSnapshot(slotTime.minusMinutes(20), minus20));
        lenient().when(mtPromotionAdRealtimeSnapshotService.selectByShopIdAndBizDateAndSlotTime(eq(1L), eq(slotTime.toLocalDate()), eq(slotTime.minusMinutes(10))))
                .thenReturn(buildRealtimeSnapshot(slotTime.minusMinutes(10), minus10));
        lenient().when(mtPromotionAdRealtimeSnapshotService.selectByShopIdAndBizDateAndSlotTime(eq(1L), eq(slotTime.toLocalDate()), eq(slotTime)))
                .thenReturn(buildRealtimeSnapshot(slotTime, current));
    }

    private MtPromotionBidRecord buildHistoryWithBaseline(
            LocalDateTime slotTime,
            String nextBidAmount,
            String totalCost,
            String baselineBidAmount,
            String pricingBranch
    ) {
        MtPromotionBidRecord record = buildHistory(slotTime, nextBidAmount, totalCost);
        PromotionDecisionDetail detail = new PromotionDecisionDetail();
        detail.setWindowBaselineBidAmount(new BigDecimal(baselineBidAmount));
        detail.setPricingBranch(pricingBranch);
        record.setDecisionDetailJson(JsonUtil.toJson(detail));
        return record;
    }

    private MtPromotionBidRecord buildHistoryWithRampContext(
            LocalDateTime slotTime,
            String nextBidAmount,
            String totalCost,
            PromotionExecutionWindow executionWindow,
            String lowPeakBaselineBidAmount,
            String peakWindowKey,
            String peakEntryBidAmount
    ) {
        MtPromotionBidRecord record = buildHistory(slotTime, nextBidAmount, totalCost);
        record.setWindowKey(executionWindow.getWindowKey());
        PromotionDecisionDetail detail = new PromotionDecisionDetail();
        detail.setPricingBranch("SMOOTH_PEAK_RAMP");
        detail.setStrategyConfigHash(buildStrategyConfigHash(buildStrategyConfigJson("auto")));
        detail.setWindowBaselineBidAmount(new BigDecimal(lowPeakBaselineBidAmount));
        detail.setLowPeakBaselineBidAmount(new BigDecimal(lowPeakBaselineBidAmount));
        detail.setLowPeakBaselineSource("INHERITED");
        detail.setPeakWindowKey(peakWindowKey);
        detail.setPeakEntryBidAmount(new BigDecimal(peakEntryBidAmount));
        detail.setPeakEntryBidSource("INHERITED");
        record.setDecisionDetailJson(JsonUtil.toJson(detail));
        return record;
    }

    private MtPromotionBidRecord buildHistoryWithPrePeakWarmupContext(
            LocalDateTime slotTime,
            String nextBidAmount,
            String totalCost,
            PromotionExecutionWindow executionWindow,
            String lowPeakBaselineBidAmount,
            String nextPeakWindowKey,
            String rampTargetBidAmount
    ) {
        MtPromotionBidRecord record = buildHistory(slotTime, nextBidAmount, totalCost);
        record.setWindowKey(executionWindow.getWindowKey());
        PromotionDecisionDetail detail = new PromotionDecisionDetail();
        detail.setPricingBranch("SMOOTH_PEAK_RAMP");
        detail.setStrategyConfigHash(buildStrategyConfigHash(buildStrategyConfigJson("auto")));
        detail.setWindowBaselineBidAmount(new BigDecimal(lowPeakBaselineBidAmount));
        detail.setLowPeakBaselineBidAmount(new BigDecimal(lowPeakBaselineBidAmount));
        detail.setLowPeakBaselineSource("INHERITED");
        detail.setPeakBidPhase("PRE_PEAK_RAMP_UP");
        detail.setNextPeakWindowKey(nextPeakWindowKey);
        detail.setPeakRampTargetBidAmount(new BigDecimal(rampTargetBidAmount));
        record.setDecisionDetailJson(JsonUtil.toJson(detail));
        return record;
    }

    private MtPromotionRecentOrderStatistic buildRecentOrderStatistic(
            LocalDate bizDate,
            boolean trusted,
            String totalOrderCount,
            String averageDailyOrderCount,
            String lowPeakOrderShare
    ) {
        MtPromotionRecentOrderStatistic statistic = new MtPromotionRecentOrderStatistic();
        statistic.setShopId(1L);
        statistic.setBizDate(bizDate);
        statistic.setLookbackStartDate(bizDate.minusDays(15));
        statistic.setLookbackEndDate(bizDate.minusDays(1));
        statistic.setSampleDatesJson(JsonUtil.toJson(List.of(
                bizDate.minusDays(7),
                bizDate.minusDays(6),
                bizDate.minusDays(5),
                bizDate.minusDays(4),
                bizDate.minusDays(3),
                bizDate.minusDays(2),
                bizDate.minusDays(1)
        )));
        statistic.setTrusted(trusted);
        statistic.setTotalOrderCount(Integer.valueOf(totalOrderCount));
        statistic.setAverageDailyOrderCount(new BigDecimal(averageDailyOrderCount));
        statistic.setLowPeakOrderCount(new BigDecimal(totalOrderCount).multiply(new BigDecimal(lowPeakOrderShare)).intValue());
        statistic.setLowPeakOrderShare(new BigDecimal(lowPeakOrderShare));
        return statistic;
    }

    private MtPromotionBidRecord executePostPeakRampDownScenario(
            MtPromotionRecentOrderStatistic statistic,
            String previousBidAmount,
            int expectedBidCents
    ) throws Exception {
        return executePostPeakRampDownScenario(statistic, previousBidAmount, "0.60", expectedBidCents);
    }

    private MtPromotionBidRecord executePostPeakRampDownScenario(
            MtPromotionRecentOrderStatistic statistic,
            String previousBidAmount,
            String lowPeakBaselineBidAmount,
            int expectedBidCents
    ) throws Exception {
        PromotionBidDecisionService decisionService = new PromotionBidDecisionService(
                new com.gogoga.backend.service.promotion.PromotionDecisionEngine()
        );
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        MtPromotionAutoBidService service = buildService(decisionService, objectMapper);

        LocalDateTime slotTime = LocalDateTime.of(2026, 4, 8, 15, 30);
        LocalDateTime generatedAt = LocalDateTime.of(2026, 4, 8, 4, 10);
        MtPromotionAutoBidCandidate candidate = buildCandidate();
        PromotionExecutionWindow window = PromotionExecutionWindow.of(
                LocalDateTime.of(2026, 4, 8, 9, 30),
                LocalDateTime.of(2026, 4, 8, 23, 0)
        );
        PromotionMetricsSnapshot snapshot = buildTrustedSnapshot("3.30", "0.30");
        PromotionPlanStatusOpenApiResult statusOpenApiResult = buildStatusResult();
        MtPromotionSetting currentSetting = buildCurrentSetting(true, candidate.getStrategyConfigJson());
        ShopPeakWindowProfileLookupResult peakLookupResult = buildUsablePeakLookup(
                generatedAt,
                false,
                null,
                List.of(buildPeakWindow(11, 14))
        );
        String peakWindowKey = "WEEKDAY:11:00-14:00:2026-04-08T04:10";

        when(mtPromotionSettingMapper.selectAutoBidCandidates(slotTime)).thenReturn(List.of(candidate));
        when(mtPromotionBidRecordMapper.selectByShopIdAndBizDateAndSlotTime(eq(1L), any(), eq(slotTime))).thenReturn(null);
        when(mtPromotionSettingService.normalizeForExecution(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mtPromotionSettingService.getOrCreateByShopId(1L)).thenReturn(currentSetting);
        when(mtPowerAccountService.getOrCreateAccount(1L)).thenReturn(buildPowerAccount(25000));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(slotTime.minusMinutes(10))))
                .thenReturn(new PromotionTimeWindowResult(true, "BUSINESS_TIME_MATCHED", window));
        when(promotionTimeWindowEvaluator.evaluate(any(), eq("09:00-22:00"), eq(slotTime)))
                .thenReturn(new PromotionTimeWindowResult(true, "BUSINESS_TIME_MATCHED", window));
        when(mtPromotionOpenApiClient.queryPlanStatus(eq(candidate), eq(slotTime), eq(window), eq("WINDOW_STATUS_CHECK")))
                .thenReturn(statusOpenApiResult);
        when(promotionMetricsProvider.queryMetrics(eq(candidate), any(PromotionExecutionWindow.class), eq(slotTime))).thenReturn(snapshot);
        when(mtPromotionBidRecordMapper.selectLatestSuccessByShopIdAndWindowKey(eq(1L), eq(window.getWindowKey()), anyInt()))
                .thenReturn(List.of(buildHistoryWithRampContext(
                        slotTime.minusMinutes(10),
                        previousBidAmount,
                        "3.10",
                        window,
                        lowPeakBaselineBidAmount,
                        peakWindowKey,
                        "1.20"
                )));
        when(mtPromotionOpenApiClient.updatePlanBid(eq(candidate), eq(expectedBidCents), eq(slotTime), eq(window), eq("SLOT_BID_UPDATE")))
                .thenReturn(buildUpdateResult(expectedBidCents));
        when(shopPeakWindowProfileService.lookup(1L, slotTime)).thenReturn(peakLookupResult);
        when(promotionRecentOrderStatisticService.resolve(1L, slotTime.toLocalDate(), peakLookupResult)).thenReturn(statistic);

        ArgumentCaptor<MtPromotionBidRecord> recordCaptor = ArgumentCaptor.forClass(MtPromotionBidRecord.class);
        doAnswer(invocation -> 1).when(mtPromotionBidRecordMapper).insert(recordCaptor.capture());

        service.executeSlot(slotTime);
        return recordCaptor.getValue();
    }

    private String buildStrategyConfigHash(String strategyConfigJson) {
        return MtPromotionSettingHistoryService.strategyConfigHash(strategyConfigJson);
    }

    private PromotionMetricsSnapshot buildTrustedSnapshot(String windowCost, String slotCostDelta) {
        PromotionMetricsSnapshot snapshot = new PromotionMetricsSnapshot();
        snapshot.setPromotionBalance(new BigDecimal("100.00"));
        snapshot.setCostCounterValue(new BigDecimal("30.30"));
        snapshot.setWindowCost(new BigDecimal(windowCost));
        snapshot.setWindowIncome(new BigDecimal("66.00"));
        snapshot.setWindowCostRatio(new BigDecimal("0.050000"));
        snapshot.setSlotCostDelta(new BigDecimal(slotCostDelta));
        snapshot.setMetricsTrusted(true);
        return snapshot;
    }

    private PromotionBidOpenApiResult buildUpdateResult(int bidCents) {
        PromotionBidOpenApiResult updateResult = new PromotionBidOpenApiResult();
        updateResult.setSuccess(true);
        updateResult.setPayloadJson("{\"ok\":true}");
        updateResult.setBid(bidCents);
        return updateResult;
    }

    private PromotionPlanStatusOpenApiResult buildStatusResult() {
        PromotionPlanStatusOpenApiResult statusOpenApiResult = new PromotionPlanStatusOpenApiResult();
        statusOpenApiResult.setSuccess(true);
        statusOpenApiResult.setStatus(MtPromotionOpenApiClient.STATUS_OPEN);
        statusOpenApiResult.setPayloadJson("{\"status\":0}");
        return statusOpenApiResult;
    }

    private ShopPeakWindowProfileLookupResult buildUsablePeakLookup(
            LocalDateTime generatedAt,
            boolean peakMatched,
            String matchedWindow,
            List<ShopPeakWindow> peakWindows
    ) {
        ShopPeakWindowProfileLookupResult peakLookupResult = new ShopPeakWindowProfileLookupResult();
        peakLookupResult.setDayType(ShopPeakDayType.WEEKDAY);
        peakLookupResult.setProfileDayType(ShopPeakDayType.WEEKDAY);
        peakLookupResult.setCalendarDayType(ChinaWorkdayCalendarDayType.ADJUSTED_WORKDAY);
        peakLookupResult.setHolidayName("劳动节补班");
        peakLookupResult.setProfileStatus(ShopPeakWindowProfileLookupResult.STATUS_USABLE);
        peakLookupResult.setGatingEnabled(true);
        peakLookupResult.setPeakMatched(peakMatched);
        peakLookupResult.setMatchedWindow(matchedWindow);
        peakLookupResult.setGeneratedAt(generatedAt);
        peakLookupResult.setPeakWindows(peakWindows);
        peakLookupResult.setSampleDayCount(3);
        peakLookupResult.setSampleOrderCount(205);
        return peakLookupResult;
    }

    private ShopPeakWindow buildPeakWindow(int startHour, int endHour) {
        ShopPeakWindow window = new ShopPeakWindow();
        window.setStartHour(startHour);
        window.setEndHour(endHour);
        window.setStartTime("%02d:00".formatted(startHour));
        window.setEndTime("%02d:00".formatted(endHour));
        window.setOrderCount(68);
        return window;
    }

    private MtPromotionSetting buildCurrentSetting(boolean promotionEnabled, String strategyConfigJson) {
        MtPromotionSetting setting = new MtPromotionSetting();
        setting.setShopId(1L);
        setting.setPromotionEnabled(promotionEnabled);
        setting.setStrategyConfigured(true);
        setting.setStrategyConfigJson(strategyConfigJson);
        return setting;
    }

    private MtPowerAccount buildPowerAccount(int balance) {
        MtPowerAccount account = new MtPowerAccount();
        account.setBalance(balance);
        return account;
    }

    private com.gogoga.backend.entity.mt.MtPromotionAdRealtimeSnapshot buildRealtimeSnapshot(LocalDateTime slotTime, int dayShowCount) {
        com.gogoga.backend.entity.mt.MtPromotionAdRealtimeSnapshot snapshot =
                new com.gogoga.backend.entity.mt.MtPromotionAdRealtimeSnapshot();
        snapshot.setShopId(1L);
        snapshot.setBizDate(slotTime.toLocalDate());
        snapshot.setSlotTime(slotTime);
        snapshot.setDayShowCount(dayShowCount);
        return snapshot;
    }

    private MtPromotionAutoBidService buildService(PromotionBidDecisionService decisionService, ObjectMapper objectMapper) {
        MtPromotionAutoBidService service = new MtPromotionAutoBidService(
                mtPromotionSettingMapper,
                mtPromotionBidRecordMapper,
                promotionTimeWindowEvaluator,
                promotionMetricsProvider,
                mtPromotionOpenApiClient,
                decisionService,
                new PromotionExposureDecisionService(mtPromotionAdRealtimeSnapshotService),
                promotionPowerConsumeService,
                mtPromotionSettingService,
                mtPowerAccountService,
                mtShopClient,
                mtShopService,
                mtPromotionAdRealtimeSnapshotService,
                mtPromotionAdDataService,
                null,
                new PromotionLogMessageBuilder(),
                shopPeakWindowProfileService,
                objectMapper
        );
        service.setPromotionRecentOrderStatisticService(promotionRecentOrderStatisticService);
        return service;
    }

    private MtPromotionAutoBidService buildServiceWithLogMarkdown(PromotionBidDecisionService decisionService, ObjectMapper objectMapper) {
        MtPromotionAutoBidService service = new MtPromotionAutoBidService(
                mtPromotionSettingMapper,
                mtPromotionBidRecordMapper,
                promotionTimeWindowEvaluator,
                promotionMetricsProvider,
                mtPromotionOpenApiClient,
                decisionService,
                new PromotionExposureDecisionService(mtPromotionAdRealtimeSnapshotService),
                promotionPowerConsumeService,
                mtPromotionSettingService,
                mtPowerAccountService,
                mtShopClient,
                mtShopService,
                mtPromotionAdRealtimeSnapshotService,
                mtPromotionAdDataService,
                promotionLogMarkdownService,
                new PromotionLogMessageBuilder(),
                shopPeakWindowProfileService,
                objectMapper
        );
        service.setPromotionRecentOrderStatisticService(promotionRecentOrderStatisticService);
        return service;
    }

    private MtPromotionAutoBidService buildServiceWithBidConstraint(PromotionBidDecisionService decisionService, ObjectMapper objectMapper) {
        MtPromotionAutoBidService service = new MtPromotionAutoBidService(
                mtPromotionSettingMapper,
                mtPromotionBidRecordMapper,
                promotionTimeWindowEvaluator,
                promotionMetricsProvider,
                mtPromotionOpenApiClient,
                decisionService,
                new PromotionExposureDecisionService(mtPromotionAdRealtimeSnapshotService),
                promotionPowerConsumeService,
                mtPromotionSettingService,
                mtPowerAccountService,
                mtShopClient,
                mtShopService,
                mtPromotionAdRealtimeSnapshotService,
                mtPromotionAdDataService,
                null,
                null,
                new PromotionLogMessageBuilder(),
                shopPeakWindowProfileService,
                mtPromotionBidConstraintService,
                objectMapper,
                null,
                null
        );
        service.setPromotionRecentOrderStatisticService(promotionRecentOrderStatisticService);
        return service;
    }
}
