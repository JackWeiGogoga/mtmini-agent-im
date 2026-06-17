package com.gogoga.backend.service;

import com.gogoga.backend.constant.YouzanConstant;
import com.gogoga.backend.entity.youzan.YzAppOrder;
import com.gogoga.backend.entity.youzan.YzCallbackEvent;
import com.gogoga.backend.entity.youzan.YzShop;
import com.gogoga.backend.mapper.YzAppAuthMapper;
import com.gogoga.backend.mapper.YzAppOrderMapper;
import com.gogoga.backend.mapper.YzCallbackEventMapper;
import com.gogoga.backend.mapper.YzShopMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class YouzanCallbackServiceTest {

    @Mock
    private YouzanMessageDecryptService youzanMessageDecryptService;

    @Mock
    private YouzanTokenService youzanTokenService;

    @Mock
    private YzCallbackEventMapper yzCallbackEventMapper;

    @Mock
    private YzShopMapper yzShopMapper;

    @Mock
    private YzAppOrderMapper yzAppOrderMapper;

    @Mock
    private YzAppAuthMapper yzAppAuthMapper;

    @InjectMocks
    private YouzanCallbackService youzanCallbackService;

    @Test
    void shouldPersistSubscribeMessageWithoutMeituanRelation() {
        mockEventInsert();
        String payload = "{\"type\":\"APP_SUBSCRIBE\",\"orderNo\":\"E001\",\"kdtId\":30311000,\"rootKdtId\":30311000,"
                + "\"appId\":100000401,\"buyerId\":910011,\"buyerPhone\":\"158****6536\",\"openId\":\"open-1\","
                + "\"shopName\":\"有赞店\",\"skuVersionText\":\"基础版\",\"skuIntervalText\":\"30\","
                + "\"price\":21000,\"status\":20,\"payTime\":1553145106305}";
        when(youzanMessageDecryptService.decrypt("cipher%2Btext")).thenReturn(payload);
        doAnswer(invocation -> {
            YzShop shop = invocation.getArgument(0);
            shop.setId(11L);
            return 1;
        }).when(yzShopMapper).insert(any(YzShop.class));

        youzanCallbackService.handleCallback(request("message=cipher%2Btext"));

        ArgumentCaptor<YzAppOrder> captor = ArgumentCaptor.forClass(YzAppOrder.class);
        verify(yzAppOrderMapper).insert(captor.capture());
        YzAppOrder order = captor.getValue();
        assertEquals("E001", order.getOrderNo());
        assertEquals(11L, order.getYzShopId());
        assertEquals("UNUSED", order.getUseStatus());
        assertNull(order.getUsedMtShopId());
        verify(yzCallbackEventMapper, atLeastOnce()).updateById(any(YzCallbackEvent.class));
    }

    @Test
    void shouldPreserveUsedStatusWhenRepeatedSubscribeMessageArrives() {
        mockEventInsert();
        String payload = "{\"type\":\"APP_SUBSCRIBE\",\"orderNo\":\"E002\",\"kdtId\":30311001,"
                + "\"skuIntervalText\":\"30\",\"status\":20}";
        when(youzanMessageDecryptService.decrypt("cipher")).thenReturn(payload);
        YzShop shop = new YzShop();
        shop.setId(12L);
        shop.setKdtId(30311001L);
        when(yzShopMapper.selectByKdtId(30311001L)).thenReturn(shop);
        YzAppOrder existing = new YzAppOrder();
        existing.setId(21L);
        existing.setOrderNo("E002");
        existing.setUseStatus(YouzanConstant.ORDER_USE_STATUS_USED);
        existing.setUsedMtShopId(100L);
        when(yzAppOrderMapper.selectByOrderNo("E002")).thenReturn(existing);

        youzanCallbackService.handleCallback(request("message=cipher"));

        ArgumentCaptor<YzAppOrder> captor = ArgumentCaptor.forClass(YzAppOrder.class);
        verify(yzAppOrderMapper).updateById(captor.capture());
        assertEquals(YouzanConstant.ORDER_USE_STATUS_USED, captor.getValue().getUseStatus());
        assertEquals(100L, captor.getValue().getUsedMtShopId());
    }

    @Test
    void shouldAuditMalformedMessageWithoutBusinessWrites() {
        mockEventInsert();
        when(youzanMessageDecryptService.decrypt("bad")).thenThrow(new IllegalArgumentException("bad cipher"));

        youzanCallbackService.handleCallback(request("message=bad"));

        verify(yzShopMapper, never()).insert(any());
        verify(yzAppOrderMapper, never()).insert(any());
        ArgumentCaptor<YzCallbackEvent> captor = ArgumentCaptor.forClass(YzCallbackEvent.class);
        verify(yzCallbackEventMapper).updateById(captor.capture());
        assertEquals(YouzanConstant.PROCESS_STATUS_FAILED, captor.getValue().getProcessStatus());
    }

    private MockHttpServletRequest request(String queryString) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/callbacks/youzan/app");
        request.setQueryString(queryString);
        return request;
    }

    private void mockEventInsert() {
        doAnswer(invocation -> {
            YzCallbackEvent event = invocation.getArgument(0);
            event.setId(1L);
            return 1;
        }).when(yzCallbackEventMapper).insert(any(YzCallbackEvent.class));
    }
}
