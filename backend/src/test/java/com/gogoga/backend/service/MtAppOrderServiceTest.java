package com.gogoga.backend.service;

import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.entity.mt.MtAppOrder;
import com.gogoga.backend.mapper.MtAppOrderMapper;
import com.meituan.sdk.MeituanClient;
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
    private MtShopRightService mtShopRightService;

    @InjectMocks
    private MtAppOrderService mtAppOrderService;

    @Test
    void shouldMarkOrderProcessedOnlyAfterRightApplied() {
        MtAppOrder appliedOrder = buildPendingOrder(1L, "order-applied");
        MtAppOrder unappliedOrder = buildPendingOrder(2L, "order-unapplied");
        when(mtAppOrderMapper.selectPendingRightOrders()).thenReturn(List.of(appliedOrder, unappliedOrder));
        when(mtShopRightService.applyOrderRights(any(MtAppOrder.class), any())).thenReturn(true, false);

        mtAppOrderService.applyPendingRights();

        ArgumentCaptor<MtAppOrder> captor = ArgumentCaptor.forClass(MtAppOrder.class);
        verify(mtAppOrderMapper).updateById(captor.capture());
        assertSame(appliedOrder, captor.getValue());
        assertEquals(1, appliedOrder.getType());
        assertEquals(0, unappliedOrder.getType());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldShareBuyInfoCreditStateAcrossPendingOrders() {
        MtAppOrder firstOrder = buildPendingOrder(1L, "order-1");
        MtAppOrder secondOrder = buildPendingOrder(2L, "order-2");
        when(mtAppOrderMapper.selectPendingRightOrders()).thenReturn(List.of(firstOrder, secondOrder));
        when(mtShopRightService.applyOrderRights(any(MtAppOrder.class), any())).thenReturn(true, true);

        mtAppOrderService.applyPendingRights();

        ArgumentCaptor<Set<String>> setCaptor = ArgumentCaptor.forClass(Set.class);
        verify(mtShopRightService).applyOrderRights(eq(firstOrder), setCaptor.capture());
        verify(mtShopRightService).applyOrderRights(eq(secondOrder), setCaptor.capture());
        assertSame(setCaptor.getAllValues().get(0), setCaptor.getAllValues().get(1));
    }

    @Test
    void shouldDoNothingWhenNoPendingOrdersExist() {
        when(mtAppOrderMapper.selectPendingRightOrders()).thenReturn(List.of());

        mtAppOrderService.applyPendingRights();

        verify(mtShopRightService, never()).applyOrderRights(any(MtAppOrder.class), any());
        verify(mtAppOrderMapper, never()).updateById(any());
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
