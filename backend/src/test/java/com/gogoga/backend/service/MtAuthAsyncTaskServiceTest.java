package com.gogoga.backend.service;

import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.entity.mt.MtAccessToken;
import com.gogoga.backend.entity.mt.MtPromotionSetting;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.enums.MtAsyncTaskTypeEnum;
import com.gogoga.backend.mapper.MtPromotionSettingMapper;
import com.gogoga.backend.service.async.MtAuthPostProcessTaskPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MtAuthAsyncTaskServiceTest {

    @Mock
    private MtAsyncTaskService mtAsyncTaskService;

    @Mock
    private MtProperties mtProperties;

    @Mock
    private MtAccessTokenService mtAccessTokenService;

    @Mock
    private MtShopService mtShopService;

    @Mock
    private MtShopRightService mtShopRightService;

    @Mock
    private MtShopPoiIdBackfillService mtShopPoiIdBackfillService;

    @Mock
    private MtPromotionAdDataService mtPromotionAdDataService;

    @Mock
    private MtHistoricalOrderBackfillService mtHistoricalOrderBackfillService;

    @Mock
    private MtOrderPushControlService mtOrderPushControlService;

    @Mock
    private MtPromotionSettingMapper mtPromotionSettingMapper;

    @InjectMocks
    private MtAuthAsyncTaskService mtAuthAsyncTaskService;

    @BeforeEach
    void setUp() {
        MtProperties.Promotion promotion = new MtProperties.Promotion();
        MtProperties.AsyncTask asyncTask = new MtProperties.AsyncTask();
        promotion.setAsyncTask(asyncTask);
        lenient().when(mtProperties.getPromotion()).thenReturn(promotion);
        lenient().when(mtAsyncTaskService.enqueueTask(any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(new com.gogoga.backend.service.async.MtAsyncTaskEnqueueResult(1L, true));
    }

    @Test
    void shouldEnqueueBlockOrderPushTaskForNewAuthorizationWithoutRetry() {
        mtAuthAsyncTaskService.enqueuePostAuthorizationTasks(1L, "access-token", true);

        ArgumentCaptor<MtAsyncTaskTypeEnum> typeCaptor = ArgumentCaptor.forClass(MtAsyncTaskTypeEnum.class);
        ArgumentCaptor<Integer> retryCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(mtAsyncTaskService, times(4)).enqueueTask(
                typeCaptor.capture(),
                any(),
                eq("1"),
                any(MtAuthPostProcessTaskPayload.class),
                any(),
                retryCaptor.capture()
        );
        assertEquals(
                java.util.List.of(
                        MtAsyncTaskTypeEnum.AUTH_BLOCK_ORDER_PUSH,
                        MtAsyncTaskTypeEnum.AUTH_BACKFILL_POI_ID,
                        MtAsyncTaskTypeEnum.AUTH_SYNC_RIGHTS,
                        MtAsyncTaskTypeEnum.AUTH_SYNC_RECENT_AD_DATA
                ),
                typeCaptor.getAllValues()
        );
        assertEquals(0, retryCaptor.getAllValues().get(0));
        verify(mtShopService, never()).tryMarkHistoricalOrderBackfillProcessing(any());
    }

    @Test
    void shouldNotEnqueueBlockOrderPushTaskForRepeatedAuthorization() {
        mtAuthAsyncTaskService.enqueuePostAuthorizationTasks(1L, "access-token", false);

        ArgumentCaptor<MtAsyncTaskTypeEnum> typeCaptor = ArgumentCaptor.forClass(MtAsyncTaskTypeEnum.class);
        verify(mtAsyncTaskService, times(3)).enqueueTask(
                typeCaptor.capture(),
                any(),
                eq("1"),
                any(MtAuthPostProcessTaskPayload.class),
                any(),
                anyInt()
        );
        org.junit.jupiter.api.Assertions.assertFalse(
                typeCaptor.getAllValues().contains(MtAsyncTaskTypeEnum.AUTH_BLOCK_ORDER_PUSH)
        );
        org.junit.jupiter.api.Assertions.assertFalse(
                typeCaptor.getAllValues().contains(MtAsyncTaskTypeEnum.AUTH_HISTORICAL_ORDER_BACKFILL)
        );
        verify(mtShopService, never()).tryMarkHistoricalOrderBackfillProcessing(any());
    }

    @Test
    void shouldExecuteRightsTaskWithCurrentAccessToken() {
        MtShop shop = new MtShop();
        shop.setId(1L);
        MtAccessToken token = new MtAccessToken();
        token.setAccessToken("current-token");
        MtAuthPostProcessTaskPayload payload = new MtAuthPostProcessTaskPayload();
        payload.setShopId(1L);

        when(mtShopService.findById(1L)).thenReturn(shop);
        when(mtAccessTokenService.findByShopId(1L)).thenReturn(token);

        mtAuthAsyncTaskService.executeAuthSyncRightsTask(payload);

        verify(mtShopRightService).syncRightsFromBuyInfo(shop, "current-token");
    }

    @Test
    void shouldBlockOrderPushWhenPromotionSwitchIsNotEnabled() {
        MtAuthPostProcessTaskPayload payload = new MtAuthPostProcessTaskPayload();
        payload.setShopId(1L);
        MtShop shop = new MtShop();
        shop.setId(1L);
        MtAccessToken token = new MtAccessToken();
        token.setId(10L);
        token.setShopId(1L);
        token.setAccessToken("current-token");
        token.setOpBizCode("op-1");
        MtPromotionSetting setting = new MtPromotionSetting();
        setting.setPromotionEnabled(false);

        when(mtShopService.findById(1L)).thenReturn(shop);
        when(mtAccessTokenService.findByShopId(1L)).thenReturn(token);
        when(mtPromotionSettingMapper.selectByShopId(1L)).thenReturn(setting);
        when(mtOrderPushControlService.blockOrderPush(token, "INITIAL_AUTH"))
                .thenReturn(MtOrderPushControlService.OrderPushSyncResult.succeeded());

        mtAuthAsyncTaskService.executeAuthBlockOrderPushTask(payload);

        verify(mtOrderPushControlService).blockOrderPush(token, "INITIAL_AUTH");
    }

    @Test
    void shouldSkipBlockOrderPushWhenPromotionSwitchIsEnabled() {
        MtAuthPostProcessTaskPayload payload = new MtAuthPostProcessTaskPayload();
        payload.setShopId(1L);
        MtShop shop = new MtShop();
        shop.setId(1L);
        MtAccessToken token = new MtAccessToken();
        token.setAccessToken("current-token");
        MtPromotionSetting setting = new MtPromotionSetting();
        setting.setPromotionEnabled(true);

        when(mtShopService.findById(1L)).thenReturn(shop);
        when(mtAccessTokenService.findByShopId(1L)).thenReturn(token);
        when(mtPromotionSettingMapper.selectByShopId(1L)).thenReturn(setting);

        mtAuthAsyncTaskService.executeAuthBlockOrderPushTask(payload);

        verify(mtOrderPushControlService, never()).blockOrderPush(any(), any());
    }

    @Test
    void shouldKeepTaskNonThrowingWhenBlockOrderPushReturnsFailure() {
        MtAuthPostProcessTaskPayload payload = new MtAuthPostProcessTaskPayload();
        payload.setShopId(1L);
        MtShop shop = new MtShop();
        shop.setId(1L);
        MtAccessToken token = new MtAccessToken();
        token.setId(10L);
        token.setShopId(1L);
        token.setAccessToken("current-token");
        token.setOpBizCode("op-1");

        when(mtShopService.findById(1L)).thenReturn(shop);
        when(mtAccessTokenService.findByShopId(1L)).thenReturn(token);
        when(mtPromotionSettingMapper.selectByShopId(1L)).thenReturn(null);
        when(mtOrderPushControlService.blockOrderPush(token, "INITIAL_AUTH"))
                .thenReturn(MtOrderPushControlService.OrderPushSyncResult.failure("push failed"));

        mtAuthAsyncTaskService.executeAuthBlockOrderPushTask(payload);

        verify(mtOrderPushControlService).blockOrderPush(token, "INITIAL_AUTH");
    }
}
