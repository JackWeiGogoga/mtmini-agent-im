package com.gogoga.backend.service;

import com.gogoga.backend.constant.MtH5GuideCodeConstant;
import com.gogoga.backend.enums.BackendPortalType;
import com.gogoga.backend.enums.DashboardStatsRangeTypeEnum;
import com.gogoga.backend.enums.MtAccessTokenAuthStatusEnum;
import com.gogoga.backend.entity.mt.MtAccessToken;
import com.gogoga.backend.entity.mt.MtPowerAccount;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.entity.mt.MtShopRight;
import com.gogoga.backend.util.UserContext;
import com.gogoga.backend.vo.MtH5AuthorizationStatusVO;
import com.gogoga.backend.vo.MtH5HomeDashboardVO;
import com.gogoga.backend.vo.MtH5TabStatusVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * H5 首页聚合看板服务。
 */
@Service
@RequiredArgsConstructor
public class MtH5HomeDashboardService {

    private static final int POWER_INSUFFICIENT_THRESHOLD = 500_000;

    private final MtH5ShopService mtH5ShopService;
    private final MtShopRightService mtShopRightService;
    private final MtPowerAccountService mtPowerAccountService;
    private final ShopDashboardMetricsService shopDashboardMetricsService;
    private final MtH5GuideStateService mtH5GuideStateService;
    private final MtAccessTokenService mtAccessTokenService;

    public MtH5HomeDashboardVO getDashboard() {
        return getDashboard(DashboardStatsRangeTypeEnum.LAST_7_DAYS);
    }

    public MtH5HomeDashboardVO getDashboard(DashboardStatsRangeTypeEnum statsRange) {
        MtShop shop = mtH5ShopService.getCurrentShop();
        boolean hidePower = UserContext.getActivePortal() == BackendPortalType.YOUZAN;
        MtPowerAccount powerAccount = hidePower ? null : mtPowerAccountService.getOrCreateAccount(shop.getId());

        MtH5HomeDashboardVO dashboard = new MtH5HomeDashboardVO();
        dashboard.setShop(buildShopSummary(shop));
        dashboard.setMembership(buildMembershipSummary(shop.getId()));
        dashboard.setMarketingStats(buildMarketingStats(shop.getId(), statsRange));
        dashboard.setTabStatus(buildTabStatus(powerAccount, hidePower));
        dashboard.setGuide(buildGuideSummary(shop.getId()));
        dashboard.setAuthorization(buildLocalAuthorizationStatus(shop.getId()));
        if (!hidePower) {
            dashboard.setPowerStats(buildPowerStats(shop.getId(), powerAccount, statsRange));
        }
        return dashboard;
    }

    public MtH5TabStatusVO getTabStatus() {
        boolean hidePower = UserContext.getActivePortal() == BackendPortalType.YOUZAN;
        MtShop shop = mtH5ShopService.getCurrentShop();
        MtPowerAccount powerAccount = hidePower ? null : mtPowerAccountService.getOrCreateAccount(shop.getId());
        return buildTabStatus(powerAccount, hidePower);
    }

    private MtH5AuthorizationStatusVO buildLocalAuthorizationStatus(Long shopId) {
        MtAccessToken token = mtAccessTokenService.findByShopId(shopId);
        MtH5AuthorizationStatusVO status = new MtH5AuthorizationStatusVO();
        status.setAuthStatus(token == null ? null : token.getAuthStatus());
        status.setAuthStatusChangedAt(token == null ? null : token.getAuthStatusChangedAt());
        status.setLastAuthFailureAt(token == null ? null : token.getLastAuthFailureAt());
        boolean reauthRequired = token == null
                || MtAccessTokenAuthStatusEnum.REAUTH_REQUIRED.getCode() == (token.getAuthStatus() == null
                ? MtAccessTokenAuthStatusEnum.VALID.getCode()
                : token.getAuthStatus());
        status.setReauthRequired(reauthRequired);
        status.setStatus(reauthRequired
                ? MtH5AuthorizationStatusVO.STATUS_REAUTH_REQUIRED
                : MtH5AuthorizationStatusVO.STATUS_VALID);
        status.setMessage(reauthRequired ? "授权失败，请重新授权！" : null);
        return status;
    }

    private MtH5HomeDashboardVO.ShopSummary buildShopSummary(MtShop shop) {
        MtH5HomeDashboardVO.ShopSummary summary = new MtH5HomeDashboardVO.ShopSummary();
        summary.setShopName(firstNonBlank(shop.getPoiName(), shop.getName(), "美团门店"));
        summary.setPoiId(shop.getPoiId());
        summary.setEpoiId(shop.getEpoiId());
        return summary;
    }

    private MtH5HomeDashboardVO.MembershipSummary buildMembershipSummary(Long shopId) {
        MtShopRight activeRight = mtShopRightService.findHomepageActiveRight(shopId);
        MtH5HomeDashboardVO.MembershipSummary summary = new MtH5HomeDashboardVO.MembershipSummary();
        summary.setActive(activeRight != null);
        summary.setExpireAt(activeRight == null ? null : activeRight.getExpireAt());
        return summary;
    }

    private MtH5HomeDashboardVO.MarketingStatsSummary buildMarketingStats(Long shopId,
                                                                          DashboardStatsRangeTypeEnum statsRange) {
        ShopDashboardMetricsService.MarketingStatsSnapshot snapshot =
                shopDashboardMetricsService.buildMarketingStats(shopId, statsRange);
        MtH5HomeDashboardVO.MarketingStatsSummary summary = new MtH5HomeDashboardVO.MarketingStatsSummary();
        summary.setCurrentIncome(snapshot.getCurrentIncome());
        summary.setPreviousIncome(snapshot.getPreviousIncome());
        return summary;
    }

    private MtH5HomeDashboardVO.PowerStatsSummary buildPowerStats(Long shopId,
                                                                  MtPowerAccount powerAccount,
                                                                  DashboardStatsRangeTypeEnum statsRange) {
        ShopDashboardMetricsService.PowerStatsSnapshot snapshot =
                shopDashboardMetricsService.buildPowerStats(shopId, powerAccount, statsRange);
        MtH5HomeDashboardVO.PowerStatsSummary summary = new MtH5HomeDashboardVO.PowerStatsSummary();
        summary.setCurrentConsumedPower(snapshot.getCurrentConsumedPower());
        summary.setPreviousConsumedPower(snapshot.getPreviousConsumedPower());
        summary.setCurrentEstimatedPower(snapshot.getCurrentEstimatedPower());
        summary.setPreviousEstimatedPower(snapshot.getPreviousEstimatedPower());
        summary.setBalance(snapshot.getBalance());
        summary.setTotalRecharge(snapshot.getTotalRecharge());
        summary.setTotalConsume(snapshot.getTotalConsume());
        summary.setInsufficient(isPowerInsufficient(snapshot.getBalance()));
        summary.setStatusText(Boolean.TRUE.equals(summary.getInsufficient()) ? "不足" : null);
        return summary;
    }

    private MtH5TabStatusVO buildTabStatus(MtPowerAccount powerAccount, boolean hidePower) {
        MtH5TabStatusVO status = new MtH5TabStatusVO();
        status.setLogUnread(false);
        boolean powerInsufficient = !hidePower && isPowerInsufficient(powerAccount == null ? null : powerAccount.getBalance());
        status.setPowerInsufficient(powerInsufficient);
        status.setPowerStatusText(powerInsufficient ? "不足" : null);
        return status;
    }

    private MtH5HomeDashboardVO.GuideSummary buildGuideSummary(Long shopId) {
        MtH5HomeDashboardVO.GuideSummary summary = new MtH5HomeDashboardVO.GuideSummary();
        summary.setHomeGuideCode(MtH5GuideCodeConstant.CURRENT_HOME_FIRST_AUTH_GUIDE);
        summary.setHomeGuideCompleted(Boolean.TRUE.equals(
                mtH5GuideStateService.getGuideState(shopId, MtH5GuideCodeConstant.CURRENT_HOME_FIRST_AUTH_GUIDE).getCompleted()
        ));
        return summary;
    }

    private boolean isPowerInsufficient(Integer balance) {
        return balance != null && balance < POWER_INSUFFICIENT_THRESHOLD;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
