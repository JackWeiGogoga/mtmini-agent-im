package com.gogoga.backend.service;

import com.gogoga.backend.entity.mt.MtAppOrder;
import com.gogoga.backend.mapper.MtAppOrderMapper;
import com.gogoga.backend.service.sms.notification.MerchantLifecycleSmsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MtAppOrderRightApplicationServiceTest {

    private MtAppOrderMapper orderMapper;
    private MtShopRightService rightService;
    private MerchantLifecycleSmsService lifecycleSmsService;
    private MtAppOrderRightApplicationService service;

    @BeforeEach
    void setUp() {
        orderMapper = mock(MtAppOrderMapper.class);
        rightService = mock(MtShopRightService.class);
        lifecycleSmsService = mock(MerchantLifecycleSmsService.class);
        service = new MtAppOrderRightApplicationService(orderMapper, rightService, lifecycleSmsService);
    }

    @Test
    void shouldApplyRightsMarkOrderAndEnqueuePurchaseEventWithinOneMethod() {
        MtAppOrder order = order(1L, 7);
        when(orderMapper.selectByIdForUpdate(1L)).thenReturn(order);
        when(rightService.applyOrderRights(any(), any())).thenAnswer(invocation -> {
            invocation.<Set<String>>getArgument(1).add("10:H5_PROMOTION");
            return true;
        });

        MtAppOrderRightApplicationResult result = service.applyOne(order, Set.of());

        assertTrue(result.applied());
        assertEquals(Set.of("10:H5_PROMOTION"), result.consumedBuyInfoCreditKeys());
        assertEquals(1, order.getType());
        verify(orderMapper).updateById(order);
        verify(lifecycleSmsService).enqueuePurchaseGuide(
                org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.eq("order-1"),
                org.mockito.ArgumentMatchers.eq(7),
                any()
        );
    }

    @Test
    void shouldLeaveOrderPendingWhenRightsCannotBeApplied() {
        MtAppOrder order = order(1L, 7);
        when(orderMapper.selectByIdForUpdate(1L)).thenReturn(order);
        when(rightService.applyOrderRights(any(), any())).thenReturn(false);

        MtAppOrderRightApplicationResult result = service.applyOne(order, Set.of());

        assertFalse(result.applied());
        assertEquals(0, order.getType());
        verify(orderMapper, never()).updateById(any());
        verify(lifecycleSmsService, never()).enqueuePurchaseGuide(any(), any(), any(), any());
    }

    @Test
    void shouldKeepRightsAndOrderWhenSmsTriggerUnexpectedlyFails() {
        MtAppOrder order = order(1L, 7);
        when(orderMapper.selectByIdForUpdate(1L)).thenReturn(order);
        when(rightService.applyOrderRights(any(), any())).thenReturn(true);
        when(lifecycleSmsService.enqueuePurchaseGuide(any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("event insert failed"));

        MtAppOrderRightApplicationResult result = assertDoesNotThrow(() -> service.applyOne(order, Set.of()));

        assertTrue(result.applied());
        assertEquals(1, order.getType());
        verify(orderMapper).updateById(order);
    }

    @Test
    void shouldKeepAppliedRightsAndProcessedOrderWhenUnsupportedDaysAreSkippedBySmsLayer() {
        MtAppOrder order = order(1L, 15);
        when(orderMapper.selectByIdForUpdate(1L)).thenReturn(order);
        when(rightService.applyOrderRights(any(), any())).thenReturn(true);
        when(lifecycleSmsService.enqueuePurchaseGuide(any(), any(), any(), any())).thenReturn(null);

        MtAppOrderRightApplicationResult result = service.applyOne(order, Set.of());

        assertTrue(result.applied());
        assertEquals(1, order.getType());
        verify(orderMapper).updateById(order);
    }

    private MtAppOrder order(Long id, int days) {
        MtAppOrder order = new MtAppOrder();
        order.setId(id);
        order.setOrderId("order-" + id);
        order.setShopId(10L);
        order.setVersion("ver-1");
        order.setDays(days);
        order.setEndTime(1_800_000_000L);
        order.setType(0);
        return order;
    }
}
