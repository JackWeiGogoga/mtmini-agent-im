package com.gogoga.backend.service;

import com.github.pagehelper.PageInfo;
import com.gogoga.backend.entity.LoginUser;
import com.gogoga.backend.entity.User;
import com.gogoga.backend.entity.UserShopBinding;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.enums.BackendPortalType;
import com.gogoga.backend.enums.AdminDataScopeTypeEnum;
import com.gogoga.backend.exception.BusinessException;
import com.gogoga.backend.mapper.SalesUserShopBindingMapper;
import com.gogoga.backend.mapper.UserMapper;
import com.gogoga.backend.mapper.UserShopBindingMapper;
import com.gogoga.backend.service.impl.UserShopBindingServiceImpl;
import com.gogoga.backend.util.UserContext;
import com.gogoga.backend.vo.VisibleUserOptionVO;
import com.gogoga.backend.vo.UserShopBatchBindResultVO;
import com.gogoga.backend.vo.UserShopBindingVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserShopBindingServiceImplTest {

    @Mock
    private UserShopBindingMapper userShopBindingMapper;

    @Mock
    private SalesUserShopBindingMapper salesUserShopBindingMapper;

    @Mock
    private MtShopService mtShopService;

    @Mock
    private UserMapper userMapper;

    @Mock
    private AdminDataScopeService adminDataScopeService;

    @InjectMocks
    private UserShopBindingServiceImpl userShopBindingService;

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void shouldBindCurrentUserShopSuccessfully() {
        mockCurrentUser("user-1");

        MtShop shop = new MtShop();
        shop.setId(101L);
        shop.setPoiId("poi-001");
        shop.setEpoiId("epoi-001");
        shop.setPoiName("测试门店");
        when(userShopBindingMapper.selectByUserIdAndPoiId("user-1", "poi-001")).thenReturn(null);
        when(userShopBindingMapper.selectByPoiId("poi-001")).thenReturn(null);
        when(mtShopService.getByPoiIdOrThrow("poi-001")).thenReturn(shop);
        doAnswer(invocation -> {
            UserShopBinding binding = invocation.getArgument(0);
            binding.setId(1001L);
            return 1;
        }).when(userShopBindingMapper).insert(any(UserShopBinding.class));

        UserShopBindingVO result = userShopBindingService.bindCurrentUserShop("poi-001", null);

        assertEquals(1001L, result.getBindingId());
        assertEquals("poi-001", result.getPoiId());
        assertEquals("测试门店", result.getShopName());
        verify(userShopBindingMapper).insert(any(UserShopBinding.class));
    }

    @Test
    void shouldRejectDuplicateBinding() {
        mockCurrentUser("user-1");

        UserShopBinding existing = new UserShopBinding();
        existing.setId(1L);
        existing.setUserId("user-1");
        existing.setPoiId("poi-001");
        when(userShopBindingMapper.selectByUserIdAndPoiId("user-1", "poi-001")).thenReturn(existing);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> userShopBindingService.bindCurrentUserShop("poi-001", null)
        );

        assertEquals("该门店已绑定到当前用户，请勿重复绑定", exception.getMessage());
    }

    @Test
    void shouldRejectMissingShopWhenBinding() {
        mockCurrentUser("user-1");

        when(userShopBindingMapper.selectByUserIdAndPoiId("user-1", "poi-404")).thenReturn(null);
        when(userShopBindingMapper.selectByPoiId("poi-404")).thenReturn(null);
        when(mtShopService.getByPoiIdOrThrow("poi-404"))
                .thenThrow(new BusinessException("门店不存在或尚未同步，请确认店铺ID"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> userShopBindingService.bindCurrentUserShop("poi-404", null)
        );

        assertEquals("门店不存在或尚未同步，请确认店铺ID", exception.getMessage());
    }

    @Test
    void shouldRejectShopAlreadyBoundByAnotherUser() {
        mockCurrentUser("user-1");

        UserShopBinding existing = new UserShopBinding();
        existing.setUserId("user-2");
        existing.setPoiId("poi-003");
        when(userShopBindingMapper.selectByUserIdAndPoiId("user-1", "poi-003")).thenReturn(null);
        when(userShopBindingMapper.selectByPoiId("poi-003")).thenReturn(existing);

        User boundUser = new User();
        boundUser.setUserId("user-2");
        boundUser.setUsername("other_admin");
        when(userMapper.selectByUserId("user-2")).thenReturn(boundUser);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> userShopBindingService.bindCurrentUserShop("poi-003", null)
        );

        assertEquals("该门店已被用户「other_admin」绑定", exception.getMessage());
    }

    @Test
    void shouldListOnlyCurrentUserBoundShops() {
        mockCurrentUser("user-2");
        when(adminDataScopeService.getCurrentScopeType()).thenReturn(AdminDataScopeTypeEnum.SELF);

        UserShopBindingVO binding = new UserShopBindingVO();
        binding.setBindingId(2001L);
        binding.setPoiId("poi-002");
        when(userShopBindingMapper.selectBoundShopsByUserId("user-2", "poi-002", null))
                .thenReturn(List.of(binding));

        PageInfo<UserShopBindingVO> result = userShopBindingService.listCurrentUserShops("poi-002", null, 1, 10);

        assertEquals(1, result.getList().size());
        assertEquals("poi-002", result.getList().get(0).getPoiId());
        verify(adminDataScopeService).validateCurrentUserCanViewTargetUser(null);
        verify(userShopBindingMapper).selectBoundShopsByUserId("user-2", "poi-002", null);
    }

    @Test
    void shouldRedactPowerFieldsForYouzanPortalShopRows() {
        mockCurrentUser("youzan-1", BackendPortalType.YOUZAN);
        when(adminDataScopeService.getCurrentScopeType()).thenReturn(AdminDataScopeTypeEnum.SELF);

        UserShopBindingVO binding = new UserShopBindingVO();
        binding.setBindingId(2101L);
        binding.setPoiId("poi-youzan");
        binding.setPowerBalance(100);
        binding.setTotalRecharge(200);
        binding.setTotalGrant(300);
        binding.setTotalConsume(400);
        when(userShopBindingMapper.selectBoundShopsByUserId("youzan-1", null, null))
                .thenReturn(List.of(binding));

        PageInfo<UserShopBindingVO> result = userShopBindingService.listCurrentUserShops(null, null, 1, 10);

        UserShopBindingVO row = result.getList().get(0);
        assertNull(row.getPowerBalance());
        assertNull(row.getTotalRecharge());
        assertNull(row.getTotalGrant());
        assertNull(row.getTotalConsume());
    }

    @Test
    void shouldBatchBindAndCollectFailures() {
        mockCurrentUser("user-9");

        MtShop shop = new MtShop();
        shop.setId(901L);
        shop.setPoiId("poi-901");
        shop.setPoiName("可绑定门店");

        when(userShopBindingMapper.selectByUserIdAndPoiId("user-9", "poi-901")).thenReturn(null);
        when(userShopBindingMapper.selectByPoiId("poi-901")).thenReturn(null);
        when(mtShopService.getByPoiIdOrThrow("poi-901")).thenReturn(shop);
        doAnswer(invocation -> {
            UserShopBinding binding = invocation.getArgument(0);
            binding.setId(9001L);
            return 1;
        }).when(userShopBindingMapper).insert(any(UserShopBinding.class));

        UserShopBinding occupied = new UserShopBinding();
        occupied.setUserId("user-8");
        occupied.setPoiId("poi-occupied");
        when(userShopBindingMapper.selectByUserIdAndPoiId("user-9", "poi-occupied")).thenReturn(null);
        when(userShopBindingMapper.selectByPoiId("poi-occupied")).thenReturn(occupied);

        User occupiedUser = new User();
        occupiedUser.setUserId("user-8");
        occupiedUser.setNickname("已绑定用户");
        when(userMapper.selectByUserId("user-8")).thenReturn(occupiedUser);

        when(userShopBindingMapper.selectByUserIdAndPoiId("user-9", "poi-missing")).thenReturn(null);
        when(userShopBindingMapper.selectByPoiId("poi-missing")).thenReturn(null);
        when(mtShopService.getByPoiIdOrThrow("poi-missing"))
                .thenThrow(new BusinessException("门店不存在或尚未同步，请确认店铺ID"));

        UserShopBatchBindResultVO result = userShopBindingService.batchBindCurrentUserShops(
                List.of("poi-901", "poi-occupied", "poi-missing", "poi-901"),
                null
        );

        assertEquals(3, result.getTotalCount());
        assertEquals(1, result.getSuccessCount());
        assertEquals(2, result.getFailureCount());
        assertEquals(List.of("poi-901"), result.getSuccessPoiIds());
        assertEquals("poi-occupied", result.getFailureItems().get(0).getPoiId());
        assertEquals("该门店已被用户「已绑定用户」绑定", result.getFailureItems().get(0).getErrorMessage());
        assertEquals("poi-missing", result.getFailureItems().get(1).getPoiId());
    }

    @Test
    void shouldRejectUnbindWhenBindingDoesNotBelongToCurrentUser() {
        mockCurrentUser("user-3");
        UserShopBinding binding = new UserShopBinding();
        binding.setId(3001L);
        binding.setUserId("user-9");
        when(userShopBindingMapper.selectById(3001L)).thenReturn(binding);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> userShopBindingService.unbindCurrentUserShop(3001L)
        );

        assertEquals("绑定记录不存在或无权解绑", exception.getMessage());
    }

    @Test
    void shouldListMergedMerchantRowsForSuperAdminAllDomain() {
        mockSuperAdmin("admin-1");
        when(adminDataScopeService.getCurrentScopeType()).thenReturn(AdminDataScopeTypeEnum.ALL);

        UserShopBindingVO merchant = new UserShopBindingVO();
        merchant.setShopId(101L);
        merchant.setPoiId("poi-shared");
        merchant.setBindingDomain("ALL");
        merchant.setBindingStatus("BOTH");
        merchant.setOrdinaryBindingId(1L);
        merchant.setOrdinaryBoundUserId("ordinary-user");
        merchant.setSalesBindingId(2L);
        merchant.setSalesBoundUserId("sales-user");
        when(userShopBindingMapper.selectAdminMerchantShops("ALL", "ALL", null, null, null))
                .thenReturn(List.of(merchant));

        PageInfo<UserShopBindingVO> result = userShopBindingService.listCurrentUserShops(null, null, "ALL", 1, 10);

        assertEquals(1, result.getList().size());
        assertEquals("BOTH", result.getList().get(0).getBindingStatus());
        assertEquals("ordinary-user", result.getList().get(0).getOrdinaryBoundUserId());
        assertEquals("sales-user", result.getList().get(0).getSalesBoundUserId());
        verify(userShopBindingMapper).selectAdminMerchantShops("ALL", "ALL", null, null, null);
    }

    @Test
    void shouldRejectSuperAdminBindWithoutBindingDomain() {
        mockSuperAdmin("admin-1");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> userShopBindingService.bindCurrentUserShop("poi-001", null, null)
        );

        assertEquals("请选择普通商户或网销用户绑定域", exception.getMessage());
    }

    @Test
    void shouldBindSalesDomainForSuperAdmin() {
        mockSuperAdmin("admin-1");

        MtShop shop = new MtShop();
        shop.setId(101L);
        shop.setPoiId("poi-sales");
        shop.setPoiName("网销绑定门店");
        when(salesUserShopBindingMapper.selectByUserIdAndPoiId("admin-1", "poi-sales")).thenReturn(null);
        when(salesUserShopBindingMapper.selectByPoiId("poi-sales")).thenReturn(null);
        when(mtShopService.getByPoiIdOrThrow("poi-sales")).thenReturn(shop);
        doAnswer(invocation -> {
            UserShopBinding binding = invocation.getArgument(0);
            binding.setId(9001L);
            return 1;
        }).when(salesUserShopBindingMapper).insert(any(UserShopBinding.class));

        UserShopBindingVO result = userShopBindingService.bindCurrentUserShop("poi-sales", null, "SALES");

        assertEquals(9001L, result.getBindingId());
        assertEquals("SALES", result.getBindingDomain());
        assertEquals("网销用户", result.getBindingDomainName());
        verify(salesUserShopBindingMapper).insert(any(UserShopBinding.class));
    }

    @Test
    void shouldUnbindSuperAdminSalesDomainFromSalesTableOnly() {
        mockSuperAdmin("admin-1");
        UserShopBinding binding = new UserShopBinding();
        binding.setId(3001L);
        binding.setUserId("sales-user");
        when(salesUserShopBindingMapper.selectById(3001L)).thenReturn(binding);
        when(adminDataScopeService.canCurrentUserManageBindingsFor("sales-user")).thenReturn(true);
        when(salesUserShopBindingMapper.deleteById(3001L)).thenReturn(1);

        userShopBindingService.unbindCurrentUserShop(3001L, "SALES");

        verify(salesUserShopBindingMapper).deleteById(3001L);
    }

    @Test
    void shouldRejectOrdinaryPortalRequestingSalesDomain() {
        mockCurrentUser("user-1", BackendPortalType.ORDINARY);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> userShopBindingService.listCurrentUserShops(null, null, "SALES", 1, 10)
        );

        assertEquals("当前工作台无权访问所选绑定域", exception.getMessage());
    }

    @Test
    void shouldPassBindingStatusForSuperAdminMerchantList() {
        mockSuperAdmin("admin-1");
        when(adminDataScopeService.getCurrentScopeType()).thenReturn(AdminDataScopeTypeEnum.ALL);

        UserShopBindingVO unbound = new UserShopBindingVO();
        unbound.setShopId(202L);
        unbound.setBindingStatus("UNBOUND");
        when(userShopBindingMapper.selectAdminMerchantShops("ALL", "UNBOUND", null, null, null))
                .thenReturn(List.of(unbound));

        PageInfo<UserShopBindingVO> result = userShopBindingService
                .listCurrentUserShops(null, null, "ALL", "UNBOUND", 1, 10);

        assertEquals(1, result.getList().size());
        assertEquals("UNBOUND", result.getList().get(0).getBindingStatus());
        verify(userShopBindingMapper).selectAdminMerchantShops("ALL", "UNBOUND", null, null, null);
    }

    @Test
    void shouldPassPowerBalanceSortDirectionForSuperAdminMerchantList() {
        mockSuperAdmin("admin-1");
        when(adminDataScopeService.getCurrentScopeType()).thenReturn(AdminDataScopeTypeEnum.ALL);
        when(userShopBindingMapper.selectAdminMerchantShops("ALL", "ALL", null, null, "ASC"))
                .thenReturn(List.of());

        userShopBindingService.listCurrentUserShops(
                null,
                null,
                "ALL",
                "ALL",
                "powerBalance",
                "ascend",
                1,
                10
        );

        verify(userShopBindingMapper).selectAdminMerchantShops("ALL", "ALL", null, null, "ASC");
    }

    @Test
    void shouldPassPromotionStatusForSuperAdminMerchantList() {
        mockSuperAdmin("admin-1");
        when(adminDataScopeService.getCurrentScopeType()).thenReturn(AdminDataScopeTypeEnum.ALL);
        when(userShopBindingMapper.selectAdminMerchantShops("ALL", "ALL", null, null, "ENABLED", null))
                .thenReturn(List.of());

        userShopBindingService.listCurrentUserShops(
                null,
                null,
                "ALL",
                "ALL",
                "ENABLED",
                null,
                null,
                1,
                10
        );

        verify(userShopBindingMapper).selectAdminMerchantShops("ALL", "ALL", null, null, "ENABLED", null);
    }

    @Test
    void shouldPassPromotionStatusForOrdinaryPortalList() {
        mockCurrentUser("merchant-1", BackendPortalType.ORDINARY);
        when(adminDataScopeService.getCurrentScopeType()).thenReturn(AdminDataScopeTypeEnum.SELF);
        when(userShopBindingMapper.selectBoundShopsByUserId("merchant-1", null, "DISABLED", null))
                .thenReturn(List.of());

        userShopBindingService.listCurrentUserShops(
                null,
                null,
                null,
                "ALL",
                "DISABLED",
                null,
                null,
                1,
                10
        );

        verify(userShopBindingMapper).selectBoundShopsByUserId("merchant-1", null, "DISABLED", null);
    }

    @Test
    void shouldPassPromotionStatusForSalesPortalList() {
        mockCurrentUser("sales-1", BackendPortalType.SALES);
        when(adminDataScopeService.getCurrentScopeType()).thenReturn(AdminDataScopeTypeEnum.SELF);
        when(salesUserShopBindingMapper.selectBoundShopsByUserId("sales-1", null, "ENABLED", null))
                .thenReturn(List.of());

        userShopBindingService.listCurrentUserShops(
                null,
                null,
                null,
                "ALL",
                "ENABLED",
                null,
                null,
                1,
                10
        );

        verify(salesUserShopBindingMapper).selectBoundShopsByUserId("sales-1", null, "ENABLED", null);
    }

    @Test
    void shouldListVisibleUsersFromDataScopeService() {
        VisibleUserOptionVO option = new VisibleUserOptionVO();
        option.setUserId("user-1");
        option.setDisplayName("测试管理员");
        when(adminDataScopeService.listCurrentVisibleUsers("测试")).thenReturn(List.of(option));

        List<VisibleUserOptionVO> result = userShopBindingService.listVisibleUsers("测试");

        assertEquals(1, result.size());
        assertEquals("user-1", result.get(0).getUserId());
    }

    private void mockCurrentUser(String userId) {
        mockCurrentUser(userId, null);
    }

    private void mockCurrentUser(String userId, BackendPortalType activePortal) {
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(userId);
        loginUser.setActivePortal(activePortal);
        UserContext.setLoginUser(loginUser);
    }

    private void mockSuperAdmin(String userId) {
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(userId);
        loginUser.setActivePortal(BackendPortalType.ADMIN);
        loginUser.setRoles(Set.of("super_admin"));
        UserContext.setLoginUser(loginUser);
    }
}
