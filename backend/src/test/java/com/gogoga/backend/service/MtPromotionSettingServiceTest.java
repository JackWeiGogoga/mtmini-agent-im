package com.gogoga.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gogoga.backend.client.MtPromotionOpenApiClient;
import com.gogoga.backend.entity.mt.MtAccessToken;
import com.gogoga.backend.entity.mt.MtPowerAccount;
import com.gogoga.backend.entity.mt.MtPromotionSetting;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.exception.BusinessException;
import com.gogoga.backend.mapper.MtPromotionSettingMapper;
import com.gogoga.backend.param.MtH5PromotionSettingsUpdateParam;
import com.gogoga.backend.service.promotion.PromotionPlanStatusUpdateResult;
import com.gogoga.backend.service.promotion.ShopPeakDayType;
import com.gogoga.backend.service.promotion.ShopPeakWindow;
import com.gogoga.backend.service.promotion.ShopPeakWindowProfileLookupResult;
import com.gogoga.backend.service.promotion.ShopPeakWindowProfileService;
import com.gogoga.backend.vo.MtH5PromotionSettingsVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

/**
 * 门店推广配置服务测试
 *
 * @author system
 * @since 2026-03-24
 */
@ExtendWith(MockitoExtension.class)
class MtPromotionSettingServiceTest {

    @Mock
    private MtPromotionSettingMapper mtPromotionSettingMapper;

    @Mock
    private MtH5ShopService mtH5ShopService;

    @Mock
    private MtAccessTokenService mtAccessTokenService;

    @Mock
    private MtPromotionOpenApiClient mtPromotionOpenApiClient;

    @Mock
    private MtOrderPushControlService mtOrderPushControlService;

    @Mock
    private MtPowerAccountService mtPowerAccountService;

    @Mock
    private ShopPeakWindowProfileService shopPeakWindowProfileService;

    @Mock
    private MtHistoricalOrderBackfillService mtHistoricalOrderBackfillService;

    @Mock
    private MtPromotionSettingHistoryService mtPromotionSettingHistoryService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private MtPromotionSettingService mtPromotionSettingService;

    @BeforeEach
    void setUp() {
        lenient().when(mtPowerAccountService.getOrCreateAccount(anyLong())).thenReturn(buildPowerAccount(2000));
    }

    @Test
    void shouldInitDefaultSettingsWhenCurrentShopHasNoRecord() {
        MtShop shop = new MtShop();
        shop.setId(101L);
        when(mtH5ShopService.getCurrentShop()).thenReturn(shop);
        when(mtPromotionSettingMapper.selectByShopId(101L))
                .thenReturn(null)
                .thenReturn(buildUnconfiguredSetting(101L, false, true));
        mockAutoTimeModeAvailability(false);

        MtH5PromotionSettingsVO settingsVO = mtPromotionSettingService.getCurrentSettings();

        assertFalse(settingsVO.getPromotionEnabled());
        assertTrue(settingsVO.getBalanceReminderEnabled());
        assertFalse(settingsVO.getStrategyConfigured());
        assertTrue(settingsVO.getStrategyEntryVisible());
        assertFalse(settingsVO.getAutoTimeModeAvailable());
        assertEquals("正常营业 5 天后解锁", settingsVO.getAutoTimeModeHintText());
        assertNull(settingsVO.getStrategyConfigJson());
        verify(mtPromotionSettingMapper).initDefaultIfAbsent(eq(101L), eq(null), eq(false), any());
        verify(mtPromotionSettingMapper, never()).updateById(any(MtPromotionSetting.class));
    }

    @Test
    void shouldExposePeakWindowTextsWhenAutoTimeModeIsAvailable() {
        MtShop shop = new MtShop();
        shop.setId(102L);
        when(mtH5ShopService.getCurrentShop()).thenReturn(shop);
        when(mtPromotionSettingMapper.selectByShopId(102L)).thenReturn(buildConfiguredSetting(102L, false, true));
        ShopPeakWindow lunch = new ShopPeakWindow();
        lunch.setStartTime("11:00");
        lunch.setEndTime("14:00");
        ShopPeakWindow dinner = new ShopPeakWindow();
        dinner.setStartTime("18:00");
        dinner.setEndTime("20:00");
        mockAutoTimeModeAvailability(true, List.of(lunch, dinner));

        MtH5PromotionSettingsVO settingsVO = mtPromotionSettingService.getCurrentSettings();

        assertTrue(settingsVO.getAutoTimeModeAvailable());
        assertEquals(List.of("11:00-14:00", "18:00-20:00"), settingsVO.getAutoTimeModePeakWindows());
        assertEquals("工作日", settingsVO.getAutoTimeModeDayTypeText());
    }

    @Test
    void shouldUseCurrentShopContextWhenUpdatingPromotionEnabled() {
        MtShop shop = new MtShop();
        shop.setId(202L);
        shop.setPoiId("poi-202");
        when(mtH5ShopService.getCurrentShop()).thenReturn(shop);
        when(mtPromotionSettingMapper.selectByShopId(202L)).thenReturn(buildConfiguredSetting(202L, false, true));
        mockAutoTimeModeAvailability(true);
        MtAccessToken accessToken = new MtAccessToken();
        accessToken.setAccessToken("token-202");
        accessToken.setOpBizCode("op-202");
        when(mtAccessTokenService.findByShopId(202L)).thenReturn(accessToken);
        PromotionPlanStatusUpdateResult remoteResult = new PromotionPlanStatusUpdateResult();
        remoteResult.setSuccess(true);
        when(mtPromotionOpenApiClient.updatePlanStatus(
                eq(202L),
                eq("poi-202"),
                eq("token-202"),
                eq(MtPromotionOpenApiClient.STATUS_OPEN),
                eq(null),
                eq(null),
                eq("MANUAL_SWITCH_ENABLE")
        )).thenReturn(remoteResult);
        when(mtOrderPushControlService.unblockOrderPush(eq(accessToken), eq("MANUAL_SWITCH_ENABLE")))
                .thenReturn(MtOrderPushControlService.OrderPushSyncResult.succeeded());

        MtH5PromotionSettingsUpdateParam param = new MtH5PromotionSettingsUpdateParam();
        param.setPromotionEnabled(true);

        MtH5PromotionSettingsVO settingsVO = mtPromotionSettingService.updateCurrentSettings(param);

        assertTrue(settingsVO.getPromotionEnabled());
        assertTrue(settingsVO.getBalanceReminderEnabled());
        verify(mtPromotionSettingMapper).selectByShopId(202L);
        verify(mtPromotionSettingMapper, never()).selectByShopId(999L);
        verify(mtPromotionOpenApiClient).updatePlanStatus(
                eq(202L),
                eq("poi-202"),
                eq("token-202"),
                eq(MtPromotionOpenApiClient.STATUS_OPEN),
                eq(null),
                eq(null),
                eq("MANUAL_SWITCH_ENABLE")
        );
        verify(mtOrderPushControlService).unblockOrderPush(eq(accessToken), eq("MANUAL_SWITCH_ENABLE"));
        verify(mtPromotionSettingMapper).updateById(any(MtPromotionSetting.class));
    }

    @Test
    void shouldRejectManualPromotionEnableWhenPowerBelowExecutionThreshold() {
        MtShop shop = new MtShop();
        shop.setId(205L);
        shop.setPoiId("poi-205");
        when(mtH5ShopService.getCurrentShop()).thenReturn(shop);
        when(mtPromotionSettingMapper.selectByShopId(205L)).thenReturn(buildConfiguredSetting(205L, false, true));
        when(mtPowerAccountService.getOrCreateAccount(205L)).thenReturn(buildPowerAccount(1999));

        MtH5PromotionSettingsUpdateParam param = new MtH5PromotionSettingsUpdateParam();
        param.setPromotionEnabled(true);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> mtPromotionSettingService.updateCurrentSettings(param)
        );

        assertEquals(400, exception.getCode());
        assertEquals("算力不足，请先充值再开启", exception.getMessage());
        verify(mtPromotionOpenApiClient, never()).updatePlanStatus(any(), any(), any(), any(), any(), any(), any());
        verify(mtOrderPushControlService, never()).unblockOrderPush(any(), any());
        verify(mtPromotionSettingMapper, never()).updateById(any(MtPromotionSetting.class));
    }

    @Test
    void shouldUnlockAutoTimeModeManuallyWhenPeakProfileBecomesUsable() {
        MtShop shop = new MtShop();
        shop.setId(203L);
        when(mtH5ShopService.getCurrentShop()).thenReturn(shop);
        when(mtPromotionSettingMapper.selectByShopId(203L)).thenReturn(buildConfiguredSetting(203L, false, true));
        MtAccessToken accessToken = new MtAccessToken();
        accessToken.setAccessToken("token-203");
        when(mtAccessTokenService.findByShopId(203L)).thenReturn(accessToken);
        when(mtHistoricalOrderBackfillService.tryBackfillRecentOrdersAndRefreshPeakProfile(
                eq(shop),
                eq("token-203"),
                eq(MtHistoricalOrderBackfillService.TRIGGER_MANUAL_UNLOCK)
        )).thenReturn(MtHistoricalOrderBackfillService.BackfillUnlockResult.completed(
                true,
                null,
                MtHistoricalOrderBackfillService.UNLOCK_SUCCESS_MESSAGE,
                1,
                1
        ));
        mockAutoTimeModeAvailability(true);

        MtH5PromotionSettingsVO settingsVO = mtPromotionSettingService.unlockCurrentAutoTimeMode();

        assertTrue(settingsVO.getAutoTimeModeAvailable());
        assertTrue(settingsVO.getAutoTimeModeUnlockAttempted());
        assertEquals(MtHistoricalOrderBackfillService.UNLOCK_SUCCESS_MESSAGE, settingsVO.getAutoTimeModeUnlockMessage());
    }

    @Test
    void shouldKeepAutoTimeModeLockedWhenManualUnlockHasInsufficientData() {
        MtShop shop = new MtShop();
        shop.setId(204L);
        when(mtH5ShopService.getCurrentShop()).thenReturn(shop);
        when(mtPromotionSettingMapper.selectByShopId(204L)).thenReturn(buildConfiguredSetting(204L, false, true));
        MtAccessToken accessToken = new MtAccessToken();
        accessToken.setAccessToken("token-204");
        when(mtAccessTokenService.findByShopId(204L)).thenReturn(accessToken);
        when(mtHistoricalOrderBackfillService.tryBackfillRecentOrdersAndRefreshPeakProfile(
                eq(shop),
                eq("token-204"),
                eq(MtHistoricalOrderBackfillService.TRIGGER_MANUAL_UNLOCK)
        )).thenReturn(MtHistoricalOrderBackfillService.BackfillUnlockResult.completed(
                false,
                "SAMPLE_INSUFFICIENT",
                MtHistoricalOrderBackfillService.UNLOCK_INSUFFICIENT_MESSAGE,
                0,
                0
        ));
        mockAutoTimeModeAvailability(false);

        MtH5PromotionSettingsVO settingsVO = mtPromotionSettingService.unlockCurrentAutoTimeMode();

        assertFalse(settingsVO.getAutoTimeModeAvailable());
        assertTrue(settingsVO.getAutoTimeModeUnlockAttempted());
        assertEquals(MtHistoricalOrderBackfillService.UNLOCK_INSUFFICIENT_MESSAGE, settingsVO.getAutoTimeModeUnlockMessage());
        assertEquals("SAMPLE_INSUFFICIENT", settingsVO.getAutoTimeModeUnavailableReason());
    }

    @Test
    void shouldUpdateBalanceReminderIndependently() {
        MtShop shop = new MtShop();
        shop.setId(303L);
        when(mtH5ShopService.getCurrentShop()).thenReturn(shop);
        when(mtPromotionSettingMapper.selectByShopId(303L)).thenReturn(buildConfiguredSetting(303L, true, true));
        mockAutoTimeModeAvailability(true);

        MtH5PromotionSettingsUpdateParam param = new MtH5PromotionSettingsUpdateParam();
        param.setBalanceReminderEnabled(false);

        MtH5PromotionSettingsVO settingsVO = mtPromotionSettingService.updateCurrentSettings(param);

        assertTrue(settingsVO.getPromotionEnabled());
        assertFalse(settingsVO.getBalanceReminderEnabled());
        verify(mtPromotionOpenApiClient, never()).updatePlanStatus(any(), any(), any(), any(), any(), any(), any());
        verify(mtOrderPushControlService, never()).unblockOrderPush(any(), any());
        verify(mtOrderPushControlService, never()).blockOrderPush(any(), any());
        verify(mtPromotionSettingMapper).updateById(any(MtPromotionSetting.class));
    }

    @Test
    void shouldSaveSettingsWhenHistoryRecordingFails() {
        MtShop shop = new MtShop();
        shop.setId(305L);
        when(mtH5ShopService.getCurrentShop()).thenReturn(shop);
        when(mtPromotionSettingMapper.selectByShopId(305L)).thenReturn(buildConfiguredSetting(305L, true, true));
        mockAutoTimeModeAvailability(true);
        doThrow(new RuntimeException("history unavailable"))
                .when(mtPromotionSettingHistoryService)
                .recordIfChanged(any(), any(), any(), any());

        MtH5PromotionSettingsUpdateParam param = new MtH5PromotionSettingsUpdateParam();
        param.setBalanceReminderEnabled(false);

        MtH5PromotionSettingsVO settingsVO = mtPromotionSettingService.updateCurrentSettings(param);

        assertFalse(settingsVO.getBalanceReminderEnabled());
        verify(mtPromotionSettingMapper).updateById(any(MtPromotionSetting.class));
    }

    @Test
    void shouldDisablePromotionByClosingRemotePlanOnly() {
        MtShop shop = new MtShop();
        shop.setId(304L);
        shop.setPoiId("poi-304");
        when(mtH5ShopService.getCurrentShop()).thenReturn(shop);
        when(mtPromotionSettingMapper.selectByShopId(304L)).thenReturn(buildConfiguredSetting(304L, true, true));
        mockAutoTimeModeAvailability(true);
        MtAccessToken accessToken = new MtAccessToken();
        accessToken.setAccessToken("token-304");
        accessToken.setOpBizCode("op-304");
        when(mtAccessTokenService.findByShopId(304L)).thenReturn(accessToken);
        PromotionPlanStatusUpdateResult remoteResult = new PromotionPlanStatusUpdateResult();
        remoteResult.setSuccess(true);
        when(mtPromotionOpenApiClient.updatePlanStatus(
                eq(304L),
                eq("poi-304"),
                eq("token-304"),
                eq(MtPromotionOpenApiClient.STATUS_CLOSE),
                eq(null),
                eq(null),
                eq("MANUAL_SWITCH_DISABLE")
        )).thenReturn(remoteResult);
        when(mtOrderPushControlService.blockOrderPush(eq(accessToken), eq("MANUAL_SWITCH_DISABLE")))
                .thenReturn(MtOrderPushControlService.OrderPushSyncResult.succeeded());

        MtH5PromotionSettingsUpdateParam param = new MtH5PromotionSettingsUpdateParam();
        param.setPromotionEnabled(false);

        MtH5PromotionSettingsVO settingsVO = mtPromotionSettingService.updateCurrentSettings(param);

        assertFalse(settingsVO.getPromotionEnabled());
        verify(mtPromotionOpenApiClient).updatePlanStatus(
                eq(304L),
                eq("poi-304"),
                eq("token-304"),
                eq(MtPromotionOpenApiClient.STATUS_CLOSE),
                eq(null),
                eq(null),
                eq("MANUAL_SWITCH_DISABLE")
        );
        verify(mtOrderPushControlService).blockOrderPush(eq(accessToken), eq("MANUAL_SWITCH_DISABLE"));
        verify(mtPromotionSettingMapper).updateById(any(MtPromotionSetting.class));
    }

    @Test
    void shouldStopOrderPushWhenExplicitCloseIsSubmittedWhileLocalSwitchAlreadyDisabled() {
        MtShop shop = new MtShop();
        shop.setId(307L);
        shop.setPoiId("poi-307");
        when(mtH5ShopService.getCurrentShop()).thenReturn(shop);
        when(mtPromotionSettingMapper.selectByShopId(307L)).thenReturn(buildConfiguredSetting(307L, false, true));
        mockAutoTimeModeAvailability(true);
        MtAccessToken accessToken = new MtAccessToken();
        accessToken.setAccessToken("token-307");
        accessToken.setOpBizCode("op-307");
        accessToken.setOrderPushBlocked(false);
        when(mtAccessTokenService.findByShopId(307L)).thenReturn(accessToken);
        PromotionPlanStatusUpdateResult remoteResult = new PromotionPlanStatusUpdateResult();
        remoteResult.setSuccess(true);
        when(mtPromotionOpenApiClient.updatePlanStatus(
                eq(307L),
                eq("poi-307"),
                eq("token-307"),
                eq(MtPromotionOpenApiClient.STATUS_CLOSE),
                eq(null),
                eq(null),
                eq("MANUAL_SWITCH_DISABLE")
        )).thenReturn(remoteResult);
        when(mtOrderPushControlService.blockOrderPush(eq(accessToken), eq("MANUAL_SWITCH_DISABLE")))
                .thenReturn(MtOrderPushControlService.OrderPushSyncResult.succeeded());

        MtH5PromotionSettingsUpdateParam param = new MtH5PromotionSettingsUpdateParam();
        param.setPromotionEnabled(false);

        MtH5PromotionSettingsVO settingsVO = mtPromotionSettingService.updateCurrentSettings(param);

        assertFalse(settingsVO.getPromotionEnabled());
        verify(mtOrderPushControlService).blockOrderPush(eq(accessToken), eq("MANUAL_SWITCH_DISABLE"));
        verify(mtPromotionSettingMapper).updateById(any(MtPromotionSetting.class));
    }

    @Test
    void shouldRollbackLocalPromotionEnabledWhenRemoteSyncFails() {
        MtShop shop = new MtShop();
        shop.setId(305L);
        shop.setPoiId("poi-305");
        when(mtH5ShopService.getCurrentShop()).thenReturn(shop);
        when(mtPromotionSettingMapper.selectByShopId(305L)).thenReturn(buildConfiguredSetting(305L, false, true));
        mockAutoTimeModeAvailability(true);
        MtAccessToken accessToken = new MtAccessToken();
        accessToken.setAccessToken("token-305");
        when(mtAccessTokenService.findByShopId(305L)).thenReturn(accessToken);
        PromotionPlanStatusUpdateResult remoteResult = new PromotionPlanStatusUpdateResult();
        remoteResult.setSuccess(false);
        remoteResult.setErrorMsg("更新美团推广计划状态失败");
        when(mtPromotionOpenApiClient.updatePlanStatus(
                eq(305L),
                eq("poi-305"),
                eq("token-305"),
                eq(MtPromotionOpenApiClient.STATUS_OPEN),
                eq(null),
                eq(null),
                eq("MANUAL_SWITCH_ENABLE")
        )).thenReturn(remoteResult);

        MtH5PromotionSettingsUpdateParam param = new MtH5PromotionSettingsUpdateParam();
        param.setPromotionEnabled(true);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> mtPromotionSettingService.updateCurrentSettings(param)
        );

        assertEquals(500, exception.getCode());
        assertEquals("更新美团推广计划状态失败", exception.getMessage());
        verify(mtOrderPushControlService, never()).unblockOrderPush(any(), any());
        verify(mtOrderPushControlService, never()).blockOrderPush(any(), any());
        verify(mtPromotionSettingMapper, never()).updateById(any(MtPromotionSetting.class));
    }

    @Test
    void shouldRollbackLocalPromotionEnabledWhenOrderPushSyncFails() {
        MtShop shop = new MtShop();
        shop.setId(306L);
        shop.setPoiId("poi-306");
        when(mtH5ShopService.getCurrentShop()).thenReturn(shop);
        when(mtPromotionSettingMapper.selectByShopId(306L)).thenReturn(buildConfiguredSetting(306L, false, true));
        mockAutoTimeModeAvailability(true);
        MtAccessToken accessToken = new MtAccessToken();
        accessToken.setAccessToken("token-306");
        accessToken.setOpBizCode("op-306");
        when(mtAccessTokenService.findByShopId(306L)).thenReturn(accessToken);
        PromotionPlanStatusUpdateResult remoteResult = new PromotionPlanStatusUpdateResult();
        remoteResult.setSuccess(true);
        when(mtPromotionOpenApiClient.updatePlanStatus(
                eq(306L),
                eq("poi-306"),
                eq("token-306"),
                eq(MtPromotionOpenApiClient.STATUS_OPEN),
                eq(null),
                eq(null),
                eq("MANUAL_SWITCH_ENABLE")
        )).thenReturn(remoteResult);
        when(mtOrderPushControlService.unblockOrderPush(eq(accessToken), eq("MANUAL_SWITCH_ENABLE")))
                .thenReturn(MtOrderPushControlService.OrderPushSyncResult.failure("同步订单推送失败"));

        MtH5PromotionSettingsUpdateParam param = new MtH5PromotionSettingsUpdateParam();
        param.setPromotionEnabled(true);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> mtPromotionSettingService.updateCurrentSettings(param)
        );

        assertEquals(500, exception.getCode());
        assertEquals("同步订单推送失败", exception.getMessage());
        verify(mtPromotionSettingMapper, never()).updateById(any(MtPromotionSetting.class));
    }

    @Test
    void shouldTryUnlockAutoTimeModeWhenEnablingBusinessTimePromotion() {
        MtShop shop = new MtShop();
        shop.setId(308L);
        shop.setPoiId("poi-308");
        when(mtH5ShopService.getCurrentShop()).thenReturn(shop);
        when(mtPromotionSettingMapper.selectByShopId(308L)).thenReturn(buildBusinessTimeConfiguredSetting(308L, false, true));
        mockAutoTimeModeAvailability(false);
        MtAccessToken accessToken = new MtAccessToken();
        accessToken.setAccessToken("token-308");
        accessToken.setOpBizCode("op-308");
        when(mtAccessTokenService.findByShopId(308L)).thenReturn(accessToken);
        PromotionPlanStatusUpdateResult remoteResult = new PromotionPlanStatusUpdateResult();
        remoteResult.setSuccess(true);
        when(mtPromotionOpenApiClient.updatePlanStatus(
                eq(308L),
                eq("poi-308"),
                eq("token-308"),
                eq(MtPromotionOpenApiClient.STATUS_OPEN),
                eq(null),
                eq(null),
                eq("MANUAL_SWITCH_ENABLE")
        )).thenReturn(remoteResult);
        when(mtOrderPushControlService.unblockOrderPush(eq(accessToken), eq("MANUAL_SWITCH_ENABLE")))
                .thenReturn(MtOrderPushControlService.OrderPushSyncResult.succeeded());
        when(mtHistoricalOrderBackfillService.tryBackfillRecentOrdersAndRefreshPeakProfile(
                eq(shop),
                eq("token-308"),
                eq(MtHistoricalOrderBackfillService.TRIGGER_ENABLE_PROMOTION)
        )).thenReturn(MtHistoricalOrderBackfillService.BackfillUnlockResult.completed(
                false,
                "SAMPLE_INSUFFICIENT",
                MtHistoricalOrderBackfillService.UNLOCK_INSUFFICIENT_MESSAGE,
                0,
                0
        ));

        MtH5PromotionSettingsUpdateParam param = new MtH5PromotionSettingsUpdateParam();
        param.setPromotionEnabled(true);

        MtH5PromotionSettingsVO settingsVO = mtPromotionSettingService.updateCurrentSettings(param);

        assertTrue(settingsVO.getPromotionEnabled());
        verify(mtHistoricalOrderBackfillService).tryBackfillRecentOrdersAndRefreshPeakProfile(
                eq(shop),
                eq("token-308"),
                eq(MtHistoricalOrderBackfillService.TRIGGER_ENABLE_PROMOTION)
        );
        verify(mtPromotionSettingMapper).updateById(any(MtPromotionSetting.class));
    }

    @Test
    void shouldNotBlockBusinessTimePromotionWhenUnlockAttemptThrows() {
        MtShop shop = new MtShop();
        shop.setId(309L);
        shop.setPoiId("poi-309");
        when(mtH5ShopService.getCurrentShop()).thenReturn(shop);
        when(mtPromotionSettingMapper.selectByShopId(309L)).thenReturn(buildBusinessTimeConfiguredSetting(309L, false, true));
        mockAutoTimeModeAvailability(false);
        MtAccessToken accessToken = new MtAccessToken();
        accessToken.setAccessToken("token-309");
        accessToken.setOpBizCode("op-309");
        when(mtAccessTokenService.findByShopId(309L)).thenReturn(accessToken);
        PromotionPlanStatusUpdateResult remoteResult = new PromotionPlanStatusUpdateResult();
        remoteResult.setSuccess(true);
        when(mtPromotionOpenApiClient.updatePlanStatus(
                eq(309L),
                eq("poi-309"),
                eq("token-309"),
                eq(MtPromotionOpenApiClient.STATUS_OPEN),
                eq(null),
                eq(null),
                eq("MANUAL_SWITCH_ENABLE")
        )).thenReturn(remoteResult);
        when(mtOrderPushControlService.unblockOrderPush(eq(accessToken), eq("MANUAL_SWITCH_ENABLE")))
                .thenReturn(MtOrderPushControlService.OrderPushSyncResult.succeeded());
        when(mtHistoricalOrderBackfillService.tryBackfillRecentOrdersAndRefreshPeakProfile(any(), any(), any()))
                .thenThrow(new RuntimeException("order api failed"));

        MtH5PromotionSettingsUpdateParam param = new MtH5PromotionSettingsUpdateParam();
        param.setPromotionEnabled(true);

        MtH5PromotionSettingsVO settingsVO = mtPromotionSettingService.updateCurrentSettings(param);

        assertTrue(settingsVO.getPromotionEnabled());
        verify(mtPromotionSettingMapper).updateById(any(MtPromotionSetting.class));
    }

    @Test
    void shouldRejectUpdateWhenNoSupportedFieldProvided() {
        MtH5PromotionSettingsUpdateParam param = new MtH5PromotionSettingsUpdateParam();

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> mtPromotionSettingService.updateCurrentSettings(param)
        );

        assertEquals(400, exception.getCode());
        assertEquals("至少提供一个可更新字段", exception.getMessage());
        verify(mtH5ShopService, never()).getCurrentShop();
    }

    @Test
    void shouldSaveStrategyConfigWhenStrategyIsSubmitted() throws Exception {
        MtShop shop = new MtShop();
        shop.setId(404L);
        when(mtH5ShopService.getCurrentShop()).thenReturn(shop);
        when(mtPromotionSettingMapper.selectByShopId(404L)).thenReturn(buildConfiguredSetting(404L, true, true));
        mockAutoTimeModeAvailability(true);

        MtH5PromotionSettingsUpdateParam param = new MtH5PromotionSettingsUpdateParam();
        param.setStrategyConfigJson("{\"priority\":\"rate\",\"timeMode\":\"auto\",\"rateMode\":\"control\",\"customSlots\":[{\"start\":\"10:00\",\"end\":\"11:00\"}],\"rateMin\":\"5%\",\"rateMax\":\"6%\"}");
        param.setStrategyConfigured(true);

        MtH5PromotionSettingsVO settingsVO = mtPromotionSettingService.updateCurrentSettings(param);

        assertTrue(settingsVO.getStrategyConfigured());
        assertTrue(settingsVO.getStrategyEntryVisible());
        assertNotNull(settingsVO.getStrategyConfigJson());
        assertTrue(settingsVO.getStrategyConfigJson().contains("\"priority\":\"rate\""));
        assertTrue(settingsVO.getStrategyConfigJson().contains("\"rateMin\":\"5%\""));
        MtPromotionSettingService.StrategyConfig savedConfig = objectMapper.readValue(
                settingsVO.getStrategyConfigJson(),
                MtPromotionSettingService.StrategyConfig.class
        );
        assertEquals(new BigDecimal("0.10"), savedConfig.getBidMin());
        assertEquals(new BigDecimal("4.00"), savedConfig.getBidMax());
        verify(mtPromotionOpenApiClient, never()).updatePlanStatus(any(), any(), any(), any(), any(), any(), any());
        verify(mtPromotionSettingMapper).updateById(any(MtPromotionSetting.class));
    }

    @Test
    void shouldRejectAutoTimeModeWhenTrustedPeakWindowUnavailable() {
        MtShop shop = new MtShop();
        shop.setId(406L);
        when(mtH5ShopService.getCurrentShop()).thenReturn(shop);
        when(mtPromotionSettingMapper.selectByShopId(406L)).thenReturn(buildConfiguredSetting(406L, true, true));
        mockAutoTimeModeAvailability(false);

        MtH5PromotionSettingsUpdateParam param = new MtH5PromotionSettingsUpdateParam();
        param.setStrategyConfigJson("{\"priority\":\"rate\",\"timeMode\":\"auto\",\"rateMode\":\"control\",\"customSlots\":[],\"rateMin\":\"5%\",\"rateMax\":\"6%\"}");
        param.setStrategyConfigured(true);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> mtPromotionSettingService.updateCurrentSettings(param)
        );

        assertEquals(400, exception.getCode());
        assertEquals("当前门店暂无可信高峰期配置，请先使用按营业时长", exception.getMessage());
        verify(mtPromotionSettingMapper, never()).updateById(any(MtPromotionSetting.class));
    }

    @Test
    void shouldMergeAdjacentCustomSlotsWhenSavingStrategyConfig() {
        MtShop shop = new MtShop();
        shop.setId(405L);
        when(mtH5ShopService.getCurrentShop()).thenReturn(shop);
        when(mtPromotionSettingMapper.selectByShopId(405L)).thenReturn(buildConfiguredSetting(405L, true, true));
        mockAutoTimeModeAvailability(true);

        MtH5PromotionSettingsUpdateParam param = new MtH5PromotionSettingsUpdateParam();
        param.setStrategyConfigJson(
                "{\"priority\":\"rate\",\"timeMode\":\"custom\",\"rateMode\":\"control\",\"customSlots\":["
                        + "{\"start\":\"10:00\",\"end\":\"11:00\"},{\"start\":\"11:00\",\"end\":\"12:00\"},"
                        + "{\"start\":\"22:00\",\"end\":\"00:00\"},{\"start\":\"00:00\",\"end\":\"02:00\"}"
                        + "],\"rateMin\":\"5%\",\"rateMax\":\"6%\"}"
        );
        param.setStrategyConfigured(true);

        MtH5PromotionSettingsVO settingsVO = mtPromotionSettingService.updateCurrentSettings(param);

        assertTrue(settingsVO.getStrategyConfigJson().contains("\"start\":\"10:00\",\"end\":\"12:00\""));
        assertTrue(settingsVO.getStrategyConfigJson().contains("\"start\":\"22:00\",\"end\":\"02:00\""));
    }

    @Test
    void shouldKeepMinutePrecisionWhenSavingCustomSlots() {
        MtShop shop = new MtShop();
        shop.setId(407L);
        when(mtH5ShopService.getCurrentShop()).thenReturn(shop);
        when(mtPromotionSettingMapper.selectByShopId(407L)).thenReturn(buildConfiguredSetting(407L, true, true));
        mockAutoTimeModeAvailability(true);

        MtH5PromotionSettingsUpdateParam param = new MtH5PromotionSettingsUpdateParam();
        param.setStrategyConfigJson(
                "{\"priority\":\"rate\",\"timeMode\":\"custom\",\"rateMode\":\"control\",\"customSlots\":["
                        + "{\"start\":\"10:15\",\"end\":\"11:45\"}"
                        + "],\"rateMin\":\"5%\",\"rateMax\":\"6%\",\"bidMin\":0.3,\"bidMax\":0.9}"
        );
        param.setStrategyConfigured(true);

        MtH5PromotionSettingsVO settingsVO = mtPromotionSettingService.updateCurrentSettings(param);

        assertTrue(settingsVO.getStrategyConfigJson().contains("\"start\":\"10:15\",\"end\":\"11:45\""));
        assertTrue(settingsVO.getStrategyConfigJson().contains("\"bidMin\":0.30"));
        assertTrue(settingsVO.getStrategyConfigJson().contains("\"bidMax\":0.90"));
    }

    @Test
    void shouldRejectInvalidStrategyRateRange() {
        MtShop shop = new MtShop();
        shop.setId(505L);
        when(mtH5ShopService.getCurrentShop()).thenReturn(shop);
        when(mtPromotionSettingMapper.selectByShopId(505L)).thenReturn(buildConfiguredSetting(505L, true, true));
        mockAutoTimeModeAvailability(true);

        MtH5PromotionSettingsUpdateParam param = new MtH5PromotionSettingsUpdateParam();
        param.setStrategyConfigJson("{\"priority\":\"rate\",\"timeMode\":\"auto\",\"rateMode\":\"control\",\"customSlots\":[],\"rateMin\":\"8%\",\"rateMax\":\"6%\"}");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> mtPromotionSettingService.updateCurrentSettings(param)
        );

        assertEquals(400, exception.getCode());
        assertEquals("推广费率区间不正确", exception.getMessage());
    }

    @Test
    void shouldRejectInvalidStrategyBidRange() {
        MtShop shop = new MtShop();
        shop.setId(507L);
        when(mtH5ShopService.getCurrentShop()).thenReturn(shop);
        when(mtPromotionSettingMapper.selectByShopId(507L)).thenReturn(buildConfiguredSetting(507L, true, true));
        mockAutoTimeModeAvailability(true);

        MtH5PromotionSettingsUpdateParam param = new MtH5PromotionSettingsUpdateParam();
        param.setStrategyConfigJson("{\"priority\":\"rate\",\"timeMode\":\"auto\",\"rateMode\":\"control\",\"customSlots\":[],\"rateMin\":\"5%\",\"rateMax\":\"6%\",\"bidMin\":1.0,\"bidMax\":0.9}");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> mtPromotionSettingService.updateCurrentSettings(param)
        );

        assertEquals(400, exception.getCode());
        assertEquals("AI出价范围不正确", exception.getMessage());
    }

    @Test
    void shouldRejectOverlappedCustomSlots() {
        MtShop shop = new MtShop();
        shop.setId(506L);
        when(mtH5ShopService.getCurrentShop()).thenReturn(shop);
        when(mtPromotionSettingMapper.selectByShopId(506L)).thenReturn(buildConfiguredSetting(506L, true, true));
        mockAutoTimeModeAvailability(true);

        MtH5PromotionSettingsUpdateParam param = new MtH5PromotionSettingsUpdateParam();
        param.setStrategyConfigJson(
                "{\"priority\":\"rate\",\"timeMode\":\"custom\",\"rateMode\":\"control\",\"customSlots\":["
                        + "{\"start\":\"10:00\",\"end\":\"12:00\"},{\"start\":\"11:00\",\"end\":\"13:00\"}"
                        + "],\"rateMin\":\"5%\",\"rateMax\":\"6%\"}"
        );

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> mtPromotionSettingService.updateCurrentSettings(param)
        );

        assertEquals(400, exception.getCode());
        assertEquals("自定义时段不能重叠", exception.getMessage());
    }

    private MtPromotionSetting buildConfiguredSetting(Long shopId, boolean promotionEnabled, boolean balanceReminderEnabled) {
        MtPromotionSetting setting = new MtPromotionSetting();
        setting.setId(shopId);
        setting.setShopId(shopId);
        setting.setPromotionEnabled(promotionEnabled);
        setting.setBalanceReminderEnabled(balanceReminderEnabled);
        setting.setStrategyConfigured(true);
        setting.setStrategyConfigJson("{\"priority\":\"rate\",\"timeMode\":\"auto\",\"rateMode\":\"control\",\"rateMin\":\"5%\",\"rateMax\":\"10%\"}");
        return setting;
    }

    private MtPromotionSetting buildBusinessTimeConfiguredSetting(Long shopId, boolean promotionEnabled, boolean balanceReminderEnabled) {
        MtPromotionSetting setting = buildConfiguredSetting(shopId, promotionEnabled, balanceReminderEnabled);
        setting.setStrategyConfigJson("{\"priority\":\"rate\",\"timeMode\":\"business\",\"rateMode\":\"control\",\"rateMin\":\"5%\",\"rateMax\":\"10%\"}");
        return setting;
    }

    private MtPromotionSetting buildUnconfiguredSetting(Long shopId, boolean promotionEnabled, boolean balanceReminderEnabled) {
        MtPromotionSetting setting = new MtPromotionSetting();
        setting.setId(shopId);
        setting.setShopId(shopId);
        setting.setPromotionEnabled(promotionEnabled);
        setting.setBalanceReminderEnabled(balanceReminderEnabled);
        setting.setStrategyConfigured(false);
        setting.setStrategyConfigJson(null);
        return setting;
    }

    private MtPowerAccount buildPowerAccount(int balance) {
        MtPowerAccount account = new MtPowerAccount();
        account.setBalance(balance);
        return account;
    }

    private void mockAutoTimeModeAvailability(boolean available) {
        mockAutoTimeModeAvailability(available, List.of());
    }

    private void mockAutoTimeModeAvailability(boolean available, List<ShopPeakWindow> peakWindows) {
        ShopPeakWindowProfileLookupResult lookupResult = new ShopPeakWindowProfileLookupResult();
        lookupResult.setProfileStatus(
                available
                        ? ShopPeakWindowProfileLookupResult.STATUS_USABLE
                        : ShopPeakWindowProfileLookupResult.STATUS_UNAVAILABLE
        );
        lookupResult.setDayType(ShopPeakDayType.WEEKDAY);
        lookupResult.setPeakWindows(peakWindows);
        lenient().when(shopPeakWindowProfileService.lookup(anyLong(), any()))
                .thenReturn(lookupResult);
    }

}
