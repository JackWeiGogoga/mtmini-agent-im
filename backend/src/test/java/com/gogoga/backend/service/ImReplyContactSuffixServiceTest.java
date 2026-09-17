package com.gogoga.backend.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImReplyContactSuffixServiceTest {

    @Mock
    private ShopOperationSettingsService settingsService;

    @InjectMocks
    private ImReplyContactSuffixService service;

    @Test
    void appendsSuffixWhenShopHasContactPhone() {
        when(settingsService.resolveEffectiveContactPhone(100L)).thenReturn("400-820-8820");

        assertEquals("好的，已经为您备注。\n-门店400-820-8820",
                service.append(100L, "好的，已经为您备注。"));
    }

    @Test
    void keepsContentUnchangedWhenNoContactPhone() {
        when(settingsService.resolveEffectiveContactPhone(100L)).thenReturn(null);

        assertEquals("好的，已经为您备注。", service.append(100L, "好的，已经为您备注。"));
    }

    @Test
    void skipsAppendWhenReplyAlreadyStatesTheSameNumber() {
        // 知识库里就写了门店电话时，模型可能已经在正文里播报过，再拼一次会重复
        assertEquals("门店电话是 400 820 8820，欢迎来电。",
                service.appendPhone("门店电话是 400 820 8820，欢迎来电。", "400-820-8820"));
    }

    @Test
    void appendsWhenReplyMentionsADifferentNumber() {
        assertEquals("订单尾号 1234 已确认。\n-门店400-820-8820",
                service.appendPhone("订单尾号 1234 已确认。", "400-820-8820"));
    }

    @Test
    void trimsContentBeforeAppending() {
        assertEquals("已收到。\n-门店13800000000",
                service.appendPhone("  已收到。\n\n ", "13800000000"));
    }

    @Test
    void returnsContentUnchangedWhenContentIsBlank() {
        assertNull(service.appendPhone(null, "13800000000"));
        assertEquals("   ", service.appendPhone("   ", "13800000000"));
    }
}
