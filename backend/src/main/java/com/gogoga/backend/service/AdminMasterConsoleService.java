package com.gogoga.backend.service;

import com.github.pagehelper.PageInfo;
import com.gogoga.backend.entity.mt.MtAccessToken;
import com.gogoga.backend.entity.mt.MtPowerAccount;
import com.gogoga.backend.enums.DashboardStatsRangeTypeEnum;
import com.gogoga.backend.enums.ImBroadcastRepeatModeEnum;
import com.gogoga.backend.enums.ImBroadcastSettingStatusEnum;
import com.gogoga.backend.enums.MtAccessTokenAuthStatusEnum;
import com.gogoga.backend.param.ImBroadcastSettingUpdateParam;
import com.gogoga.backend.param.ShopOperationSettingsUpdateParam;
import com.gogoga.backend.util.ExcelExportUtil;
import com.gogoga.backend.vo.AdminMasterConsoleRowVO;
import com.gogoga.backend.vo.ImBroadcastContentOptimizeVO;
import com.gogoga.backend.vo.ImBroadcastSettingVO;
import com.gogoga.backend.vo.ShopOperationSettingsVO;
import com.gogoga.backend.vo.UserShopBindingVO;
import com.gogoga.backend.vo.VisibleUserOptionVO;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 后台总控台服务。
 */
@Service
@RequiredArgsConstructor
public class AdminMasterConsoleService {

    /** 群发星期数字与中文名的映射，1=周一 … 7=周日。 */
    private static final Map<Integer, String> WEEK_DAY_NAMES = Map.of(
            1, "周一", 2, "周二", 3, "周三", 4, "周四",
            5, "周五", 6, "周六", 7, "周日");

    private final UserShopBindingService userShopBindingService;
    private final MtAccessTokenService mtAccessTokenService;
    private final MtPowerAccountService mtPowerAccountService;
    private final ShopDashboardMetricsService shopDashboardMetricsService;
    private final ShopOperationSettingsService shopOperationSettingsService;
    private final MtImBroadcastSettingService mtImBroadcastSettingService;
    private final ImBroadcastContentService imBroadcastContentService;

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

    public byte[] exportXlsx(String keyword,
                             String targetUserId,
                             String bindingDomain,
                             String bindingStatus,
                             String sortField,
                             String sortOrder,
                             DashboardStatsRangeTypeEnum statsRange) {
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
        PageInfo<AdminMasterConsoleRowVO> rows = buildRows(page, statsRange);
        return ExcelExportUtil.toXlsx("总控台", List.of(
                ExcelExportUtil.column("店铺名称", AdminMasterConsoleRowVO::getShopName),
                ExcelExportUtil.column("门店ID", AdminMasterConsoleRowVO::getPoiId),
                ExcelExportUtil.column("绑定域", AdminMasterConsoleRowVO::getBindingDomainName),
                ExcelExportUtil.column("所属用户", AdminMasterConsoleRowVO::getBoundUserName),
                ExcelExportUtil.column("所属网销", this::resolveExportSalesUserName),
                ExcelExportUtil.column("网销手机号", AdminMasterConsoleRowVO::getSalesBoundUserPhone),
                ExcelExportUtil.column("授权状态", AdminMasterConsoleRowVO::getAuthStatusText),
                ExcelExportUtil.column("IM自动回复开关", row -> Boolean.TRUE.equals(row.getAutoReplyEnabled()) ? "开启" : "关闭"),
                ExcelExportUtil.column("定时群发", this::formatExportBroadcast),
                ExcelExportUtil.column("累计群发条数", row -> row.getBroadcastSetting() == null
                        ? 0L : row.getBroadcastSetting().getTotalSentCount()),
                ExcelExportUtil.column("本期消耗算力", AdminMasterConsoleRowVO::getCurrentConsumedPower),
                ExcelExportUtil.column("剩余算力", AdminMasterConsoleRowVO::getPowerBalance),
                ExcelExportUtil.column("功能剩余时间", row -> formatRemainingRightTime(row.getRightExpireAt()))
        ), rows.getList());
    }

    public List<VisibleUserOptionVO> listVisibleUsers(String keyword, String bindingDomain) {
        return userShopBindingService.listVisibleUsers(keyword, bindingDomain);
    }

    public ShopOperationSettingsVO updateOperationSettings(Long shopId, ShopOperationSettingsUpdateParam param) {
        userShopBindingService.requireCurrentUserVisibleShop(shopId, null);
        return shopOperationSettingsService.updateSettings(
                shopId,
                param,
                ShopOperationSettingsService.CHANGE_SOURCE_ADMIN
        );
    }

    public ImBroadcastSettingVO getBroadcastSetting(Long shopId) {
        userShopBindingService.requireCurrentUserVisibleShop(shopId, null);
        return mtImBroadcastSettingService.getSettings(shopId);
    }

    public ImBroadcastSettingVO saveBroadcastSetting(Long shopId, ImBroadcastSettingUpdateParam param) {
        userShopBindingService.requireCurrentUserVisibleShop(shopId, null);
        return mtImBroadcastSettingService.saveSettings(
                shopId, param, MtImBroadcastSettingService.CHANGE_SOURCE_ADMIN);
    }

    public ImBroadcastSettingVO deleteBroadcastSetting(Long shopId) {
        userShopBindingService.requireCurrentUserVisibleShop(shopId, null);
        return mtImBroadcastSettingService.deleteSettings(
                shopId, MtImBroadcastSettingService.CHANGE_SOURCE_ADMIN);
    }

    public ImBroadcastContentOptimizeVO optimizeBroadcastContent(Long shopId, String content) {
        userShopBindingService.requireCurrentUserVisibleShop(shopId, null);
        // 代运营优化仍扣目标门店的算力，权益与算力校验都在 service 内按目标门店进行。
        return imBroadcastContentService.optimize(shopId, content);
    }

    private String formatExportBroadcast(AdminMasterConsoleRowVO row) {
        ImBroadcastSettingVO setting = row.getBroadcastSetting();
        if (setting == null || ImBroadcastSettingStatusEnum.NONE.name().equals(setting.getStatus())) {
            return ImBroadcastSettingStatusEnum.NONE.getDesc();
        }
        String weekDays = setting.getWeekDays() == null ? "" : setting.getWeekDays().stream()
                .map(WEEK_DAY_NAMES::get)
                .collect(Collectors.joining("、"));
        String repeatMode = ImBroadcastRepeatModeEnum.ONCE.name().equals(setting.getRepeatMode()) ? "一次" : "每次";
        String schedule = weekDays + " " + setting.getSendTime() + " " + repeatMode;
        return ImBroadcastSettingStatusEnum.ONCE_COMPLETED.name().equals(setting.getStatus())
                ? setting.getStatusName() + "，执行于 " + setting.getLastTriggerDate() + "（" + schedule + "）"
                : schedule;
    }

    public void requireVisibleShop(Long shopId) {
        userShopBindingService.requireCurrentUserVisibleShop(shopId, null);
    }

    private PageInfo<AdminMasterConsoleRowVO> buildRows(PageInfo<UserShopBindingVO> bindingPage,
                                                        DashboardStatsRangeTypeEnum statsRange) {
        List<UserShopBindingVO> bindings = bindingPage.getList() == null
                ? Collections.emptyList()
                : bindingPage.getList();
        List<AdminMasterConsoleRowVO> rows = bindings.stream()
                .map(binding -> buildRow(binding, statsRange))
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

    private AdminMasterConsoleRowVO buildRow(UserShopBindingVO binding,
                                             DashboardStatsRangeTypeEnum statsRange) {
        MtPowerAccount powerAccount = mtPowerAccountService.getOrCreateAccount(binding.getShopId());
        MtAccessToken accessToken = mtAccessTokenService.findByShopId(binding.getShopId());
        ShopOperationSettingsVO operationSettings = shopOperationSettingsService.buildSettings(binding.getShopId());
        ShopDashboardMetricsService.PowerStatsSnapshot powerStats =
                shopDashboardMetricsService.buildPowerStats(binding.getShopId(), powerAccount, statsRange);

        AdminMasterConsoleRowVO row = new AdminMasterConsoleRowVO();
        row.setBindingId(binding.getBindingId());
        row.setShopId(binding.getShopId());
        row.setPoiId(binding.getPoiId());
        row.setEpoiId(binding.getEpoiId());
        row.setShopName(binding.getShopName());
        row.setAddress(binding.getAddress());
        row.setContactPhone(binding.getContactPhone());
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
        row.setSalesBoundUserId(resolveSalesUserId(binding));
        row.setSalesBoundUserName(resolveSalesUserName(binding));
        row.setSalesBoundUserPhone(binding.getSalesBoundUserPhone());
        row.setSalesBindTime(binding.getSalesBindTime());
        row.setOrdinaryBindingCount(binding.getOrdinaryBindingCount());
        row.setSalesBindingCount(binding.getSalesBindingCount());

        row.setPowerBalance(powerStats.getBalance());
        row.setTotalRecharge(powerStats.getTotalRecharge());
        row.setTotalGrant(powerAccount.getTotalGrant());
        row.setTotalConsume(powerStats.getTotalConsume());
        row.setCurrentConsumedPower(powerStats.getCurrentConsumedPower());
        row.setPreviousConsumedPower(powerStats.getPreviousConsumedPower());

        applyAuthorizationStatus(row, accessToken);
        row.setRightExpireAt(binding.getRightExpireAt());
        row.setBindTime(binding.getBindTime());
        row.setAutoReplyEnabled(operationSettings != null
                && Boolean.TRUE.equals(operationSettings.getAutoReplyEnabled()));
        row.setBroadcastSetting(mtImBroadcastSettingService.getSettings(binding.getShopId()));
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

    private String resolveExportSalesUserName(AdminMasterConsoleRowVO row) {
        return StringUtils.firstNonBlank(row.getSalesBoundUserName(), row.getSalesBoundUserId());
    }

    private String resolveSalesUserId(UserShopBindingVO binding) {
        boolean salesDomainRow = StringUtils.equals(binding.getBindingDomain(), "SALES");
        return StringUtils.firstNonBlank(
                binding.getSalesBoundUserId(),
                salesDomainRow ? binding.getBoundUserId() : null
        );
    }

    private String resolveSalesUserName(UserShopBindingVO binding) {
        boolean salesDomainRow = StringUtils.equals(binding.getBindingDomain(), "SALES");
        return StringUtils.firstNonBlank(
                binding.getSalesBoundUserName(),
                salesDomainRow ? binding.getBoundUserName() : null,
                resolveSalesUserId(binding)
        );
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
}
