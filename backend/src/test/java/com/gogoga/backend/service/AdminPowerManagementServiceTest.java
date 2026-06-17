package com.gogoga.backend.service;

import com.github.pagehelper.PageInfo;
import com.gogoga.backend.entity.LoginUser;
import com.gogoga.backend.entity.mt.MtPowerAccount;
import com.gogoga.backend.entity.mt.MtPowerConsume;
import com.gogoga.backend.entity.mt.MtPowerConsumeDetail;
import com.gogoga.backend.entity.mt.MtPowerRecharge;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.exception.BusinessException;
import com.gogoga.backend.mapper.MtPowerConsumeDetailMapper;
import com.gogoga.backend.mapper.MtPowerConsumeMapper;
import com.gogoga.backend.mapper.MtPowerRechargeMapper;
import com.gogoga.backend.mapper.UserShopBindingMapper;
import com.gogoga.backend.util.UserContext;
import com.gogoga.backend.vo.AdminPowerAccountSummaryVO;
import com.gogoga.backend.vo.AdminPowerConsumeRecordVO;
import com.gogoga.backend.vo.AdminPowerGrantVO;
import com.gogoga.backend.vo.AdminPowerManagementRowVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminPowerManagementServiceTest {

    @Mock
    private UserShopBindingMapper userShopBindingMapper;

    @Mock
    private UserShopBindingService userShopBindingService;

    @Mock
    private MtShopService mtShopService;

    @Mock
    private MtPowerAccountService mtPowerAccountService;

    @Mock
    private MtPowerRechargeMapper mtPowerRechargeMapper;

    @Mock
    private MtPowerConsumeMapper mtPowerConsumeMapper;

    @Mock
    private MtPowerConsumeDetailMapper mtPowerConsumeDetailMapper;

    @Mock
    private AdminPowerGrantService adminPowerGrantService;

    @InjectMocks
    private AdminPowerManagementService adminPowerManagementService;

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    @Test
    void shouldBuildPowerManagementRowsForSuperAdmin() {
        LoginUser loginUser = new LoginUser();
        loginUser.setRoles(java.util.Set.of("super_admin"));
        UserContext.setLoginUser(loginUser);

        AdminPowerManagementRowVO row = new AdminPowerManagementRowVO();
        row.setBindingId(1L);
        row.setShopId(101L);
        row.setPoiId("poi-101");
        row.setShopName("测试门店");
        row.setBalance(80);
        row.setRechargeBalance(30);
        row.setGrantBalance(50);
        row.setTotalRecharge(100);
        row.setTotalGrant(70);
        row.setTotalConsume(90);
        row.setBindingDomain("ORDINARY");
        row.setBindingDomainName("普通商户");
        when(userShopBindingMapper.selectAdminPowerManagedMerchantShops("ALL", "ALL", null, "测试", "CRITICAL", null))
                .thenReturn(List.of(row));

        PageInfo<AdminPowerManagementRowVO> result = adminPowerManagementService.listRows("测试", null, "CRITICAL", 1, 10);

        assertEquals(1, result.getList().size());
        assertEquals("CRITICAL", result.getList().get(0).getRiskStatus());
        assertEquals(80, result.getList().get(0).getBalance());
        assertEquals("ORDINARY", result.getList().get(0).getBindingDomain());
        verify(userShopBindingMapper).selectAdminPowerManagedMerchantShops("ALL", "ALL", null, "测试", "CRITICAL", null);
    }

    @Test
    void shouldListPowerManagementRowsByBindingDomainForSuperAdmin() {
        LoginUser loginUser = new LoginUser();
        loginUser.setRoles(java.util.Set.of("super_admin"));
        UserContext.setLoginUser(loginUser);

        AdminPowerManagementRowVO ordinaryRow = new AdminPowerManagementRowVO();
        ordinaryRow.setBindingId(1L);
        ordinaryRow.setShopId(101L);
        ordinaryRow.setPoiId("poi-101");
        ordinaryRow.setShopName("测试门店");
        ordinaryRow.setBindingDomain("ORDINARY");
        ordinaryRow.setBindingDomainName("普通商户");
        ordinaryRow.setBalance(25000);

        AdminPowerManagementRowVO salesRow = new AdminPowerManagementRowVO();
        salesRow.setBindingId(2L);
        salesRow.setShopId(101L);
        salesRow.setPoiId("poi-101");
        salesRow.setShopName("测试门店");
        salesRow.setBindingDomain("SALES");
        salesRow.setBindingDomainName("网销用户");
        salesRow.setBalance(25000);

        ordinaryRow.setBindingStatus("BOTH");
        ordinaryRow.setOrdinaryBindingId(1L);
        ordinaryRow.setSalesBindingId(2L);
        when(userShopBindingMapper.selectAdminPowerManagedMerchantShops("ALL", "ALL", null, null, null, null))
                .thenReturn(List.of(ordinaryRow));
        when(userShopBindingMapper.selectAdminPowerManagedMerchantShops("SALES", "ALL", "sales-1", null, null, null))
                .thenReturn(List.of(salesRow));

        PageInfo<AdminPowerManagementRowVO> allRows = adminPowerManagementService
                .listRows(null, null, "ALL", null, 1, 10);
        PageInfo<AdminPowerManagementRowVO> salesRows = adminPowerManagementService
                .listRows(null, "sales-1", "SALES", null, 1, 10);

        assertEquals(1, allRows.getList().size());
        assertEquals(101L, allRows.getList().get(0).getShopId());
        assertEquals("BOTH", allRows.getList().get(0).getBindingStatus());
        assertEquals("NORMAL", allRows.getList().get(0).getRiskStatus());
        assertEquals(1, salesRows.getList().size());
        assertEquals("SALES", salesRows.getList().get(0).getBindingDomain());
    }

    @Test
    void shouldFilterPowerManagementRowsByBindingStatusForSuperAdmin() {
        LoginUser loginUser = new LoginUser();
        loginUser.setRoles(java.util.Set.of("super_admin"));
        UserContext.setLoginUser(loginUser);

        AdminPowerManagementRowVO unboundRow = new AdminPowerManagementRowVO();
        unboundRow.setShopId(202L);
        unboundRow.setPoiId("poi-202");
        unboundRow.setBindingStatus("UNBOUND");
        unboundRow.setBalance(25000);
        when(userShopBindingMapper.selectAdminPowerManagedMerchantShops("ALL", "UNBOUND", null, null, null, null))
                .thenReturn(List.of(unboundRow));

        PageInfo<AdminPowerManagementRowVO> result = adminPowerManagementService
                .listRows(null, null, "ALL", "UNBOUND", null, 1, 10);

        assertEquals(1, result.getList().size());
        assertEquals("UNBOUND", result.getList().get(0).getBindingStatus());
        verify(userShopBindingMapper).selectAdminPowerManagedMerchantShops("ALL", "UNBOUND", null, null, null, null);
    }

    @Test
    void shouldPassBalanceSortDirectionForPowerManagementRows() {
        LoginUser loginUser = new LoginUser();
        loginUser.setRoles(java.util.Set.of("super_admin"));
        UserContext.setLoginUser(loginUser);

        when(userShopBindingMapper.selectAdminPowerManagedMerchantShops("ALL", "ALL", null, null, null, "DESC"))
                .thenReturn(List.of());

        adminPowerManagementService.listRows(
                null,
                null,
                "ALL",
                "ALL",
                null,
                "balance",
                "descend",
                1,
                10
        );

        verify(userShopBindingMapper).selectAdminPowerManagedMerchantShops("ALL", "ALL", null, null, null, "DESC");
    }

    @Test
    void shouldBuildSummaryAndGrantRecordsForVisibleShop() {
        LoginUser loginUser = new LoginUser();
        loginUser.setRoles(java.util.Set.of("super_admin"));
        UserContext.setLoginUser(loginUser);

        MtShop shop = new MtShop();
        shop.setId(101L);
        shop.setPoiId("poi-101");
        shop.setEpoiId("epoi-101");
        shop.setPoiName("测试门店");
        when(mtShopService.findById(101L)).thenReturn(shop);

        MtPowerAccount account = new MtPowerAccount();
        account.setBalance(5000);
        account.setRechargeBalance(200);
        account.setGrantBalance(160);
        account.setTotalRecharge(500);
        account.setTotalGrant(300);
        account.setTotalConsume(440);
        when(mtPowerAccountService.getOrCreateAccount(101L)).thenReturn(account);

        AdminPowerManagementRowVO binding = new AdminPowerManagementRowVO();
        binding.setBoundUserId("user-1");
        binding.setBoundUserName("运营A");
        when(userShopBindingMapper.selectPowerManagedMerchantByShopId(101L)).thenReturn(binding);

        AdminPowerGrantVO grant = new AdminPowerGrantVO();
        grant.setGrantNo("PG-001");
        PageInfo<AdminPowerGrantVO> grantPage = new PageInfo<>(List.of(grant));
        when(adminPowerGrantService.listByShopId(101L, null, null, 1, 10)).thenReturn(grantPage);

        AdminPowerAccountSummaryVO summary = adminPowerManagementService.getSummary(101L);
        PageInfo<AdminPowerGrantVO> grantRecords = adminPowerManagementService.listGrantRecords(101L, null, null, 1, 10);

        assertEquals("WARNING", summary.getRiskStatus());
        assertEquals("运营A", summary.getBoundUserName());
        assertEquals(1, grantRecords.getList().size());
        assertEquals("PG-001", grantRecords.getList().get(0).getGrantNo());
    }

    @Test
    void shouldBuildConsumeRecordsWithSourceSplit() {
        LoginUser loginUser = new LoginUser();
        loginUser.setRoles(java.util.Set.of("super_admin"));
        UserContext.setLoginUser(loginUser);

        MtShop shop = new MtShop();
        shop.setId(101L);
        when(mtShopService.findById(101L)).thenReturn(shop);

        MtPowerConsume consume = new MtPowerConsume();
        consume.setId(11L);
        consume.setConsumeNo("PC-001");
        consume.setShopId(101L);
        consume.setSourceType(PromotionPowerConsumeService.CONSUME_SOURCE_LLM);
        consume.setSourceId("source-1");
        consume.setPowerAmount(30);
        consume.setBalanceBefore(120);
        consume.setBalanceAfter(90);
        consume.setRemark("推广费用 LLM 计算算力消耗，tokens=2100");
        consume.setCreateTime(LocalDateTime.of(2026, 4, 12, 10, 0));
        when(mtPowerConsumeMapper.selectByShopIdAndCreateTimeBetween(101L, null, null))
                .thenReturn(List.of(consume));

        MtPowerConsumeDetail grantDetail = new MtPowerConsumeDetail();
        grantDetail.setConsumeId(11L);
        grantDetail.setSourceBalanceType(MtPowerConsumeService.SOURCE_BALANCE_GRANT);
        grantDetail.setPowerAmount(10);
        grantDetail.setBalanceBefore(20);
        grantDetail.setBalanceAfter(10);

        MtPowerConsumeDetail rechargeDetail = new MtPowerConsumeDetail();
        rechargeDetail.setConsumeId(11L);
        rechargeDetail.setSourceBalanceType(MtPowerConsumeService.SOURCE_BALANCE_RECHARGE);
        rechargeDetail.setPowerAmount(20);
        rechargeDetail.setBalanceBefore(100);
        rechargeDetail.setBalanceAfter(80);

        when(mtPowerConsumeDetailMapper.selectByConsumeIds(List.of(11L)))
                .thenReturn(List.of(grantDetail, rechargeDetail));

        PageInfo<AdminPowerConsumeRecordVO> records = adminPowerManagementService
                .listConsumeRecords(101L, null, null, 1, 10);

        assertEquals(1, records.getList().size());
        assertEquals("LLM 计算", records.getList().get(0).getSourceTypeText());
        assertEquals(2, records.getList().get(0).getDetails().size());
        assertEquals("赠送算力", records.getList().get(0).getDetails().get(0).getSourceBalanceTypeText());
        assertEquals("实充算力", records.getList().get(0).getDetails().get(1).getSourceBalanceTypeText());
    }

    @Test
    void shouldRejectNonSuperAdmin() {
        LoginUser loginUser = new LoginUser();
        loginUser.setRoles(java.util.Set.of("admin"));
        UserContext.setLoginUser(loginUser);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> adminPowerManagementService.listRows(null, null, null, 1, 10)
        );

        assertEquals("仅超级管理员可执行该操作", exception.getMessage());
    }

    @Test
    void shouldReadRechargeRecords() {
        LoginUser loginUser = new LoginUser();
        loginUser.setRoles(java.util.Set.of("super_admin"));
        UserContext.setLoginUser(loginUser);

        MtShop shop = new MtShop();
        shop.setId(101L);
        when(mtShopService.findById(101L)).thenReturn(shop);

        MtPowerRecharge recharge = new MtPowerRecharge();
        recharge.setId(7L);
        recharge.setRechargeNo("PR-001");
        recharge.setShopId(101L);
        recharge.setPowerAmount(200);
        recharge.setTotalAmountFen(1990);
        recharge.setPayStatus(2);
        when(mtPowerRechargeMapper.selectByShopIdAndCreateTimeBetween(101L, null, null))
                .thenReturn(List.of(recharge));

        assertEquals(1, adminPowerManagementService.listRechargeRecords(101L, null, null, 1, 10).getList().size());
        verify(mtPowerRechargeMapper).selectByShopIdAndCreateTimeBetween(101L, null, null);
    }

    @Test
    void shouldAllowSuperAdminToReadSummaryWithoutBinding() {
        LoginUser loginUser = new LoginUser();
        loginUser.setRoles(java.util.Set.of("super_admin"));
        UserContext.setLoginUser(loginUser);

        MtShop shop = new MtShop();
        shop.setId(202L);
        shop.setPoiId("poi-202");
        when(mtShopService.findById(202L)).thenReturn(shop);

        MtPowerAccount account = new MtPowerAccount();
        account.setBalance(25000);
        when(mtPowerAccountService.getOrCreateAccount(202L)).thenReturn(account);

        AdminPowerAccountSummaryVO summary = adminPowerManagementService.getSummary(202L);

        assertEquals(202L, summary.getShopId());
        verify(mtShopService).findById(202L);
    }
}
