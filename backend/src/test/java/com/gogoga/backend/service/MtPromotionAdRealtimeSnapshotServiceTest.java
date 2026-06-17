package com.gogoga.backend.service;

import com.gogoga.backend.entity.mt.MtPromotionAdRealtimeSnapshot;
import com.gogoga.backend.entity.mt.MtPromotionAdDaily;
import com.gogoga.backend.entity.mt.MtPromotionAdHourlyDetail;
import com.gogoga.backend.mapper.MtPromotionAdDailyMapper;
import com.gogoga.backend.mapper.MtPromotionAdHourlyDetailMapper;
import com.gogoga.backend.mapper.MtPromotionAdRealtimeSnapshotMapper;
import com.meituan.sdk.MeituanClient;
import com.meituan.sdk.MeituanResponse;
import com.meituan.sdk.model.wmoperNg.waimaiad.adDataDetail.AdDataDetailRequest;
import com.meituan.sdk.model.wmoperNg.waimaiad.adDataDetail.AdDataDetailResponse;
import com.meituan.sdk.model.wmoperNg.waimaiad.adDataDetail.AdDataInfoList;
import com.meituan.sdk.model.wmoperNg.waimaiad.decrypt.DecryptResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MtPromotionAdRealtimeSnapshotServiceTest {

    @Mock
    private MtPromotionAdRealtimeSnapshotMapper mtPromotionAdRealtimeSnapshotMapper;

    @Mock
    private MtPromotionAdDailyMapper mtPromotionAdDailyMapper;

    @Mock
    private MtPromotionAdHourlyDetailMapper mtPromotionAdHourlyDetailMapper;

    @Mock
    private MeituanClient meituanClient;

    private MtPromotionAdRealtimeSnapshotService mtPromotionAdRealtimeSnapshotService;

    @BeforeEach
    void setUp() {
        MtWaimaiAdDecryptService decryptService = new MtWaimaiAdDecryptService(meituanClient);
        mtPromotionAdRealtimeSnapshotService = new MtPromotionAdRealtimeSnapshotService(
                mtPromotionAdRealtimeSnapshotMapper,
                mtPromotionAdDailyMapper,
                mtPromotionAdHourlyDetailMapper,
                meituanClient,
                decryptService
        );
    }

    @Test
    void shouldCaptureRealtimeSnapshotForCurrentHour() throws Exception {
        LocalDateTime slotTime = LocalDateTime.of(2026, 4, 14, 0, 20);
        when(meituanClient.invokeApi(
                any(com.meituan.sdk.model.wmoperNg.waimaiad.adDataDetail.AdDataDetailRequest.class),
                eq("token")
        )).thenReturn(buildAdDataDetailResponse(
                "encrypted-day-show",
                "encrypted-day-click",
                "encrypted-day-debit",
                List.of(
                        buildRow("00:00-01:00", "encrypted-hour-show", "encrypted-hour-click", "encrypted-hour-debit"),
                        buildRow("01:00-02:00", "10", "1", "100")
                )
        ));
        AtomicInteger decryptCallIndex = new AtomicInteger();
        when(meituanClient.invokeApi(
                any(com.meituan.sdk.model.wmoperNg.waimaiad.decrypt.DecryptRequest.class),
                eq("token")
        )).thenAnswer(invocation -> switch (decryptCallIndex.getAndIncrement()) {
            case 0 -> buildDecryptResponse("41");
            case 1 -> buildDecryptResponse("2");
            case 2 -> buildDecryptResponse("356");
            case 3 -> buildDecryptResponse("41");
            case 4 -> buildDecryptResponse("2");
            case 5 -> buildDecryptResponse("356");
            default -> throw new IllegalStateException("unexpected decrypt invoke count");
        });

        MtPromotionAdRealtimeSnapshot snapshot =
                mtPromotionAdRealtimeSnapshotService.captureCurrentSlotSnapshot(1001L, "token", slotTime);

        ArgumentCaptor<AdDataDetailRequest> requestCaptor = ArgumentCaptor.forClass(AdDataDetailRequest.class);
        verify(meituanClient).invokeApi(requestCaptor.capture(), eq("token"));
        assertEquals("20260414", requestCaptor.getValue().getDate());

        assertEquals(1001L, snapshot.getShopId());
        assertEquals(slotTime.toLocalDate(), snapshot.getBizDate());
        assertEquals(slotTime, snapshot.getSlotTime());
        assertEquals("00:00-01:00", snapshot.getCurrentHourRange());
        assertEquals(41, snapshot.getDayShowCount());
        assertEquals(2, snapshot.getDayClickCount());
        assertEquals(356L, snapshot.getDayDebitCents());
        assertEquals(41, snapshot.getHourShowCount());
        assertEquals(2, snapshot.getHourClickCount());
        assertEquals(356L, snapshot.getHourDebitCents());
        assertTrue(snapshot.getPayloadJson().contains("mt.wmoper.ng.waimaiad.data.detail.realtime"));

        ArgumentCaptor<MtPromotionAdRealtimeSnapshot> captor =
                ArgumentCaptor.forClass(MtPromotionAdRealtimeSnapshot.class);
        verify(mtPromotionAdRealtimeSnapshotMapper).insertOrUpdate(captor.capture());
        assertEquals("00:00-01:00", captor.getValue().getCurrentHourRange());

        ArgumentCaptor<MtPromotionAdDaily> dailyCaptor = ArgumentCaptor.forClass(MtPromotionAdDaily.class);
        verify(mtPromotionAdDailyMapper).insertOrUpdate(dailyCaptor.capture());
        assertEquals(slotTime.toLocalDate(), dailyCaptor.getValue().getBizDate());
        assertEquals(41, dailyCaptor.getValue().getShowCount());
        assertEquals(2, dailyCaptor.getValue().getClickCount());
        assertEquals(356L, dailyCaptor.getValue().getDebitCents());

        ArgumentCaptor<MtPromotionAdHourlyDetail> hourlyCaptor =
                ArgumentCaptor.forClass(MtPromotionAdHourlyDetail.class);
        verify(mtPromotionAdHourlyDetailMapper, org.mockito.Mockito.times(2)).insertOrUpdate(hourlyCaptor.capture());
        assertEquals("00:00-01:00", hourlyCaptor.getAllValues().get(0).getTimeRange());
        assertEquals(41, hourlyCaptor.getAllValues().get(0).getShowCount());
        assertEquals(2, hourlyCaptor.getAllValues().get(0).getClickCount());
        assertEquals(356L, hourlyCaptor.getAllValues().get(0).getDebitCents());
        assertEquals("01:00-02:00", hourlyCaptor.getAllValues().get(1).getTimeRange());
        assertEquals(10, hourlyCaptor.getAllValues().get(1).getShowCount());
    }

    @Test
    void shouldUsePlainNumericValuesWithoutDecrypt() throws Exception {
        LocalDateTime slotTime = LocalDateTime.of(2026, 4, 14, 9, 10);
        when(meituanClient.invokeApi(
                any(com.meituan.sdk.model.wmoperNg.waimaiad.adDataDetail.AdDataDetailRequest.class),
                eq("token")
        )).thenReturn(buildAdDataDetailResponse(
                "88",
                "12",
                "999",
                List.of(buildRow("09:00-10:00", "11", "1", "123"))
        ));

        MtPromotionAdRealtimeSnapshot snapshot =
                mtPromotionAdRealtimeSnapshotService.captureCurrentSlotSnapshot(1001L, "token", slotTime);

        assertEquals(88, snapshot.getDayShowCount());
        assertEquals(12, snapshot.getDayClickCount());
        assertEquals(999L, snapshot.getDayDebitCents());
        assertEquals(11, snapshot.getHourShowCount());
        assertEquals(1, snapshot.getHourClickCount());
        assertEquals(123L, snapshot.getHourDebitCents());
        verify(meituanClient, never()).invokeApi(
                any(com.meituan.sdk.model.wmoperNg.waimaiad.decrypt.DecryptRequest.class),
                eq("token")
        );
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

    private AdDataInfoList buildRow(String time, String show, String click, String debit) {
        AdDataInfoList row = new AdDataInfoList();
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
