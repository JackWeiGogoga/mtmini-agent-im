package com.gogoga.backend.service;

import com.gogoga.backend.entity.LoginUser;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.exception.BusinessException;
import com.gogoga.backend.util.UserContext;
import com.gogoga.backend.vo.MtH5HomeVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 美团 H5 门店上下文服务测试
 *
 * @author system
 * @since 2026-03-24
 */
@ExtendWith(MockitoExtension.class)
class MtH5ShopServiceTest {

    @Mock
    private MtShopService mtShopService;

    @InjectMocks
    private MtH5ShopService mtH5ShopService;

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void shouldResolveCurrentShopBySessionEpoiId() {
        LoginUser loginUser = new LoginUser();
        loginUser.setEpoiId("17740748781");
        UserContext.setLoginUser(loginUser);

        MtShop shop = new MtShop();
        shop.setEpoiId("17740748781");
        shop.setPoiId("poi-001");
        shop.setPoiName("测试门店");
        when(mtShopService.findByEpoiId("17740748781")).thenReturn(shop);

        MtH5HomeVO homeInfo = mtH5ShopService.getHomeInfo();

        assertEquals("测试门店", homeInfo.getShopName());
        assertEquals("poi-001", homeInfo.getPoiId());
        assertEquals("17740748781", homeInfo.getEpoiId());
        verify(mtShopService).findByEpoiId("17740748781");
    }

    @Test
    void shouldRejectWhenSessionEpoiIdIsMissing() {
        UserContext.setLoginUser(new LoginUser());

        BusinessException exception = assertThrows(BusinessException.class, () -> mtH5ShopService.getCurrentShop());

        assertEquals(403, exception.getCode());
        assertEquals("当前门店登录态无效", exception.getMessage());
    }
}
