package com.gogoga.backend.service.sms.notification;

import com.gogoga.backend.config.SmsProperties;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.entity.sms.SmsNotificationEvent;
import com.gogoga.backend.enums.SmsNotificationSceneEnum;
import com.gogoga.backend.mapper.MtShopRightMapper;
import com.gogoga.backend.service.MtShopService;
import com.gogoga.backend.service.sms.ShopContactPhoneParser;
import com.gogoga.backend.util.JsonUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MerchantLifecycleSmsSceneHandlerTest {

    private MtShopService shopService;
    private MtShopRightMapper rightMapper;
    private ShopContactPhoneParser phoneParser;
    private MerchantLifecycleSmsSceneHandler handler;

    @BeforeEach
    void setUp() {
        shopService = mock(MtShopService.class);
        rightMapper = mock(MtShopRightMapper.class);
        phoneParser = mock(ShopContactPhoneParser.class);
        handler = new MerchantLifecycleSmsSceneHandler(
                shopService,
                rightMapper,
                phoneParser,
                new SmsProperties()
        );
    }

    @Test
    void shouldReadLatestShopContactsWhenEventBecomesExecutable() {
        SmsNotificationEvent event = event(SmsNotificationSceneEnum.PURCHASE_GUIDE,
                JsonUtil.toJson(Map.of()));
        MtShop latestShop = new MtShop();
        latestShop.setContactPhone("19355654200/13858611082");
        when(shopService.findById(10L)).thenReturn(latestShop);
        when(phoneParser.parse(latestShop)).thenReturn(List.of("19355654200", "13858611082"));

        SmsScenePreparation result = handler.prepare(event);

        assertFalse(result.cancelled());
        assertEquals(List.of("19355654200", "13858611082"), result.recipients());
    }

    @Test
    void shouldCancelOldExpiryEventAfterRenewalChangesMaximumExpiry() {
        LocalDateTime expected = LocalDateTime.of(2026, 8, 1, 23, 59, 59);
        SmsNotificationEvent event = event(SmsNotificationSceneEnum.APP_EXPIRED,
                JsonUtil.toJson(Map.of("expectedExpireAt", expected.toString())));
        when(rightMapper.selectMaxAppRightExpireAt(10L)).thenReturn(expected.plusDays(30));

        SmsScenePreparation result = handler.prepare(event);

        assertTrue(result.cancelled());
        verify(shopService, never()).findById(any());
    }

    @Test
    void shouldCancelExpiryDeliveryWhenAnyApplicationRightIsStillActive() {
        LocalDateTime expected = LocalDateTime.of(2026, 8, 1, 23, 59, 59);
        SmsNotificationEvent event = event(SmsNotificationSceneEnum.APP_EXPIRED,
                JsonUtil.toJson(Map.of("expectedExpireAt", expected.toString())));
        when(rightMapper.selectMaxAppRightExpireAt(10L)).thenReturn(expected);
        when(rightMapper.existsActiveAppRight(org.mockito.ArgumentMatchers.eq(10L), any())).thenReturn(true);

        assertFalse(handler.canSend(event, "13800138000"));
    }

    @Test
    void shouldAllowExpiredEventAndUseCurrentRecipientWhenRightsRemainExpired() {
        LocalDateTime expected = LocalDateTime.of(2026, 1, 1, 23, 59, 59);
        SmsNotificationEvent event = event(SmsNotificationSceneEnum.APP_EXPIRED,
                JsonUtil.toJson(Map.of("expectedExpireAt", expected.toString())));
        MtShop shop = new MtShop();
        when(rightMapper.selectMaxAppRightExpireAt(10L)).thenReturn(expected);
        when(rightMapper.existsActiveAppRight(org.mockito.ArgumentMatchers.eq(10L), any())).thenReturn(false);
        when(shopService.findById(10L)).thenReturn(shop);
        when(phoneParser.parse(shop)).thenReturn(List.of("13800138000"));

        assertTrue(handler.canSend(event, "13800138000"));
    }

    private SmsNotificationEvent event(SmsNotificationSceneEnum scene, String contextJson) {
        SmsNotificationEvent event = new SmsNotificationEvent();
        event.setScene(scene.name());
        event.setShopId(10L);
        event.setContextJson(contextJson);
        return event;
    }
}
