package com.gogoga.backend.service;

import com.github.pagehelper.PageInfo;
import com.gogoga.backend.client.MtPromotionOpenApiClient;
import com.gogoga.backend.client.result.PeerBidPriceOpenApiResult;
import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.entity.LoginUser;
import com.gogoga.backend.entity.mt.MtAccessToken;
import com.gogoga.backend.entity.mt.MtAsyncTaskBacklogSummary;
import com.gogoga.backend.entity.mt.MtPromotionBidRecord;
import com.gogoga.backend.exception.BusinessException;
import com.gogoga.backend.mapper.MtPromotionBidRecordMapper;
import com.gogoga.backend.mapper.MtPromotionSettingMapper;
import com.gogoga.backend.mapper.MtShopPeakWindowProfileMapper;
import com.gogoga.backend.mapper.MtShopMapper;
import com.gogoga.backend.enums.BackendPortalType;
import com.gogoga.backend.enums.DashboardStatsRangeTypeEnum;
import com.gogoga.backend.entity.mt.MtPowerAccount;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.param.MtH5ManualPromotionBidParam;
import com.gogoga.backend.param.MtH5PromotionSettingsUpdateParam;
import com.gogoga.backend.service.promotion.PromotionExecutionWindow;
import com.gogoga.backend.service.promotion.PromotionTimeWindowEvaluator;
import com.gogoga.backend.service.promotion.PromotionTimeWindowResult;
import com.gogoga.backend.service.promotion.ShopPeakWindowProfileLookupResult;
import com.gogoga.backend.service.promotion.ShopPeakWindowProfileService;
import com.gogoga.backend.vo.AdminAsyncTaskMetricsVO;
import com.gogoga.backend.vo.AdminMasterConsolePromotionLogSummaryVO;
import com.gogoga.backend.vo.AdminMasterConsoleRowVO;
import com.gogoga.backend.vo.MtH5ManualPromotionBidVO;
import com.gogoga.backend.vo.MtH5PromotionSettingsVO;
import com.gogoga.backend.vo.UserShopBindingVO;
import com.gogoga.backend.util.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminMasterConsoleServiceTest {

    @Mock
    private UserShopBindingService userShopBindingService;

    @Mock
    private MtAccessTokenService mtAccessTokenService;

    @Mock
    private MtPowerAccountService mtPowerAccountService;

    @Mock
    private MtPromotionSettingService mtPromotionSettingService;

    @Mock
    private MtPromotionBidRecordMapper mtPromotionBidRecordMapper;

    @Mock
    private MtPromotionSettingMapper mtPromotionSettingMapper;

    @Mock
    private MtPromotionAutoBidService mtPromotionAutoBidService;

    @Mock
    private MtH5HomeDashboardService mtH5HomeDashboardService;

    @Mock
    private MtPromotionLogService mtPromotionLogService;

    @Mock
    private ShopDashboardMetricsService shopDashboardMetricsService;

    @Mock
    private MtPromotionOpenApiClient mtPromotionOpenApiClient;

    @Mock
    private PromotionTimeWindowEvaluator promotionTimeWindowEvaluator;

    @Mock
    private ShopPeakWindowProfileService shopPeakWindowProfileService;

    @Mock
    private MtShopMapper mtShopMapper;

    @Mock
    private MtShopPeakWindowProfileMapper mtShopPeakWindowProfileMapper;

    @Mock
    private MtAsyncTaskService mtAsyncTaskService;

    @Mock
    private MtAsyncTaskMetricsService mtAsyncTaskMetricsService;

    @Mock
    private MtProperties mtProperties;

    @InjectMocks
    private AdminMasterConsoleService adminMasterConsoleService;

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void shouldBuildMasterConsoleRowsFromCurrentUserBindings() {
        UserShopBindingVO binding = new UserShopBindingVO();
        binding.setBindingId(1L);
        binding.setShopId(101L);
        binding.setPoiId("poi-101");
        binding.setEpoiId("epoi-101");
        binding.setShopName("测试门店");
        binding.setAddress("测试地址");
        binding.setRightExpireAt(LocalDateTime.of(2026, 5, 1, 0, 0));
        binding.setBindTime(LocalDateTime.of(2026, 4, 1, 0, 0));

        PageInfo<UserShopBindingVO> pageInfo = new PageInfo<>(List.of(binding));
        pageInfo.setTotal(1);
        pageInfo.setPageNum(1);
        pageInfo.setPageSize(10);
        pageInfo.setPages(1);
        binding.setBoundUserId("user-1");
        binding.setBoundUserName("代理A");
        when(userShopBindingService.listCurrentUserShops("测试", null, 1, 10)).thenReturn(pageInfo);

        MtPowerAccount account = new MtPowerAccount();
        account.setBalance(88);
        when(mtPowerAccountService.getOrCreateAccount(101L)).thenReturn(account);

        MtH5PromotionSettingsVO settings = new MtH5PromotionSettingsVO();
        settings.setPromotionEnabled(true);
        settings.setStrategyConfigured(true);
        settings.setStrategyEntryVisible(true);
        settings.setStrategyConfigJson("{\"priority\":\"rate\"}");
        when(mtPromotionSettingService.buildSettingsVO(eq(101L), any())).thenReturn(settings);

        ShopDashboardMetricsService.MarketingStatsSnapshot marketing = new ShopDashboardMetricsService.MarketingStatsSnapshot();
        marketing.setCurrentCost(new BigDecimal("66.00"));
        marketing.setPreviousCost(new BigDecimal("55.00"));
        marketing.setCurrentCostRatio(new BigDecimal("0.100000"));
        marketing.setPreviousCostRatio(new BigDecimal("0.090000"));
        ShopDashboardMetricsService.PowerStatsSnapshot power = new ShopDashboardMetricsService.PowerStatsSnapshot();
        power.setBalance(88);
        power.setCurrentConsumedPower(12);
        power.setPreviousConsumedPower(6);
        when(shopDashboardMetricsService.buildPowerStats(101L, account, DashboardStatsRangeTypeEnum.LAST_30_DAYS)).thenReturn(power);

        ShopDashboardMetricsService.OrderStatsSnapshot order = new ShopDashboardMetricsService.OrderStatsSnapshot();
        order.setCurrentCompletedIncome(new BigDecimal("888.00"));
        order.setPreviousCompletedIncome(new BigDecimal("777.00"));
        order.setCurrentCompletedOrderCount(23);
        order.setPreviousCompletedOrderCount(20);

        marketing.setCurrentExposure(50);
        marketing.setPreviousExposure(40);
        when(shopDashboardMetricsService.buildDashboardStatsBatch(List.of(101L), DashboardStatsRangeTypeEnum.LAST_30_DAYS))
                .thenReturn(Map.of(101L, dashboardStats(marketing, order)));
        MtPromotionBidRecord latestSuccessRecord = new MtPromotionBidRecord();
        latestSuccessRecord.setShopId(101L);
        latestSuccessRecord.setNextBidAmount(new BigDecimal("0.80"));
        latestSuccessRecord.setMetricsPayloadJson("{\"promotionBalance\":\"123.45\"}");
        when(mtPromotionBidRecordMapper.selectLatestSuccessByShopIds(List.of(101L))).thenReturn(List.of(latestSuccessRecord));
        when(mtPromotionBidRecordMapper.selectLatestByShopIds(List.of(101L))).thenReturn(List.of());

        PageInfo<AdminMasterConsoleRowVO> result = adminMasterConsoleService.listRows(
                "测试",
                null,
                DashboardStatsRangeTypeEnum.LAST_30_DAYS,
                1,
                10
        );

        assertEquals(1, result.getList().size());
        assertEquals("poi-101", result.getList().get(0).getPoiId());
        assertEquals("user-1", result.getList().get(0).getBoundUserId());
        assertEquals("代理A", result.getList().get(0).getBoundUserName());
        assertEquals(new BigDecimal("888.00"), result.getList().get(0).getCurrentCompletedIncome());
        assertEquals(50, result.getList().get(0).getCurrentPaidExposure());
        assertEquals(new BigDecimal("0.80"), result.getList().get(0).getLatestPromotionBid());
        assertEquals(new BigDecimal("123.45"), result.getList().get(0).getPromotionBalance());
        assertEquals(88, result.getList().get(0).getPowerBalance());
        assertEquals(1, result.getTotal());
    }

    @Test
    void shouldRedactPowerColumnsForYouzanPortalRows() {
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId("youzan-1");
        loginUser.setActivePortal(BackendPortalType.YOUZAN);
        UserContext.setLoginUser(loginUser);

        UserShopBindingVO binding = new UserShopBindingVO();
        binding.setBindingId(1L);
        binding.setShopId(101L);
        binding.setPoiId("poi-101");
        PageInfo<UserShopBindingVO> pageInfo = new PageInfo<>(List.of(binding));
        pageInfo.setTotal(1);
        when(userShopBindingService.listCurrentUserShops(null, null, 1, 10)).thenReturn(pageInfo);

        MtH5PromotionSettingsVO settings = new MtH5PromotionSettingsVO();
        settings.setPromotionEnabled(true);
        settings.setStrategyConfigured(true);
        settings.setStrategyEntryVisible(true);
        when(mtPromotionSettingService.buildSettingsVO(eq(101L), any())).thenReturn(settings);
        when(shopDashboardMetricsService.buildDashboardStatsBatch(List.of(101L), DashboardStatsRangeTypeEnum.TODAY))
                .thenReturn(Map.of(101L, dashboardStats(
                        new ShopDashboardMetricsService.MarketingStatsSnapshot(),
                        new ShopDashboardMetricsService.OrderStatsSnapshot()
                )));

        PageInfo<AdminMasterConsoleRowVO> result = adminMasterConsoleService.listRows(
                null,
                null,
                DashboardStatsRangeTypeEnum.TODAY,
                1,
                10
        );

        AdminMasterConsoleRowVO row = result.getList().get(0);
        assertNull(row.getPowerBalance());
        assertNull(row.getCurrentConsumedPower());
        assertNull(row.getPreviousConsumedPower());
        verify(mtPowerAccountService, never()).getOrCreateAccount(101L);
        verify(shopDashboardMetricsService, never()).buildPowerStats(eq(101L), any(), eq(DashboardStatsRangeTypeEnum.TODAY));
    }

    @Test
    void shouldRestrictShopScopedOperationsToBoundShop() {
        MtShop shop = new MtShop();
        shop.setId(101L);
        when(userShopBindingService.requireCurrentUserVisibleShop(101L, null)).thenReturn(shop);
        mockSummaryDependencies();

        MtH5PromotionSettingsVO settings = new MtH5PromotionSettingsVO();
        when(mtPromotionSettingService.getSettingsByShopId(101L)).thenReturn(settings);

        MtH5PromotionSettingsUpdateParam param = new MtH5PromotionSettingsUpdateParam();
        adminMasterConsoleService.getPromotionSettings(101L);
        adminMasterConsoleService.updatePromotionSettings(101L, param);
        adminMasterConsoleService.listPromotionLogs(101L, 1, 20);
        adminMasterConsoleService.getPromotionLogSummary(101L);

        verify(userShopBindingService, times(4)).requireCurrentUserVisibleShop(101L, null);
        verify(mtPromotionSettingService).getSettingsByShopId(101L);
        verify(mtPromotionSettingService).updateSettingsByShopIdWithRemoteSync(
                eq(101L),
                any(MtH5PromotionSettingsUpdateParam.class),
                eq(shop)
        );
        verify(mtPromotionLogService).listLogs(shop, 1, 20);
    }

    @Test
    void shouldPassBindingDomainIntoMasterConsoleListRows() {
        UserShopBindingVO binding = new UserShopBindingVO();
        binding.setBindingId(1L);
        binding.setShopId(101L);
        binding.setPoiId("poi-sales");
        binding.setBindingDomain("SALES");
        binding.setBindingDomainName("网销用户");
        PageInfo<UserShopBindingVO> pageInfo = new PageInfo<>(List.of(binding));
        pageInfo.setTotal(1);
        when(userShopBindingService.listCurrentUserShops("门店", "sales-user", "SALES", null, null, null, 1, 10))
                .thenReturn(pageInfo);

        MtH5PromotionSettingsVO settings = new MtH5PromotionSettingsVO();
        settings.setPromotionEnabled(true);
        settings.setStrategyConfigured(true);
        settings.setStrategyEntryVisible(true);
        when(mtPromotionSettingService.buildSettingsVO(eq(101L), any())).thenReturn(settings);
        when(mtPowerAccountService.getOrCreateAccount(101L)).thenReturn(new MtPowerAccount());
        when(shopDashboardMetricsService.buildPowerStats(eq(101L), any(), eq(DashboardStatsRangeTypeEnum.TODAY)))
                .thenReturn(new ShopDashboardMetricsService.PowerStatsSnapshot());
        when(shopDashboardMetricsService.buildDashboardStatsBatch(List.of(101L), DashboardStatsRangeTypeEnum.TODAY))
                .thenReturn(Map.of(101L, dashboardStats(
                        new ShopDashboardMetricsService.MarketingStatsSnapshot(),
                        new ShopDashboardMetricsService.OrderStatsSnapshot()
                )));

        PageInfo<AdminMasterConsoleRowVO> result = adminMasterConsoleService.listRows(
                "门店",
                "sales-user",
                "SALES",
                DashboardStatsRangeTypeEnum.TODAY,
                1,
                10
        );

        assertEquals(1, result.getList().size());
        assertEquals("SALES", result.getList().get(0).getBindingDomain());
        assertEquals("网销用户", result.getList().get(0).getBindingDomainName());
        verify(userShopBindingService).listCurrentUserShops("门店", "sales-user", "SALES", null, null, null, 1, 10);
    }

    @Test
    void shouldPassBindingStatusIntoMasterConsoleListRows() {
        UserShopBindingVO binding = new UserShopBindingVO();
        binding.setShopId(202L);
        binding.setPoiId("poi-202");
        binding.setBindingStatus("UNBOUND");
        PageInfo<UserShopBindingVO> pageInfo = new PageInfo<>(List.of(binding));
        pageInfo.setTotal(1);
        when(userShopBindingService.listCurrentUserShops(null, null, "ALL", "UNBOUND", null, null, 1, 10))
                .thenReturn(pageInfo);

        MtH5PromotionSettingsVO settings = new MtH5PromotionSettingsVO();
        settings.setPromotionEnabled(false);
        settings.setStrategyConfigured(false);
        settings.setStrategyEntryVisible(true);
        when(mtPromotionSettingService.buildSettingsVO(eq(202L), any())).thenReturn(settings);
        when(mtPowerAccountService.getOrCreateAccount(202L)).thenReturn(new MtPowerAccount());
        when(shopDashboardMetricsService.buildPowerStats(eq(202L), any(), eq(DashboardStatsRangeTypeEnum.TODAY)))
                .thenReturn(new ShopDashboardMetricsService.PowerStatsSnapshot());
        when(shopDashboardMetricsService.buildDashboardStatsBatch(List.of(202L), DashboardStatsRangeTypeEnum.TODAY))
                .thenReturn(Map.of(202L, dashboardStats(
                        new ShopDashboardMetricsService.MarketingStatsSnapshot(),
                        new ShopDashboardMetricsService.OrderStatsSnapshot()
                )));

        PageInfo<AdminMasterConsoleRowVO> result = adminMasterConsoleService.listRows(
                null,
                null,
                "ALL",
                "UNBOUND",
                DashboardStatsRangeTypeEnum.TODAY,
                1,
                10
        );

        assertEquals(1, result.getList().size());
        assertEquals("UNBOUND", result.getList().get(0).getBindingStatus());
        verify(userShopBindingService).listCurrentUserShops(null, null, "ALL", "UNBOUND", null, null, 1, 10);
    }

    @Test
    void shouldPassPromotionStatusIntoMasterConsoleListRows() {
        PageInfo<UserShopBindingVO> pageInfo = new PageInfo<>(List.of());
        when(userShopBindingService.listCurrentUserShops(null, null, "ALL", "ALL", "ENABLED", null, null, 1, 10))
                .thenReturn(pageInfo);

        adminMasterConsoleService.listRows(
                null,
                null,
                "ALL",
                "ALL",
                "ENABLED",
                DashboardStatsRangeTypeEnum.TODAY,
                null,
                null,
                1,
                10
        );

        verify(userShopBindingService).listCurrentUserShops(null, null, "ALL", "ALL", "ENABLED", null, null, 1, 10);
    }

    @Test
    void shouldExportMasterConsoleRowsWithSalesAndPowerColumns() throws Exception {
        UserShopBindingVO binding = new UserShopBindingVO();
        binding.setShopId(303L);
        binding.setPoiId("poi-303");
        binding.setShopName("导出门店");
        binding.setSalesBoundUserId("sales-1");
        binding.setSalesBoundUserName("网销A");
        binding.setSalesBoundUserPhone("13800000000");
        binding.setPowerBalance(5000);
        binding.setTotalConsume(120);
        binding.setRightExpireAt(LocalDateTime.now().plusDays(2).plusHours(3));
        PageInfo<UserShopBindingVO> pageInfo = new PageInfo<>(List.of(binding));
        when(userShopBindingService.listCurrentUserShops(
                "导出",
                null,
                "ALL",
                "ALL",
                "powerBalance",
                "descend",
                1,
                10001
        )).thenReturn(pageInfo);

        MtH5PromotionSettingsVO settings = new MtH5PromotionSettingsVO();
        settings.setPromotionEnabled(true);
        when(mtPromotionSettingService.buildSettingsVO(eq(303L), any())).thenReturn(settings);

        byte[] content = adminMasterConsoleService.exportXlsx(
                "导出",
                null,
                "ALL",
                "ALL",
                "powerBalance",
                "descend"
        );

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            assertEquals("店铺名称", workbook.getSheetAt(0).getRow(0).getCell(0).getStringCellValue());
            assertEquals("导出门店", workbook.getSheetAt(0).getRow(1).getCell(0).getStringCellValue());
            assertEquals("poi-303", workbook.getSheetAt(0).getRow(1).getCell(1).getStringCellValue());
            assertEquals("网销A", workbook.getSheetAt(0).getRow(1).getCell(2).getStringCellValue());
            assertEquals("13800000000", workbook.getSheetAt(0).getRow(1).getCell(3).getStringCellValue());
            assertEquals("已开启", workbook.getSheetAt(0).getRow(1).getCell(4).getStringCellValue());
            assertEquals("有效", workbook.getSheetAt(0).getRow(1).getCell(5).getStringCellValue());
            assertEquals("120", workbook.getSheetAt(0).getRow(1).getCell(6).getStringCellValue());
            assertEquals("5000", workbook.getSheetAt(0).getRow(1).getCell(7).getStringCellValue());
        }
    }

    @Test
    void shouldRejectPromotionWriteForSalesDomainRow() {
        MtH5PromotionSettingsUpdateParam param = new MtH5PromotionSettingsUpdateParam();

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> adminMasterConsoleService.updatePromotionSettings(101L, "SALES", param)
        );

        assertEquals("网销绑定门店仅支持查看，不能修改推广配置", exception.getMessage());
        verify(userShopBindingService, never()).requireCurrentUserVisibleShop(101L, "SALES");
        verify(mtPromotionSettingService, never()).updateSettingsByShopIdWithRemoteSync(any(), any(), any());
    }

    @Test
    void shouldAllowAdminSuperAdminToUpdateUnboundPromotionSettings() {
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId("admin-1");
        loginUser.setActivePortal(BackendPortalType.ADMIN);
        loginUser.setRoles(java.util.Set.of("super_admin"));
        UserContext.setLoginUser(loginUser);

        MtShop shop = new MtShop();
        shop.setId(202L);
        when(userShopBindingService.requireCurrentUserVisibleShop(202L, null)).thenReturn(shop);

        MtH5PromotionSettingsUpdateParam param = new MtH5PromotionSettingsUpdateParam();
        MtH5PromotionSettingsVO settings = new MtH5PromotionSettingsVO();
        when(mtPromotionSettingService.updateSettingsByShopIdWithRemoteSync(202L, param, shop))
                .thenReturn(settings);

        adminMasterConsoleService.updatePromotionSettings(202L, null, param);

        verify(userShopBindingService).requireCurrentUserVisibleShop(202L, null);
        verify(mtPromotionSettingService).updateSettingsByShopIdWithRemoteSync(202L, param, shop);
    }

    @Test
    void shouldUpdateManualPromotionBidThroughExistingAutoBidService() {
        MtShop shop = new MtShop();
        shop.setId(101L);
        when(userShopBindingService.requireCurrentUserVisibleShop(101L, null)).thenReturn(shop);

        MtH5ManualPromotionBidParam param = new MtH5ManualPromotionBidParam();
        param.setBidAmount(new BigDecimal("0.88"));
        MtH5ManualPromotionBidVO vo = new MtH5ManualPromotionBidVO();
        vo.setEffectiveBidAmount(new BigDecimal("0.88"));
        when(mtPromotionAutoBidService.updateCurrentWindowBidManually(shop, new BigDecimal("0.88")))
                .thenReturn(vo);

        MtH5ManualPromotionBidVO result = adminMasterConsoleService.updateManualPromotionBid(101L, null, param);

        assertEquals(new BigDecimal("0.88"), result.getEffectiveBidAmount());
        verify(userShopBindingService).requireCurrentUserVisibleShop(101L, null);
        verify(mtPromotionAutoBidService).updateCurrentWindowBidManually(shop, new BigDecimal("0.88"));
    }

    @Test
    void shouldRejectManualPromotionBidForSalesDomainRow() {
        MtH5ManualPromotionBidParam param = new MtH5ManualPromotionBidParam();
        param.setBidAmount(new BigDecimal("0.88"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> adminMasterConsoleService.updateManualPromotionBid(101L, "SALES", param)
        );

        assertEquals("网销绑定门店仅支持查看，不能修改推广配置", exception.getMessage());
        verify(userShopBindingService, never()).requireCurrentUserVisibleShop(101L, "SALES");
        verify(mtPromotionAutoBidService, never()).updateCurrentWindowBidManually(any(), any());
    }

    @Test
    void shouldQueryPeerAverageBidForPromotionLogSummary() {
        MtShop shop = new MtShop();
        shop.setId(101L);
        shop.setPoiId("poi-101");
        shop.setPoiName("测试门店");
        when(userShopBindingService.requireCurrentUserVisibleShop(101L, null)).thenReturn(shop);
        mockSummaryDependencies();

        MtAccessToken token = new MtAccessToken();
        token.setAccessToken("token");
        when(mtAccessTokenService.findByShopId(101L)).thenReturn(token);

        PeerBidPriceOpenApiResult peerBidResult = new PeerBidPriceOpenApiResult();
        peerBidResult.setSuccess(true);
        peerBidResult.setAverageBidAmount(new BigDecimal("0.66"));
        when(mtPromotionOpenApiClient.queryPeerAverageBid(any(), any(), eq(null), eq("MASTER_CONSOLE_LOG_DRAWER")))
                .thenReturn(peerBidResult);

        AdminMasterConsolePromotionLogSummaryVO summary = adminMasterConsoleService.getPromotionLogSummary(101L);

        assertEquals(new BigDecimal("0.66"), summary.getLatestPeerAverageBid());
        verify(mtAccessTokenService).findByShopId(101L);
    }

    @Test
    void shouldBuildAsyncTaskMetricsSnapshot() {
        MtProperties.Promotion promotion = new MtProperties.Promotion();
        promotion.getAsyncTask().setEnabled(true);
        promotion.getAsyncTask().setWorkerEnabled(true);
        promotion.getAsyncTask().setPollSize(20);
        promotion.getAsyncTask().setPollDelayMs(1000L);
        when(mtProperties.getPromotion()).thenReturn(promotion);
        when(mtAsyncTaskMetricsService.snapshot()).thenReturn(java.util.Map.of(
                "PROMOTION_AUTO_BID.enqueue", 12L,
                "PROMOTION_AUTO_BID.success", 10L
        ));

        MtAsyncTaskBacklogSummary summary = new MtAsyncTaskBacklogSummary();
        summary.setTaskType("PROMOTION_AUTO_BID");
        summary.setPendingCount(5L);
        summary.setReadyCount(3L);
        summary.setProcessingCount(2L);
        summary.setSuccessCount(10L);
        summary.setDeadCount(1L);
        when(mtAsyncTaskService.selectBacklogSummary(any())).thenReturn(List.of(summary));

        AdminAsyncTaskMetricsVO result = adminMasterConsoleService.getAsyncTaskMetrics();

        assertEquals(true, result.getAsyncTaskEnabled());
        assertEquals(true, result.getWorkerEnabled());
        assertEquals(20, result.getPollSize());
        assertEquals(1000L, result.getPollDelayMs());
        assertEquals(12L, result.getCounters().get("PROMOTION_AUTO_BID.enqueue"));
        assertEquals(1, result.getTaskBacklogs().size());
        assertEquals("PROMOTION_AUTO_BID", result.getTaskBacklogs().get(0).getTaskType());
        assertEquals(3L, result.getTaskBacklogs().get(0).getReadyCount());
    }

    private void mockSummaryDependencies() {
        when(promotionTimeWindowEvaluator.evaluate(any(), any(), any())).thenReturn(
                new PromotionTimeWindowResult(
                        true,
                        "营业时间内",
                        PromotionExecutionWindow.of(
                                LocalDateTime.of(2026, 4, 18, 9, 0),
                                LocalDateTime.of(2026, 4, 18, 21, 0)
                        )
                )
        );

        ShopPeakWindowProfileLookupResult lookupResult = new ShopPeakWindowProfileLookupResult();
        lookupResult.setGatingEnabled(true);
        lookupResult.setPeakMatched(false);
        lookupResult.setProfileStatus(ShopPeakWindowProfileLookupResult.STATUS_UNAVAILABLE);
        lookupResult.setUnavailableReason("样本不足");
        when(shopPeakWindowProfileService.lookup(any(), any())).thenReturn(lookupResult);
    }

    private ShopDashboardMetricsService.DashboardStatsSnapshot dashboardStats(
            ShopDashboardMetricsService.MarketingStatsSnapshot marketing,
            ShopDashboardMetricsService.OrderStatsSnapshot order
    ) {
        ShopDashboardMetricsService.DashboardStatsSnapshot dashboardStats =
                new ShopDashboardMetricsService.DashboardStatsSnapshot();
        dashboardStats.setMarketingStats(marketing);
        dashboardStats.setOrderStats(order);
        return dashboardStats;
    }
}
