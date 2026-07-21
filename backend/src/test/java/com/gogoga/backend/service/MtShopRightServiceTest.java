package com.gogoga.backend.service;

import com.gogoga.backend.annotation.RequiresShopRight;
import com.gogoga.backend.client.MtAppBuyInfoOpenApiClient;
import com.gogoga.backend.constant.MtShopRightCodeConstant;
import com.gogoga.backend.entity.mt.MtAppOrder;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.entity.mt.MtShopRight;
import com.gogoga.backend.entity.mt.MtVersionServiceMapping;
import com.gogoga.backend.mapper.MtShopRightMapper;
import com.gogoga.backend.mapper.MtVersionServiceMappingMapper;
import com.meituan.sdk.model.wmoperNg.waimaiad.adAppBuyInfo.AdAppBuyInfoResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 门店权益服务测试
 *
 * @author system
 * @since 2026-03-25
 */
@ExtendWith(MockitoExtension.class)
class MtShopRightServiceTest {

    @Mock
    private MtShopRightMapper mtShopRightMapper;

    @Mock
    private MtVersionServiceMappingMapper mtVersionServiceMappingMapper;

    @Mock
    private MtH5ShopService mtH5ShopService;

    @Mock
    private MtAppBuyInfoOpenApiClient mtAppBuyInfoOpenApiClient;

    @InjectMocks
    private MtShopRightService mtShopRightService;

    @Test
    void shouldGrantPromotionAccessWhenVipRightIsActive() {
        MtShop shop = new MtShop();
        shop.setId(10L);
        when(mtH5ShopService.getCurrentShop()).thenReturn(shop);

        MtShopRight vipRight = new MtShopRight();
        vipRight.setShopId(10L);
        vipRight.setServiceCode(MtShopRightCodeConstant.VIP_CODE);
        vipRight.setType(2);
        vipRight.setExpireAt(LocalDateTime.now().plusDays(1));
        when(mtShopRightMapper.selectByShopId(10L)).thenReturn(List.of(vipRight));

        boolean hasRight = mtShopRightService.hasCurrentRight(RequiresShopRight.Logical.OR, MtShopRightCodeConstant.H5_PROMOTION);

        assertTrue(hasRight);
    }

    @Test
    void shouldDenyPromotionAccessWhenNoActiveRightExists() {
        MtShop shop = new MtShop();
        shop.setId(11L);
        when(mtH5ShopService.getCurrentShop()).thenReturn(shop);

        MtShopRight expiredRight = new MtShopRight();
        expiredRight.setShopId(11L);
        expiredRight.setServiceCode(MtShopRightCodeConstant.H5_PROMOTION);
        expiredRight.setType(2);
        expiredRight.setExpireAt(LocalDateTime.now().minusMinutes(1));
        when(mtShopRightMapper.selectByShopId(11L)).thenReturn(List.of(expiredRight));

        boolean hasRight = mtShopRightService.hasCurrentRight(RequiresShopRight.Logical.OR, MtShopRightCodeConstant.H5_PROMOTION);

        assertFalse(hasRight);
    }

    @Test
    void shouldExtendActiveRightByOrderDaysWhenApplyingOrderRights() {
        MtVersionServiceMapping mapping = new MtVersionServiceMapping();
        mapping.setMtVersion("ver-1");
        mapping.setServiceCode(MtShopRightCodeConstant.H5_PROMOTION);
        mapping.setEnabled(true);
        when(mtVersionServiceMappingMapper.selectEnabledByMtVersion("ver-1")).thenReturn(List.of(mapping));

        MtShopRight currentRight = new MtShopRight();
        currentRight.setId(99L);
        currentRight.setShopId(20L);
        currentRight.setServiceCode(MtShopRightCodeConstant.H5_PROMOTION);
        currentRight.setType(2);
        LocalDateTime currentExpireAt = LocalDateTime.now().plusDays(3).withNano(0);
        currentRight.setExpireAt(currentExpireAt);
        currentRight.setLastOrderId("old-order");
        when(mtShopRightMapper.selectByShopIdAndServiceCode(20L, MtShopRightCodeConstant.H5_PROMOTION))
                .thenReturn(currentRight);

        MtAppOrder order = new MtAppOrder();
        order.setShopId(20L);
        order.setOrderId("order-001");
        order.setVersion("ver-1");
        order.setDays(30);
        order.setEndTime(LocalDateTime.now().plusDays(30).atZone(ZoneId.systemDefault()).toEpochSecond());

        boolean applied = mtShopRightService.applyOrderRights(order);

        assertTrue(applied);
        ArgumentCaptor<MtShopRight> captor = ArgumentCaptor.forClass(MtShopRight.class);
        verify(mtShopRightMapper).updateById(captor.capture());
        assertEquals(currentExpireAt.plusDays(30), captor.getValue().getExpireAt());
        assertEquals("order-001", captor.getValue().getLastOrderId());
    }

    @Test
    void shouldNotShortenAccumulatedRightWhenSyncingBuyInfo() {
        MtVersionServiceMapping mapping = new MtVersionServiceMapping();
        mapping.setMtVersion("ver-2");
        mapping.setServiceCode(MtShopRightCodeConstant.H5_PROMOTION);
        mapping.setEnabled(true);
        when(mtVersionServiceMappingMapper.selectEnabledByMtVersion("ver-2")).thenReturn(List.of(mapping));

        LocalDateTime existingExpireAt = LocalDateTime.now().plusDays(37).withNano(0);
        MtShopRight currentRight = new MtShopRight();
        currentRight.setId(100L);
        currentRight.setShopId(21L);
        currentRight.setServiceCode(MtShopRightCodeConstant.H5_PROMOTION);
        currentRight.setType(2);
        currentRight.setExpireAt(existingExpireAt);
        currentRight.setLastOrderId("order-037");
        when(mtShopRightMapper.selectByShopIdAndServiceCode(21L, MtShopRightCodeConstant.H5_PROMOTION))
                .thenReturn(currentRight);

        AdAppBuyInfoResponse buyInfo = new AdAppBuyInfoResponse();
        buyInfo.setVersion("ver-2");
        buyInfo.setEndTime(LocalDateTime.now().plusDays(30).atZone(ZoneId.systemDefault()).toEpochSecond());
        when(mtAppBuyInfoOpenApiClient.queryBuyInfo("token-1")).thenReturn(Optional.of(buyInfo));

        MtShop shop = new MtShop();
        shop.setId(21L);

        boolean applied = mtShopRightService.syncRightsFromBuyInfo(shop, "token-1");

        assertTrue(applied);
        ArgumentCaptor<MtShopRight> captor = ArgumentCaptor.forClass(MtShopRight.class);
        verify(mtShopRightMapper).updateById(captor.capture());
        assertEquals(existingExpireAt, captor.getValue().getExpireAt());
        assertEquals("order-037", captor.getValue().getLastOrderId());
    }

    @Test
    void shouldGrantMissingRightFromFirstOrderEndTime() {
        MtVersionServiceMapping mapping = new MtVersionServiceMapping();
        mapping.setMtVersion("ver-3");
        mapping.setServiceCode(MtShopRightCodeConstant.H5_PROMOTION);
        mapping.setEnabled(true);
        when(mtVersionServiceMappingMapper.selectEnabledByMtVersion("ver-3")).thenReturn(List.of(mapping));
        when(mtShopRightMapper.selectByShopIdAndServiceCode(22L, MtShopRightCodeConstant.H5_PROMOTION)).thenReturn(null);

        LocalDateTime orderExpireAt = LocalDateTime.now().plusDays(7).withNano(0);
        MtAppOrder order = new MtAppOrder();
        order.setShopId(22L);
        order.setOrderId("order-007");
        order.setVersion("ver-3");
        order.setDays(7);
        order.setEndTime(orderExpireAt.atZone(ZoneId.systemDefault()).toEpochSecond());

        boolean applied = mtShopRightService.applyOrderRights(order);

        assertTrue(applied);
        ArgumentCaptor<MtShopRight> captor = ArgumentCaptor.forClass(MtShopRight.class);
        verify(mtShopRightMapper).insert(captor.capture());
        assertEquals(orderExpireAt, captor.getValue().getExpireAt());
        assertEquals("order-007", captor.getValue().getLastOrderId());
    }

    @Test
    void shouldNotApplyOrderWhenRequiredFieldsAreMissing() {
        MtAppOrder order = new MtAppOrder();
        order.setShopId(23L);
        order.setOrderId("order-missing-days");
        order.setVersion("ver-4");
        order.setEndTime(LocalDateTime.now().plusDays(7).atZone(ZoneId.systemDefault()).toEpochSecond());

        boolean applied = mtShopRightService.applyOrderRights(order);

        assertFalse(applied);
        verify(mtVersionServiceMappingMapper, never()).selectEnabledByMtVersion(any());
        verify(mtShopRightMapper, never()).insert(any());
        verify(mtShopRightMapper, never()).updateById(any());
    }

    @Test
    void shouldConsumeBuyInfoCreditOnlyOnceForSameEndTimeOrders() {
        MtVersionServiceMapping mapping = new MtVersionServiceMapping();
        mapping.setMtVersion("ver-5");
        mapping.setServiceCode(MtShopRightCodeConstant.H5_PROMOTION);
        mapping.setEnabled(true);
        when(mtVersionServiceMappingMapper.selectEnabledByMtVersion("ver-5")).thenReturn(List.of(mapping));

        LocalDateTime buyInfoExpireAt = LocalDateTime.now().plusDays(7).withNano(0);
        MtShopRight currentRight = new MtShopRight();
        currentRight.setId(101L);
        currentRight.setShopId(24L);
        currentRight.setServiceCode(MtShopRightCodeConstant.H5_PROMOTION);
        currentRight.setType(2);
        currentRight.setExpireAt(buyInfoExpireAt);
        currentRight.setLastOrderId(null);
        when(mtShopRightMapper.selectByShopIdAndServiceCode(24L, MtShopRightCodeConstant.H5_PROMOTION))
                .thenReturn(currentRight);

        MtAppOrder firstOrder = buildOrder(24L, "order-same-1", "ver-5", 7, buyInfoExpireAt);
        MtAppOrder secondOrder = buildOrder(24L, "order-same-2", "ver-5", 7, buyInfoExpireAt);
        Set<String> consumedBuyInfoCreditKeys = new HashSet<>();

        boolean firstApplied = mtShopRightService.applyOrderRights(firstOrder, consumedBuyInfoCreditKeys);
        assertTrue(firstApplied);
        assertEquals(buyInfoExpireAt, currentRight.getExpireAt());
        assertEquals("order-same-1", currentRight.getLastOrderId());

        boolean secondApplied = mtShopRightService.applyOrderRights(secondOrder, consumedBuyInfoCreditKeys);
        assertTrue(secondApplied);
        assertEquals(buyInfoExpireAt.plusDays(7), currentRight.getExpireAt());
        assertEquals("order-same-2", currentRight.getLastOrderId());
    }

    @Test
    void shouldNotApplyOrderWhenVersionMappingIsMissing() {
        when(mtVersionServiceMappingMapper.selectEnabledByMtVersion("ver-missing")).thenReturn(List.of());

        MtAppOrder order = buildOrder(25L, "order-missing-mapping", "ver-missing", 7,
                LocalDateTime.now().plusDays(7).withNano(0));

        boolean applied = mtShopRightService.applyOrderRights(order);

        assertFalse(applied);
        verify(mtShopRightMapper, never()).insert(any());
        verify(mtShopRightMapper, never()).updateById(any());
    }

    @Test
    void shouldPreferVipRightForHomepageMembership() {
        MtShopRight promotionRight = new MtShopRight();
        promotionRight.setShopId(30L);
        promotionRight.setServiceCode(MtShopRightCodeConstant.H5_PROMOTION);
        promotionRight.setType(2);
        promotionRight.setExpireAt(LocalDateTime.now().plusDays(1));

        MtShopRight vipRight = new MtShopRight();
        vipRight.setShopId(30L);
        vipRight.setServiceCode(MtShopRightCodeConstant.VIP_CODE);
        vipRight.setType(2);
        vipRight.setExpireAt(LocalDateTime.now().plusDays(2));

        when(mtShopRightMapper.selectByShopId(30L)).thenReturn(List.of(promotionRight, vipRight));

        MtShopRight result = mtShopRightService.findHomepageActiveRight(30L);

        assertEquals(MtShopRightCodeConstant.VIP_CODE, result.getServiceCode());
    }

    @Test
    void shouldReturnNullWhenHomepageMembershipHasNoActiveRight() {
        MtShopRight expiredRight = new MtShopRight();
        expiredRight.setShopId(31L);
        expiredRight.setServiceCode(MtShopRightCodeConstant.H5_PROMOTION);
        expiredRight.setType(2);
        expiredRight.setExpireAt(LocalDateTime.now().minusMinutes(1));
        when(mtShopRightMapper.selectByShopId(31L)).thenReturn(List.of(expiredRight));

        MtShopRight result = mtShopRightService.findHomepageActiveRight(31L);

        assertNull(result);
    }

    @Test
    void shouldIgnoreAppOpenOnlyRightForHomepageMembership() {
        MtShopRight openOnlyRight = new MtShopRight();
        openOnlyRight.setShopId(32L);
        openOnlyRight.setServiceCode(MtShopRightCodeConstant.MT_APP_OPEN_ONLY);
        openOnlyRight.setType(2);
        openOnlyRight.setExpireAt(LocalDateTime.now().plusDays(30));
        when(mtShopRightMapper.selectByShopId(32L)).thenReturn(List.of(openOnlyRight));

        MtShopRight result = mtShopRightService.findHomepageActiveRight(32L);

        assertNull(result);
    }

    @Test
    void shouldGrantAutoReplyButNotBroadcastForAutoReplyOnlyRight() {
        when(mtShopRightMapper.selectByShopId(41L))
                .thenReturn(List.of(activeRight(41L, MtShopRightCodeConstant.IM_AUTO_REPLY_ONLY)));

        assertTrue(mtShopRightService.hasAutoReplyRight(41L));
        assertFalse(mtShopRightService.hasBroadcastRight(41L));
    }

    @Test
    void shouldGrantBothCapabilitiesForPromotionRight() {
        when(mtShopRightMapper.selectByShopId(42L))
                .thenReturn(List.of(activeRight(42L, MtShopRightCodeConstant.H5_PROMOTION)));

        assertTrue(mtShopRightService.hasAutoReplyRight(42L));
        assertTrue(mtShopRightService.hasBroadcastRight(42L));
    }

    @Test
    void shouldDenyBothCapabilitiesOnceAutoReplyOnlyRightExpired() {
        MtShopRight expired = activeRight(43L, MtShopRightCodeConstant.IM_AUTO_REPLY_ONLY);
        expired.setExpireAt(LocalDateTime.now().minusMinutes(1));
        when(mtShopRightMapper.selectByShopId(43L)).thenReturn(List.of(expired));

        assertFalse(mtShopRightService.hasAutoReplyRight(43L));
        assertFalse(mtShopRightService.hasBroadcastRight(43L));
    }

    @Test
    void shouldKeepBroadcastUntilPromotionRightExpiresEvenWhenAutoReplyOnlyIsHeld() {
        // 版本切换后两条权益并存：群发能力跟着 H5_PROMOTION 自己的到期时间走。
        MtShopRight expiredPromotion = activeRight(44L, MtShopRightCodeConstant.H5_PROMOTION);
        expiredPromotion.setExpireAt(LocalDateTime.now().minusMinutes(1));
        when(mtShopRightMapper.selectByShopId(44L)).thenReturn(List.of(
                expiredPromotion, activeRight(44L, MtShopRightCodeConstant.IM_AUTO_REPLY_ONLY)));

        assertTrue(mtShopRightService.hasAutoReplyRight(44L));
        assertFalse(mtShopRightService.hasBroadcastRight(44L));
    }

    @Test
    void shouldShowAutoReplyOnlyRightOnHomepageMembership() {
        when(mtShopRightMapper.selectByShopId(45L))
                .thenReturn(List.of(activeRight(45L, MtShopRightCodeConstant.IM_AUTO_REPLY_ONLY)));

        MtShopRight result = mtShopRightService.findHomepageActiveRight(45L);

        // 不纳入会员卡的话，只持有该权益的门店首页会拿不到有效期。
        assertEquals(MtShopRightCodeConstant.IM_AUTO_REPLY_ONLY, result.getServiceCode());
    }

    private MtShopRight activeRight(Long shopId, String serviceCode) {
        MtShopRight right = new MtShopRight();
        right.setShopId(shopId);
        right.setServiceCode(serviceCode);
        right.setType(2);
        right.setExpireAt(LocalDateTime.now().plusDays(30));
        return right;
    }

    private MtAppOrder buildOrder(Long shopId, String orderId, String version, Integer days, LocalDateTime expireAt) {
        MtAppOrder order = new MtAppOrder();
        order.setShopId(shopId);
        order.setOrderId(orderId);
        order.setVersion(version);
        order.setDays(days);
        order.setEndTime(expireAt.atZone(ZoneId.systemDefault()).toEpochSecond());
        return order;
    }
}
