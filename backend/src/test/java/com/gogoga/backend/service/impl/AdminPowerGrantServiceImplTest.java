package com.gogoga.backend.service.impl;

import com.gogoga.backend.entity.LoginUser;
import com.gogoga.backend.entity.mt.MtPowerGrant;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.exception.BusinessException;
import com.gogoga.backend.mapper.MtPowerGrantMapper;
import com.gogoga.backend.service.MtPowerAccountService;
import com.gogoga.backend.service.MtShopService;
import com.gogoga.backend.util.UserContext;
import com.gogoga.backend.vo.AdminPowerGrantVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminPowerGrantServiceImplTest {

    @Mock
    private MtPowerGrantMapper mtPowerGrantMapper;

    @Mock
    private MtShopService mtShopService;

    @Mock
    private MtPowerAccountService mtPowerAccountService;

    @InjectMocks
    private AdminPowerGrantServiceImpl adminPowerGrantService;

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    @Test
    void shouldCreateGrantAndCreditGrantBalance() {
        MtShop shop = new MtShop();
        shop.setId(1L);
        shop.setPoiId("poi-1");
        shop.setPoiName("测试门店");
        when(mtShopService.findById(1L)).thenReturn(shop);

        LoginUser loginUser = new LoginUser();
        loginUser.setUserId("admin-1");
        loginUser.setUserName("系统管理员");
        loginUser.setRoles(java.util.Set.of("super_admin"));
        UserContext.setLoginUser(loginUser);

        ArgumentCaptor<MtPowerGrant> grantCaptor = ArgumentCaptor.forClass(MtPowerGrant.class);

        AdminPowerGrantVO result = adminPowerGrantService.createGrant(1L, 100, "活动补发", "4月补发");

        verify(mtPowerGrantMapper).insert(grantCaptor.capture());
        verify(mtPowerAccountService).addAdminGrant(1L, 100);
        MtPowerGrant grant = grantCaptor.getValue();
        assertEquals("admin-1", grant.getOperatorUserId());
        assertEquals("系统管理员", grant.getOperatorUsername());
        assertEquals("活动补发", grant.getGrantReason());
        assertEquals("4月补发", grant.getRemark());
        assertEquals(1L, result.getShopId());
        assertEquals("测试门店", result.getShopName());
        assertEquals("poi-1", result.getPoiId());
        assertEquals(100, result.getPowerAmount());
    }

    @Test
    void shouldRejectBlankGrantReason() {
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId("admin-1");
        loginUser.setUserName("系统管理员");
        loginUser.setRoles(java.util.Set.of("super_admin"));
        UserContext.setLoginUser(loginUser);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> adminPowerGrantService.createGrant(1L, 100, " ", null));

        assertEquals("发放原因不能为空", exception.getMessage());
        verify(mtPowerGrantMapper, never()).insert(any());
        verify(mtPowerAccountService, never()).addAdminGrant(anyLong(), anyInt());
    }

    @Test
    void shouldListGrantHistoryByShopId() {
        MtShop shop = new MtShop();
        shop.setId(1L);
        shop.setPoiId("poi-1");
        shop.setPoiName("测试门店");
        when(mtShopService.findById(1L)).thenReturn(shop);

        LoginUser loginUser = new LoginUser();
        loginUser.setUserId("admin-1");
        loginUser.setUserName("系统管理员");
        loginUser.setRoles(java.util.Set.of("super_admin"));
        UserContext.setLoginUser(loginUser);

        MtPowerGrant grant = new MtPowerGrant();
        grant.setId(11L);
        grant.setGrantNo("PG-1");
        grant.setShopId(1L);
        grant.setPowerAmount(30);
        grant.setGrantReason("人工补偿");
        when(mtPowerGrantMapper.selectByShopId(1L)).thenReturn(List.of(grant));

        List<AdminPowerGrantVO> result = adminPowerGrantService.listByShopId(1L);

        assertEquals(1, result.size());
        assertEquals("PG-1", result.get(0).getGrantNo());
        assertEquals("测试门店", result.get(0).getShopName());
    }

    @Test
    void shouldRejectGrantForNonSuperAdmin() {
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId("user-1");
        loginUser.setUserName("普通管理员");
        UserContext.setLoginUser(loginUser);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> adminPowerGrantService.createGrant(1L, 100, "活动补发", null));

        assertEquals("仅超级管理员可执行该操作", exception.getMessage());
        verify(mtPowerGrantMapper, never()).insert(any());
    }
}
