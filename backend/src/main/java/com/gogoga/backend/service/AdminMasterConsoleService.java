package com.gogoga.backend.service;

import com.github.pagehelper.PageInfo;
import com.gogoga.backend.entity.mt.MtAccessToken;
import com.gogoga.backend.entity.mt.MtPowerAccount;
import com.gogoga.backend.enums.DashboardStatsRangeTypeEnum;
import com.gogoga.backend.enums.MtAccessTokenAuthStatusEnum;
import com.gogoga.backend.util.ExcelExportUtil;
import com.gogoga.backend.vo.AdminMasterConsoleRowVO;
import com.gogoga.backend.vo.UserShopBindingVO;
import com.gogoga.backend.vo.VisibleUserOptionVO;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 后台总控台服务。
 */
@Service
@RequiredArgsConstructor
public class AdminMasterConsoleService {

    private final UserShopBindingService userShopBindingService;
    private final MtAccessTokenService mtAccessTokenService;
    private final MtPowerAccountService mtPowerAccountService;
    private final ShopDashboardMetricsService shopDashboardMetricsService;

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
                ExcelExportUtil.column("好评数", AdminMasterConsoleRowVO::getGoodCommentCount),
                ExcelExportUtil.column("差评数", AdminMasterConsoleRowVO::getBadCommentCount),
                ExcelExportUtil.column("差评回复率", row -> formatRate(row.getBadCommentReplyRate())),
                ExcelExportUtil.column("本期消耗算力", AdminMasterConsoleRowVO::getCurrentConsumedPower),
                ExcelExportUtil.column("剩余算力", AdminMasterConsoleRowVO::getPowerBalance),
                ExcelExportUtil.column("功能剩余时间", row -> formatRemainingRightTime(row.getRightExpireAt()))
        ), rows.getList());
    }

    public List<VisibleUserOptionVO> listVisibleUsers(String keyword, String bindingDomain) {
        return userShopBindingService.listVisibleUsers(keyword, bindingDomain);
    }

    private PageInfo<AdminMasterConsoleRowVO> buildRows(PageInfo<UserShopBindingVO> bindingPage,
                                                        DashboardStatsRangeTypeEnum statsRange) {
        List<UserShopBindingVO> bindings = bindingPage.getList() == null
                ? Collections.emptyList()
                : bindingPage.getList();
        List<Long> shopIds = bindings.stream()
                .map(UserShopBindingVO::getShopId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, ShopDashboardMetricsService.DashboardStatsSnapshot> dashboardStatsByShopId =
                shopDashboardMetricsService.buildDashboardStatsBatch(shopIds, statsRange);

        List<AdminMasterConsoleRowVO> rows = bindings.stream()
                .map(binding -> buildRow(binding, statsRange, dashboardStatsByShopId.get(binding.getShopId())))
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
                                             DashboardStatsRangeTypeEnum statsRange,
                                             ShopDashboardMetricsService.DashboardStatsSnapshot dashboardStats) {
        MtPowerAccount powerAccount = mtPowerAccountService.getOrCreateAccount(binding.getShopId());
        MtAccessToken accessToken = mtAccessTokenService.findByShopId(binding.getShopId());
        ShopDashboardMetricsService.DashboardStatsSnapshot safeDashboardStats = dashboardStats == null
                ? shopDashboardMetricsService.buildDashboardStats(binding.getShopId(), statsRange)
                : dashboardStats;
        ShopDashboardMetricsService.CommentStatsSnapshot commentStats = safeDashboardStats.getCommentStats();
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

        row.setGoodCommentCount(commentStats.getGoodCommentCount());
        row.setPreviousGoodCommentCount(commentStats.getPreviousGoodCommentCount());
        row.setBadCommentCount(commentStats.getBadCommentCount());
        row.setPreviousBadCommentCount(commentStats.getPreviousBadCommentCount());
        row.setRepliedBadCommentCount(commentStats.getRepliedBadCommentCount());
        row.setBadCommentReplyRate(commentStats.getBadCommentReplyRate());
        row.setPreviousBadCommentReplyRate(commentStats.getPreviousBadCommentReplyRate());

        row.setPowerBalance(powerStats.getBalance());
        row.setTotalRecharge(powerStats.getTotalRecharge());
        row.setTotalGrant(powerAccount.getTotalGrant());
        row.setTotalConsume(powerStats.getTotalConsume());
        row.setCurrentConsumedPower(powerStats.getCurrentConsumedPower());
        row.setPreviousConsumedPower(powerStats.getPreviousConsumedPower());

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

    private String formatRate(BigDecimal value) {
        if (value == null) {
            return "";
        }
        return value.multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString() + "%";
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
