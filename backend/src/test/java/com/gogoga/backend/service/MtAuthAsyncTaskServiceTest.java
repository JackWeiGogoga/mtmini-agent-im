package com.gogoga.backend.service;

import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.client.MtImConfigurationClient;
import com.gogoga.backend.entity.mt.MtAccessToken;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.enums.MtAsyncTaskTypeEnum;
import com.gogoga.backend.service.async.MtAsyncTaskEnqueueResult;
import com.gogoga.backend.service.async.MtAuthPostProcessTaskPayload;
import com.gogoga.backend.param.ShopOperationSettingsUpdateParam;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.inOrder;
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
    private MtOrderPushControlService mtOrderPushControlService;

    @Mock
    private MtImConfigurationClient mtImConfigurationClient;

    @Mock
    private ShopOperationSettingsService shopOperationSettingsService;

    @InjectMocks
    private MtAuthAsyncTaskService mtAuthAsyncTaskService;

    @BeforeEach
    void setUp() {
        lenient().when(mtProperties.getAsyncTask()).thenReturn(new MtProperties.AsyncTask());
        lenient().when(mtAsyncTaskService.enqueueTask(any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(new MtAsyncTaskEnqueueResult(1L, true));
    }

    @Test
    void shouldEnqueuePoiIdBackfillAsPostAuthorizationTask() {
        mtAuthAsyncTaskService.enqueuePostAuthorizationTasks(1L, "access-token");

        ArgumentCaptor<MtAsyncTaskTypeEnum> typeCaptor = ArgumentCaptor.forClass(MtAsyncTaskTypeEnum.class);
        verify(mtAsyncTaskService, times(4)).enqueueTask(
                typeCaptor.capture(),
                any(),
                eq("1"),
                any(MtAuthPostProcessTaskPayload.class),
                any(),
                anyInt()
        );
        assertEquals(List.of(
                MtAsyncTaskTypeEnum.AUTH_BLOCK_ORDER_PUSH,
                MtAsyncTaskTypeEnum.AUTH_BACKFILL_POI_ID,
                MtAsyncTaskTypeEnum.AUTH_SYNC_RIGHTS,
                MtAsyncTaskTypeEnum.AUTH_CONFIGURE_IM
        ), typeCaptor.getAllValues());
    }

    @Test
    void repeatedSameAuthorizationUsesTheSameIdempotencyKeyForEachTaskType() {
        mtAuthAsyncTaskService.enqueuePostAuthorizationTasks(1L, "access-token");
        mtAuthAsyncTaskService.enqueuePostAuthorizationTasks(1L, "access-token");

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(mtAsyncTaskService, times(8)).enqueueTask(
                any(), keyCaptor.capture(), eq("1"), any(), any(), anyInt());
        assertEquals(keyCaptor.getAllValues().subList(0, 4), keyCaptor.getAllValues().subList(4, 8));
    }

    @Test
    void shouldExecutePoiIdBackfillWithCurrentAccessToken() {
        MtShop shop = new MtShop();
        shop.setId(1L);
        MtAccessToken token = new MtAccessToken();
        token.setShopId(1L);
        token.setAccessToken("current-token");
        MtAuthPostProcessTaskPayload payload = new MtAuthPostProcessTaskPayload();
        payload.setShopId(1L);

        when(mtShopService.findById(1L)).thenReturn(shop);
        when(mtAccessTokenService.findByShopId(1L)).thenReturn(token);

        mtAuthAsyncTaskService.executeAuthBackfillPoiIdTask(payload);

        verify(mtShopPoiIdBackfillService).tryBackfillAfterAuthorization(shop, "current-token");
    }

    @Test
    void shouldConfigureImInFixedOrderAndEnableLocalSwitchOnlyAfterBothWritesSucceed() {
        MtAuthPostProcessTaskPayload payload = prepareContext();
        when(mtImConfigurationClient.enablePoiIm("current-token"))
                .thenReturn(new MtImConfigurationClient.ImConfigurationResult(
                        true, "SET_POI_IM_STATUS", null, "safe"));
        when(mtImConfigurationClient.disableAllSmartReplies("current-token"))
                .thenReturn(new MtImConfigurationClient.ImConfigurationResult(
                        true, "SET_AUTO_SEND_CONFIG", null, "safe"));

        mtAuthAsyncTaskService.executeAuthConfigureImTask(payload);

        InOrder order = inOrder(mtImConfigurationClient, shopOperationSettingsService);
        order.verify(mtImConfigurationClient).enablePoiIm("current-token");
        order.verify(mtImConfigurationClient).disableAllSmartReplies("current-token");
        ArgumentCaptor<ShopOperationSettingsUpdateParam> settings =
                ArgumentCaptor.forClass(ShopOperationSettingsUpdateParam.class);
        order.verify(shopOperationSettingsService).updateSettings(
                eq(1L), settings.capture(), eq(ShopOperationSettingsService.CHANGE_SOURCE_AUTH),
                eq("AUTH_IM_CONFIGURED"));
        assertEquals(true, settings.getValue().getAutoReplyEnabled());
    }

    @Test
    void retryRepeatsOnlyTheTwoFixedWritesAndReliesOnLocalSettingIdempotency() {
        MtAuthPostProcessTaskPayload payload = prepareContext();
        when(mtImConfigurationClient.enablePoiIm("current-token"))
                .thenReturn(new MtImConfigurationClient.ImConfigurationResult(
                        true, "SET_POI_IM_STATUS", null, "safe"));
        when(mtImConfigurationClient.disableAllSmartReplies("current-token"))
                .thenReturn(new MtImConfigurationClient.ImConfigurationResult(
                        true, "SET_AUTO_SEND_CONFIG", null, "safe"));

        mtAuthAsyncTaskService.executeAuthConfigureImTask(payload);
        mtAuthAsyncTaskService.executeAuthConfigureImTask(payload);

        verify(mtImConfigurationClient, times(2)).enablePoiIm("current-token");
        verify(mtImConfigurationClient, times(2)).disableAllSmartReplies("current-token");
        verify(shopOperationSettingsService, times(2)).updateSettings(
                eq(1L), any(ShopOperationSettingsUpdateParam.class),
                eq(ShopOperationSettingsService.CHANGE_SOURCE_AUTH), eq("AUTH_IM_CONFIGURED"));
    }

    @Test
    void shouldKeepLocalSwitchOffWhenFirstRemoteWriteFails() {
        MtAuthPostProcessTaskPayload payload = prepareContext();
        when(mtImConfigurationClient.enablePoiIm("current-token"))
                .thenReturn(new MtImConfigurationClient.ImConfigurationResult(
                        false, "SET_POI_IM_STATUS", "FAILED", "safe"));

        assertThrows(IllegalStateException.class,
                () -> mtAuthAsyncTaskService.executeAuthConfigureImTask(payload));

        verify(mtImConfigurationClient, never()).disableAllSmartReplies(any());
        ArgumentCaptor<ShopOperationSettingsUpdateParam> settings =
                ArgumentCaptor.forClass(ShopOperationSettingsUpdateParam.class);
        verify(shopOperationSettingsService).updateSettings(
                eq(1L), settings.capture(), eq(ShopOperationSettingsService.CHANGE_SOURCE_AUTH),
                eq("AUTH_IM_CONFIG_FAILED:SET_POI_IM_STATUS"));
        assertEquals(false, settings.getValue().getAutoReplyEnabled());
    }

    @Test
    void shouldKeepLocalSwitchOffWhenSecondRemoteWriteFails() {
        MtAuthPostProcessTaskPayload payload = prepareContext();
        when(mtImConfigurationClient.enablePoiIm("current-token"))
                .thenReturn(new MtImConfigurationClient.ImConfigurationResult(
                        true, "SET_POI_IM_STATUS", null, "safe"));
        when(mtImConfigurationClient.disableAllSmartReplies("current-token"))
                .thenReturn(new MtImConfigurationClient.ImConfigurationResult(
                        false, "SET_AUTO_SEND_CONFIG", "FAILED", "safe"));

        assertThrows(IllegalStateException.class,
                () -> mtAuthAsyncTaskService.executeAuthConfigureImTask(payload));

        ArgumentCaptor<ShopOperationSettingsUpdateParam> settings =
                ArgumentCaptor.forClass(ShopOperationSettingsUpdateParam.class);
        verify(shopOperationSettingsService).updateSettings(
                eq(1L), settings.capture(), eq(ShopOperationSettingsService.CHANGE_SOURCE_AUTH),
                eq("AUTH_IM_CONFIG_FAILED:SET_AUTO_SEND_CONFIG"));
        assertEquals(false, settings.getValue().getAutoReplyEnabled());
    }

    private MtAuthPostProcessTaskPayload prepareContext() {
        MtShop shop = new MtShop();
        shop.setId(1L);
        MtAccessToken token = new MtAccessToken();
        token.setShopId(1L);
        token.setAccessToken("current-token");
        MtAuthPostProcessTaskPayload payload = new MtAuthPostProcessTaskPayload();
        payload.setShopId(1L);
        when(mtShopService.findById(1L)).thenReturn(shop);
        when(mtAccessTokenService.findByShopId(1L)).thenReturn(token);
        return payload;
    }
}
