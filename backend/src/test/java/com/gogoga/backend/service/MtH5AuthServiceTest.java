package com.gogoga.backend.service;

import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.entity.LoginUser;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.exception.BusinessException;
import com.gogoga.backend.param.MtH5DirectLoginParam;
import com.gogoga.backend.vo.MtH5LoginVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 美团 H5 直登服务测试
 *
 * @author system
 * @since 2026-03-24
 */
@ExtendWith(MockitoExtension.class)
class MtH5AuthServiceTest {

    @Spy
    private MtProperties mtProperties = new MtProperties();

    @Mock
    private MtShopService mtShopService;

    @Mock
    private LoginService loginService;

    @InjectMocks
    private MtH5AuthService mtH5AuthService;

    @Test
    void shouldLoginByWmPoiIdAndReturnMinimalPayload() {
        MtShop shop = new MtShop();
        shop.setId(10L);
        shop.setEpoiId("17740748781");
        shop.setPoiId("poi-001");
        shop.setPoiName("测试门店");

        LoginUser loginUser = new LoginUser();
        loginUser.setToken("token-001");
        loginUser.setUserName("测试门店");
        loginUser.setEpoiId("17740748781");

        MtH5DirectLoginParam param = new MtH5DirectLoginParam();
        param.setWmPoiId("17740748781");
        param.setBizToken("biz-token");
        param.setPlatform("0");

        when(mtShopService.findByEpoiId("17740748781")).thenReturn(shop);
        when(loginService.loginMtShop(shop)).thenReturn(loginUser);

        MtH5LoginVO result = mtH5AuthService.login(param);

        assertEquals("token-001", result.getToken());
        assertEquals("17740748781", result.getShopInfo().getEpoiId());
        assertEquals("poi-001", result.getShopInfo().getPoiId());
        assertEquals("测试门店", result.getShopInfo().getNickName());
        verify(mtShopService).findByEpoiId("17740748781");
        verify(loginService).loginMtShop(shop);
    }

    @Test
    void shouldRejectBlankWmPoiId() {
        MtH5DirectLoginParam param = new MtH5DirectLoginParam();
        param.setWmPoiId(" ");

        BusinessException exception = assertThrows(BusinessException.class, () -> mtH5AuthService.login(param));

        assertEquals(400, exception.getCode());
        assertEquals("wmPoiId不能为空", exception.getMessage());
    }

    @Test
    void shouldRejectWhenShopIsMissing() {
        MtH5DirectLoginParam param = new MtH5DirectLoginParam();
        param.setWmPoiId("missing-epoiid");

        when(mtShopService.findByEpoiId("missing-epoiid")).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class, () -> mtH5AuthService.login(param));

        assertEquals(404, exception.getCode());
        assertTrue(exception.getMessage().contains("门店尚未完成授权"));
    }
}
