package com.gogoga.backend.service;

import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.entity.mt.MtAppOrder;
import com.gogoga.backend.mapper.MtAppOrderMapper;
import com.meituan.sdk.MeituanClient;
import com.meituan.sdk.MeituanResponse;
import com.meituan.sdk.model.wmoperNg.waimaiad.appOrderList.AppOrderListRequest;
import com.meituan.sdk.model.wmoperNg.waimaiad.appOrderList.AppOrderListResponse;
import com.meituan.sdk.model.wmoperNg.waimaiad.appOrderList.Order;
import com.meituan.sdk.model.wmoperNg.waimaiad.appOrderList.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 美团订阅订单服务测试
 */
@ExtendWith(MockitoExtension.class)
class MtAppOrderServiceTest {

    @Mock
    private MtAppOrderMapper mtAppOrderMapper;

    @Mock
    private MtShopService mtShopService;

    @Mock
    private MeituanClient meituanClient;

    @Mock
    private MtProperties mtProperties;

    @Mock
    private MtAppOrderRightApplicationService rightApplicationService;

    @InjectMocks
    private MtAppOrderService mtAppOrderService;

    @Test
    void shouldMarkOrderProcessedOnlyAfterRightApplied() {
        MtAppOrder appliedOrder = buildPendingOrder(1L, "order-applied");
        MtAppOrder unappliedOrder = buildPendingOrder(2L, "order-unapplied");
        when(mtAppOrderMapper.selectPendingRightOrders()).thenReturn(List.of(appliedOrder, unappliedOrder));
        when(rightApplicationService.applyOne(any(MtAppOrder.class), any())).thenReturn(
                new MtAppOrderRightApplicationResult(true, Set.of()),
                new MtAppOrderRightApplicationResult(false, Set.of())
        );

        mtAppOrderService.applyPendingRights();

        verify(rightApplicationService).applyOne(eq(appliedOrder), any());
        verify(rightApplicationService).applyOne(eq(unappliedOrder), any());
        verify(mtAppOrderMapper, never()).updateById(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldShareBuyInfoCreditStateAcrossPendingOrders() {
        MtAppOrder firstOrder = buildPendingOrder(1L, "order-1");
        MtAppOrder secondOrder = buildPendingOrder(2L, "order-2");
        when(mtAppOrderMapper.selectPendingRightOrders()).thenReturn(List.of(firstOrder, secondOrder));
        when(rightApplicationService.applyOne(any(MtAppOrder.class), any())).thenReturn(
                new MtAppOrderRightApplicationResult(true, Set.of("credit-1")),
                new MtAppOrderRightApplicationResult(true, Set.of("credit-1"))
        );

        mtAppOrderService.applyPendingRights();

        ArgumentCaptor<Set<String>> setCaptor = ArgumentCaptor.forClass(Set.class);
        verify(rightApplicationService).applyOne(eq(firstOrder), setCaptor.capture());
        verify(rightApplicationService).applyOne(eq(secondOrder), setCaptor.capture());
        assertSame(setCaptor.getAllValues().get(0), setCaptor.getAllValues().get(1));
        assertEquals(Set.of("credit-1"), setCaptor.getAllValues().get(1));
    }

    @Test
    void shouldDoNothingWhenNoPendingOrdersExist() {
        when(mtAppOrderMapper.selectPendingRightOrders()).thenReturn(List.of());

        mtAppOrderService.applyPendingRights();

        verify(rightApplicationService, never()).applyOne(any(MtAppOrder.class), any());
        verify(mtAppOrderMapper, never()).updateById(any());
    }

    @Test
    void shouldPullSpecifiedRangeAndInsertNewOrder() throws Exception {
        configureAppOrder();
        when(meituanClient.invokeApi(any(AppOrderListRequest.class)))
                .thenReturn(successResponse(buildSdkOrder(1001L, "v7")));
        when(mtAppOrderMapper.selectByOrderId("1001")).thenReturn(null);
        when(mtAppOrderMapper.selectPendingRightOrders()).thenReturn(List.of());

        MtAppOrderService.PullResult result = mtAppOrderService.pullOrders(1_752_742_600L, 1_752_746_200L);

        assertEquals(154, result.appId());
        assertEquals(1, result.processedPages());
        assertEquals(1, result.totalFetched());
        assertEquals(1, result.createdCount());
        assertEquals(0, result.updatedCount());
        verify(mtAppOrderMapper).insert(any(MtAppOrder.class));
    }

    @Test
    void shouldUpdateExistingOrderWithoutResettingAppliedType() throws Exception {
        configureAppOrder();
        MtAppOrder existing = buildPendingOrder(1L, "1001");
        existing.setType(1);
        when(meituanClient.invokeApi(any(AppOrderListRequest.class)))
                .thenReturn(successResponse(buildSdkOrder(1001L, "v7")));
        when(mtAppOrderMapper.selectByOrderId("1001")).thenReturn(existing);
        when(mtAppOrderMapper.selectPendingRightOrders()).thenReturn(List.of());

        MtAppOrderService.PullResult result = mtAppOrderService.pullOrders(1_752_742_600L, 1_752_746_200L);

        assertEquals(0, result.createdCount());
        assertEquals(1, result.updatedCount());
        assertEquals(1, existing.getType());
        verify(mtAppOrderMapper).updateById(existing);
        verify(mtAppOrderMapper, never()).insert(any());
    }

    @Test
    void shouldFailWhenMeituanRequestFails() throws Exception {
        configureAppOrder();
        MeituanResponse<AppOrderListResponse> response = new MeituanResponse<>();
        response.setCode("SYSTEM_ERROR");
        response.setMsg("upstream failed");
        when(meituanClient.invokeApi(any(AppOrderListRequest.class))).thenReturn(response);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> mtAppOrderService.pullOrders(1_752_742_600L, 1_752_746_200L)
        );

        assertEquals("拉取美团订阅订单失败，code=SYSTEM_ERROR，msg=upstream failed", exception.getMessage());
        verify(mtAppOrderMapper, never()).insert(any());
    }

    private void configureAppOrder() {
        MtProperties.AppOrder appOrder = new MtProperties.AppOrder();
        appOrder.setAppId(154);
        appOrder.setPageSize(100);
        when(mtProperties.getAppOrder()).thenReturn(appOrder);
    }

    private MeituanResponse<AppOrderListResponse> successResponse(Order order) {
        Result result = new Result();
        result.setSuccess(true);
        AppOrderListResponse payload = new AppOrderListResponse();
        payload.setResult(result);
        payload.setOrder(List.of(order));
        MeituanResponse<AppOrderListResponse> response = new MeituanResponse<>();
        response.setCode("OP_SUCCESS");
        response.setData(payload);
        return response;
    }

    private Order buildSdkOrder(Long orderId, String version) {
        Order order = new Order();
        order.setOrderId(orderId);
        order.setOrderViewId(orderId);
        order.setAppId(154L);
        order.setServiceCode("33654866");
        order.setPayStatus(1L);
        order.setVersion(version);
        order.setVersionName("测试版本");
        order.setDays(7L);
        order.setStartTime(1_752_742_600L);
        order.setEndTime(1_753_347_400L);
        order.setRightStatus(1L);
        order.setRealPayPrice(100L);
        order.setOrderTime(1_752_743_000L);
        return order;
    }

    private MtAppOrder buildPendingOrder(Long id, String orderId) {
        MtAppOrder order = new MtAppOrder();
        order.setId(id);
        order.setOrderId(orderId);
        order.setShopId(10L);
        order.setVersion("ver-1");
        order.setDays(7);
        order.setEndTime(1_800_000_000L);
        order.setType(0);
        order.setUpdateTime(LocalDateTime.now().minusDays(1));
        return order;
    }
}
