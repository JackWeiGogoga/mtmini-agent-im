package com.gogoga.backend.service;

import com.gogoga.backend.entity.mt.MtAccessToken;
import com.gogoga.backend.entity.mt.MtPromotionAdDaily;
import com.gogoga.backend.entity.mt.MtPromotionAdHourlyDetail;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.mapper.MtAccessTokenMapper;
import com.gogoga.backend.mapper.MtPromotionAdDailyMapper;
import com.gogoga.backend.mapper.MtPromotionAdHourlyDetailMapper;
import com.meituan.sdk.MeituanClient;
import com.meituan.sdk.MeituanResponse;
import com.meituan.sdk.model.wmoperNg.waimaiad.adDataDetail.AdDataDetailRequest;
import com.meituan.sdk.model.wmoperNg.waimaiad.adDataDetail.AdDataDetailResponse;
import com.meituan.sdk.model.wmoperNg.waimaiad.adDataDetail.AdDataInfoList;
import com.meituan.sdk.model.wmoperNg.waimaiad.adDataHistory.AdDataHistoryRequest;
import com.meituan.sdk.model.wmoperNg.waimaiad.adDataHistory.AdDataHistoryResponse;
import com.meituan.sdk.model.wmoperNg.waimaiad.decrypt.DecryptResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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

@ExtendWith(MockitoExtension.class)
class MtPromotionAdDataServiceTest {

    @Mock
    private MtAccessTokenMapper mtAccessTokenMapper;

    @Mock
    private MtPromotionAdDailyMapper mtPromotionAdDailyMapper;

    @Mock
    private MtPromotionAdHourlyDetailMapper mtPromotionAdHourlyDetailMapper;

    @Mock
    private MtShopService mtShopService;

    @Mock
    private MeituanClient meituanClient;

    @Mock
    private MtAccessTokenService mtAccessTokenService;

    @Mock
    private MtPromotionSettingService mtPromotionSettingService;

    private MtPromotionAdDataService mtPromotionAdDataService;

    @BeforeEach
    void setUp() {
        MtWaimaiAdDecryptService decryptService = new MtWaimaiAdDecryptService(meituanClient);
        MtMeituanAuthorizationFailureHandler authFailureHandler = new MtMeituanAuthorizationFailureHandler(
                mtAccessTokenService,
                mtPromotionSettingService
        );
        mtPromotionAdDataService = new MtPromotionAdDataService(
                mtAccessTokenMapper,
                mtPromotionAdDailyMapper,
                mtPromotionAdHourlyDetailMapper,
                mtShopService,
                meituanClient,
                decryptService,
                authFailureHandler
        );
    }

    @Test
    void shouldSyncAdDataAndPersistDailyAndHourlyRows() throws Exception {
        MtAccessToken token = new MtAccessToken();
        token.setShopId(1001L);
        token.setAccessToken("token");
        when(mtAccessTokenMapper.selectTrafficSyncCandidates(any(LocalDateTime.class))).thenReturn(List.of(token));
        when(meituanClient.invokeApi(
                any(com.meituan.sdk.model.wmoperNg.waimaiad.adDataDetail.AdDataDetailRequest.class),
                eq("token")
        )).thenReturn(buildAdDataDetailResponse(
                "encrypted-day-show",
                "encrypted-day-click",
                "encrypted-day-debit",
                List.of(
                        buildAdDataInfo("10:00-11:00", "encrypted-show-1", "encrypted-click-1", "encrypted-debit-1"),
                        buildAdDataInfo("11:00-12:00", "encrypted-show-2", "encrypted-click-2", "encrypted-debit-2")
                )
        ));
        AtomicInteger decryptCallIndex = new AtomicInteger();
        when(meituanClient.invokeApi(
                any(com.meituan.sdk.model.wmoperNg.waimaiad.decrypt.DecryptRequest.class),
                eq("token")
        )).thenAnswer(invocation -> switch (decryptCallIndex.getAndIncrement()) {
            case 0 -> buildDecryptResponse("30");
            case 1 -> buildDecryptResponse("8");
            case 2 -> buildDecryptResponse("456");
            case 3 -> buildDecryptResponse("11");
            case 4 -> buildDecryptResponse("2");
            case 5 -> buildDecryptResponse("123");
            case 6 -> buildDecryptResponse("19");
            case 7 -> buildDecryptResponse("6");
            case 8 -> buildDecryptResponse("333");
            default -> throw new IllegalStateException("unexpected decrypt invoke count");
        });

        mtPromotionAdDataService.syncAdData(LocalDate.of(2026, 4, 13));

        ArgumentCaptor<AdDataDetailRequest> requestCaptor = ArgumentCaptor.forClass(AdDataDetailRequest.class);
        verify(meituanClient).invokeApi(requestCaptor.capture(), eq("token"));
        assertEquals("20260413", requestCaptor.getValue().getDate());

        ArgumentCaptor<MtPromotionAdDaily> dailyCaptor = ArgumentCaptor.forClass(MtPromotionAdDaily.class);
        verify(mtPromotionAdDailyMapper).insertOrUpdate(dailyCaptor.capture());
        MtPromotionAdDaily daily = dailyCaptor.getValue();
        assertEquals(1001L, daily.getShopId());
        assertEquals(LocalDate.of(2026, 4, 13), daily.getBizDate());
        assertEquals(1, daily.getChannel());
        assertEquals(30, daily.getShowCount());
        assertEquals(8, daily.getClickCount());
        assertEquals(456L, daily.getDebitCents());
        assertTrue(daily.getPayloadJson().contains("mt.wmoper.ng.waimaiad.data.detail"));

        verify(mtPromotionAdHourlyDetailMapper).deleteByShopIdAndBizDateAndChannel(1001L, LocalDate.of(2026, 4, 13), 1);
        ArgumentCaptor<MtPromotionAdHourlyDetail> detailCaptor = ArgumentCaptor.forClass(MtPromotionAdHourlyDetail.class);
        verify(mtPromotionAdHourlyDetailMapper, times(2)).insertOrUpdate(detailCaptor.capture());
        List<MtPromotionAdHourlyDetail> details = detailCaptor.getAllValues();
        assertEquals("10:00-11:00", details.get(0).getTimeRange());
        assertEquals(11, details.get(0).getShowCount());
        assertEquals(2, details.get(0).getClickCount());
        assertEquals(123L, details.get(0).getDebitCents());
        assertEquals("11:00-12:00", details.get(1).getTimeRange());
        assertEquals(19, details.get(1).getShowCount());
        assertEquals(6, details.get(1).getClickCount());
        assertEquals(333L, details.get(1).getDebitCents());
    }

    @Test
    void shouldUsePlainNumericMetricsWithoutDecrypt() throws Exception {
        MtAccessToken token = new MtAccessToken();
        token.setShopId(1002L);
        token.setAccessToken("token");
        when(mtAccessTokenMapper.selectTrafficSyncCandidates(any(LocalDateTime.class))).thenReturn(List.of(token));
        when(meituanClient.invokeApi(
                any(com.meituan.sdk.model.wmoperNg.waimaiad.adDataDetail.AdDataDetailRequest.class),
                eq("token")
        )).thenReturn(buildAdDataDetailResponse(
                "88",
                "12",
                "999",
                List.of(buildAdDataInfo("10:00-11:00", "11", "1", "123"))
        ));

        mtPromotionAdDataService.syncAdData(LocalDate.of(2026, 4, 13));

        ArgumentCaptor<MtPromotionAdDaily> dailyCaptor = ArgumentCaptor.forClass(MtPromotionAdDaily.class);
        verify(mtPromotionAdDailyMapper).insertOrUpdate(dailyCaptor.capture());
        assertEquals(88, dailyCaptor.getValue().getShowCount());
        assertEquals(12, dailyCaptor.getValue().getClickCount());
        assertEquals(999L, dailyCaptor.getValue().getDebitCents());
        verify(meituanClient, never()).invokeApi(
                any(com.meituan.sdk.model.wmoperNg.waimaiad.decrypt.DecryptRequest.class),
            eq("token")
        );
    }

    @Test
    void shouldNotOverwritePersistedNonZeroDataWhenDetailReturnsAllZero() throws Exception {
        LocalDate bizDate = LocalDate.of(2026, 4, 13);
        MtAccessToken token = new MtAccessToken();
        token.setShopId(1002L);
        token.setAccessToken("token");
        when(mtAccessTokenMapper.selectTrafficSyncCandidates(any(LocalDateTime.class))).thenReturn(List.of(token));
        when(meituanClient.invokeApi(any(AdDataDetailRequest.class), eq("token")))
                .thenReturn(buildAdDataDetailResponse("0", "0", "0", List.of(
                        buildAdDataInfo("10:00-11:00", "0", "0", "0")
                )));

        MtPromotionAdDaily existingDaily = new MtPromotionAdDaily();
        existingDaily.setShopId(1002L);
        existingDaily.setBizDate(bizDate);
        existingDaily.setChannel(1);
        existingDaily.setShowCount(88);
        existingDaily.setClickCount(12);
        existingDaily.setDebitCents(999L);
        when(mtPromotionAdDailyMapper.selectByShopIdAndBizDateAndChannel(1002L, bizDate, 1))
                .thenReturn(existingDaily);

        mtPromotionAdDataService.syncAdData(bizDate);

        verify(mtPromotionAdDailyMapper, never()).insertOrUpdate(any(MtPromotionAdDaily.class));
        verify(mtPromotionAdHourlyDetailMapper, never()).deleteByShopIdAndBizDateAndChannel(any(), any(), any());
        verify(mtPromotionAdHourlyDetailMapper, never()).insertOrUpdate(any(MtPromotionAdHourlyDetail.class));
    }

    @Test
    void shouldSkipBackfillWhenStatusAlreadySuccessful() throws Exception {
        MtShop shop = new MtShop();
        shop.setId(1001L);
        shop.setPromotionAdBackfillStatus(MtPromotionAdDataService.BACKFILL_STATUS_SUCCESS);
        when(mtShopService.findById(1001L)).thenReturn(shop);

        mtPromotionAdDataService.ensureRecentAdDataBackfillBeforeLogGeneration(1001L, "token");

        verify(mtShopService, never()).updatePromotionAdBackfillState(any(), any(), any());
        verify(meituanClient, never()).invokeApi(
                any(com.meituan.sdk.model.wmoperNg.waimaiad.adDataDetail.AdDataDetailRequest.class),
                eq("token")
        );
    }

    @Test
    void shouldMarkEmptySuccessWhenRecentSevenDaysHaveNoAdData() throws Exception {
        MtShop shop = new MtShop();
        shop.setId(1001L);
        shop.setPromotionAdBackfillStatus(MtPromotionAdDataService.BACKFILL_STATUS_INIT);
        when(mtShopService.findById(1001L)).thenReturn(shop);
        when(meituanClient.invokeApi(
                any(AdDataHistoryRequest.class),
                eq("token")
        )).thenReturn(buildAdDataHistoryResponse(List.of(
                buildAdDataHistoryInfo("2026-04-07", "0", "0", "0"),
                buildAdDataHistoryInfo("2026-04-08", "0", "0", "0"),
                buildAdDataHistoryInfo("2026-04-09", "0", "0", "0"),
                buildAdDataHistoryInfo("2026-04-10", "0", "0", "0"),
                buildAdDataHistoryInfo("2026-04-11", "0", "0", "0"),
                buildAdDataHistoryInfo("2026-04-12", "0", "0", "0"),
                buildAdDataHistoryInfo("2026-04-13", "0", "0", "0")
        )));
        when(meituanClient.invokeApi(
                any(com.meituan.sdk.model.wmoperNg.waimaiad.adDataDetail.AdDataDetailRequest.class),
                eq("token")
        )).thenReturn(buildAdDataDetailResponse("0", "0", "0", List.of()));

        mtPromotionAdDataService.ensureRecentAdDataBackfillBeforeLogGeneration(1001L, "token");

        verify(mtShopService).updatePromotionAdBackfillState(eq(1001L), eq(MtPromotionAdDataService.BACKFILL_STATUS_EMPTY_SUCCESS), any(LocalDateTime.class));
        verify(mtPromotionAdDailyMapper, times(8)).insertOrUpdate(any(MtPromotionAdDaily.class));
        verify(mtPromotionAdHourlyDetailMapper).deleteByShopIdAndBizDateAndChannel(eq(1001L), any(LocalDate.class), eq(1));
        verify(mtPromotionAdHourlyDetailMapper, never()).insertOrUpdate(any(MtPromotionAdHourlyDetail.class));
    }

    @Test
    void shouldRetryFailedBackfillAfterOneDay() throws Exception {
        MtShop shop = new MtShop();
        shop.setId(1001L);
        shop.setPromotionAdBackfillStatus(MtPromotionAdDataService.BACKFILL_STATUS_FAILED);
        shop.setPromotionAdBackfillAt(LocalDateTime.now().minusDays(2));
        when(mtShopService.findById(1001L)).thenReturn(shop);
        when(meituanClient.invokeApi(
                any(AdDataHistoryRequest.class),
                eq("token")
        )).thenReturn(buildAdDataHistoryResponse(List.of(
                buildAdDataHistoryInfo("2026-04-07", "5", "1", "66")
        )));
        when(meituanClient.invokeApi(
                any(com.meituan.sdk.model.wmoperNg.waimaiad.adDataDetail.AdDataDetailRequest.class),
                eq("token")
        )).thenReturn(buildAdDataDetailResponse("5", "1", "66", List.of()));

        mtPromotionAdDataService.ensureRecentAdDataBackfillBeforeLogGeneration(1001L, "token");

        verify(mtShopService).updatePromotionAdBackfillState(eq(1001L), eq(MtPromotionAdDataService.BACKFILL_STATUS_SUCCESS), any(LocalDateTime.class));
    }

    @Test
    void shouldNotRetryFailedBackfillWithinOneDay() throws Exception {
        MtShop shop = new MtShop();
        shop.setId(1001L);
        shop.setPromotionAdBackfillStatus(MtPromotionAdDataService.BACKFILL_STATUS_FAILED);
        shop.setPromotionAdBackfillAt(LocalDateTime.now().minusHours(6));
        when(mtShopService.findById(1001L)).thenReturn(shop);

        mtPromotionAdDataService.ensureRecentAdDataBackfillBeforeLogGeneration(1001L, "token");

        verify(mtShopService, never()).updatePromotionAdBackfillState(any(), any(), any());
        verify(meituanClient, never()).invokeApi(
                any(com.meituan.sdk.model.wmoperNg.waimaiad.adDataDetail.AdDataDetailRequest.class),
                eq("token")
        );
        verify(meituanClient, never()).invokeApi(
                any(AdDataHistoryRequest.class),
                eq("token")
        );
    }

    @Test
    void shouldMarkFailedWhenBackfillThrows() throws Exception {
        MtShop shop = new MtShop();
        shop.setId(1001L);
        shop.setPromotionAdBackfillStatus(MtPromotionAdDataService.BACKFILL_STATUS_INIT);
        when(mtShopService.findById(1001L)).thenReturn(shop);
        when(meituanClient.invokeApi(
                any(AdDataHistoryRequest.class),
                eq("token")
        )).thenThrow(new IllegalStateException("mock failure"));

        mtPromotionAdDataService.ensureRecentAdDataBackfillBeforeLogGeneration(1001L, "token");

        verify(mtShopService).updatePromotionAdBackfillState(eq(1001L), eq(MtPromotionAdDataService.BACKFILL_STATUS_FAILED), any(LocalDateTime.class));
    }

    @Test
    void shouldBackfillHistoryDailyAndOnlyFetchYesterdayDetail() throws Exception {
        MtShop shop = new MtShop();
        shop.setId(1001L);
        shop.setPromotionAdBackfillStatus(MtPromotionAdDataService.BACKFILL_STATUS_INIT);
        when(mtShopService.findById(1001L)).thenReturn(shop);
        when(meituanClient.invokeApi(any(AdDataHistoryRequest.class), eq("token")))
                .thenReturn(buildAdDataHistoryResponse(List.of(
                        buildAdDataHistoryInfo("2026-04-07", "10", "1", "100"),
                        buildAdDataHistoryInfo("2026-04-13", "20", "2", "200")
                )));
        when(meituanClient.invokeApi(any(AdDataDetailRequest.class), eq("token")))
                .thenReturn(buildAdDataDetailResponse("20", "2", "200", List.of()));

        mtPromotionAdDataService.ensureRecentAdDataBackfillBeforeLogGeneration(1001L, "token");

        ArgumentCaptor<AdDataHistoryRequest> historyCaptor = ArgumentCaptor.forClass(AdDataHistoryRequest.class);
        verify(meituanClient).invokeApi(historyCaptor.capture(), eq("token"));
        assertEquals(1, historyCaptor.getValue().getChannel());
        assertEquals(7, historyCaptor.getValue().getDays());
        assertEquals(3, historyCaptor.getValue().getType());

        ArgumentCaptor<AdDataDetailRequest> detailCaptor = ArgumentCaptor.forClass(AdDataDetailRequest.class);
        verify(meituanClient).invokeApi(detailCaptor.capture(), eq("token"));
        assertEquals(
                LocalDate.now(ZoneId.of("Asia/Shanghai")).minusDays(1).format(DateTimeFormatter.BASIC_ISO_DATE),
                detailCaptor.getValue().getDate()
        );
    }

    @Test
    void shouldMarkReauthWhenAdDetailAuthFailurePersists() throws Exception {
        MtAccessToken token = new MtAccessToken();
        token.setShopId(2001L);
        token.setAccessToken("bad-token");
        when(mtAccessTokenMapper.selectTrafficSyncCandidates(any(LocalDateTime.class))).thenReturn(List.of(token));
        when(mtAccessTokenService.refreshTokenForShopWithResult(2001L))
                .thenReturn(MtAccessTokenService.TokenRefreshResult.failure(10009, "refresh failed", "REMOTE_REFRESH_FAILED"));
        when(meituanClient.invokeApi(any(AdDataDetailRequest.class), eq("bad-token")))
                .thenReturn(buildAdDataDetailAuthFailureResponse());

        mtPromotionAdDataService.syncAdData(LocalDate.of(2026, 4, 13));

        verify(mtAccessTokenService).markReauthRequired(2001L);
        verify(mtPromotionSettingService).disablePromotionByShopId(
                2001L,
                MtMeituanAuthorizationFailureHandler.CHANGE_REASON_AUTH_FAILURE
        );
        verify(mtPromotionAdDailyMapper, never()).insertOrUpdate(any(MtPromotionAdDaily.class));
    }

    @Test
    void shouldRetryAdDetailWithRefreshedTokenAndKeepAuthorizationValid() throws Exception {
        MtAccessToken token = new MtAccessToken();
        token.setShopId(2002L);
        token.setAccessToken("old-token");
        MtAccessToken refreshedToken = new MtAccessToken();
        refreshedToken.setShopId(2002L);
        refreshedToken.setAccessToken("new-token");
        when(mtAccessTokenMapper.selectTrafficSyncCandidates(any(LocalDateTime.class))).thenReturn(List.of(token));
        when(mtAccessTokenService.refreshTokenForShopWithResult(2002L))
                .thenReturn(MtAccessTokenService.TokenRefreshResult.success(refreshedToken, 0, "success"));
        when(meituanClient.invokeApi(any(AdDataDetailRequest.class), eq("old-token")))
                .thenReturn(buildAdDataDetailAuthFailureResponse());
        when(meituanClient.invokeApi(any(AdDataDetailRequest.class), eq("new-token")))
                .thenReturn(buildAdDataDetailResponse("5", "1", "66", List.of()));

        mtPromotionAdDataService.syncAdData(LocalDate.of(2026, 4, 13));

        verify(mtAccessTokenService, never()).markReauthRequired(any());
        verify(mtPromotionSettingService, never()).disablePromotionByShopId(any(), any());
        verify(meituanClient).invokeApi(any(AdDataDetailRequest.class), eq("old-token"));
        verify(meituanClient).invokeApi(any(AdDataDetailRequest.class), eq("new-token"));
        verify(mtPromotionAdDailyMapper).insertOrUpdate(any(MtPromotionAdDaily.class));
    }

    @Test
    void shouldNotMarkReauthWhenAdDetailFailureIsNotAuthFailure() throws Exception {
        MtAccessToken token = new MtAccessToken();
        token.setShopId(2003L);
        token.setAccessToken("token");
        when(mtAccessTokenMapper.selectTrafficSyncCandidates(any(LocalDateTime.class))).thenReturn(List.of(token));
        when(meituanClient.invokeApi(any(AdDataDetailRequest.class), eq("token")))
                .thenReturn(buildAdDataDetailBusinessFailureResponse("SYSTEM_BUSY", "系统繁忙"));

        mtPromotionAdDataService.syncAdData(LocalDate.of(2026, 4, 13));

        verify(mtAccessTokenService, never()).refreshTokenForShopWithResult(any());
        verify(mtAccessTokenService, never()).markReauthRequired(any());
        verify(mtPromotionSettingService, never()).disablePromotionByShopId(any(), any());
        verify(mtPromotionAdDailyMapper, never()).insertOrUpdate(any(MtPromotionAdDaily.class));
    }

    @Test
    void shouldMarkReauthWhenAdHistoryAuthFailurePersistsDuringBackfill() throws Exception {
        MtShop shop = new MtShop();
        shop.setId(2004L);
        shop.setPromotionAdBackfillStatus(MtPromotionAdDataService.BACKFILL_STATUS_INIT);
        when(mtShopService.findById(2004L)).thenReturn(shop);
        when(mtAccessTokenService.refreshTokenForShopWithResult(2004L))
                .thenReturn(MtAccessTokenService.TokenRefreshResult.failure(10009, "refresh failed", "REMOTE_REFRESH_FAILED"));
        when(meituanClient.invokeApi(any(AdDataHistoryRequest.class), eq("bad-token")))
                .thenReturn(buildAdDataHistoryAuthFailureResponse());

        mtPromotionAdDataService.ensureRecentAdDataBackfillBeforeLogGeneration(2004L, "bad-token");

        verify(mtAccessTokenService).markReauthRequired(2004L);
        verify(mtPromotionSettingService).disablePromotionByShopId(
                2004L,
                MtMeituanAuthorizationFailureHandler.CHANGE_REASON_AUTH_FAILURE
        );
        verify(mtShopService).updatePromotionAdBackfillState(eq(2004L), eq(MtPromotionAdDataService.BACKFILL_STATUS_FAILED), any(LocalDateTime.class));
    }

    private MeituanResponse<AdDataDetailResponse> buildAdDataDetailResponse(String show,
                                                                            String click,
                                                                            String debit,
                                                                            List<AdDataInfoList> rows) {
        com.meituan.sdk.model.wmoperNg.waimaiad.adDataDetail.Result result =
                new com.meituan.sdk.model.wmoperNg.waimaiad.adDataDetail.Result();
        result.setSuccess(true);
        result.setCode("0");
        result.setMessage("success");

        AdDataDetailResponse payload = new AdDataDetailResponse();
        payload.setResult(result);
        payload.setShow(show);
        payload.setClick(click);
        payload.setDebit(debit);
        payload.setAdDataInfoList(rows);

        MeituanResponse<AdDataDetailResponse> response = new MeituanResponse<>();
        response.setCode("OP_SUCCESS");
        response.setMsg("success");
        response.setData(payload);
        return response;
    }

    private MeituanResponse<AdDataDetailResponse> buildAdDataDetailAuthFailureResponse() {
        return buildAdDataDetailBusinessFailureResponse("OP_UNIAUTH_FAILED", "鉴权失败");
    }

    private MeituanResponse<AdDataDetailResponse> buildAdDataDetailBusinessFailureResponse(String code, String message) {
        com.meituan.sdk.model.wmoperNg.waimaiad.adDataDetail.Result result =
                new com.meituan.sdk.model.wmoperNg.waimaiad.adDataDetail.Result();
        result.setSuccess(false);
        result.setCode(code);
        result.setMessage(message);

        AdDataDetailResponse payload = new AdDataDetailResponse();
        payload.setResult(result);

        MeituanResponse<AdDataDetailResponse> response = new MeituanResponse<>();
        response.setCode("OP_SUCCESS");
        response.setMsg("success");
        response.setData(payload);
        return response;
    }

    private MeituanResponse<AdDataHistoryResponse> buildAdDataHistoryResponse(
            List<com.meituan.sdk.model.wmoperNg.waimaiad.adDataHistory.AdDataInfoList> rows) {
        com.meituan.sdk.model.wmoperNg.waimaiad.adDataHistory.Result result =
                new com.meituan.sdk.model.wmoperNg.waimaiad.adDataHistory.Result();
        result.setSuccess(true);
        result.setCode("0");
        result.setMessage("success");

        AdDataHistoryResponse payload = new AdDataHistoryResponse();
        payload.setResult(result);
        payload.setAdDataInfoList(rows);

        MeituanResponse<AdDataHistoryResponse> response = new MeituanResponse<>();
        response.setCode("OP_SUCCESS");
        response.setMsg("success");
        response.setData(payload);
        return response;
    }

    private MeituanResponse<AdDataHistoryResponse> buildAdDataHistoryAuthFailureResponse() {
        com.meituan.sdk.model.wmoperNg.waimaiad.adDataHistory.Result result =
                new com.meituan.sdk.model.wmoperNg.waimaiad.adDataHistory.Result();
        result.setSuccess(false);
        result.setCode("OP_UNIAUTH_FAILED");
        result.setMessage("鉴权失败");

        AdDataHistoryResponse payload = new AdDataHistoryResponse();
        payload.setResult(result);

        MeituanResponse<AdDataHistoryResponse> response = new MeituanResponse<>();
        response.setCode("OP_SUCCESS");
        response.setMsg("success");
        response.setData(payload);
        return response;
    }

    private AdDataInfoList buildAdDataInfo(String time, String show, String click, String debit) {
        AdDataInfoList row = new AdDataInfoList();
        row.setTime(time);
        row.setShow(show);
        row.setClick(click);
        row.setDebit(debit);
        return row;
    }

    private com.meituan.sdk.model.wmoperNg.waimaiad.adDataHistory.AdDataInfoList buildAdDataHistoryInfo(
            String time,
            String show,
            String click,
            String debit
    ) {
        com.meituan.sdk.model.wmoperNg.waimaiad.adDataHistory.AdDataInfoList row =
                new com.meituan.sdk.model.wmoperNg.waimaiad.adDataHistory.AdDataInfoList();
        row.setTime(time);
        row.setShow(show);
        row.setClick(click);
        row.setDebit(debit);
        return row;
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
}
