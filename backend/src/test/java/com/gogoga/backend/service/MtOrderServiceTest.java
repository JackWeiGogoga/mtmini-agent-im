package com.gogoga.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gogoga.backend.entity.mt.MtOrder;
import com.gogoga.backend.entity.mt.MtOrderDetail;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.mapper.MtOrderDetailMapper;
import com.gogoga.backend.mapper.MtOrderMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 美团订单服务测试
 *
 * @author system
 * @since 2026-03-28
 */
@ExtendWith(MockitoExtension.class)
class MtOrderServiceTest {

    @Mock
    private MtOrderMapper mtOrderMapper;

    @Mock
    private MtOrderDetailMapper mtOrderDetailMapper;

    @Mock
    private MtShopService mtShopService;

    @Mock
    private MtShopPoiIdBackfillService mtShopPoiIdBackfillService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private MtOrderService mtOrderService;

    @Test
    void shouldInsertOrderDetailsAndIncomeWhenCompletedOrderArrives() {
        MtShop shop = buildShop();
        when(mtShopService.findByEpoiId("shop-001")).thenReturn(shop);
        when(mtShopPoiIdBackfillService.tryBackfillFromOrderCallback(shop, "order-001")).thenReturn(shop);
        when(mtOrderMapper.selectByOrderIdForUpdate("order-001")).thenReturn(null);

        mtOrderService.handleOrderNotify(buildParameterMap(buildCompletedMessage("order-001", "1234")));

        ArgumentCaptor<MtOrder> orderCaptor = ArgumentCaptor.forClass(MtOrder.class);
        verify(mtOrderMapper).insert(orderCaptor.capture());
        assertEquals("order-001", orderCaptor.getValue().getOrderId());
        assertEquals(Integer.valueOf(8), orderCaptor.getValue().getStatus());
        assertEquals(new BigDecimal("12.34"), orderCaptor.getValue().getIncome());

        ArgumentCaptor<MtOrderDetail> detailCaptor = ArgumentCaptor.forClass(MtOrderDetail.class);
        verify(mtOrderDetailMapper).deleteByMtOrderId("order-001");
        verify(mtOrderDetailMapper).insert(detailCaptor.capture());
        assertEquals("order-001", detailCaptor.getValue().getMtOrderId());
        assertEquals("米饭", detailCaptor.getValue().getFoodName());
        assertEquals(Integer.valueOf(2), detailCaptor.getValue().getQuantity());
        verify(mtShopPoiIdBackfillService).tryBackfillFromOrderCallback(shop, "order-001");
    }

    @Test
    void shouldUpdateExistingOrderAndRebuildDetailsWhenCompletionCallbackArrives() {
        MtOrder existing = new MtOrder();
        existing.setId(99L);
        existing.setOrderId("order-002");
        existing.setIncome(null);

        MtShop shop = buildShop();
        when(mtShopService.findByEpoiId("shop-001")).thenReturn(shop);
        when(mtShopPoiIdBackfillService.tryBackfillFromOrderCallback(shop, "order-002")).thenReturn(shop);
        when(mtOrderMapper.selectByOrderIdForUpdate("order-002")).thenReturn(existing);

        mtOrderService.handleOrderNotify(buildParameterMap(buildCompletedMessage("order-002", "2580")));

        ArgumentCaptor<MtOrder> orderCaptor = ArgumentCaptor.forClass(MtOrder.class);
        verify(mtOrderMapper).updateById(orderCaptor.capture());
        assertEquals(Long.valueOf(99L), orderCaptor.getValue().getId());
        assertEquals(Integer.valueOf(8), orderCaptor.getValue().getStatus());
        assertEquals(new BigDecimal("25.80"), orderCaptor.getValue().getIncome());

        verify(mtOrderDetailMapper).deleteByMtOrderId(eq("order-002"));
        verify(mtOrderDetailMapper).insert(any(MtOrderDetail.class));
    }

    @Test
    void shouldPersistIncomeWhenOrderIsNotCompletedYet() {
        MtShop shop = buildShop();
        when(mtShopService.findByEpoiId("shop-001")).thenReturn(shop);
        when(mtShopPoiIdBackfillService.tryBackfillFromOrderCallback(shop, "order-003")).thenReturn(shop);
        when(mtOrderMapper.selectByOrderIdForUpdate("order-003")).thenReturn(null);

        mtOrderService.handleOrderNotify(buildParameterMap(
                "{\"order_id\":\"order-003\",\"status\":\"4\",\"poiReceiveDetail\":\"{\\\"wmPoiReceiveCent\\\":1888}\","
                        + "\"wmAppOrderFoods\":\"[{\\\"food_name\\\":\\\"米饭\\\",\\\"quantity\\\":\\\"1\\\"}]\"}"
        ));

        ArgumentCaptor<MtOrder> orderCaptor = ArgumentCaptor.forClass(MtOrder.class);
        verify(mtOrderMapper).insert(orderCaptor.capture());
        assertEquals(new BigDecimal("18.88"), orderCaptor.getValue().getIncome());
    }

    @Test
    void shouldContinuePersistingOrderWhenPoiIdBackfillThrows() {
        MtShop shop = buildShop();
        shop.setPoiId(null);
        when(mtShopService.findByEpoiId("shop-001")).thenReturn(shop);
        when(mtOrderMapper.selectByOrderIdForUpdate("order-004")).thenReturn(null);
        when(mtShopPoiIdBackfillService.tryBackfillFromOrderCallback(shop, "order-004"))
                .thenThrow(new IllegalStateException("boom"));

        mtOrderService.handleOrderNotify(buildParameterMap(buildCompletedMessage("order-004", "990")));

        ArgumentCaptor<MtOrder> orderCaptor = ArgumentCaptor.forClass(MtOrder.class);
        verify(mtOrderMapper).insert(orderCaptor.capture());
        assertEquals("order-004", orderCaptor.getValue().getOrderId());
        assertNull(orderCaptor.getValue().getPoiId());
        verify(mtOrderDetailMapper).insert(any(MtOrderDetail.class));
    }

    @Test
    void shouldPersistHistoricalOrderDetailPayload() {
        MtShop shop = buildShop();
        when(mtOrderMapper.selectByOrderIdForUpdate("order-005")).thenReturn(null);

        mtOrderService.syncHistoricalOrderDetail(shop, Map.of(
                "orderId", "order-005",
                "status", 8,
                "poiReceiveDetail", "{\"wmPoiReceiveCent\":1999}",
                "detail", List.of(Map.of("foodName", "面条", "quantity", 1, "price", 12.0))
        ));

        ArgumentCaptor<MtOrder> orderCaptor = ArgumentCaptor.forClass(MtOrder.class);
        verify(mtOrderMapper).insert(orderCaptor.capture());
        assertEquals("order-005", orderCaptor.getValue().getOrderId());
        assertEquals(new BigDecimal("19.99"), orderCaptor.getValue().getIncome());

        ArgumentCaptor<MtOrderDetail> detailCaptor = ArgumentCaptor.forClass(MtOrderDetail.class);
        verify(mtOrderDetailMapper).insert(detailCaptor.capture());
        assertEquals("面条", detailCaptor.getValue().getFoodName());
    }

    @Test
    void shouldNotDowngradeExistingOrderStatusWhenOlderCallbackArrives() {
        MtOrder existing = new MtOrder();
        existing.setId(100L);
        existing.setOrderId("order-006");
        existing.setStatus(4);
        existing.setOrderConfirmTime(1777020151L);

        MtShop shop = buildShop();
        when(mtShopService.findByEpoiId("shop-001")).thenReturn(shop);
        when(mtShopPoiIdBackfillService.tryBackfillFromOrderCallback(shop, "order-006")).thenReturn(shop);
        when(mtOrderMapper.selectByOrderIdForUpdate("order-006")).thenReturn(existing);

        mtOrderService.handleOrderNotify(buildParameterMap(
                "{\"order_id\":\"order-006\",\"status\":\"2\",\"orderConfirmTime\":\"0\","
                        + "\"wmAppOrderFoods\":\"[{\\\"food_name\\\":\\\"米饭\\\",\\\"quantity\\\":\\\"1\\\"}]\"}"
        ));

        ArgumentCaptor<MtOrder> orderCaptor = ArgumentCaptor.forClass(MtOrder.class);
        verify(mtOrderMapper).updateById(orderCaptor.capture());
        assertEquals(Integer.valueOf(4), orderCaptor.getValue().getStatus());
        assertEquals(Long.valueOf(1777020151L), orderCaptor.getValue().getOrderConfirmTime());
    }

    @Test
    void shouldFallbackToUpdateWhenInsertHitsDuplicateKey() {
        MtShop shop = buildShop();
        MtOrder latest = new MtOrder();
        latest.setId(200L);
        latest.setOrderId("order-007");
        latest.setStatus(2);
        when(mtShopService.findByEpoiId("shop-001")).thenReturn(shop);
        when(mtShopPoiIdBackfillService.tryBackfillFromOrderCallback(shop, "order-007")).thenReturn(shop);
        when(mtOrderMapper.selectByOrderIdForUpdate("order-007")).thenReturn(null, latest);
        when(mtOrderMapper.insert(any(MtOrder.class))).thenThrow(new DuplicateKeyException("duplicate"));

        mtOrderService.handleOrderNotify(buildParameterMap(buildCompletedMessage("order-007", "3154")));

        ArgumentCaptor<MtOrder> orderCaptor = ArgumentCaptor.forClass(MtOrder.class);
        verify(mtOrderMapper).updateById(orderCaptor.capture());
        assertEquals(Long.valueOf(200L), orderCaptor.getValue().getId());
        assertEquals(Integer.valueOf(8), orderCaptor.getValue().getStatus());
        verify(mtOrderDetailMapper).deleteByMtOrderId("order-007");
    }

    private MtShop buildShop() {
        MtShop shop = new MtShop();
        shop.setId(1L);
        shop.setPoiId("poi-001");
        return shop;
    }

    private Map<String, String[]> buildParameterMap(String message) {
        return Map.of(
                "ePoiId", new String[]{"shop-001"},
                "message", new String[]{message}
        );
    }

    private String buildCompletedMessage(String orderId, String incomeCent) {
        return "{\"order_id\":\"" + orderId + "\","
                + "\"status\":\"8\","
                + "\"recipient_name\":\"张三\","
                + "\"poiReceiveDetail\":\"{\\\"wmPoiReceiveCent\\\":" + incomeCent + "}\","
                + "\"wmAppOrderFoods\":\"[{\\\"food_name\\\":\\\"米饭\\\",\\\"quantity\\\":\\\"2\\\",\\\"price\\\":3.5}]\"}";
    }
}
