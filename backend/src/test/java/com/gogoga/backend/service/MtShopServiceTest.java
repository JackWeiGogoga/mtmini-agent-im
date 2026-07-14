package com.gogoga.backend.service;

import com.gogoga.backend.config.PowerProperties;
import com.gogoga.backend.entity.mt.MtPowerGrant;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.mapper.MtPowerGrantMapper;
import com.gogoga.backend.mapper.MtShopMapper;
import com.meituan.sdk.auth.MeituanTokenData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MtShopServiceTest {

    @Mock
    private MtShopMapper mtShopMapper;
    @Mock
    private MtPowerGrantMapper mtPowerGrantMapper;
    @Mock
    private MtPowerAccountService mtPowerAccountService;

    @Test
    void saveAuthorizedShopGrantsPowerForNewShopWithStableGrantNo() {
        PowerProperties powerProperties = new PowerProperties();
        powerProperties.setNewAuthorizedShopGrantAmount(800000);
        MtShopService service = new MtShopService(mtShopMapper, mtPowerGrantMapper, mtPowerAccountService, powerProperties);
        when(mtShopMapper.selectByEpoiId("epoi-1")).thenReturn(null);
        doAnswer(invocation -> {
            MtShop shop = invocation.getArgument(0);
            shop.setId(100L);
            return 1;
        }).when(mtShopMapper).insert(any(MtShop.class));

        service.saveAuthorizedShop(tokenData(), null);

        ArgumentCaptor<MtPowerGrant> grantCaptor = ArgumentCaptor.forClass(MtPowerGrant.class);
        verify(mtPowerGrantMapper).insert(grantCaptor.capture());
        assertEquals("AUTH_BONUS:100", grantCaptor.getValue().getGrantNo());
        assertEquals(800000, grantCaptor.getValue().getPowerAmount());
        verify(mtPowerAccountService).addAdminGrant(100L, 800000);
    }

    @Test
    void saveAuthorizedShopTreatsDuplicateGrantAsIdempotent() {
        PowerProperties powerProperties = new PowerProperties();
        powerProperties.setNewAuthorizedShopGrantAmount(800000);
        MtShopService service = new MtShopService(mtShopMapper, mtPowerGrantMapper, mtPowerAccountService, powerProperties);
        when(mtShopMapper.selectByEpoiId("epoi-1")).thenReturn(null);
        doAnswer(invocation -> {
            MtShop shop = invocation.getArgument(0);
            shop.setId(100L);
            return 1;
        }).when(mtShopMapper).insert(any(MtShop.class));
        doThrow(new DuplicateKeyException("duplicate")).when(mtPowerGrantMapper).insert(any(MtPowerGrant.class));

        service.saveAuthorizedShop(tokenData(), null);

        verify(mtPowerAccountService, never()).addAdminGrant(any(), any());
    }

    @Test
    void updatePoiIdIfMissingShouldPersistPoiId() {
        MtShop shop = new MtShop();
        shop.setId(100L);
        MtShopService service = new MtShopService(
                mtShopMapper, mtPowerGrantMapper, mtPowerAccountService, new PowerProperties());

        MtShop result = service.updatePoiIdIfMissing(shop, "poi-100");

        assertSame(shop, result);
        assertEquals("poi-100", shop.getPoiId());
        verify(mtShopMapper).updateById(shop);
    }

    @Test
    void updatePoiIdIfMissingShouldKeepExistingPoiId() {
        MtShop shop = new MtShop();
        shop.setId(100L);
        shop.setPoiId("poi-existing");
        MtShopService service = new MtShopService(
                mtShopMapper, mtPowerGrantMapper, mtPowerAccountService, new PowerProperties());

        MtShop result = service.updatePoiIdIfMissing(shop, "poi-new");

        assertSame(shop, result);
        assertEquals("poi-existing", shop.getPoiId());
        verify(mtShopMapper, never()).updateById(any());
    }

    private MeituanTokenData tokenData() {
        MeituanTokenData tokenData = new MeituanTokenData();
        tokenData.setOpBizCode("epoi-1");
        tokenData.setOpBizName("门店1");
        return tokenData;
    }
}
