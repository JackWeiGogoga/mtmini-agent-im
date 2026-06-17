package com.gogoga.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gogoga.backend.constant.MtH5GuideCodeConstant;
import com.gogoga.backend.enums.BackendPortalType;
import com.gogoga.backend.enums.DashboardStatsRangeTypeEnum;
import com.gogoga.backend.enums.MtAccessTokenAuthStatusEnum;
import com.gogoga.backend.entity.mt.MtAccessToken;
import com.gogoga.backend.entity.mt.MtPowerAccount;
import com.gogoga.backend.entity.mt.MtPromotionBidRecord;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.entity.mt.MtShopRight;
import com.gogoga.backend.mapper.MtPromotionBidRecordMapper;
import com.gogoga.backend.service.promotion.PromotionTimeWindowEvaluator;
import com.gogoga.backend.service.promotion.PromotionTimeWindowResult;
import com.gogoga.backend.util.UserContext;
import com.gogoga.backend.vo.MtH5HomeDashboardVO;
import com.gogoga.backend.vo.MtH5AuthorizationStatusVO;
import com.gogoga.backend.vo.MtH5PromotionSettingsVO;
import com.gogoga.backend.vo.MtH5TabStatusVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * H5 首页聚合看板服务
 *
 * @author system
 * @since 2026-04-07
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MtH5HomeDashboardService {

    private static final int POWER_INSUFFICIENT_THRESHOLD = 500_000;
    private static final BigDecimal MIN_MANUAL_BID_AMOUNT = new BigDecimal("0.10");
    private static final BigDecimal MAX_MANUAL_BID_AMOUNT = new BigDecimal("4.00");

    private final MtH5ShopService mtH5ShopService;
    private final MtPromotionSettingService mtPromotionSettingService;
    private final MtShopRightService mtShopRightService;
    private final MtPowerAccountService mtPowerAccountService;
    private final ShopDashboardMetricsService shopDashboardMetricsService;
    private final MtPromotionBidRecordMapper mtPromotionBidRecordMapper;
    private final PromotionTimeWindowEvaluator promotionTimeWindowEvaluator;
    private final MtPromotionLogService mtPromotionLogService;
    private final MtH5GuideStateService mtH5GuideStateService;
    private final MtAccessTokenService mtAccessTokenService;
    private final ObjectMapper objectMapper;

    /**
     * 获取首页聚合看板
     *
     * @return 首页聚合看板
     */
    public MtH5HomeDashboardVO getDashboard() {
        return getDashboard(DashboardStatsRangeTypeEnum.LAST_7_DAYS);
    }

    public MtH5HomeDashboardVO getDashboard(DashboardStatsRangeTypeEnum statsRange) {
        MtShop shop = mtH5ShopService.getCurrentShop();
        MtH5PromotionSettingsVO settings = mtPromotionSettingService.getCurrentSettings();
        boolean hidePower = UserContext.getActivePortal() == BackendPortalType.YOUZAN;
        MtPowerAccount powerAccount = hidePower ? null : mtPowerAccountService.getOrCreateAccount(shop.getId());

        MtH5HomeDashboardVO dashboard = new MtH5HomeDashboardVO();
        dashboard.setShop(buildShopSummary(shop));
        dashboard.setMembership(buildMembershipSummary(shop.getId()));
        dashboard.setPromotion(buildPromotionSummary(shop.getId(), settings));
        dashboard.setMarketingStats(buildMarketingStats(shop.getId(), statsRange));
        dashboard.setCurrentBid(buildCurrentBidSummary(shop, settings));
        dashboard.setTabStatus(buildTabStatus(shop, powerAccount, hidePower));
        dashboard.setGuide(buildGuideSummary(shop.getId()));
        dashboard.setAuthorization(buildLocalAuthorizationStatus(shop.getId()));
        if (!hidePower) {
            dashboard.setPowerStats(buildPowerStats(shop.getId(), powerAccount, statsRange));
        }
        return dashboard;
    }

    private MtH5AuthorizationStatusVO buildLocalAuthorizationStatus(Long shopId) {
        return buildAuthorizationStatus(mtAccessTokenService.findByShopId(shopId));
    }

    private MtH5AuthorizationStatusVO buildAuthorizationStatus(MtAccessToken token) {
        MtH5AuthorizationStatusVO status = new MtH5AuthorizationStatusVO();
        status.setAuthStatus(token == null ? null : token.getAuthStatus());
        status.setAuthStatusChangedAt(token == null ? null : token.getAuthStatusChangedAt());
        status.setLastAuthFailureAt(token == null ? null : token.getLastAuthFailureAt());
        boolean reauthRequired = token == null
                || MtAccessTokenAuthStatusEnum.REAUTH_REQUIRED.getCode() == statusValue(token.getAuthStatus());
        status.setReauthRequired(reauthRequired);
        if (reauthRequired) {
            status.setStatus(MtH5AuthorizationStatusVO.STATUS_REAUTH_REQUIRED);
            status.setMessage("授权失败，请重新授权！");
        } else {
            status.setStatus(MtH5AuthorizationStatusVO.STATUS_VALID);
            status.setMessage(null);
        }
        return status;
    }

    private int statusValue(Integer value) {
        return value == null ? MtAccessTokenAuthStatusEnum.VALID.getCode() : value;
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

    private MtH5HomeDashboardVO.GuideSummary buildGuideSummary(Long shopId) {
        MtH5HomeDashboardVO.GuideSummary summary = new MtH5HomeDashboardVO.GuideSummary();
        summary.setHomeGuideCode(MtH5GuideCodeConstant.CURRENT_HOME_FIRST_AUTH_GUIDE);
        summary.setHomeGuideCompleted(Boolean.TRUE.equals(
                mtH5GuideStateService.getGuideState(shopId, MtH5GuideCodeConstant.CURRENT_HOME_FIRST_AUTH_GUIDE).getCompleted()
        ));
        return summary;
    }

    private MtH5HomeDashboardVO.PromotionSummary buildPromotionSummary(Long shopId, MtH5PromotionSettingsVO settings) {
        MtH5HomeDashboardVO.PromotionSummary summary = new MtH5HomeDashboardVO.PromotionSummary();
        summary.setPromotionEnabled(Boolean.TRUE.equals(settings.getPromotionEnabled()));
        summary.setBalanceReminderEnabled(Boolean.TRUE.equals(settings.getBalanceReminderEnabled()));
        summary.setStrategyConfigured(Boolean.TRUE.equals(settings.getStrategyConfigured()));
        summary.setStrategyEntryVisible(Boolean.TRUE.equals(settings.getStrategyEntryVisible()));
        summary.setStrategyConfigJson(settings.getStrategyConfigJson());
        summary.setAutoTimeModeAvailable(Boolean.TRUE.equals(settings.getAutoTimeModeAvailable()));
        summary.setAutoTimeModeHintText(settings.getAutoTimeModeHintText());
        summary.setPromotionDisabledReasonText(resolvePromotionDisabledReasonText(shopId, settings));
        return summary;
    }

    private String resolvePromotionDisabledReasonText(Long shopId, MtH5PromotionSettingsVO settings) {
        if (Boolean.TRUE.equals(settings.getPromotionEnabled())) {
            return null;
        }
        MtPromotionBidRecord record = mtPromotionBidRecordMapper.selectLatestByShopIdAndDecisionReason(
                shopId,
                "REMOTE_PLAN_STATUS_CLOSED"
        );
        if (record == null) {
            return null;
        }
        return "美团商家端推广计划已关闭，AI推广已自动停止，请重新开启商家端推广后再试。";
    }

    private MtH5HomeDashboardVO.MarketingStatsSummary buildMarketingStats(Long shopId, DashboardStatsRangeTypeEnum statsRange) {
        ShopDashboardMetricsService.MarketingStatsSnapshot snapshot =
                shopDashboardMetricsService.buildMarketingStats(shopId, statsRange);

        MtH5HomeDashboardVO.MarketingStatsSummary summary = new MtH5HomeDashboardVO.MarketingStatsSummary();
        summary.setCurrentExposure(snapshot.getCurrentExposure());
        summary.setPreviousExposure(snapshot.getPreviousExposure());
        summary.setCurrentIncome(snapshot.getCurrentIncome());
        summary.setPreviousIncome(snapshot.getPreviousIncome());
        summary.setCurrentCost(snapshot.getCurrentCost());
        summary.setPreviousCost(snapshot.getPreviousCost());
        summary.setCurrentCostRatio(snapshot.getCurrentCostRatio());
        summary.setPreviousCostRatio(snapshot.getPreviousCostRatio());
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

    public MtH5TabStatusVO getTabStatus() {
        MtShop shop = mtH5ShopService.getCurrentShop();
        boolean hidePower = UserContext.getActivePortal() == BackendPortalType.YOUZAN;
        MtPowerAccount powerAccount = hidePower ? null : mtPowerAccountService.getOrCreateAccount(shop.getId());
        return buildTabStatus(shop, powerAccount, hidePower);
    }

    private MtH5TabStatusVO buildTabStatus(MtShop shop, MtPowerAccount powerAccount, boolean hidePower) {
        MtH5TabStatusVO status = new MtH5TabStatusVO();
        status.setLogUnread(mtPromotionLogService.hasUnreadLogs(shop));
        boolean powerInsufficient = !hidePower && isPowerInsufficient(powerAccount == null ? null : powerAccount.getBalance());
        status.setPowerInsufficient(powerInsufficient);
        status.setPowerStatusText(powerInsufficient ? "不足" : null);
        return status;
    }

    private boolean isPowerInsufficient(Integer balance) {
        return balance != null && balance < POWER_INSUFFICIENT_THRESHOLD;
    }

    public MtH5HomeDashboardVO.CurrentBidSummary buildCurrentBidSummary(MtShop shop, MtH5PromotionSettingsVO settings) {
        return buildCurrentBidSummary(shop, settings, mtPromotionBidRecordMapper.selectLatestByShopId(shop.getId()));
    }

    public MtH5HomeDashboardVO.CurrentBidSummary buildCurrentBidSummary(MtShop shop,
                                                                        MtH5PromotionSettingsVO settings,
                                                                        MtPromotionBidRecord latestRecord) {
        MtH5HomeDashboardVO.CurrentBidSummary summary = new MtH5HomeDashboardVO.CurrentBidSummary();
        summary.setMinBidAmount(MIN_MANUAL_BID_AMOUNT);
        summary.setMaxBidAmount(MAX_MANUAL_BID_AMOUNT);

        if (latestRecord != null) {
            summary.setLatestExecuteStatus(latestRecord.getExecuteStatus());
            summary.setPreviousBidAmount(latestRecord.getPreviousBidAmount());
            summary.setLatestSuccessSlotTime(latestRecord.getSlotTime());
            if ("SUCCESS".equals(latestRecord.getExecuteStatus())) {
                summary.setCurrentBidAmount(latestRecord.getNextBidAmount());
            } else if ("SKIPPED".equals(latestRecord.getExecuteStatus())) {
                summary.setDisplayStatusText("已跳过");
            } else if ("FAILED".equals(latestRecord.getExecuteStatus())) {
                summary.setDisplayStatusText("执行失败");
            }
        }

        PromotionTimeWindowResult timeWindowResult = evaluateCurrentWindow(shop, settings);
        boolean inWindow = timeWindowResult != null && timeWindowResult.isExecutable();
        summary.setInPromotionWindow(inWindow);
        summary.setEditable(inWindow);
        summary.setDisabledReason(inWindow ? null : "非推广时段");
        if (timeWindowResult != null && timeWindowResult.getExecutionWindow() != null) {
            summary.setWindowKey(timeWindowResult.getExecutionWindow().getWindowKey());
            summary.setWindowStartAt(timeWindowResult.getExecutionWindow().getWindowStartAt());
            summary.setWindowEndAt(timeWindowResult.getExecutionWindow().getWindowEndAt());
        }
        return summary;
    }

    private PromotionTimeWindowResult evaluateCurrentWindow(MtShop shop, MtH5PromotionSettingsVO settings) {
        if (shop == null || settings == null || !Boolean.TRUE.equals(settings.getPromotionEnabled())
                || !Boolean.TRUE.equals(settings.getStrategyConfigured())
                || settings.getStrategyConfigJson() == null || settings.getStrategyConfigJson().isBlank()) {
            return new PromotionTimeWindowResult(false, "PROMOTION_NOT_READY", null);
        }
        try {
            MtPromotionSettingService.StrategyConfig strategyConfig = objectMapper.readValue(
                    settings.getStrategyConfigJson(),
                    MtPromotionSettingService.StrategyConfig.class
            );
            return promotionTimeWindowEvaluator.evaluate(
                    mtPromotionSettingService.normalizeForExecution(strategyConfig),
                    shop.getShippingTime(),
                    LocalDateTime.now()
            );
        } catch (Exception e) {
            log.warn("首页当前出价窗口判断失败，shopId={}, error={}", shop.getId(), e.getMessage());
            return new PromotionTimeWindowResult(false, "INVALID_STRATEGY_CONFIG", null);
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
