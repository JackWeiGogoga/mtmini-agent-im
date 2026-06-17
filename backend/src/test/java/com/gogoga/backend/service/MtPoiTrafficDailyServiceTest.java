package com.gogoga.backend.service;

import com.gogoga.backend.entity.mt.MtAccessToken;
import com.gogoga.backend.entity.mt.MtPoiTrafficDaily;
import com.gogoga.backend.mapper.MtAccessTokenMapper;
import com.gogoga.backend.mapper.MtPoiTrafficDailyMapper;
import com.meituan.sdk.MeituanClient;
import com.meituan.sdk.MeituanResponse;
import com.meituan.sdk.model.wmoperNg.waimaiad.decrypt.DecryptResponse;
import com.meituan.sdk.model.wmoperNg.waimaiad.poiAdEffectdata.Item;
import com.meituan.sdk.model.wmoperNg.waimaiad.poiAdEffectdata.PoiAdEffectdataResponse;
import com.meituan.sdk.model.wmoperNg.waimaiad.poiAdEffectdata.Prop;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 门店营业分析日流量服务测试
 *
 * @author system
 * @since 2026-04-10
 */
@ExtendWith(MockitoExtension.class)
class MtPoiTrafficDailyServiceTest {

    @Mock
    private MtAccessTokenMapper mtAccessTokenMapper;

    @Mock
    private MtPoiTrafficDailyMapper mtPoiTrafficDailyMapper;

    @Mock
    private MeituanClient meituanClient;

    @Mock
    private MtAccessTokenService mtAccessTokenService;

    @Mock
    private MtPromotionSettingService mtPromotionSettingService;

    private MtPoiTrafficDailyService mtPoiTrafficDailyService;

    @BeforeEach
    void setUp() {
        MtWaimaiAdDecryptService mtWaimaiAdDecryptService = new MtWaimaiAdDecryptService(meituanClient);
        MtMeituanAuthorizationFailureHandler authFailureHandler = new MtMeituanAuthorizationFailureHandler(
                mtAccessTokenService,
                mtPromotionSettingService
        );
        mtPoiTrafficDailyService = new MtPoiTrafficDailyService(
                mtAccessTokenMapper,
                mtPoiTrafficDailyMapper,
                meituanClient,
                mtWaimaiAdDecryptService,
                authFailureHandler
        );
    }

    @Test
    void shouldSyncTrafficAndDecryptEncryptedMetrics() throws Exception {
        MtAccessToken token = new MtAccessToken();
        token.setShopId(1001L);
        token.setAccessToken("token");
        when(mtAccessTokenMapper.selectTrafficSyncCandidates(any(LocalDateTime.class))).thenReturn(List.of(token));
        AtomicInteger poiTrafficCallIndex = new AtomicInteger();
        when(meituanClient.invokeApi(
                any(com.meituan.sdk.model.wmoperNg.waimaiad.poiAdEffectdata.PoiAdEffectdataRequest.class),
                eq("token")
        )).thenAnswer(invocation -> switch (poiTrafficCallIndex.getAndIncrement()) {
            case 0 -> buildPoiTrafficResponse(MtPoiTrafficDailyService.FLOW_PROP_NATURAL, "encrypted-show-1", "encrypted-click-1");
            case 1 -> buildPoiTrafficResponse(MtPoiTrafficDailyService.FLOW_PROP_PAID, "encrypted-show-2", "encrypted-click-2");
            case 2 -> buildPoiTrafficResponse(MtPoiTrafficDailyService.FLOW_PROP_ALL, "encrypted-show-3", "encrypted-click-3");
            default -> throw new IllegalStateException("unexpected poi traffic invoke count");
        });
        AtomicInteger decryptCallIndex = new AtomicInteger();
        when(meituanClient.invokeApi(
                any(com.meituan.sdk.model.wmoperNg.waimaiad.decrypt.DecryptRequest.class),
                eq("token")
        )).thenAnswer(invocation -> switch (decryptCallIndex.getAndIncrement()) {
            case 0 -> buildDecryptResponse("12");
            case 1 -> buildDecryptResponse("4");
            case 2 -> buildDecryptResponse("23");
            case 3 -> buildDecryptResponse("5");
            case 4 -> buildDecryptResponse("123");
            case 5 -> buildDecryptResponse("45");
            default -> throw new IllegalStateException("unexpected decrypt invoke count");
        });

        mtPoiTrafficDailyService.syncTrafficData(LocalDate.of(2026, 4, 9));

        ArgumentCaptor<MtPoiTrafficDaily> recordCaptor = ArgumentCaptor.forClass(MtPoiTrafficDaily.class);
        verify(mtPoiTrafficDailyMapper, times(3)).insertOrUpdate(recordCaptor.capture());
        List<MtPoiTrafficDaily> records = recordCaptor.getAllValues();
        assertEquals(3, records.size());
        assertEquals(MtPoiTrafficDailyService.FLOW_PROP_NATURAL, records.get(0).getFlowProp());
        assertEquals(12, records.get(0).getExposureCount());
        assertEquals(4, records.get(0).getVisitCount());
        assertEquals(MtPoiTrafficDailyService.FLOW_PROP_PAID, records.get(1).getFlowProp());
        assertEquals(23, records.get(1).getExposureCount());
        assertEquals(5, records.get(1).getVisitCount());
        assertEquals(MtPoiTrafficDailyService.FLOW_PROP_ALL, records.get(2).getFlowProp());
        assertEquals(123, records.get(2).getExposureCount());
        assertEquals(45, records.get(2).getVisitCount());
        assertTrue(records.get(2).getPayloadJson().contains("mt.wmoper.ng.waimaiad.poi.effectdata"));
    }

    @Test
    void shouldUsePlainNumericMetricsWithoutDecrypt() throws Exception {
        MtAccessToken token = new MtAccessToken();
        token.setShopId(1002L);
        token.setAccessToken("token");
        when(mtAccessTokenMapper.selectTrafficSyncCandidates(any(LocalDateTime.class))).thenReturn(List.of(token));
        AtomicInteger poiTrafficCallIndex = new AtomicInteger();
        when(meituanClient.invokeApi(
                any(com.meituan.sdk.model.wmoperNg.waimaiad.poiAdEffectdata.PoiAdEffectdataRequest.class),
                eq("token")
        )).thenAnswer(invocation -> switch (poiTrafficCallIndex.getAndIncrement()) {
            case 0 -> buildPoiTrafficResponseWithPlainMetrics(MtPoiTrafficDailyService.FLOW_PROP_NATURAL, "11", "1");
            case 1 -> buildPoiTrafficResponseWithPlainMetrics(MtPoiTrafficDailyService.FLOW_PROP_PAID, "22", "2");
            case 2 -> buildPoiTrafficResponseWithPlainMetrics(MtPoiTrafficDailyService.FLOW_PROP_ALL, "88", "12");
            default -> throw new IllegalStateException("unexpected poi traffic invoke count");
        });

        mtPoiTrafficDailyService.syncTrafficData(LocalDate.of(2026, 4, 9));

        ArgumentCaptor<MtPoiTrafficDaily> recordCaptor = ArgumentCaptor.forClass(MtPoiTrafficDaily.class);
        verify(mtPoiTrafficDailyMapper, times(3)).insertOrUpdate(recordCaptor.capture());
        List<MtPoiTrafficDaily> records = recordCaptor.getAllValues();
        assertEquals(11, records.get(0).getExposureCount());
        assertEquals(1, records.get(0).getVisitCount());
        assertEquals(22, records.get(1).getExposureCount());
        assertEquals(2, records.get(1).getVisitCount());
        assertEquals(88, records.get(2).getExposureCount());
        assertEquals(12, records.get(2).getVisitCount());
    }

    @Test
    void shouldShortCircuitKnownZeroCiphertextWhenSyncTraffic() throws Exception {
        MtAccessToken token = new MtAccessToken();
        token.setShopId(1003L);
        token.setAccessToken("token");
        when(mtAccessTokenMapper.selectTrafficSyncCandidates(any(LocalDateTime.class))).thenReturn(List.of(token));
        when(meituanClient.invokeApi(
                any(com.meituan.sdk.model.wmoperNg.waimaiad.poiAdEffectdata.PoiAdEffectdataRequest.class),
                eq("token")
        )).thenAnswer(invocation -> switch (invocation.getArgument(0).getClass().getSimpleName()) {
            default -> buildPoiTrafficResponse(
                    MtPoiTrafficDailyService.FLOW_PROP_NATURAL,
                    "UdK7p6F4o8KhAXeeeni8Pw==",
                    "UdK7p6F4o8KhAXeeeni8Pw=="
            );
        });

        mtPoiTrafficDailyService.syncTrafficData(LocalDate.of(2026, 4, 9));

        verify(meituanClient, never()).invokeApi(
                any(com.meituan.sdk.model.wmoperNg.waimaiad.decrypt.DecryptRequest.class),
                eq("token")
        );
    }

    @Test
    void shouldMarkReauthAndContinueOtherShopsWhenTrafficAuthFailurePersists() throws Exception {
        MtAccessToken badToken = new MtAccessToken();
        badToken.setShopId(2001L);
        badToken.setAccessToken("bad-token");
        MtAccessToken goodToken = new MtAccessToken();
        goodToken.setShopId(2002L);
        goodToken.setAccessToken("good-token");
        when(mtAccessTokenMapper.selectTrafficSyncCandidates(any(LocalDateTime.class))).thenReturn(List.of(badToken, goodToken));
        when(mtAccessTokenService.refreshTokenForShopWithResult(2001L))
                .thenReturn(MtAccessTokenService.TokenRefreshResult.failure(10009, "refresh failed", "REMOTE_REFRESH_FAILED"));
        when(meituanClient.invokeApi(
                any(com.meituan.sdk.model.wmoperNg.waimaiad.poiAdEffectdata.PoiAdEffectdataRequest.class),
                eq("bad-token")
        )).thenReturn(buildPoiTrafficAuthFailureResponse());
        when(meituanClient.invokeApi(
                any(com.meituan.sdk.model.wmoperNg.waimaiad.poiAdEffectdata.PoiAdEffectdataRequest.class),
                eq("good-token")
        )).thenAnswer(invocation -> buildPoiTrafficResponseWithPlainMetrics(
                invocation.<com.meituan.sdk.model.wmoperNg.waimaiad.poiAdEffectdata.PoiAdEffectdataRequest>getArgument(0).getFlowProp(),
                "11",
                "1"
        ));

        mtPoiTrafficDailyService.syncTrafficData(LocalDate.of(2026, 4, 9));

        verify(mtAccessTokenService).markReauthRequired(2001L);
        verify(mtPromotionSettingService).disablePromotionByShopId(
                2001L,
                MtMeituanAuthorizationFailureHandler.CHANGE_REASON_AUTH_FAILURE
        );
        verify(mtPoiTrafficDailyMapper, times(3)).insertOrUpdate(any(MtPoiTrafficDaily.class));
    }

    @Test
    void shouldRetryTrafficWithRefreshedTokenAndKeepAuthorizationValid() throws Exception {
        MtAccessToken token = new MtAccessToken();
        token.setShopId(2003L);
        token.setAccessToken("old-token");
        MtAccessToken refreshedToken = new MtAccessToken();
        refreshedToken.setShopId(2003L);
        refreshedToken.setAccessToken("new-token");
        when(mtAccessTokenMapper.selectTrafficSyncCandidates(any(LocalDateTime.class))).thenReturn(List.of(token));
        when(mtAccessTokenService.refreshTokenForShopWithResult(2003L))
                .thenReturn(MtAccessTokenService.TokenRefreshResult.success(refreshedToken, 0, "success"));
        when(meituanClient.invokeApi(
                any(com.meituan.sdk.model.wmoperNg.waimaiad.poiAdEffectdata.PoiAdEffectdataRequest.class),
                eq("old-token")
        )).thenReturn(buildPoiTrafficAuthFailureResponse());
        when(meituanClient.invokeApi(
                any(com.meituan.sdk.model.wmoperNg.waimaiad.poiAdEffectdata.PoiAdEffectdataRequest.class),
                eq("new-token")
        )).thenAnswer(invocation -> buildPoiTrafficResponseWithPlainMetrics(
                invocation.<com.meituan.sdk.model.wmoperNg.waimaiad.poiAdEffectdata.PoiAdEffectdataRequest>getArgument(0).getFlowProp(),
                "22",
                "2"
        ));

        mtPoiTrafficDailyService.syncTrafficData(LocalDate.of(2026, 4, 9));

        verify(mtAccessTokenService, never()).markReauthRequired(any());
        verify(mtPromotionSettingService, never()).disablePromotionByShopId(any(), any());
        verify(meituanClient).invokeApi(
                any(com.meituan.sdk.model.wmoperNg.waimaiad.poiAdEffectdata.PoiAdEffectdataRequest.class),
                eq("old-token")
        );
        verify(meituanClient, times(3)).invokeApi(
                any(com.meituan.sdk.model.wmoperNg.waimaiad.poiAdEffectdata.PoiAdEffectdataRequest.class),
                eq("new-token")
        );
        verify(mtPoiTrafficDailyMapper, times(3)).insertOrUpdate(any(MtPoiTrafficDaily.class));
    }

    @Test
    void shouldNotMarkReauthWhenTrafficFailureIsNotAuthFailure() throws Exception {
        MtAccessToken token = new MtAccessToken();
        token.setShopId(2004L);
        token.setAccessToken("token");
        when(mtAccessTokenMapper.selectTrafficSyncCandidates(any(LocalDateTime.class))).thenReturn(List.of(token));
        when(meituanClient.invokeApi(
                any(com.meituan.sdk.model.wmoperNg.waimaiad.poiAdEffectdata.PoiAdEffectdataRequest.class),
                eq("token")
        )).thenReturn(buildPoiTrafficBusinessFailureResponse("SYSTEM_BUSY", "系统繁忙"));

        mtPoiTrafficDailyService.syncTrafficData(LocalDate.of(2026, 4, 9));

        verify(mtAccessTokenService, never()).refreshTokenForShopWithResult(any());
        verify(mtAccessTokenService, never()).markReauthRequired(any());
        verify(mtPromotionSettingService, never()).disablePromotionByShopId(any(), any());
        verify(mtPoiTrafficDailyMapper, never()).insertOrUpdate(any());
    }

    @Test
    void shouldNotHandleDecryptAuthFailureInTrafficSync() throws Exception {
        MtAccessToken token = new MtAccessToken();
        token.setShopId(2005L);
        token.setAccessToken("token");
        when(mtAccessTokenMapper.selectTrafficSyncCandidates(any(LocalDateTime.class))).thenReturn(List.of(token));
        when(meituanClient.invokeApi(
                any(com.meituan.sdk.model.wmoperNg.waimaiad.poiAdEffectdata.PoiAdEffectdataRequest.class),
                eq("token")
        )).thenReturn(buildPoiTrafficResponse(MtPoiTrafficDailyService.FLOW_PROP_NATURAL, "encrypted-show", "1"));
        when(meituanClient.invokeApi(
                any(com.meituan.sdk.model.wmoperNg.waimaiad.decrypt.DecryptRequest.class),
                eq("token")
        )).thenReturn(buildDecryptAuthFailureResponse());

        mtPoiTrafficDailyService.syncTrafficData(LocalDate.of(2026, 4, 9));

        verify(mtAccessTokenService, never()).refreshTokenForShopWithResult(any());
        verify(mtAccessTokenService, never()).markReauthRequired(any());
        verify(mtPromotionSettingService, never()).disablePromotionByShopId(any(), any());
        verify(mtPoiTrafficDailyMapper, never()).insertOrUpdate(any());
    }

    private MeituanResponse<PoiAdEffectdataResponse> buildPoiTrafficResponse(int flowProp, String show, String click) {
        com.meituan.sdk.model.wmoperNg.waimaiad.poiAdEffectdata.Result result =
                new com.meituan.sdk.model.wmoperNg.waimaiad.poiAdEffectdata.Result();
        result.setSuccess(true);
        result.setCode("0");
        result.setMessage("success");

        Prop prop = new Prop();
        prop.setFlowProp(flowProp);
        prop.setPos("all");
        prop.setShow(show);
        prop.setClick(click);

        Item item = new Item();
        item.setDt(20260409L);
        item.setData(List.of(prop));

        PoiAdEffectdataResponse payload = new PoiAdEffectdataResponse();
        payload.setResult(result);
        payload.setData(List.of(item));

        MeituanResponse<PoiAdEffectdataResponse> response = new MeituanResponse<>();
        response.setCode("OP_SUCCESS");
        response.setMsg("success");
        response.setData(payload);
        return response;
    }

    private MeituanResponse<PoiAdEffectdataResponse> buildPoiTrafficResponseWithPlainMetrics(int flowProp,
                                                                                            String show,
                                                                                            String click) {
        com.meituan.sdk.model.wmoperNg.waimaiad.poiAdEffectdata.Result result =
                new com.meituan.sdk.model.wmoperNg.waimaiad.poiAdEffectdata.Result();
        result.setSuccess(true);
        result.setCode("0");
        result.setMessage("success");

        Prop prop = new Prop();
        prop.setFlowProp(flowProp);
        prop.setPos("all");
        prop.setShow(show);
        prop.setClick(click);

        Item item = new Item();
        item.setDt(20260409L);
        item.setData(List.of(prop));

        PoiAdEffectdataResponse payload = new PoiAdEffectdataResponse();
        payload.setResult(result);
        payload.setData(List.of(item));

        MeituanResponse<PoiAdEffectdataResponse> response = new MeituanResponse<>();
        response.setCode("OP_SUCCESS");
        response.setMsg("success");
        response.setData(payload);
        return response;
    }

    private MeituanResponse<PoiAdEffectdataResponse> buildPoiTrafficAuthFailureResponse() {
        return buildPoiTrafficBusinessFailureResponse("OP_UNIAUTH_FAILED", "鉴权失败");
    }

    private MeituanResponse<PoiAdEffectdataResponse> buildPoiTrafficBusinessFailureResponse(String code, String message) {
        com.meituan.sdk.model.wmoperNg.waimaiad.poiAdEffectdata.Result result =
                new com.meituan.sdk.model.wmoperNg.waimaiad.poiAdEffectdata.Result();
        result.setSuccess(false);
        result.setCode(code);
        result.setMessage(message);

        PoiAdEffectdataResponse payload = new PoiAdEffectdataResponse();
        payload.setResult(result);

        MeituanResponse<PoiAdEffectdataResponse> response = new MeituanResponse<>();
        response.setCode("OP_SUCCESS");
        response.setMsg("success");
        response.setData(payload);
        return response;
    }

    private MeituanResponse<DecryptResponse> buildDecryptResponse(String value) {
        com.meituan.sdk.model.wmoperNg.waimaiad.decrypt.Result result =
                new com.meituan.sdk.model.wmoperNg.waimaiad.decrypt.Result();
        result.setSuccess(true);
        result.setCode("0");
        result.setMessage("success");

        DecryptResponse payload = new DecryptResponse();
        payload.setResult(result);
        payload.setValue(value);

        MeituanResponse<DecryptResponse> response = new MeituanResponse<>();
        response.setCode("OP_SUCCESS");
        response.setMsg("success");
        response.setData(payload);
        return response;
    }

    private MeituanResponse<DecryptResponse> buildDecryptAuthFailureResponse() {
        com.meituan.sdk.model.wmoperNg.waimaiad.decrypt.Result result =
                new com.meituan.sdk.model.wmoperNg.waimaiad.decrypt.Result();
        result.setSuccess(false);
        result.setCode("OP_UNIAUTH_FAILED");
        result.setMessage("鉴权失败");

        DecryptResponse payload = new DecryptResponse();
        payload.setResult(result);

        MeituanResponse<DecryptResponse> response = new MeituanResponse<>();
        response.setCode("OP_SUCCESS");
        response.setMsg("success");
        response.setData(payload);
        return response;
    }
}
