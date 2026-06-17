package com.gogoga.backend.service;

import com.github.pagehelper.PageInfo;
import com.gogoga.backend.entity.LoginUser;
import com.gogoga.backend.entity.mt.MtAccessToken;
import com.gogoga.backend.entity.mt.MtAsyncTask;
import com.gogoga.backend.entity.mt.MtPowerConsume;
import com.gogoga.backend.entity.mt.MtPromotionBidConstraint;
import com.gogoga.backend.entity.mt.MtPromotionBidRecord;
import com.gogoga.backend.entity.mt.MtPromotionSetting;
import com.gogoga.backend.entity.mt.MtPromotionSettingHistory;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.enums.AdminDataScopeTypeEnum;
import com.gogoga.backend.enums.BackendPortalType;
import com.gogoga.backend.exception.BusinessException;
import com.gogoga.backend.mapper.MtAsyncTaskMapper;
import com.gogoga.backend.mapper.MtOrderMapper;
import com.gogoga.backend.mapper.MtPowerConsumeMapper;
import com.gogoga.backend.mapper.MtPromotionAdRealtimeSnapshotMapper;
import com.gogoga.backend.mapper.MtPromotionBidConstraintMapper;
import com.gogoga.backend.mapper.MtPromotionBidRecordMapper;
import com.gogoga.backend.mapper.MtPromotionMetricSnapshotMapper;
import com.gogoga.backend.mapper.MtPromotionSettingMapper;
import com.gogoga.backend.mapper.MtShopMapper;
import com.gogoga.backend.mapper.MtShopPeakWindowProfileMapper;
import com.gogoga.backend.mapper.MtShopRightMapper;
import com.gogoga.backend.param.AdminPricingAnalysisQueryParam;
import com.gogoga.backend.service.promotion.PromotionDecisionDetail;
import com.gogoga.backend.service.promotion.PromotionLogMessageBuilder;
import com.gogoga.backend.util.JsonUtil;
import com.gogoga.backend.util.UserContext;
import com.gogoga.backend.vo.AdminPricingAnalysisDetailVO;
import com.gogoga.backend.vo.AdminPricingAnalysisRowVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminPricingAnalysisServiceTest {

    @Mock
    private MtPromotionBidRecordMapper mtPromotionBidRecordMapper;
    @Mock
    private MtShopMapper mtShopMapper;
    @Mock
    private MtPromotionSettingMapper mtPromotionSettingMapper;
    @Mock
    private MtPromotionSettingHistoryService mtPromotionSettingHistoryService;
    @Mock
    private MtAccessTokenService mtAccessTokenService;
    @Mock
    private MtShopRightMapper mtShopRightMapper;
    @Mock
    private MtPromotionMetricSnapshotMapper mtPromotionMetricSnapshotMapper;
    @Mock
    private MtPromotionAdRealtimeSnapshotMapper mtPromotionAdRealtimeSnapshotMapper;
    @Mock
    private MtShopPeakWindowProfileMapper mtShopPeakWindowProfileMapper;
    @Mock
    private MtPromotionBidConstraintMapper mtPromotionBidConstraintMapper;
    @Mock
    private MtOrderMapper mtOrderMapper;
    @Mock
    private MtAsyncTaskMapper mtAsyncTaskMapper;
    @Mock
    private MtPowerConsumeMapper mtPowerConsumeMapper;
    @Mock
    private AdminDataScopeService adminDataScopeService;
    @Mock
    private UserShopBindingService userShopBindingService;
    @Spy
    private PromotionLogMessageBuilder promotionLogMessageBuilder = new PromotionLogMessageBuilder();

    @InjectMocks
    private AdminPricingAnalysisService adminPricingAnalysisService;

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void shouldRejectNonAdminPortal() {
        mockUser(BackendPortalType.ORDINARY, Set.of("admin"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> adminPricingAnalysisService.listRows(new AdminPricingAnalysisQueryParam())
        );

        assertEquals(403, exception.getCode());
        assertEquals("仅管理员工作台可访问定价分析", exception.getMessage());
        verify(mtPromotionBidRecordMapper, never()).selectAdminAnalysisRows(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    void shouldListRowsWithResolvedTeamScope() {
        mockUser(BackendPortalType.ADMIN, Set.of("admin"));
        when(adminDataScopeService.getCurrentScopeType()).thenReturn(AdminDataScopeTypeEnum.TEAM);
        when(adminDataScopeService.getCurrentVisibleUserIds()).thenReturn(Set.of("admin-2", "admin-3"));
        AdminPricingAnalysisQueryParam param = new AdminPricingAnalysisQueryParam();
        param.setKeyword(" poi-1 ");
        param.setPageNum(1);
        param.setPageSize(20);
        AdminPricingAnalysisRowVO row = new AdminPricingAnalysisRowVO();
        row.setDecisionReason("MAX_BID_REACHED_KEEP");
        when(mtPromotionBidRecordMapper.selectAdminAnalysisRows(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
        )).thenReturn(List.of(row));

        PageInfo<AdminPricingAnalysisRowVO> pageInfo = adminPricingAnalysisService.listRows(param);

        assertEquals("当前出价已经比较高，保持当前出价，继续观察", pageInfo.getList().get(0).getDecisionReasonText());
        verify(mtPromotionBidRecordMapper).selectAdminAnalysisRows(
                eq(null),
                eq(null),
                eq(null),
                eq("poi-1"),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq("TEAM"),
                eq(null),
                argThat(visibleUsers -> visibleUsers.contains("admin-2") && visibleUsers.contains("admin-3"))
        );
    }

    @Test
    void shouldPreserveExplicitSlotTimeRange() {
        mockUser(BackendPortalType.ADMIN, Set.of("admin"));
        when(adminDataScopeService.getCurrentScopeType()).thenReturn(AdminDataScopeTypeEnum.ALL);
        LocalDateTime startTime = LocalDateTime.of(2026, 5, 1, 0, 0);
        LocalDateTime endTime = LocalDateTime.of(2026, 5, 2, 23, 59, 59);
        AdminPricingAnalysisQueryParam param = new AdminPricingAnalysisQueryParam();
        param.setSlotStartTime(startTime);
        param.setSlotEndTime(endTime);
        when(mtPromotionBidRecordMapper.selectAdminAnalysisRows(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
        )).thenReturn(List.of());

        adminPricingAnalysisService.listRows(param);

        verify(mtPromotionBidRecordMapper).selectAdminAnalysisRows(
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(startTime),
                eq(endTime),
                eq("ALL"),
                eq(null),
                eq(List.of())
        );
    }

    @Test
    void shouldBuildDetailWithStrategySnapshotAndDiagnostics() {
        mockUser(BackendPortalType.ADMIN, Set.of("super_admin"));
        LocalDateTime slotTime = LocalDateTime.of(2026, 5, 8, 10, 20);
        String strategyJson = "{\"rateMax\":\"10%\"}";
        PromotionDecisionDetail decisionDetail = new PromotionDecisionDetail();
        decisionDetail.setStrategyConfigSnapshotJson(strategyJson);
        decisionDetail.setStrategyConfigHash(MtPromotionSettingHistoryService.strategyConfigHash(strategyJson));
        decisionDetail.setDecisionSource("rule");
        decisionDetail.setPricingBranch("fee_rate");
        decisionDetail.setPeakWindowDayType("WEEKDAY");
        decisionDetail.setPeakWindowProfileDayType("WEEKDAY");
        decisionDetail.setPeakWindowCalendarDayType("ADJUSTED_WORKDAY");
        decisionDetail.setPeakWindowHolidayName("劳动节补班");
        decisionDetail.setPeakWindowProfileStatus("USABLE");
        decisionDetail.setPeakWindowGatingEnabled(true);
        decisionDetail.setPeakWindowMatched(true);
        decisionDetail.setPeakWindowMatchedRange("10:00-11:00");
        decisionDetail.setPeakWindowSampleDayCount(5);
        decisionDetail.setPeakWindowSampleOrderCount(42);
        decisionDetail.setExposureWeakGrowthRollbackApplied(true);
        decisionDetail.setExposureWeakGrowthConsecutiveCount(5);
        decisionDetail.setExposureWeakGrowthBestSlotTime(slotTime.minusMinutes(20));
        decisionDetail.setExposureWeakGrowthBestFollowupExposure(280);
        decisionDetail.setExposureWeakGrowthBestPreviousBidAmount(new BigDecimal("0.40"));
        decisionDetail.setExposureWeakGrowthRollbackBidAmount(new BigDecimal("0.40"));

        MtPromotionBidRecord record = new MtPromotionBidRecord();
        record.setId(88L);
        record.setShopId(1008L);
        record.setSlotTime(slotTime);
        record.setWindowStartAt(slotTime.minusMinutes(30));
        record.setWindowEndAt(slotTime.plusMinutes(30));
        record.setExecuteStatus("SUCCESS");
        record.setDecisionAction("KEEP");
        record.setDecisionReason("CLICK_DETECTED_KEEP_PREVIOUS_BID");
        record.setNextBidAmount(new BigDecimal("0.40"));
        record.setMetricsPayloadJson("{\"metrics\":true}");
        record.setUpdatePayloadJson(JsonUtil.toJson(Map.of(
                "statusCheck", Map.of(
                        "status", 0,
                        "bid", 40,
                        "budget", new BigDecimal("100.00"),
                        "budgetUsed", new BigDecimal("35.50"),
                        "availableBudget", new BigDecimal("64.50"),
                        "responseCode", "REMOTE_ERROR",
                        "responseMsg", "远端接口失败",
                        "responseData", Map.of("errMsg", "推广计划不存在")
                ),
                "bidUpdate", Map.of("bid", 40)
        )));
        record.setDecisionDetailJson(JsonUtil.toJson(decisionDetail));
        record.setLogTaskId(66L);

        MtShop shop = new MtShop();
        shop.setId(1008L);
        shop.setPoiId("poi-1008");
        shop.setEpoiId("epoi-1008");
        shop.setPoiName("测试门店");
        MtPromotionSetting setting = new MtPromotionSetting();
        setting.setShopId(1008L);
        setting.setPromotionEnabled(true);
        setting.setStrategyConfigured(true);
        setting.setStrategyConfigJson(strategyJson);
        MtAccessToken token = new MtAccessToken();
        token.setAccessToken("token-1008");
        token.setExpiresAt(LocalDateTime.now().plusDays(1));
        MtPromotionSettingHistory history = new MtPromotionSettingHistory();
        history.setShopId(1008L);
        history.setVersionNo(1);
        MtAsyncTask autoBidTask = new MtAsyncTask();
        autoBidTask.setId(55L);
        MtAsyncTask logTask = new MtAsyncTask();
        logTask.setId(66L);
        MtPowerConsume consume = new MtPowerConsume();
        consume.setShopId(1008L);
        MtPromotionBidConstraint bidConstraint = new MtPromotionBidConstraint();
        bidConstraint.setShopId(1008L);
        bidConstraint.setPoiId("poi-1008");
        bidConstraint.setMaxBidAmount(new BigDecimal("3.00"));
        bidConstraint.setSource(MtPromotionBidConstraintService.SOURCE_MEITUAN_ERROR);
        bidConstraint.setRawMessage("出价不能高于3.0元");
        bidConstraint.setObservedAt(slotTime.plusMinutes(5));
        bidConstraint.setExpireAt(slotTime.plusDays(7));

        when(mtPromotionBidRecordMapper.selectById(88L)).thenReturn(record);
        when(mtShopMapper.selectById(1008L)).thenReturn(shop);
        when(mtPromotionSettingMapper.selectByShopId(1008L)).thenReturn(setting);
        when(mtAccessTokenService.findByShopId(1008L)).thenReturn(token);
        when(mtShopRightMapper.selectByShopId(1008L)).thenReturn(List.of());
        when(mtPromotionSettingHistoryService.listLatestByShopId(1008L, 20)).thenReturn(List.of(history));
        when(mtOrderMapper.countOrdersByShopIdAndOrderSendTimeBetweenExcludingStatus(any(), any(), any(), any()))
                .thenReturn(3)
                .thenReturn(1);
        when(mtOrderMapper.sumIncomeByShopIdAndOrderSendTimeBetweenExcludingStatus(any(), any(), any(), any()))
                .thenReturn(new BigDecimal("88.00"))
                .thenReturn(new BigDecimal("22.00"));
        when(mtAsyncTaskMapper.selectByTaskTypeAndBizKey(eq("PROMOTION_AUTO_BID"), eq("1008#2026-05-08T10:20")))
                .thenReturn(autoBidTask);
        when(mtAsyncTaskMapper.selectById(66L)).thenReturn(logTask);
        when(mtPowerConsumeMapper.selectByShopIdAndSourceId(1008L, "1008-202605081020")).thenReturn(List.of(consume));
        when(mtPromotionBidConstraintMapper.selectActiveByShopId(eq(1008L), any())).thenReturn(bidConstraint);

        AdminPricingAnalysisDetailVO detail = adminPricingAnalysisService.getDetail(88L);

        assertEquals(88L, detail.getRecordSummary().getId());
        assertEquals("综合分析当前推广情况，保持当前出价",
                detail.getRecordSummary().getDecisionReasonText());
        assertEquals("测试门店", detail.getShopContext().getShopName());
        assertTrue(detail.getShopContext().getHasAccessToken());
        assertFalse(detail.getStrategyContext().getExecutionStrategySnapshotMissing());
        assertTrue(detail.getStrategyContext().getSameAsCurrent());
        assertEquals(strategyJson, detail.getStrategyContext().getExecutionStrategyConfigJson());
        assertEquals(1, detail.getStrategyContext().getHistories().size());
        assertEquals("rule", detail.getDecisionContext().getDecisionSource());
        assertTrue(detail.getDecisionContext().getExposureWeakGrowthRollbackApplied());
        assertEquals(5, detail.getDecisionContext().getExposureWeakGrowthConsecutiveCount());
        assertEquals(slotTime.minusMinutes(20), detail.getDecisionContext().getExposureWeakGrowthBestSlotTime());
        assertEquals(280, detail.getDecisionContext().getExposureWeakGrowthBestFollowupExposure());
        assertEquals(0, new BigDecimal("0.40").compareTo(detail.getDecisionContext().getExposureWeakGrowthBestPreviousBidAmount()));
        assertEquals(0, new BigDecimal("0.40").compareTo(detail.getDecisionContext().getExposureWeakGrowthRollbackBidAmount()));
        assertEquals(3, detail.getMetricsContext().getWindowOrderCount());
        assertEquals(new BigDecimal("88.00"), detail.getMetricsContext().getWindowIncome());
        assertNotNull(detail.getExecutionContext().getAutoBidTask());
        assertNotNull(detail.getExecutionContext().getLogTask());
        assertEquals(1, detail.getExecutionContext().getPowerConsumes().size());
        assertEquals("USABLE", detail.getPeakProfileContext().getSnapshot().getProfileStatus());
        assertEquals("ADJUSTED_WORKDAY", detail.getPeakProfileContext().getSnapshot().getCalendarDayType());
        assertEquals("劳动节补班", detail.getPeakProfileContext().getSnapshot().getHolidayName());
        assertTrue(detail.getPeakProfileContext().getSnapshot().getGatingEnabled());
        assertEquals("10:00-11:00", detail.getPeakProfileContext().getSnapshot().getMatchedRange());
        assertEquals(0, detail.getStatusCheckContext().getStatus());
        assertEquals(40, detail.getStatusCheckContext().getBid());
        assertEquals(0, new BigDecimal("100.00").compareTo(detail.getStatusCheckContext().getBudget()));
        assertEquals(0, new BigDecimal("35.50").compareTo(detail.getStatusCheckContext().getBudgetUsed()));
        assertEquals(0, new BigDecimal("64.50").compareTo(detail.getStatusCheckContext().getAvailableBudget()));
        assertEquals("REMOTE_ERROR", detail.getStatusCheckContext().getResponseCode());
        assertEquals("远端接口失败", detail.getStatusCheckContext().getResponseMsg());
        assertEquals("推广计划不存在", detail.getStatusCheckContext().getResponseDataErrMsg());
        assertTrue(detail.getBidConstraintContext().getActive());
        assertEquals(0, new BigDecimal("4.00").compareTo(detail.getBidConstraintContext().getGlobalMaxBidAmount()));
        assertEquals(0, new BigDecimal("3.00").compareTo(detail.getBidConstraintContext().getShopMaxBidAmount()));
        assertEquals(0, new BigDecimal("3.00").compareTo(detail.getBidConstraintContext().getEffectiveMaxBidAmount()));
        assertEquals(MtPromotionBidConstraintService.SOURCE_MEITUAN_ERROR, detail.getBidConstraintContext().getSource());
        assertEquals("出价不能高于3.0元", detail.getBidConstraintContext().getRawMessage());
        assertEquals("{\"metrics\":true}", detail.getRawPayloads().getMetricsPayloadJson());
        assertNotNull(detail.getRawPayloads().getUpdatePayloadJson());
    }

    @Test
    void shouldFlagMissingExecutionStrategySnapshotForOldRecord() {
        mockUser(BackendPortalType.ADMIN, Set.of("super_admin"));
        MtPromotionBidRecord record = new MtPromotionBidRecord();
        record.setId(89L);
        record.setShopId(1009L);
        record.setDecisionDetailJson("{}");
        MtPromotionSetting setting = new MtPromotionSetting();
        setting.setShopId(1009L);
        setting.setStrategyConfigJson("{\"rateMax\":\"8%\"}");
        when(mtPromotionBidRecordMapper.selectById(89L)).thenReturn(record);
        when(mtShopMapper.selectById(1009L)).thenReturn(new MtShop());
        when(mtPromotionSettingMapper.selectByShopId(1009L)).thenReturn(setting);
        when(mtShopRightMapper.selectByShopId(1009L)).thenReturn(List.of());
        when(mtPromotionSettingHistoryService.listLatestByShopId(1009L, 20)).thenReturn(List.of());

        AdminPricingAnalysisDetailVO detail = adminPricingAnalysisService.getDetail(89L);

        assertTrue(detail.getStrategyContext().getExecutionStrategySnapshotMissing());
        assertFalse(detail.getStrategyContext().getSameAsCurrent());
    }

    private void mockUser(BackendPortalType portalType, Set<String> roles) {
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId("admin-1");
        loginUser.setUserName("管理员");
        loginUser.setActivePortal(portalType);
        loginUser.setRoles(roles);
        UserContext.setLoginUser(loginUser);
    }
}
