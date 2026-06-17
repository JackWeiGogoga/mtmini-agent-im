package com.gogoga.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gogoga.backend.constant.MtH5GuideCodeConstant;
import com.gogoga.backend.enums.DashboardStatsRangeTypeEnum;
import com.gogoga.backend.entity.LoginUser;
import com.gogoga.backend.enums.BackendPortalType;
import com.gogoga.backend.entity.mt.MtAccessToken;
import com.gogoga.backend.entity.mt.MtPowerAccount;
import com.gogoga.backend.entity.mt.MtPromotionBidRecord;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.entity.mt.MtShopRight;
import com.gogoga.backend.mapper.MtPromotionBidRecordMapper;
import com.gogoga.backend.service.promotion.PromotionTimeWindowEvaluator;
import com.gogoga.backend.service.promotion.PromotionExecutionWindow;
import com.gogoga.backend.service.promotion.PromotionTimeWindowResult;
import com.gogoga.backend.vo.MtH5GuideStateVO;
import com.gogoga.backend.vo.MtH5HomeDashboardVO;
import com.gogoga.backend.vo.MtH5PromotionSettingsVO;
import com.gogoga.backend.util.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * H5 首页聚合看板服务测试
 *
 * @author system
 * @since 2026-04-07
 */
@ExtendWith(MockitoExtension.class)
class MtH5HomeDashboardServiceTest {

    @Mock
    private MtH5ShopService mtH5ShopService;

    @Mock
    private MtPromotionSettingService mtPromotionSettingService;

    @Mock
    private MtShopRightService mtShopRightService;

    @Mock
    private MtPowerAccountService mtPowerAccountService;

    @Mock
    private ShopDashboardMetricsService shopDashboardMetricsService;

    @Mock
    private MtPromotionBidRecordMapper mtPromotionBidRecordMapper;

    @Mock
    private PromotionTimeWindowEvaluator promotionTimeWindowEvaluator;

    @Mock
    private MtPromotionLogService mtPromotionLogService;

    @Mock
    private MtH5GuideStateService mtH5GuideStateService;

    @Mock
    private MtAccessTokenService mtAccessTokenService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private MtH5HomeDashboardService mtH5HomeDashboardService;

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void shouldBuildDashboardWithAggregatedMetrics() {
        MtShop shop = new MtShop();
        shop.setId(1L);
        shop.setPoiName("测试门店");
        shop.setPoiId("poi-1");
        shop.setEpoiId("epoi-1");
        when(mtH5ShopService.getCurrentShop()).thenReturn(shop);

        MtH5PromotionSettingsVO settings = new MtH5PromotionSettingsVO();
        settings.setPromotionEnabled(true);
        settings.setBalanceReminderEnabled(true);
        settings.setStrategyConfigured(true);
        settings.setStrategyEntryVisible(true);
        settings.setStrategyConfigJson("{\"priority\":\"rate\"}");
        settings.setAutoTimeModeAvailable(true);
        settings.setAutoTimeModeHintText("AI识别高峰期");
        when(mtPromotionSettingService.getCurrentSettings()).thenReturn(settings);

        MtShopRight activeRight = new MtShopRight();
        activeRight.setExpireAt(LocalDateTime.of(2026, 5, 1, 0, 0));
        when(mtShopRightService.findHomepageActiveRight(1L)).thenReturn(activeRight);

        MtPowerAccount powerAccount = new MtPowerAccount();
        powerAccount.setBalance(300);
        powerAccount.setTotalRecharge(500);
        powerAccount.setTotalConsume(120);
        when(mtPowerAccountService.getOrCreateAccount(1L)).thenReturn(powerAccount);

        ShopDashboardMetricsService.MarketingStatsSnapshot marketing = new ShopDashboardMetricsService.MarketingStatsSnapshot();
        marketing.setCurrentExposure(666);
        marketing.setPreviousExposure(555);
        marketing.setCurrentIncome(new BigDecimal("1000.00"));
        marketing.setPreviousIncome(new BigDecimal("800.00"));
        marketing.setCurrentCost(new BigDecimal("100.00"));
        marketing.setPreviousCost(new BigDecimal("80.00"));
        marketing.setCurrentCostRatio(new BigDecimal("0.100000"));
        marketing.setPreviousCostRatio(new BigDecimal("0.100000"));
        when(shopDashboardMetricsService.buildMarketingStats(1L, DashboardStatsRangeTypeEnum.TODAY)).thenReturn(marketing);

        ShopDashboardMetricsService.PowerStatsSnapshot powerStats = new ShopDashboardMetricsService.PowerStatsSnapshot();
        powerStats.setCurrentConsumedPower(30);
        powerStats.setPreviousConsumedPower(20);
        powerStats.setCurrentEstimatedPower(30);
        powerStats.setPreviousEstimatedPower(20);
        powerStats.setBalance(300);
        powerStats.setTotalRecharge(500);
        powerStats.setTotalConsume(120);
        when(shopDashboardMetricsService.buildPowerStats(1L, powerAccount, DashboardStatsRangeTypeEnum.TODAY)).thenReturn(powerStats);
        stubHomeGuide(1L, false);
        MtH5HomeDashboardVO dashboard = mtH5HomeDashboardService.getDashboard(DashboardStatsRangeTypeEnum.TODAY);

        assertEquals("测试门店", dashboard.getShop().getShopName());
        assertTrue(dashboard.getMembership().getActive());
        assertEquals(LocalDateTime.of(2026, 5, 1, 0, 0), dashboard.getMembership().getExpireAt());
        assertTrue(dashboard.getPromotion().getPromotionEnabled());
        assertTrue(dashboard.getPromotion().getAutoTimeModeAvailable());
        assertEquals("AI识别高峰期", dashboard.getPromotion().getAutoTimeModeHintText());
        assertEquals(666, dashboard.getMarketingStats().getCurrentExposure());
        assertEquals(555, dashboard.getMarketingStats().getPreviousExposure());
        assertEquals(new BigDecimal("100.00"), dashboard.getMarketingStats().getCurrentCost());
        assertEquals(new BigDecimal("0.100000"), dashboard.getMarketingStats().getCurrentCostRatio());
        assertEquals(30, dashboard.getPowerStats().getCurrentConsumedPower());
        assertEquals(30, dashboard.getPowerStats().getCurrentEstimatedPower());
        assertEquals(20, dashboard.getPowerStats().getPreviousEstimatedPower());
        assertEquals(300, dashboard.getPowerStats().getBalance());
        assertNotNull(dashboard.getGuide());
        assertEquals(MtH5GuideCodeConstant.CURRENT_HOME_FIRST_AUTH_GUIDE, dashboard.getGuide().getHomeGuideCode());
        assertFalse(dashboard.getGuide().getHomeGuideCompleted());
    }

    @Test
    void shouldExposeLocalReauthRequiredAuthorizationStatus() {
        MtShop shop = new MtShop();
        shop.setId(7L);
        shop.setName("授权失败门店");
        when(mtH5ShopService.getCurrentShop()).thenReturn(shop);

        MtH5PromotionSettingsVO settings = new MtH5PromotionSettingsVO();
        settings.setPromotionEnabled(false);
        when(mtPromotionSettingService.getCurrentSettings()).thenReturn(settings);
        when(mtShopRightService.findHomepageActiveRight(7L)).thenReturn(null);
        MtPowerAccount account = new MtPowerAccount();
        when(mtPowerAccountService.getOrCreateAccount(7L)).thenReturn(account);
        when(shopDashboardMetricsService.buildMarketingStats(7L, DashboardStatsRangeTypeEnum.TODAY))
                .thenReturn(new ShopDashboardMetricsService.MarketingStatsSnapshot());
        when(shopDashboardMetricsService.buildPowerStats(7L, account, DashboardStatsRangeTypeEnum.TODAY))
                .thenReturn(new ShopDashboardMetricsService.PowerStatsSnapshot());
        MtAccessToken token = new MtAccessToken();
        token.setAuthStatus(2);
        when(mtAccessTokenService.findByShopId(7L)).thenReturn(token);
        stubHomeGuide(7L, false);

        MtH5HomeDashboardVO dashboard = mtH5HomeDashboardService.getDashboard(DashboardStatsRangeTypeEnum.TODAY);

        assertNotNull(dashboard.getAuthorization());
        assertEquals("REAUTH_REQUIRED", dashboard.getAuthorization().getStatus());
        assertTrue(dashboard.getAuthorization().getReauthRequired());
        assertEquals("授权失败，请重新授权！", dashboard.getAuthorization().getMessage());
    }

    @Test
    void shouldFallbackToZeroAndInactiveWhenSourceDataIsMissing() {
        MtShop shop = new MtShop();
        shop.setId(2L);
        shop.setName("空数据门店");
        when(mtH5ShopService.getCurrentShop()).thenReturn(shop);

        MtH5PromotionSettingsVO settings = new MtH5PromotionSettingsVO();
        settings.setAutoTimeModeAvailable(false);
        settings.setAutoTimeModeHintText("正常营业 5 天后解锁");
        when(mtPromotionSettingService.getCurrentSettings()).thenReturn(settings);
        when(mtShopRightService.findHomepageActiveRight(2L)).thenReturn(null);

        MtPowerAccount powerAccount = new MtPowerAccount();
        powerAccount.setBalance(null);
        powerAccount.setTotalRecharge(null);
        powerAccount.setTotalConsume(null);
        when(mtPowerAccountService.getOrCreateAccount(2L)).thenReturn(powerAccount);

        ShopDashboardMetricsService.MarketingStatsSnapshot marketing = new ShopDashboardMetricsService.MarketingStatsSnapshot();
        marketing.setCurrentExposure(0);
        marketing.setPreviousExposure(0);
        marketing.setCurrentIncome(new BigDecimal("0.00"));
        marketing.setPreviousIncome(new BigDecimal("0.00"));
        marketing.setCurrentCost(new BigDecimal("0.00"));
        marketing.setPreviousCost(new BigDecimal("0.00"));
        marketing.setCurrentCostRatio(new BigDecimal("0.000000"));
        marketing.setPreviousCostRatio(new BigDecimal("0.000000"));
        when(shopDashboardMetricsService.buildMarketingStats(2L, DashboardStatsRangeTypeEnum.LAST_30_DAYS)).thenReturn(marketing);

        ShopDashboardMetricsService.PowerStatsSnapshot powerStats = new ShopDashboardMetricsService.PowerStatsSnapshot();
        powerStats.setCurrentConsumedPower(0);
        powerStats.setPreviousConsumedPower(0);
        powerStats.setCurrentEstimatedPower(0);
        powerStats.setPreviousEstimatedPower(0);
        powerStats.setBalance(0);
        powerStats.setTotalRecharge(0);
        powerStats.setTotalConsume(0);
        when(shopDashboardMetricsService.buildPowerStats(2L, powerAccount, DashboardStatsRangeTypeEnum.LAST_30_DAYS)).thenReturn(powerStats);
        when(mtPromotionBidRecordMapper.selectLatestByShopIdAndDecisionReason(2L, "REMOTE_PLAN_STATUS_CLOSED")).thenReturn(null);
        stubHomeGuide(2L, true);

        MtH5HomeDashboardVO dashboard = mtH5HomeDashboardService.getDashboard(DashboardStatsRangeTypeEnum.LAST_30_DAYS);

        assertNotNull(dashboard.getMembership());
        assertFalse(dashboard.getMembership().getActive());
        assertNull(dashboard.getMembership().getExpireAt());
        assertFalse(dashboard.getPromotion().getAutoTimeModeAvailable());
        assertEquals("正常营业 5 天后解锁", dashboard.getPromotion().getAutoTimeModeHintText());
        assertEquals(0, dashboard.getMarketingStats().getCurrentExposure());
        assertEquals(0, dashboard.getMarketingStats().getPreviousExposure());
        assertEquals(new BigDecimal("0.00"), dashboard.getMarketingStats().getCurrentCost());
        assertEquals(new BigDecimal("0.000000"), dashboard.getMarketingStats().getCurrentCostRatio());
        assertEquals(0, dashboard.getPowerStats().getCurrentConsumedPower());
        assertEquals(0, dashboard.getPowerStats().getCurrentEstimatedPower());
        assertEquals(0, dashboard.getPowerStats().getBalance());
        assertTrue(dashboard.getGuide().getHomeGuideCompleted());
    }

    @Test
    void shouldExposePromotionDisabledReasonWhenRemotePlanWasClosed() {
        MtShop shop = new MtShop();
        shop.setId(3L);
        shop.setName("被关闭门店");
        when(mtH5ShopService.getCurrentShop()).thenReturn(shop);

        MtH5PromotionSettingsVO settings = new MtH5PromotionSettingsVO();
        settings.setPromotionEnabled(false);
        when(mtPromotionSettingService.getCurrentSettings()).thenReturn(settings);
        when(mtShopRightService.findHomepageActiveRight(3L)).thenReturn(null);

        MtPowerAccount powerAccount = new MtPowerAccount();
        when(mtPowerAccountService.getOrCreateAccount(3L)).thenReturn(powerAccount);
        when(shopDashboardMetricsService.buildMarketingStats(3L, DashboardStatsRangeTypeEnum.LAST_7_DAYS))
                .thenReturn(new ShopDashboardMetricsService.MarketingStatsSnapshot());
        when(shopDashboardMetricsService.buildPowerStats(3L, powerAccount, DashboardStatsRangeTypeEnum.LAST_7_DAYS))
                .thenReturn(new ShopDashboardMetricsService.PowerStatsSnapshot());

        MtPromotionBidRecord record = new MtPromotionBidRecord();
        record.setDecisionReason("REMOTE_PLAN_STATUS_CLOSED");
        when(mtPromotionBidRecordMapper.selectLatestByShopIdAndDecisionReason(3L, "REMOTE_PLAN_STATUS_CLOSED")).thenReturn(record);
        stubHomeGuide(3L, false);

        MtH5HomeDashboardVO dashboard = mtH5HomeDashboardService.getDashboard();

        assertEquals("美团商家端推广计划已关闭，AI推广已自动停止，请重新开启商家端推广后再试。", dashboard.getPromotion().getPromotionDisabledReasonText());
    }

    @Test
    void shouldHidePowerStatsForYouzanPortalContext() {
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId("youzan-1");
        loginUser.setActivePortal(BackendPortalType.YOUZAN);
        UserContext.setLoginUser(loginUser);

        MtShop shop = new MtShop();
        shop.setId(4L);
        shop.setName("有赞门店");
        when(mtH5ShopService.getCurrentShop()).thenReturn(shop);

        MtH5PromotionSettingsVO settings = new MtH5PromotionSettingsVO();
        settings.setPromotionEnabled(true);
        when(mtPromotionSettingService.getCurrentSettings()).thenReturn(settings);
        when(mtShopRightService.findHomepageActiveRight(4L)).thenReturn(null);
        when(shopDashboardMetricsService.buildMarketingStats(4L, DashboardStatsRangeTypeEnum.TODAY))
                .thenReturn(new ShopDashboardMetricsService.MarketingStatsSnapshot());
        stubHomeGuide(4L, false);

        MtH5HomeDashboardVO dashboard = mtH5HomeDashboardService.getDashboard(DashboardStatsRangeTypeEnum.TODAY);

        assertNull(dashboard.getPowerStats());
        verify(mtPowerAccountService, never()).getOrCreateAccount(4L);
    }

    @Test
    void shouldExposeEditableCurrentBidFromLocalLatestSuccessInsideWindow() throws Exception {
        MtShop shop = new MtShop();
        shop.setId(5L);
        shop.setName("出价门店");
        shop.setShippingTime("09:00-22:00");
        when(mtH5ShopService.getCurrentShop()).thenReturn(shop);

        MtH5PromotionSettingsVO settings = new MtH5PromotionSettingsVO();
        settings.setPromotionEnabled(true);
        settings.setStrategyConfigured(true);
        settings.setStrategyConfigJson("{\"priority\":\"rate\",\"timeMode\":\"business\",\"rateMode\":\"control\",\"rateMin\":\"5%\",\"rateMax\":\"10%\"}");
        when(mtPromotionSettingService.getCurrentSettings()).thenReturn(settings);
        MtPromotionSettingService.StrategyConfig strategyConfig = new MtPromotionSettingService.StrategyConfig();
        strategyConfig.setTimeMode("business");
        when(objectMapper.readValue(settings.getStrategyConfigJson(), MtPromotionSettingService.StrategyConfig.class)).thenReturn(strategyConfig);
        when(mtPromotionSettingService.normalizeForExecution(strategyConfig)).thenReturn(strategyConfig);
        PromotionExecutionWindow window = PromotionExecutionWindow.of(
                LocalDateTime.of(2026, 5, 11, 9, 0),
                LocalDateTime.of(2026, 5, 11, 22, 0)
        );
        when(promotionTimeWindowEvaluator.evaluate(
                org.mockito.ArgumentMatchers.eq(strategyConfig),
                org.mockito.ArgumentMatchers.eq("09:00-22:00"),
                org.mockito.ArgumentMatchers.any()
        ))
                .thenReturn(new PromotionTimeWindowResult(true, "BUSINESS_TIME_MATCHED", window));

        MtPromotionBidRecord latest = new MtPromotionBidRecord();
        latest.setSlotTime(LocalDateTime.of(2026, 5, 11, 10, 10));
        latest.setPreviousBidAmount(new BigDecimal("0.70"));
        latest.setNextBidAmount(new BigDecimal("0.80"));
        latest.setExecuteStatus("SUCCESS");
        when(mtPromotionBidRecordMapper.selectLatestByShopId(5L)).thenReturn(latest);
        when(mtShopRightService.findHomepageActiveRight(5L)).thenReturn(null);
        MtPowerAccount account = new MtPowerAccount();
        account.setBalance(600000);
        when(mtPowerAccountService.getOrCreateAccount(5L)).thenReturn(account);
        when(shopDashboardMetricsService.buildMarketingStats(5L, DashboardStatsRangeTypeEnum.TODAY))
                .thenReturn(new ShopDashboardMetricsService.MarketingStatsSnapshot());
        when(shopDashboardMetricsService.buildPowerStats(5L, account, DashboardStatsRangeTypeEnum.TODAY))
                .thenReturn(new ShopDashboardMetricsService.PowerStatsSnapshot());
        stubHomeGuide(5L, false);

        MtH5HomeDashboardVO dashboard = mtH5HomeDashboardService.getDashboard(DashboardStatsRangeTypeEnum.TODAY);

        assertTrue(dashboard.getCurrentBid().getInPromotionWindow());
        assertTrue(dashboard.getCurrentBid().getEditable());
        assertEquals(new BigDecimal("0.80"), dashboard.getCurrentBid().getCurrentBidAmount());
        assertEquals(new BigDecimal("0.70"), dashboard.getCurrentBid().getPreviousBidAmount());
        assertEquals(new BigDecimal("0.10"), dashboard.getCurrentBid().getMinBidAmount());
        assertEquals(new BigDecimal("4.00"), dashboard.getCurrentBid().getMaxBidAmount());
    }

    @Test
    void shouldExposeLatestSkippedBidStatusInsideWindow() throws Exception {
        MtShop shop = new MtShop();
        shop.setId(6L);
        shop.setName("跳过门店");
        shop.setShippingTime("09:00-22:00");
        when(mtH5ShopService.getCurrentShop()).thenReturn(shop);

        MtH5PromotionSettingsVO settings = new MtH5PromotionSettingsVO();
        settings.setPromotionEnabled(true);
        settings.setStrategyConfigured(true);
        settings.setStrategyConfigJson("{\"priority\":\"rate\",\"timeMode\":\"business\"}");
        when(mtPromotionSettingService.getCurrentSettings()).thenReturn(settings);
        MtPromotionSettingService.StrategyConfig strategyConfig = new MtPromotionSettingService.StrategyConfig();
        strategyConfig.setTimeMode("business");
        when(objectMapper.readValue(settings.getStrategyConfigJson(), MtPromotionSettingService.StrategyConfig.class)).thenReturn(strategyConfig);
        when(mtPromotionSettingService.normalizeForExecution(strategyConfig)).thenReturn(strategyConfig);
        PromotionExecutionWindow window = PromotionExecutionWindow.of(
                LocalDateTime.of(2026, 5, 11, 9, 0),
                LocalDateTime.of(2026, 5, 11, 22, 0)
        );
        when(promotionTimeWindowEvaluator.evaluate(
                org.mockito.ArgumentMatchers.eq(strategyConfig),
                org.mockito.ArgumentMatchers.eq("09:00-22:00"),
                org.mockito.ArgumentMatchers.any()
        ))
                .thenReturn(new PromotionTimeWindowResult(true, "BUSINESS_TIME_MATCHED", window));

        MtPromotionBidRecord latest = new MtPromotionBidRecord();
        latest.setSlotTime(LocalDateTime.of(2026, 5, 11, 10, 20));
        latest.setExecuteStatus("SKIPPED");
        latest.setPreviousBidAmount(new BigDecimal("0.80"));
        when(mtPromotionBidRecordMapper.selectLatestByShopId(6L)).thenReturn(latest);
        when(mtShopRightService.findHomepageActiveRight(6L)).thenReturn(null);
        MtPowerAccount account = new MtPowerAccount();
        account.setBalance(600000);
        when(mtPowerAccountService.getOrCreateAccount(6L)).thenReturn(account);
        when(shopDashboardMetricsService.buildMarketingStats(6L, DashboardStatsRangeTypeEnum.TODAY))
                .thenReturn(new ShopDashboardMetricsService.MarketingStatsSnapshot());
        when(shopDashboardMetricsService.buildPowerStats(6L, account, DashboardStatsRangeTypeEnum.TODAY))
                .thenReturn(new ShopDashboardMetricsService.PowerStatsSnapshot());
        stubHomeGuide(6L, false);

        MtH5HomeDashboardVO dashboard = mtH5HomeDashboardService.getDashboard(DashboardStatsRangeTypeEnum.TODAY);

        assertTrue(dashboard.getCurrentBid().getInPromotionWindow());
        assertTrue(dashboard.getCurrentBid().getEditable());
        assertNull(dashboard.getCurrentBid().getCurrentBidAmount());
        assertEquals("SKIPPED", dashboard.getCurrentBid().getLatestExecuteStatus());
        assertEquals("已跳过", dashboard.getCurrentBid().getDisplayStatusText());
        assertEquals(new BigDecimal("0.80"), dashboard.getCurrentBid().getPreviousBidAmount());
    }

    private void stubHomeGuide(Long shopId, boolean completed) {
        MtH5GuideStateVO guideState = new MtH5GuideStateVO();
        guideState.setGuideCode(MtH5GuideCodeConstant.CURRENT_HOME_FIRST_AUTH_GUIDE);
        guideState.setCompleted(completed);
        when(mtH5GuideStateService.getGuideState(shopId, MtH5GuideCodeConstant.CURRENT_HOME_FIRST_AUTH_GUIDE))
                .thenReturn(guideState);
    }
}
