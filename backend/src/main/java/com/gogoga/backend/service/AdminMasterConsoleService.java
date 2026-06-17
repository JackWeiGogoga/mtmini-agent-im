package com.gogoga.backend.service;

import com.github.pagehelper.PageInfo;
import com.gogoga.backend.client.result.PeerBidPriceOpenApiResult;
import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.enums.BackendPortalType;
import com.gogoga.backend.enums.DashboardStatsRangeTypeEnum;
import com.gogoga.backend.enums.MtAccessTokenAuthStatusEnum;
import com.gogoga.backend.enums.ShopBindingDomainEnum;
import com.gogoga.backend.entity.mt.MtAccessToken;
import com.gogoga.backend.entity.mt.MtAsyncTaskBacklogSummary;
import com.gogoga.backend.entity.mt.MtPromotionBidRecord;
import com.gogoga.backend.entity.mt.MtPromotionAutoBidCandidate;
import com.gogoga.backend.entity.mt.MtPromotionSetting;
import com.gogoga.backend.entity.mt.MtPowerAccount;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.entity.mt.MtShopPeakWindowProfile;
import com.gogoga.backend.mapper.MtPromotionBidRecordMapper;
import com.gogoga.backend.mapper.MtPromotionSettingMapper;
import com.gogoga.backend.mapper.MtShopMapper;
import com.gogoga.backend.mapper.MtShopPeakWindowProfileMapper;
import com.gogoga.backend.exception.BusinessException;
import com.gogoga.backend.param.MtH5ManualPromotionBidParam;
import com.gogoga.backend.param.MtH5PromotionSettingsUpdateParam;
import com.gogoga.backend.client.MtPromotionOpenApiClient;
import com.gogoga.backend.vo.AdminAsyncTaskMetricsVO;
import com.gogoga.backend.vo.AdminMasterConsolePromotionLogSummaryVO;
import com.gogoga.backend.vo.AdminMasterConsoleRowVO;
import com.gogoga.backend.vo.MtH5HomeDashboardVO;
import com.gogoga.backend.vo.MtH5ManualPromotionBidVO;
import com.gogoga.backend.vo.MtH5PromotionLogVO;
import com.gogoga.backend.vo.MtH5PromotionSettingsVO;
import com.gogoga.backend.vo.UserShopBindingVO;
import com.gogoga.backend.vo.VisibleUserOptionVO;
import com.gogoga.backend.service.promotion.PromotionTimeWindowEvaluator;
import com.gogoga.backend.service.promotion.PromotionTimeWindowResult;
import com.gogoga.backend.service.promotion.ShopPeakWindow;
import com.gogoga.backend.service.promotion.ShopPeakWindowProfileLookupResult;
import com.gogoga.backend.service.promotion.ShopPeakWindowProfileService;
import com.gogoga.backend.util.ExcelExportUtil;
import com.gogoga.backend.util.JsonUtil;
import com.gogoga.backend.util.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 后台总控台服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminMasterConsoleService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final UserShopBindingService userShopBindingService;
    private final MtAccessTokenService mtAccessTokenService;
    private final MtPowerAccountService mtPowerAccountService;
    private final MtPromotionSettingService mtPromotionSettingService;
    private final MtPromotionBidRecordMapper mtPromotionBidRecordMapper;
    private final MtPromotionSettingMapper mtPromotionSettingMapper;
    private final MtPromotionAutoBidService mtPromotionAutoBidService;
    private final MtH5HomeDashboardService mtH5HomeDashboardService;
    private final MtPromotionLogService mtPromotionLogService;
    private final ShopDashboardMetricsService shopDashboardMetricsService;
    private final MtPromotionOpenApiClient mtPromotionOpenApiClient;
    private final PromotionTimeWindowEvaluator promotionTimeWindowEvaluator;
    private final ShopPeakWindowProfileService shopPeakWindowProfileService;
    private final MtShopMapper mtShopMapper;
    private final MtShopPeakWindowProfileMapper mtShopPeakWindowProfileMapper;
    private final MtAsyncTaskService mtAsyncTaskService;
    private final MtAsyncTaskMetricsService mtAsyncTaskMetricsService;
    private final MtProperties mtProperties;

    public PageInfo<AdminMasterConsoleRowVO> listRows(String keyword, String targetUserId, Integer pageNum, Integer pageSize) {
        return listRows(keyword, targetUserId, DashboardStatsRangeTypeEnum.LAST_7_DAYS, pageNum, pageSize);
    }

    public PageInfo<AdminMasterConsoleRowVO> listRows(String keyword,
                                                      String targetUserId,
                                                      String bindingDomain,
                                                      String bindingStatus,
                                                      DashboardStatsRangeTypeEnum statsRange,
                                                      Integer pageNum,
                                                      Integer pageSize) {
        return listRows(keyword, targetUserId, bindingDomain, bindingStatus, statsRange, null, null, pageNum, pageSize);
    }

    public PageInfo<AdminMasterConsoleRowVO> listRows(String keyword,
                                                      String targetUserId,
                                                      String bindingDomain,
                                                      String bindingStatus,
                                                      DashboardStatsRangeTypeEnum statsRange,
                                                      String sortField,
                                                      String sortOrder,
                                                      Integer pageNum,
                                                      Integer pageSize) {
        PageInfo<UserShopBindingVO> bindingPage = userShopBindingService.listCurrentUserShops(
                keyword,
                targetUserId,
                bindingDomain,
                bindingStatus,
                sortField,
                sortOrder,
                pageNum,
                pageSize
        );
        return buildRows(bindingPage, statsRange);
    }

    public PageInfo<AdminMasterConsoleRowVO> listRows(String keyword,
                                                      String targetUserId,
                                                      String bindingDomain,
                                                      String bindingStatus,
                                                      String promotionStatus,
                                                      DashboardStatsRangeTypeEnum statsRange,
                                                      String sortField,
                                                      String sortOrder,
                                                      Integer pageNum,
                                                      Integer pageSize) {
        PageInfo<UserShopBindingVO> bindingPage = userShopBindingService.listCurrentUserShops(
                keyword,
                targetUserId,
                bindingDomain,
                bindingStatus,
                promotionStatus,
                sortField,
                sortOrder,
                pageNum,
                pageSize
        );
        return buildRows(bindingPage, statsRange);
    }

    public PageInfo<AdminMasterConsoleRowVO> listRows(String keyword,
                                                      String targetUserId,
                                                      String bindingDomain,
                                                      DashboardStatsRangeTypeEnum statsRange,
                                                      Integer pageNum,
                                                      Integer pageSize) {
        return listRows(keyword, targetUserId, bindingDomain, null, statsRange, pageNum, pageSize);
    }

    public PageInfo<AdminMasterConsoleRowVO> listRows(String keyword,
                                                      String targetUserId,
                                                      DashboardStatsRangeTypeEnum statsRange,
                                                      Integer pageNum,
                                                      Integer pageSize) {
        PageInfo<UserShopBindingVO> bindingPage = userShopBindingService.listCurrentUserShops(keyword, targetUserId, pageNum, pageSize);
        return buildRows(bindingPage, statsRange);
    }

    public byte[] exportXlsx(String keyword,
                             String targetUserId,
                             String bindingDomain,
                             String bindingStatus,
                             String sortField,
                             String sortOrder) {
        PageInfo<UserShopBindingVO> page = userShopBindingService.listCurrentUserShops(
                keyword,
                targetUserId,
                bindingDomain,
                bindingStatus,
                sortField,
                sortOrder,
                1,
                ExcelExportUtil.MAX_EXPORT_ROWS + 1
        );
        return exportRows(page);
    }

    public byte[] exportXlsx(String keyword,
                             String targetUserId,
                             String bindingDomain,
                             String bindingStatus,
                             String promotionStatus,
                             String sortField,
                             String sortOrder) {
        PageInfo<UserShopBindingVO> page = userShopBindingService.listCurrentUserShops(
                keyword,
                targetUserId,
                bindingDomain,
                bindingStatus,
                promotionStatus,
                sortField,
                sortOrder,
                1,
                ExcelExportUtil.MAX_EXPORT_ROWS + 1
        );
        return exportRows(page);
    }

    private byte[] exportRows(PageInfo<UserShopBindingVO> page) {
        List<UserShopBindingVO> rows = page.getList() == null ? Collections.emptyList() : page.getList();
        Map<Long, MtPromotionSetting> promotionSettingsByShopId = loadPromotionSettingsByShopId(rows.stream()
                .map(UserShopBindingVO::getShopId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(Collectors.toList()));
        return ExcelExportUtil.toXlsx("总控台", List.of(
                ExcelExportUtil.column("店铺名称", UserShopBindingVO::getShopName),
                ExcelExportUtil.column("门店id", UserShopBindingVO::getPoiId),
                ExcelExportUtil.column("所属网销", this::resolveExportSalesUserName),
                ExcelExportUtil.column("对应的网销手机号", UserShopBindingVO::getSalesBoundUserPhone),
                ExcelExportUtil.column("开关状态", row -> resolveExportPromotionEnabled(row, promotionSettingsByShopId)),
                ExcelExportUtil.column("授权状态", row -> resolveExportAuthStatus(row.getShopId())),
                ExcelExportUtil.column("消耗算力", UserShopBindingVO::getTotalConsume),
                ExcelExportUtil.column("剩余算力", UserShopBindingVO::getPowerBalance),
                ExcelExportUtil.column("功能剩余时间", row -> formatRemainingRightTime(row.getRightExpireAt()))
        ), rows);
    }

    private String resolveExportSalesUserName(UserShopBindingVO row) {
        if (row == null) {
            return "";
        }
        boolean salesDomainRow = ShopBindingDomainEnum.SALES.name().equals(row.getBindingDomain());
        return StringUtils.firstNonBlank(
                row.getSalesBoundUserName(),
                row.getSalesBoundUserId(),
                salesDomainRow ? row.getBoundUserName() : null,
                salesDomainRow ? row.getBoundUserId() : null
        );
    }

    private String resolveExportPromotionEnabled(UserShopBindingVO row, Map<Long, MtPromotionSetting> promotionSettingsByShopId) {
        if (row == null || row.getShopId() == null) {
            return "";
        }
        MtPromotionSetting setting = promotionSettingsByShopId.get(row.getShopId());
        MtH5PromotionSettingsVO settings = mtPromotionSettingService.buildSettingsVO(row.getShopId(), setting);
        return settings != null && Boolean.TRUE.equals(settings.getPromotionEnabled()) ? "已开启" : "已关闭";
    }

    private String resolveExportAuthStatus(Long shopId) {
        MtAccessToken token = shopId == null ? null : mtAccessTokenService.findByShopId(shopId);
        Integer authStatus = token == null || token.getAuthStatus() == null
                ? MtAccessTokenAuthStatusEnum.VALID.getCode()
                : token.getAuthStatus();
        return MtAccessTokenAuthStatusEnum.REAUTH_REQUIRED.getCode() == authStatus ? "需重新授权" : "有效";
    }

    private PageInfo<AdminMasterConsoleRowVO> buildRows(PageInfo<UserShopBindingVO> bindingPage,
                                                        DashboardStatsRangeTypeEnum statsRange) {
        List<UserShopBindingVO> bindings = bindingPage.getList() == null ? Collections.emptyList() : bindingPage.getList();
        List<Long> shopIds = bindings.stream()
                .map(UserShopBindingVO::getShopId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, MtPromotionSetting> promotionSettingsByShopId = loadPromotionSettingsByShopId(shopIds);
        Map<Long, ShopDashboardMetricsService.DashboardStatsSnapshot> dashboardStatsByShopId =
                shopIds.isEmpty()
                        ? Collections.emptyMap()
                        : shopDashboardMetricsService.buildDashboardStatsBatch(shopIds, statsRange);
        Map<Long, MtPromotionBidRecord> latestSuccessBidRecordsByShopId = loadLatestSuccessBidRecordsByShopId(shopIds);
        Map<Long, MtPromotionBidRecord> latestBidRecordsByShopId = loadLatestBidRecordsByShopId(shopIds);
        List<AdminMasterConsoleRowVO> rows = bindings.stream()
                .map(binding -> buildRow(
                        binding,
                        statsRange,
                        dashboardStatsByShopId.get(binding.getShopId()),
                        promotionSettingsByShopId.get(binding.getShopId()),
                        latestSuccessBidRecordsByShopId.get(binding.getShopId()),
                        latestBidRecordsByShopId.get(binding.getShopId())
                ))
                .collect(Collectors.toList());

        PageInfo<AdminMasterConsoleRowVO> result = new PageInfo<>();
        result.setList(rows);
        result.setTotal(bindingPage.getTotal());
        result.setPageNum(bindingPage.getPageNum());
        result.setPageSize(bindingPage.getPageSize());
        result.setPages(bindingPage.getPages());
        result.setIsFirstPage(bindingPage.isIsFirstPage());
        result.setIsLastPage(bindingPage.isIsLastPage());
        result.setHasPreviousPage(bindingPage.isHasPreviousPage());
        result.setHasNextPage(bindingPage.isHasNextPage());
        return result;
    }

    private Map<Long, MtPromotionBidRecord> loadLatestSuccessBidRecordsByShopId(List<Long> shopIds) {
        if (shopIds == null || shopIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<MtPromotionBidRecord> records = mtPromotionBidRecordMapper.selectLatestSuccessByShopIds(shopIds);
        return toBidRecordMap(records);
    }

    private Map<Long, MtPromotionBidRecord> loadLatestBidRecordsByShopId(List<Long> shopIds) {
        if (shopIds == null || shopIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<MtPromotionBidRecord> records = mtPromotionBidRecordMapper.selectLatestByShopIds(shopIds);
        return toBidRecordMap(records);
    }

    private Map<Long, MtPromotionBidRecord> toBidRecordMap(List<MtPromotionBidRecord> records) {
        if (records == null || records.isEmpty()) {
            return Collections.emptyMap();
        }
        return records.stream()
                .filter(record -> record.getShopId() != null)
                .collect(Collectors.toMap(
                        MtPromotionBidRecord::getShopId,
                        record -> record,
                        (left, right) -> left
                ));
    }

    private Map<Long, MtPromotionSetting> loadPromotionSettingsByShopId(List<Long> shopIds) {
        if (shopIds == null || shopIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<MtPromotionSetting> settings = mtPromotionSettingMapper.selectByShopIds(shopIds);
        if (settings == null || settings.isEmpty()) {
            return Collections.emptyMap();
        }
        return settings.stream()
                .filter(setting -> setting.getShopId() != null)
                .collect(Collectors.toMap(
                        MtPromotionSetting::getShopId,
                        setting -> setting,
                        (left, right) -> left
                ));
    }

    public List<VisibleUserOptionVO> listVisibleUsers(String keyword) {
        return userShopBindingService.listVisibleUsers(keyword);
    }

    public List<VisibleUserOptionVO> listVisibleUsers(String keyword, String bindingDomain) {
        return userShopBindingService.listVisibleUsers(keyword, bindingDomain);
    }

    public MtH5PromotionSettingsVO getPromotionSettings(Long shopId) {
        return getPromotionSettings(shopId, null);
    }

    public MtH5PromotionSettingsVO getPromotionSettings(Long shopId, String bindingDomain) {
        MtShop shop = userShopBindingService.requireCurrentUserVisibleShop(shopId, bindingDomain);
        return mtPromotionSettingService.getSettingsByShopId(shop.getId());
    }

    public MtH5PromotionSettingsVO updatePromotionSettings(Long shopId, MtH5PromotionSettingsUpdateParam param) {
        return updatePromotionSettings(shopId, null, param);
    }

    public MtH5PromotionSettingsVO updatePromotionSettings(Long shopId, String bindingDomain, MtH5PromotionSettingsUpdateParam param) {
        if (!(UserContext.getActivePortal() == BackendPortalType.ADMIN && UserContext.isSuperAdmin())
                && ShopBindingDomainEnum.fromValue(bindingDomain) == ShopBindingDomainEnum.SALES) {
            throw new BusinessException(403, "网销绑定门店仅支持查看，不能修改推广配置");
        }
        MtShop shop = userShopBindingService.requireCurrentUserVisibleShop(shopId, bindingDomain);
        MtH5PromotionSettingsVO settings = mtPromotionSettingService.updateSettingsByShopIdWithRemoteSync(shop.getId(), param, shop);
        mtPromotionAutoBidService.resetBidToFloorIfOutOfPromotionWindowAfterSettingsSave(shop);
        return settings;
    }

    public MtH5ManualPromotionBidVO updateManualPromotionBid(Long shopId, String bindingDomain, MtH5ManualPromotionBidParam param) {
        if (!(UserContext.getActivePortal() == BackendPortalType.ADMIN && UserContext.isSuperAdmin())
                && ShopBindingDomainEnum.fromValue(bindingDomain) == ShopBindingDomainEnum.SALES) {
            throw new BusinessException(403, "网销绑定门店仅支持查看，不能修改推广配置");
        }
        MtShop shop = userShopBindingService.requireCurrentUserVisibleShop(shopId, bindingDomain);
        return mtPromotionAutoBidService.updateCurrentWindowBidManually(shop, param.getBidAmount());
    }

    public PageInfo<MtH5PromotionLogVO> listPromotionLogs(Long shopId, Integer pageNum, Integer pageSize) {
        return listPromotionLogs(shopId, null, pageNum, pageSize);
    }

    public PageInfo<MtH5PromotionLogVO> listPromotionLogs(Long shopId, String bindingDomain, Integer pageNum, Integer pageSize) {
        MtShop shop = userShopBindingService.requireCurrentUserVisibleShop(shopId, bindingDomain);
        return mtPromotionLogService.listLogs(shop, pageNum, pageSize);
    }

    public AdminMasterConsolePromotionLogSummaryVO getPromotionLogSummary(Long shopId) {
        return getPromotionLogSummary(shopId, null);
    }

    public AdminMasterConsolePromotionLogSummaryVO getPromotionLogSummary(Long shopId, String bindingDomain) {
        MtShop shop = userShopBindingService.requireCurrentUserVisibleShop(shopId, bindingDomain);
        LocalDateTime now = LocalDateTime.now();
        AdminMasterConsolePromotionLogSummaryVO summary = new AdminMasterConsolePromotionLogSummaryVO();
        summary.setGeneratedAt(now);
        summary.setLatestPeerAverageBid(queryLatestPeerAverageBid(shop));
        summary.setBusinessStatus(buildBusinessStatus(shop, now));
        summary.setPeakStatus(buildPeakStatus(shop, now));
        return summary;
    }

    public AdminAsyncTaskMetricsVO getAsyncTaskMetrics() {
        LocalDateTime now = LocalDateTime.now();
        MtProperties.AsyncTask asyncTask = mtProperties.getPromotion().getAsyncTask();
        AdminAsyncTaskMetricsVO result = new AdminAsyncTaskMetricsVO();
        result.setGeneratedAt(now);
        result.setAsyncTaskEnabled(asyncTask.isEnabled());
        result.setWorkerEnabled(asyncTask.isWorkerEnabled());
        result.setPollSize(asyncTask.getPollSize());
        result.setPollDelayMs(asyncTask.getPollDelayMs());
        result.setCounters(mtAsyncTaskMetricsService.snapshot());
        result.setTaskBacklogs(buildTaskBacklogs(mtAsyncTaskService.selectBacklogSummary(now)));
        return result;
    }

    private AdminMasterConsoleRowVO buildRow(UserShopBindingVO binding,
                                             DashboardStatsRangeTypeEnum statsRange,
                                             ShopDashboardMetricsService.DashboardStatsSnapshot dashboardStats,
                                             MtPromotionSetting promotionSetting,
                                             MtPromotionBidRecord latestSuccessBidRecord,
                                             MtPromotionBidRecord latestBidRecord) {
        boolean hidePower = UserContext.getActivePortal() == BackendPortalType.YOUZAN;
        MtPowerAccount powerAccount = hidePower ? null : mtPowerAccountService.getOrCreateAccount(binding.getShopId());
        MtAccessToken accessToken = mtAccessTokenService.findByShopId(binding.getShopId());
        MtH5PromotionSettingsVO settings = mtPromotionSettingService.buildSettingsVO(binding.getShopId(), promotionSetting);
        if (settings == null) {
            settings = new MtH5PromotionSettingsVO();
            settings.setPromotionEnabled(false);
            settings.setStrategyConfigured(false);
            settings.setStrategyEntryVisible(true);
        }
        ShopDashboardMetricsService.DashboardStatsSnapshot safeDashboardStats = dashboardStats == null
                ? shopDashboardMetricsService.buildDashboardStats(binding.getShopId(), statsRange)
                : dashboardStats;
        ShopDashboardMetricsService.MarketingStatsSnapshot marketingStats = safeDashboardStats.getMarketingStats();
        ShopDashboardMetricsService.PowerStatsSnapshot powerStats = hidePower
                ? null
                : shopDashboardMetricsService.buildPowerStats(binding.getShopId(), powerAccount, statsRange);
        ShopDashboardMetricsService.OrderStatsSnapshot orderStats = safeDashboardStats.getOrderStats();
        AdminMasterConsoleRowVO row = new AdminMasterConsoleRowVO();
        row.setBindingId(binding.getBindingId());
        row.setShopId(binding.getShopId());
        row.setPoiId(binding.getPoiId());
        row.setEpoiId(binding.getEpoiId());
        row.setShopName(binding.getShopName());
        row.setAddress(binding.getAddress());
        row.setBoundUserId(binding.getBoundUserId());
        row.setBoundUserName(binding.getBoundUserName());
        row.setBindingDomain(binding.getBindingDomain());
        row.setBindingDomainName(binding.getBindingDomainName());
        row.setBindingStatus(binding.getBindingStatus());
        row.setBindingStatusName(binding.getBindingStatusName());
        row.setOrdinaryBindingId(binding.getOrdinaryBindingId());
        row.setOrdinaryBoundUserId(binding.getOrdinaryBoundUserId());
        row.setOrdinaryBoundUserName(binding.getOrdinaryBoundUserName());
        row.setOrdinaryBindTime(binding.getOrdinaryBindTime());
        row.setSalesBindingId(binding.getSalesBindingId());
        boolean salesDomainRow = ShopBindingDomainEnum.SALES.name().equals(binding.getBindingDomain());
        row.setSalesBoundUserId(StringUtils.firstNonBlank(
                binding.getSalesBoundUserId(),
                salesDomainRow ? binding.getBoundUserId() : null
        ));
        row.setSalesBoundUserName(StringUtils.firstNonBlank(
                binding.getSalesBoundUserName(),
                salesDomainRow ? binding.getBoundUserName() : null
        ));
        row.setSalesBoundUserPhone(binding.getSalesBoundUserPhone());
        row.setSalesBindTime(binding.getSalesBindTime());
        row.setOrdinaryBindingCount(binding.getOrdinaryBindingCount());
        row.setSalesBindingCount(binding.getSalesBindingCount());

        row.setCurrentCompletedIncome(orderStats.getCurrentCompletedIncome());
        row.setPreviousCompletedIncome(orderStats.getPreviousCompletedIncome());
        row.setCurrentCompletedOrderCount(orderStats.getCurrentCompletedOrderCount());
        row.setPreviousCompletedOrderCount(orderStats.getPreviousCompletedOrderCount());

        row.setCurrentPaidExposure(marketingStats.getCurrentExposure());
        row.setPreviousPaidExposure(marketingStats.getPreviousExposure());
        row.setLatestPromotionBid(resolveLatestPromotionBid(latestSuccessBidRecord));
        row.setCurrentBid(buildCurrentBidSummary(binding, settings, latestBidRecord));

        row.setPromotionBalance(resolvePromotionBalance(latestSuccessBidRecord));
        row.setCurrentPromotionCost(marketingStats.getCurrentCost());
        row.setPreviousPromotionCost(marketingStats.getPreviousCost());
        row.setCurrentCostRatio(marketingStats.getCurrentCostRatio());
        row.setPreviousCostRatio(marketingStats.getPreviousCostRatio());

        if (!hidePower && powerStats != null) {
            row.setPowerBalance(powerStats.getBalance());
            row.setCurrentConsumedPower(powerStats.getCurrentConsumedPower());
            row.setPreviousConsumedPower(powerStats.getPreviousConsumedPower());
        }

        row.setPromotionEnabled(settings.getPromotionEnabled());
        row.setStrategyConfigured(settings.getStrategyConfigured());
        row.setStrategyEntryVisible(settings.getStrategyEntryVisible());
        applyAuthorizationStatus(row, accessToken);

        row.setRightExpireAt(binding.getRightExpireAt());
        row.setBindTime(binding.getBindTime());
        return row;
    }

    private void applyAuthorizationStatus(AdminMasterConsoleRowVO row, MtAccessToken token) {
        Integer authStatus = token == null || token.getAuthStatus() == null
                ? MtAccessTokenAuthStatusEnum.VALID.getCode()
                : token.getAuthStatus();
        boolean reauthRequired = MtAccessTokenAuthStatusEnum.REAUTH_REQUIRED.getCode() == authStatus;
        row.setAuthStatus(authStatus);
        row.setAuthStatusText(reauthRequired ? "需重新授权" : "有效");
        row.setReauthRequired(reauthRequired);
        row.setAuthStatusChangedAt(token == null ? null : token.getAuthStatusChangedAt());
        row.setLastAuthFailureAt(token == null ? null : token.getLastAuthFailureAt());
    }

    private BigDecimal resolveLatestPromotionBid(MtPromotionBidRecord latestSuccessBidRecord) {
        if (latestSuccessBidRecord == null || latestSuccessBidRecord.getNextBidAmount() == null) {
            return null;
        }
        return latestSuccessBidRecord.getNextBidAmount().setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private BigDecimal resolvePromotionBalance(MtPromotionBidRecord latestSuccessBidRecord) {
        if (latestSuccessBidRecord == null
                || latestSuccessBidRecord.getMetricsPayloadJson() == null
                || latestSuccessBidRecord.getMetricsPayloadJson().isBlank()) {
            return null;
        }
        Map<String, Object> payload = JsonUtil.toMap(latestSuccessBidRecord.getMetricsPayloadJson());
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        Object value = payload.get("promotionBalance");
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(value)).setScale(2, java.math.RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private MtH5HomeDashboardVO.CurrentBidSummary buildCurrentBidSummary(UserShopBindingVO binding,
                                                                         MtH5PromotionSettingsVO settings,
                                                                         MtPromotionBidRecord latestBidRecord) {
        if (binding == null || binding.getShopId() == null) {
            return null;
        }
        MtShop shop = mtShopMapper.selectById(binding.getShopId());
        if (shop == null) {
            shop = new MtShop();
            shop.setId(binding.getShopId());
            shop.setPoiId(binding.getPoiId());
            shop.setPoiName(binding.getShopName());
        }
        return mtH5HomeDashboardService.buildCurrentBidSummary(shop, settings, latestBidRecord);
    }

    private String formatRemainingRightTime(LocalDateTime rightExpireAt) {
        if (rightExpireAt == null) {
            return "未开通";
        }
        LocalDateTime now = LocalDateTime.now();
        if (!rightExpireAt.isAfter(now)) {
            return "已过期";
        }
        long minutes = Duration.between(now, rightExpireAt).toMinutes();
        long days = minutes / (24 * 60);
        long hours = (minutes % (24 * 60)) / 60;
        if (days > 0) {
            return days + "天" + hours + "小时";
        }
        long remainingMinutes = minutes % 60;
        if (hours > 0) {
            return hours + "小时" + remainingMinutes + "分钟";
        }
        return Math.max(remainingMinutes, 1) + "分钟";
    }

    private java.math.BigDecimal queryLatestPeerAverageBid(MtShop shop) {
        if (shop == null || shop.getId() == null) {
            return null;
        }
        MtAccessToken accessToken = mtAccessTokenService.findByShopId(shop.getId());
        MtPromotionAutoBidCandidate candidate = new MtPromotionAutoBidCandidate();
        candidate.setShopId(shop.getId());
        candidate.setPoiId(shop.getPoiId());
        candidate.setShopName(shop.getPoiName());
        candidate.setAccessToken(accessToken == null ? null : accessToken.getAccessToken());
        PeerBidPriceOpenApiResult result = mtPromotionOpenApiClient.queryPeerAverageBid(
                candidate,
                java.time.LocalDateTime.now(),
                null,
                "MASTER_CONSOLE_LOG_DRAWER"
        );
        if (result == null || !result.isSuccess()) {
            log.warn("总控台推广日志摘要查询商圈平均出价失败，shopId={}, poiId={}, errorMsg={}, payload={}",
                    shop.getId(),
                    shop.getPoiId(),
                    result == null ? null : result.getErrorMsg(),
                    result == null ? null : result.getPayloadJson());
            return null;
        }
        return result.getAverageBidAmount();
    }

    private AdminMasterConsolePromotionLogSummaryVO.BusinessStatus buildBusinessStatus(MtShop shop, LocalDateTime now) {
        AdminMasterConsolePromotionLogSummaryVO.BusinessStatus status = new AdminMasterConsolePromotionLogSummaryVO.BusinessStatus();
        String shippingTime = shop == null ? null : shop.getShippingTime();
        status.setShippingTime(shippingTime);

        MtPromotionSettingService.StrategyConfig strategyConfig = new MtPromotionSettingService.StrategyConfig();
        strategyConfig.setTimeMode("business");
        PromotionTimeWindowResult result = promotionTimeWindowEvaluator.evaluate(strategyConfig, shippingTime, now);
        status.setOpen(result != null && result.isExecutable());
        status.setReason(result == null ? null : result.getReason());
        status.setCurrentWindow(formatBusinessWindow(result));
        return status;
    }

    private AdminMasterConsolePromotionLogSummaryVO.PeakStatus buildPeakStatus(MtShop shop, LocalDateTime now) {
        AdminMasterConsolePromotionLogSummaryVO.PeakStatus status = new AdminMasterConsolePromotionLogSummaryVO.PeakStatus();
        if (shop == null || shop.getId() == null) {
            status.setTrusted(false);
            return status;
        }

        ShopPeakWindowProfileLookupResult lookupResult = shopPeakWindowProfileService.lookup(shop.getId(), now);
        status.setPeakMatched(lookupResult != null && lookupResult.isPeakMatched());
        status.setGatingEnabled(lookupResult != null && lookupResult.isGatingEnabled());
        status.setTrusted(lookupResult != null && StringUtils.equals(lookupResult.getProfileStatus(), ShopPeakWindowProfileLookupResult.STATUS_USABLE));
        status.setCurrentDayType(lookupResult == null || lookupResult.getDayType() == null ? null : lookupResult.getDayType().name());
        status.setProfileDayType(lookupResult == null || lookupResult.getProfileDayType() == null ? null : lookupResult.getProfileDayType().name());
        status.setProfileStatus(lookupResult == null ? null : lookupResult.getProfileStatus());
        status.setSampleDayCount(lookupResult == null ? null : lookupResult.getSampleDayCount());
        status.setSampleOrderCount(lookupResult == null ? null : lookupResult.getSampleOrderCount());
        status.setUnavailableReason(lookupResult == null ? null : lookupResult.getUnavailableReason());
        status.setMatchedWindow(lookupResult == null ? null : lookupResult.getMatchedWindow());

        String profileDayType = status.getProfileDayType();
        if (StringUtils.isNotBlank(profileDayType)) {
            MtShopPeakWindowProfile profile = mtShopPeakWindowProfileMapper.selectByShopIdAndDayType(shop.getId(), profileDayType);
            List<ShopPeakWindow> windows = profile == null ? List.of() : JsonUtil.toList(profile.getPeakWindowsJson(), ShopPeakWindow.class);
            status.setConfiguredWindows(windows.stream().map(ShopPeakWindow::toDisplayText).collect(Collectors.toList()));
        } else {
            status.setConfiguredWindows(List.of());
        }
        return status;
    }

    private String formatBusinessWindow(PromotionTimeWindowResult result) {
        if (result == null || result.getExecutionWindow() == null) {
            return null;
        }
        return TIME_FORMATTER.format(result.getExecutionWindow().getWindowStartAt())
                + "-"
                + TIME_FORMATTER.format(result.getExecutionWindow().getWindowEndAt());
    }

    private List<AdminAsyncTaskMetricsVO.TaskBacklogVO> buildTaskBacklogs(List<MtAsyncTaskBacklogSummary> summaries) {
        if (summaries == null || summaries.isEmpty()) {
            return Collections.emptyList();
        }
        return summaries.stream()
                .map(this::buildTaskBacklog)
                .collect(Collectors.toList());
    }

    private AdminAsyncTaskMetricsVO.TaskBacklogVO buildTaskBacklog(MtAsyncTaskBacklogSummary summary) {
        AdminAsyncTaskMetricsVO.TaskBacklogVO backlog = new AdminAsyncTaskMetricsVO.TaskBacklogVO();
        backlog.setTaskType(summary.getTaskType());
        backlog.setPendingCount(defaultCount(summary.getPendingCount()));
        backlog.setReadyCount(defaultCount(summary.getReadyCount()));
        backlog.setProcessingCount(defaultCount(summary.getProcessingCount()));
        backlog.setSuccessCount(defaultCount(summary.getSuccessCount()));
        backlog.setDeadCount(defaultCount(summary.getDeadCount()));
        return backlog;
    }

    private long defaultCount(Long count) {
        return count == null ? 0L : count;
    }
}
