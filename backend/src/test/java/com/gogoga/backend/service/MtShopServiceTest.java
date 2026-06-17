package com.gogoga.backend.service;

import com.gogoga.backend.config.PowerProperties;
import com.gogoga.backend.entity.mt.MtPowerGrant;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.mapper.MtPowerGrantMapper;
import com.gogoga.backend.mapper.MtShopMapper;
import com.meituan.sdk.auth.MeituanTokenData;
import com.meituan.sdk.model.wmoperNg.poi.wmoperngQueryPoiDetail.Data;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 美团店铺服务测试
 *
 * @author system
 * @since 2026-03-25
 */
@ExtendWith(MockitoExtension.class)
class MtShopServiceTest {

    @Mock
    private MtShopMapper mtShopMapper;

    @Mock
    private MtPromotionSettingService mtPromotionSettingService;

    @Mock
    private MtPowerGrantMapper mtPowerGrantMapper;

    @Mock
    private MtPowerAccountService mtPowerAccountService;

    @Mock
    private PowerProperties powerProperties;

    @InjectMocks
    private MtShopService mtShopService;

    @Test
    void shouldInitPromotionSettingAndGrantPowerWhenAuthorizedShopIsCreated() {
        MeituanTokenData tokenData = new MeituanTokenData();
        tokenData.setOpBizCode("epoi-001");
        tokenData.setOpBizName("测试门店");

        when(mtShopMapper.selectByEpoiId("epoi-001")).thenReturn(null);
        when(powerProperties.getNewAuthorizedShopGrantAmount()).thenReturn(800_000);
        doAnswer(invocation -> {
            MtShop shop = invocation.getArgument(0);
            shop.setId(100L);
            return 1;
        }).when(mtShopMapper).insert(any(MtShop.class));

        MtShop shop = mtShopService.saveAuthorizedShop(tokenData, null);

        assertEquals(100L, shop.getId());
        verify(mtPromotionSettingService).initDefaultForShopId(100L);
        verify(mtPowerGrantMapper).insert(argThat(grant ->
                Long.valueOf(100L).equals(grant.getShopId())
                        && Integer.valueOf(800_000).equals(grant.getPowerAmount())
                        && "新商户授权赠送".equals(grant.getGrantReason())
        ));
        verify(mtPowerAccountService).addAdminGrant(100L, 800_000);
    }

    @Test
    void shouldNotReinitPromotionSettingWhenAuthorizedShopAlreadyExists() {
        MeituanTokenData tokenData = new MeituanTokenData();
        tokenData.setOpBizCode("epoi-002");
        tokenData.setOpBizName("已有门店");

        MtShop existing = new MtShop();
        existing.setId(200L);
        existing.setEpoiId("epoi-002");
        existing.setName("已有门店");
        when(mtShopMapper.selectByEpoiId("epoi-002")).thenReturn(existing);

        MtShop shop = mtShopService.saveAuthorizedShop(tokenData, null);

        assertEquals(200L, shop.getId());
        verify(mtPromotionSettingService, never()).initDefaultForShopId(200L);
        verify(mtPowerGrantMapper, never()).insert(any(MtPowerGrant.class));
        verify(mtPowerAccountService, never()).addAdminGrant(anyLong(), anyInt());
    }

    @Test
    void shouldOnlyUpdateMissingPoiId() {
        MtShop missingPoiIdShop = new MtShop();
        missingPoiIdShop.setId(300L);

        MtShop updated = mtShopService.updatePoiIdIfMissing(missingPoiIdShop, "poi-300");
        assertEquals("poi-300", updated.getPoiId());
        verify(mtShopMapper).updateById(missingPoiIdShop);

        MtShop existingPoiIdShop = new MtShop();
        existingPoiIdShop.setId(301L);
        existingPoiIdShop.setPoiId("poi-existing");

        MtShop untouched = mtShopService.updatePoiIdIfMissing(existingPoiIdShop, "poi-new");
        assertEquals("poi-existing", untouched.getPoiId());
        verify(mtShopMapper, times(1)).updateById(any(MtShop.class));

        MtShop blankPoiIdInputShop = new MtShop();
        blankPoiIdInputShop.setId(302L);
        MtShop blankResult = mtShopService.updatePoiIdIfMissing(blankPoiIdInputShop, " ");
        assertNull(blankResult.getPoiId());
        verify(mtShopMapper, times(1)).updateById(any(MtShop.class));
    }

    @Test
    void shouldPersistShippingTimeWhenAuthorizedShopIsCreated() {
        MeituanTokenData tokenData = new MeituanTokenData();
        tokenData.setOpBizCode("epoi-003");
        tokenData.setOpBizName("新门店");

        Data shopDetail = new Data();
        shopDetail.setEpoiId("epoi-003");
        shopDetail.setName("新门店");
        shopDetail.setShippingTime("00:00-02:00,10:00-24:00");

        when(mtShopMapper.selectByEpoiId("epoi-003")).thenReturn(null);
        when(powerProperties.getNewAuthorizedShopGrantAmount()).thenReturn(800_000);
        doAnswer(invocation -> {
            MtShop shop = invocation.getArgument(0);
            shop.setId(101L);
            return 1;
        }).when(mtShopMapper).insert(any(MtShop.class));

        MtShop saved = mtShopService.saveAuthorizedShop(tokenData, shopDetail);

        assertEquals("00:00-02:00,10:00-24:00", saved.getShippingTime());
        verify(mtShopMapper).insert(argThat(shop ->
                "00:00-02:00,10:00-24:00".equals(shop.getShippingTime())
        ));
    }

    @Test
    void shouldBackfillMissingShippingTimeWhenAuthorizedShopAlreadyExists() {
        MeituanTokenData tokenData = new MeituanTokenData();
        tokenData.setOpBizCode("epoi-004");
        tokenData.setOpBizName("老门店");

        MtShop existing = new MtShop();
        existing.setId(201L);
        existing.setEpoiId("epoi-004");
        existing.setName("老门店");

        Data shopDetail = new Data();
        shopDetail.setEpoiId("epoi-004");
        shopDetail.setName("老门店");
        shopDetail.setShippingTime("09:00-21:00");

        when(mtShopMapper.selectByEpoiId("epoi-004")).thenReturn(existing);

        MtShop saved = mtShopService.saveAuthorizedShop(tokenData, shopDetail);

        assertEquals("09:00-21:00", saved.getShippingTime());
        verify(mtShopMapper).updateById(argThat(shop ->
                Long.valueOf(201L).equals(shop.getId()) && "09:00-21:00".equals(shop.getShippingTime())
        ));
        verify(mtPowerGrantMapper, never()).insert(any(MtPowerGrant.class));
        verify(mtPowerAccountService, never()).addAdminGrant(anyLong(), anyInt());
    }
}
