package com.gogoga.backend.client;

import com.gogoga.backend.service.MtPaidApiPowerConsumeService;
import com.meituan.sdk.MeituanClient;
import com.meituan.sdk.MeituanResponse;
import com.meituan.sdk.model.wmoperNg.order.wmoperOrderQueryOrderDetail.WmoperOrderQueryOrderDetailRequest;
import com.meituan.sdk.model.wmoperNg.order.wmoperOrderQueryOrderDetail.WmoperOrderQueryOrderDetailResponse;
import com.meituan.sdk.model.wmoperNg.order.wmoperngOrderList.WmoperngOrderListRequest;
import com.meituan.sdk.model.wmoperNg.order.wmoperngOrderList.WmoperngOrderListResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MtOrderOpenApiClientTest {

    @Mock
    private MeituanClient meituanClient;

    @Mock
    private MtPaidApiPowerConsumeService mtPaidApiPowerConsumeService;

    @InjectMocks
    private MtOrderOpenApiClient mtOrderOpenApiClient;

    @Test
    void shouldQueryFirstOrderIdViaTypedSdkRequest() throws Exception {
        WmoperngOrderListResponse payload = new WmoperngOrderListResponse();
        payload.setOrderIds(List.of(10001L));

        MeituanResponse<WmoperngOrderListResponse> response = new MeituanResponse<>();
        response.setCode("OP_SUCCESS");
        response.setData(payload);
        when(meituanClient.invokeApi(any(WmoperngOrderListRequest.class), eq("token"))).thenReturn(response);

        Optional<String> orderId = mtOrderOpenApiClient.queryFirstOrderId(
                1L, "token", "20260410", -1, 1, 1);

        assertEquals(Optional.of("10001"), orderId);
        ArgumentCaptor<WmoperngOrderListRequest> requestCaptor =
                ArgumentCaptor.forClass(WmoperngOrderListRequest.class);
        verify(meituanClient).invokeApi(requestCaptor.capture(), eq("token"));
        WmoperngOrderListRequest request = requestCaptor.getValue();
        assertEquals(20260410, request.getDayInt());
        assertEquals(-1, request.getOrderStatus());
        assertEquals(1, request.getPageNo());
        assertEquals(1, request.getPageSize());
        verify(mtPaidApiPowerConsumeService).tryConsumeOrderListQuery(
                1L, "20260410", -1, 1, 1);
    }

    @Test
    void shouldQueryOrderIdsWithHasMoreFlagViaTypedSdkRequest() throws Exception {
        WmoperngOrderListResponse payload = new WmoperngOrderListResponse();
        payload.setOrderIds(List.of(10001L, 10002L));
        payload.setHashMore(true);

        MeituanResponse<WmoperngOrderListResponse> response = new MeituanResponse<>();
        response.setCode("OP_SUCCESS");
        response.setData(payload);
        when(meituanClient.invokeApi(any(WmoperngOrderListRequest.class), eq("token"))).thenReturn(response);

        MtOrderOpenApiClient.OrderListQueryResult result =
                mtOrderOpenApiClient.queryOrderIds(1L, "token", "20260410", 8, 1, 20);

        assertEquals(List.of("10001", "10002"), result.orderIds());
        assertTrue(result.hasMore());
    }

    @Test
    void shouldQueryPoiIdFromOrderDetailViaTypedSdkRequest() throws Exception {
        WmoperOrderQueryOrderDetailResponse payload = new WmoperOrderQueryOrderDetailResponse();
        payload.setPoiId(90001L);

        MeituanResponse<WmoperOrderQueryOrderDetailResponse> response = new MeituanResponse<>();
        response.setCode("OP_SUCCESS");
        response.setData(payload);
        when(meituanClient.invokeApi(any(WmoperOrderQueryOrderDetailRequest.class), eq("token")))
                .thenReturn(response);

        Optional<String> poiId = mtOrderOpenApiClient.queryPoiIdByOrderId(1L, "token", "10001");

        assertEquals(Optional.of("90001"), poiId);
        ArgumentCaptor<WmoperOrderQueryOrderDetailRequest> requestCaptor =
                ArgumentCaptor.forClass(WmoperOrderQueryOrderDetailRequest.class);
        verify(meituanClient).invokeApi(requestCaptor.capture(), eq("token"));
        assertEquals(10001L, requestCaptor.getValue().getOrderId());
        verify(mtPaidApiPowerConsumeService).tryConsumeOrderDetailQuery(1L, "10001");
    }
}
