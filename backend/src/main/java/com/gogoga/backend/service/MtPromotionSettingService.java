package com.gogoga.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gogoga.backend.client.MtPromotionOpenApiClient;
import com.gogoga.backend.constant.PromotionPowerThresholdConstant;
import com.gogoga.backend.entity.LoginUser;
import com.gogoga.backend.entity.mt.MtAccessToken;
import com.gogoga.backend.entity.mt.MtPowerAccount;
import com.gogoga.backend.entity.mt.MtPromotionSetting;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.enums.MtAccessTokenAuthStatusEnum;
import com.gogoga.backend.exception.BusinessException;
import com.gogoga.backend.mapper.MtPromotionSettingMapper;
import com.gogoga.backend.service.promotion.ShopPeakWindowProfileLookupResult;
import com.gogoga.backend.service.promotion.ShopPeakWindowProfileService;
import com.gogoga.backend.service.promotion.ShopPeakWindow;
import com.gogoga.backend.param.MtH5PromotionSettingsUpdateParam;
import com.gogoga.backend.service.promotion.PromotionPlanStatusUpdateResult;
import com.gogoga.backend.util.UserContext;
import com.gogoga.backend.vo.MtH5PromotionSettingsVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 门店推广配置服务
 *
 * @author system
 * @since 2026-03-24
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MtPromotionSettingService {

    private static final int MINUTES_PER_DAY = 24 * 60;
    private static final boolean DEFAULT_PROMOTION_ENABLED = false;
    private static final boolean DEFAULT_BALANCE_REMINDER_ENABLED = true;
    private static final boolean DEFAULT_STRATEGY_CONFIGURED = false;
    private static final boolean DEFAULT_STRATEGY_ENTRY_VISIBLE = true;
    private static final String DEFAULT_STRATEGY_DECISION_MODE = "rule";
    private static final String AUTO_TIME_MODE_HINT_UNLOCK = "正常营业 5 天后解锁";
    private static final String AUTO_TIME_MODE_HINT_AVAILABLE = "AI识别高峰期";
    private static final String POWER_INSUFFICIENT_ENABLE_MESSAGE = "算力不足，请先充值再开启";
    public static final BigDecimal DEFAULT_BID_MIN = new BigDecimal("0.10");
    public static final BigDecimal DEFAULT_BID_MAX = new BigDecimal("4.00");
    private static final BigDecimal BID_STEP = new BigDecimal("0.10");

    private final MtPromotionSettingMapper mtPromotionSettingMapper;
    private final MtH5ShopService mtH5ShopService;
    private final MtAccessTokenService mtAccessTokenService;
    private final MtPromotionOpenApiClient mtPromotionOpenApiClient;
    private final MtOrderPushControlService mtOrderPushControlService;
    private final MtPowerAccountService mtPowerAccountService;
    private final ShopPeakWindowProfileService shopPeakWindowProfileService;
    private final MtHistoricalOrderBackfillService mtHistoricalOrderBackfillService;
    private final MtPromotionSettingHistoryService mtPromotionSettingHistoryService;
    private final ObjectMapper objectMapper;

    /**
     * 获取当前门店推广配置
     *
     * @return 推广配置
     */
    @Transactional(rollbackFor = Exception.class)
    public MtH5PromotionSettingsVO getCurrentSettings() {
        MtShop shop = mtH5ShopService.getCurrentShop();
        MtPromotionSetting setting = getOrCreateByShopId(shop.getId());
        return buildVO(shop.getId(), setting);
    }

    /**
     * 获取指定门店推广配置
     *
     * @param shopId 门店 ID
     * @return 推广配置
     */
    @Transactional(rollbackFor = Exception.class)
    public MtH5PromotionSettingsVO getSettingsByShopId(Long shopId) {
        return buildVO(shopId, getOrCreateByShopId(shopId));
    }

    public MtH5PromotionSettingsVO buildSettingsVO(Long shopId, MtPromotionSetting setting) {
        if (setting == null) {
            MtPromotionSetting defaultSetting = new MtPromotionSetting();
            defaultSetting.setShopId(shopId);
            defaultSetting.setPromotionEnabled(DEFAULT_PROMOTION_ENABLED);
            defaultSetting.setBalanceReminderEnabled(DEFAULT_BALANCE_REMINDER_ENABLED);
            defaultSetting.setStrategyConfigured(DEFAULT_STRATEGY_CONFIGURED);
            return buildVO(shopId, defaultSetting);
        }
        return buildVO(shopId, normalizeDefaults(setting));
    }

    /**
     * 更新当前门店推广配置
     *
     * @param param 更新参数
     * @return 推广配置
     */
    @Transactional(rollbackFor = Exception.class)
    public MtH5PromotionSettingsVO updateCurrentSettings(MtH5PromotionSettingsUpdateParam param) {
        validateHasUpdatableField(param);
        MtShop shop = mtH5ShopService.getCurrentShop();
        return updateSettingsByShopId(shop.getId(), param, true, shop);
    }

    /**
     * 手动识别高峰期并尝试解锁 AI 自动管理时段。
     *
     * @return 最新推广配置
     */
    public MtH5PromotionSettingsVO unlockCurrentAutoTimeMode() {
        MtShop shop = mtH5ShopService.getCurrentShop();
        MtPromotionSetting setting = getOrCreateByShopId(shop.getId());
        MtAccessToken accessToken = mtAccessTokenService.findByShopId(shop.getId());
        if (accessToken == null || StringUtils.isBlank(accessToken.getAccessToken())) {
            throw new BusinessException(400, "当前门店缺少美团授权，无法识别高峰期");
        }
        MtHistoricalOrderBackfillService.BackfillUnlockResult unlockResult =
                mtHistoricalOrderBackfillService.tryBackfillRecentOrdersAndRefreshPeakProfile(
                        shop,
                        accessToken.getAccessToken(),
                        MtHistoricalOrderBackfillService.TRIGGER_MANUAL_UNLOCK
                );
        return buildVO(shop.getId(), setting, unlockResult);
    }

    /**
     * 更新指定门店推广配置
     *
     * @param shopId 门店 ID
     * @param param 更新参数
     * @return 推广配置
     */
    @Transactional(rollbackFor = Exception.class)
    public MtH5PromotionSettingsVO updateSettingsByShopId(Long shopId, MtH5PromotionSettingsUpdateParam param) {
        return updateSettingsByShopId(shopId, param, false, null);
    }

    /**
     * 更新指定门店推广配置，并同步推广开关到美团
     *
     * @param shopId 门店 ID
     * @param param 更新参数
     * @param shop 当前操作的门店
     * @return 推广配置
     */
    @Transactional(rollbackFor = Exception.class)
    public MtH5PromotionSettingsVO updateSettingsByShopIdWithRemoteSync(
            Long shopId,
            MtH5PromotionSettingsUpdateParam param,
            MtShop shop
    ) {
        return updateSettingsByShopId(shopId, param, true, shop);
    }

    private MtH5PromotionSettingsVO updateSettingsByShopId(
            Long shopId,
            MtH5PromotionSettingsUpdateParam param,
            boolean syncPromotionSwitch,
            MtShop currentShop
    ) {
        validateHasUpdatableField(param);

        MtPromotionSetting setting = getOrCreateByShopId(shopId);
        MtPromotionSetting previousSetting = MtPromotionSettingHistoryService.copySetting(setting);
        Boolean previousPromotionEnabled = setting.getPromotionEnabled();
        Boolean previousBalanceReminderEnabled = setting.getBalanceReminderEnabled();
        Boolean previousStrategyConfigured = setting.getStrategyConfigured();
        String previousStrategyConfigJson = setting.getStrategyConfigJson();
        if (syncPromotionSwitch && shouldSyncPromotionSwitch(shopId, setting, param)) {
            validatePowerBeforeManualEnable(shopId, setting, param);
            syncPromotionSwitchToRemote(shopId, currentShop, param.getPromotionEnabled());
        }
        if (syncPromotionSwitch && shouldTryUnlockAutoTimeModeBeforeEnable(setting, param)) {
            runAfterCommitOrNow(() -> tryUnlockAutoTimeModeBeforeEnable(shopId, currentShop, setting));
        }
        if (param.getPromotionEnabled() != null) {
            setting.setPromotionEnabled(param.getPromotionEnabled());
        }
        if (param.getBalanceReminderEnabled() != null) {
            setting.setBalanceReminderEnabled(param.getBalanceReminderEnabled());
        }
        if (StringUtils.isNotBlank(param.getStrategyConfigJson())) {
            StrategyConfig strategyConfig = parseAndValidateStrategyConfig(param.getStrategyConfigJson());
            validateAutoTimeModeAvailability(shopId, strategyConfig);
            setting.setStrategyConfigJson(writeStrategyConfig(strategyConfig));
            setting.setStrategyConfigured(param.getStrategyConfigured() == null || param.getStrategyConfigured());
        } else if (param.getStrategyConfigured() != null) {
            setting.setStrategyConfigured(param.getStrategyConfigured());
            if (!param.getStrategyConfigured()) {
                setting.setStrategyConfigJson(null);
            }
        }
        setting.setUpdateTime(LocalDateTime.now());
        mtPromotionSettingMapper.updateById(setting);
        recordSettingHistoryBestEffort(
                previousSetting,
                setting,
                resolveChangeSource(syncPromotionSwitch),
                buildChangeReason(param, syncPromotionSwitch)
        );
        logPromotionSettingUpdated(
                shopId,
                currentShop,
                param,
                syncPromotionSwitch,
                previousPromotionEnabled,
                setting.getPromotionEnabled(),
                previousBalanceReminderEnabled,
                setting.getBalanceReminderEnabled(),
                previousStrategyConfigured,
                setting.getStrategyConfigured(),
                previousStrategyConfigJson,
                setting.getStrategyConfigJson()
        );
        return buildVO(shopId, setting);
    }

    private void logPromotionSettingUpdated(
            Long shopId,
            MtShop currentShop,
            MtH5PromotionSettingsUpdateParam param,
            boolean syncPromotionSwitch,
            Boolean previousPromotionEnabled,
            Boolean nextPromotionEnabled,
            Boolean previousBalanceReminderEnabled,
            Boolean nextBalanceReminderEnabled,
            Boolean previousStrategyConfigured,
            Boolean nextStrategyConfigured,
            String previousStrategyConfigJson,
            String nextStrategyConfigJson
    ) {
        LoginUser loginUser = UserContext.getLoginUser();
        StrategyConfig previousStrategyConfig = readStrategyConfigForLog(previousStrategyConfigJson);
        StrategyConfig nextStrategyConfig = readStrategyConfigForLog(nextStrategyConfigJson);
        log.info(
                "推广配置已更新，shopId={}, epoiId={}, poiId={}, operatorUserId={}, operatorUserName={}, activePortal={}, source={}, syncRemote={}, "
                        + "promotionEnabled={} -> {}, promotionEnabledChanged={}, balanceReminderEnabled={} -> {}, balanceReminderChanged={}, "
                        + "strategyConfigured={} -> {}, strategyConfiguredChanged={}, strategyConfigHash={} -> {}, strategyConfigChanged={}, "
                        + "previousStrategy={}, nextStrategy={}, requestedFields={}",
                shopId,
                currentShop == null ? null : currentShop.getEpoiId(),
                currentShop == null ? null : currentShop.getPoiId(),
                loginUser == null ? null : loginUser.getUserId(),
                loginUser == null ? null : loginUser.getUserName(),
                loginUser == null ? null : loginUser.getActivePortal(),
                syncPromotionSwitch ? "H5_OR_REMOTE_SYNC" : "INTERNAL_ADMIN",
                syncPromotionSwitch,
                previousPromotionEnabled,
                nextPromotionEnabled,
                !Objects.equals(previousPromotionEnabled, nextPromotionEnabled),
                previousBalanceReminderEnabled,
                nextBalanceReminderEnabled,
                !Objects.equals(previousBalanceReminderEnabled, nextBalanceReminderEnabled),
                previousStrategyConfigured,
                nextStrategyConfigured,
                !Objects.equals(previousStrategyConfigured, nextStrategyConfigured),
                strategyConfigHash(previousStrategyConfigJson),
                strategyConfigHash(nextStrategyConfigJson),
                !StringUtils.equals(previousStrategyConfigJson, nextStrategyConfigJson),
                summarizeStrategyConfig(previousStrategyConfig),
                summarizeStrategyConfig(nextStrategyConfig),
                summarizeRequestedFields(param)
        );
    }

    private StrategyConfig readStrategyConfigForLog(String strategyConfigJson) {
        if (StringUtils.isBlank(strategyConfigJson)) {
            return null;
        }
        try {
            return objectMapper.readValue(strategyConfigJson, StrategyConfig.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private String strategyConfigHash(String strategyConfigJson) {
        return MtPromotionSettingHistoryService.strategyConfigHash(strategyConfigJson);
    }

    private String resolveChangeSource(boolean syncPromotionSwitch) {
        if (UserContext.getActivePortal() == com.gogoga.backend.enums.BackendPortalType.ADMIN) {
            return MtPromotionSettingHistoryService.SOURCE_ADMIN;
        }
        return syncPromotionSwitch ? MtPromotionSettingHistoryService.SOURCE_H5 : MtPromotionSettingHistoryService.SOURCE_ADMIN;
    }

    private String buildChangeReason(MtH5PromotionSettingsUpdateParam param, boolean syncPromotionSwitch) {
        return (syncPromotionSwitch ? "REMOTE_SYNC" : "LOCAL_UPDATE") + ":" + summarizeRequestedFields(param);
    }

    private String summarizeStrategyConfig(StrategyConfig strategyConfig) {
        if (strategyConfig == null) {
            return null;
        }
        int customSlotCount = strategyConfig.getCustomSlots() == null ? 0 : strategyConfig.getCustomSlots().size();
        return "priority=" + strategyConfig.getPriority()
                + ",timeMode=" + strategyConfig.getTimeMode()
                + ",rateMode=" + strategyConfig.getRateMode()
                + ",rateMin=" + strategyConfig.getRateMin()
                + ",rateMax=" + strategyConfig.getRateMax()
                + ",bidMin=" + strategyConfig.getBidMin()
                + ",bidMax=" + strategyConfig.getBidMax()
                + ",decisionMode=" + strategyConfig.getDecisionMode()
                + ",customSlotCount=" + customSlotCount;
    }

    private String summarizeRequestedFields(MtH5PromotionSettingsUpdateParam param) {
        if (param == null) {
            return null;
        }
        return "promotionEnabled=" + (param.getPromotionEnabled() != null)
                + ",balanceReminderEnabled=" + (param.getBalanceReminderEnabled() != null)
                + ",strategyConfigJson=" + StringUtils.isNotBlank(param.getStrategyConfigJson())
                + ",strategyConfigured=" + (param.getStrategyConfigured() != null);
    }

    private boolean shouldSyncPromotionSwitch(Long shopId, MtPromotionSetting setting, MtH5PromotionSettingsUpdateParam param) {
        if (param == null || param.getPromotionEnabled() == null) {
            return false;
        }
        if (!Boolean.valueOf(Boolean.TRUE.equals(setting.getPromotionEnabled())).equals(param.getPromotionEnabled())) {
            return true;
        }
        if (Boolean.FALSE.equals(param.getPromotionEnabled())) {
            MtAccessToken accessToken = mtAccessTokenService.findByShopId(shopId);
            return accessToken != null && !Boolean.TRUE.equals(accessToken.getOrderPushBlocked());
        }
        return false;
    }

    private void validatePowerBeforeManualEnable(Long shopId, MtPromotionSetting setting, MtH5PromotionSettingsUpdateParam param) {
        if (param == null || !Boolean.TRUE.equals(param.getPromotionEnabled())) {
            return;
        }
        if (setting != null && Boolean.TRUE.equals(setting.getPromotionEnabled())) {
            return;
        }
        int balance = getCurrentPowerBalance(shopId);
        if (balance < PromotionPowerThresholdConstant.MIN_PROMOTION_EXECUTION_POWER) {
            throw new BusinessException(400, POWER_INSUFFICIENT_ENABLE_MESSAGE);
        }
    }

    private int getCurrentPowerBalance(Long shopId) {
        MtPowerAccount account = mtPowerAccountService.getOrCreateAccount(shopId);
        return account == null || account.getBalance() == null ? 0 : account.getBalance();
    }

    private void syncPromotionSwitchToRemote(Long shopId, MtShop shop, Boolean promotionEnabled) {
        if (shop == null || shop.getId() == null || !shop.getId().equals(shopId)) {
            throw new BusinessException(403, "当前门店无权修改推广状态");
        }
        MtAccessToken accessToken = mtAccessTokenService.findByShopId(shopId);
        if (accessToken == null || StringUtils.isBlank(accessToken.getAccessToken())) {
            throw new BusinessException(400, "当前门店缺少美团授权，无法同步推广状态");
        }
        if (Boolean.TRUE.equals(promotionEnabled)
                && Objects.equals(accessToken.getAuthStatus(), MtAccessTokenAuthStatusEnum.REAUTH_REQUIRED.getCode())) {
            throw new BusinessException(400, "美团授权失败，请商家重新授权后再开启推广");
        }
        PromotionPlanStatusUpdateResult result = mtPromotionOpenApiClient.updatePlanStatus(
                shopId,
                shop.getPoiId(),
                accessToken.getAccessToken(),
                Boolean.TRUE.equals(promotionEnabled) ? MtPromotionOpenApiClient.STATUS_OPEN : MtPromotionOpenApiClient.STATUS_CLOSE,
                null,
                null,
                Boolean.TRUE.equals(promotionEnabled) ? "MANUAL_SWITCH_ENABLE" : "MANUAL_SWITCH_DISABLE"
        );
        if (!result.isSuccess()) {
            throw new BusinessException(500, StringUtils.defaultIfBlank(result.getErrorMsg(), "同步美团推广状态失败"));
        }

        MtOrderPushControlService.OrderPushSyncResult orderPushResult = Boolean.TRUE.equals(promotionEnabled)
                ? mtOrderPushControlService.unblockOrderPush(accessToken, "MANUAL_SWITCH_ENABLE")
                : mtOrderPushControlService.blockOrderPush(accessToken, "MANUAL_SWITCH_DISABLE");
        if (!orderPushResult.success()) {
            throw new BusinessException(
                    500,
                    StringUtils.defaultIfBlank(
                            orderPushResult.errorMessage(),
                            Boolean.TRUE.equals(promotionEnabled) ? "同步开启订单推送失败" : "同步关闭订单推送失败"
                    )
            );
        }
    }

    private boolean shouldTryUnlockAutoTimeModeBeforeEnable(MtPromotionSetting setting, MtH5PromotionSettingsUpdateParam param) {
        if (setting == null || param == null || !Boolean.TRUE.equals(param.getPromotionEnabled())) {
            return false;
        }
        if (Boolean.TRUE.equals(setting.getPromotionEnabled()) || isAutoTimeModeAvailable(setting.getShopId())) {
            return false;
        }
        StrategyConfig strategyConfig = readStrategyConfigForLog(setting.getStrategyConfigJson());
        return strategyConfig != null
                && ("business".equals(strategyConfig.getTimeMode()) || "custom".equals(strategyConfig.getTimeMode()));
    }

    private void tryUnlockAutoTimeModeBeforeEnable(Long shopId, MtShop shop, MtPromotionSetting setting) {
        if (shop == null || shop.getId() == null || !shop.getId().equals(shopId)) {
            return;
        }
        MtAccessToken accessToken = mtAccessTokenService.findByShopId(shopId);
        if (accessToken == null || StringUtils.isBlank(accessToken.getAccessToken())) {
            log.info("跳过开启推广时识别高峰期，原因=缺少美团授权, shopId={}", shopId);
            return;
        }
        try {
            MtHistoricalOrderBackfillService.BackfillUnlockResult result =
                    mtHistoricalOrderBackfillService.tryBackfillRecentOrdersAndRefreshPeakProfile(
                            shop,
                            accessToken.getAccessToken(),
                            MtHistoricalOrderBackfillService.TRIGGER_ENABLE_PROMOTION
                    );
            log.info("开启推广时识别高峰期完成，shopId={}, strategyConfigured={}, autoTimeModeAvailable={}, unavailableReason={}",
                    shopId, setting.getStrategyConfigured(), result.autoTimeModeAvailable(), result.unavailableReason());
        } catch (RuntimeException e) {
            log.warn("开启推广时识别高峰期失败但不阻断推广开启，shopId={}", shopId, e);
        }
    }

    private void runAfterCommitOrNow(Runnable action) {
        if (action == null) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private void validateHasUpdatableField(MtH5PromotionSettingsUpdateParam param) {
        if (param == null
                || (param.getPromotionEnabled() == null
                && param.getBalanceReminderEnabled() == null
                && StringUtils.isBlank(param.getStrategyConfigJson())
                && param.getStrategyConfigured() == null)) {
            throw new BusinessException(400, "至少提供一个可更新字段");
        }
    }

    /**
     * 获取或初始化当前门店配置实体
     *
     * @return 配置实体
     */
    @Transactional(rollbackFor = Exception.class)
    public MtPromotionSetting getOrCreateCurrentSetting() {
        MtShop shop = mtH5ShopService.getCurrentShop();
        return getOrCreateByShopId(shop.getId());
    }

    /**
     * 获取或初始化指定门店配置实体
     *
     * @param shopId 门店 ID
     * @return 配置实体
     */
    @Transactional(rollbackFor = Exception.class)
    public MtPromotionSetting getOrCreateByShopId(Long shopId) {
        MtPromotionSetting setting = mtPromotionSettingMapper.selectByShopId(shopId);
        if (setting != null) {
            return normalizeDefaults(setting);
        }

        initDefaultForShopId(shopId);
        setting = mtPromotionSettingMapper.selectByShopId(shopId);
        if (setting == null) {
            throw new BusinessException(500, "初始化推广配置失败");
        }
        return normalizeDefaults(setting);
    }

    /**
     * 按门店关闭推广开关
     */
    @Transactional(rollbackFor = Exception.class)
    public void disablePromotionByShopId(Long shopId) {
        disablePromotionByShopId(shopId, "PROMOTION_BALANCE_BELOW_EXECUTION_THRESHOLD");
    }

    /**
     * 按门店关闭推广开关并记录系统原因。
     */
    @Transactional(rollbackFor = Exception.class)
    public void disablePromotionByShopId(Long shopId, String changeReason) {
        if (shopId == null) {
            return;
        }
        MtPromotionSetting setting = getOrCreateByShopId(shopId);
        if (!Boolean.TRUE.equals(setting.getPromotionEnabled())) {
            return;
        }
        MtPromotionSetting previousSetting = MtPromotionSettingHistoryService.copySetting(setting);
        setting.setPromotionEnabled(false);
        setting.setUpdateTime(LocalDateTime.now());
        mtPromotionSettingMapper.updateById(setting);
        recordSettingHistoryBestEffort(
                previousSetting,
                setting,
                MtPromotionSettingHistoryService.SOURCE_SYSTEM,
                StringUtils.defaultIfBlank(changeReason, "SYSTEM_DISABLE_PROMOTION")
        );
    }

    private void recordSettingHistoryBestEffort(
            MtPromotionSetting previousSetting,
            MtPromotionSetting setting,
            String changeSource,
            String changeReason
    ) {
        try {
            mtPromotionSettingHistoryService.recordIfChanged(
                    previousSetting,
                    setting,
                    changeSource,
                    changeReason
            );
        } catch (Exception e) {
            Long shopId = setting == null ? null : setting.getShopId();
            log.warn("Failed to record promotion setting history, shopId={}, changeSource={}",
                    shopId, changeSource, e);
        }
    }

    private MtH5PromotionSettingsVO buildVO(Long shopId, MtPromotionSetting setting) {
        return buildVO(shopId, setting, null);
    }

    private MtH5PromotionSettingsVO buildVO(
            Long shopId,
            MtPromotionSetting setting,
            MtHistoricalOrderBackfillService.BackfillUnlockResult unlockResult
    ) {
        MtH5PromotionSettingsVO vo = new MtH5PromotionSettingsVO();
        vo.setPromotionEnabled(Boolean.TRUE.equals(setting.getPromotionEnabled()));
        vo.setBalanceReminderEnabled(resolveBalanceReminderEnabled(setting.getBalanceReminderEnabled()));
        vo.setStrategyConfigured(Boolean.TRUE.equals(setting.getStrategyConfigured()));
        vo.setStrategyConfigJson(setting.getStrategyConfigJson());
        vo.setStrategyEntryVisible(DEFAULT_STRATEGY_ENTRY_VISIBLE);
        ShopPeakWindowProfileLookupResult lookupResult = lookupAutoTimeMode(shopId);
        boolean autoTimeModeAvailable = isAutoTimeModeAvailable(lookupResult);
        vo.setAutoTimeModeAvailable(autoTimeModeAvailable);
        vo.setAutoTimeModeHintText(autoTimeModeAvailable ? AUTO_TIME_MODE_HINT_AVAILABLE : AUTO_TIME_MODE_HINT_UNLOCK);
        vo.setAutoTimeModePeakWindows(resolvePeakWindowTexts(lookupResult, autoTimeModeAvailable));
        vo.setAutoTimeModeDayTypeText(resolvePeakDayTypeText(lookupResult));
        vo.setAutoTimeModePeakMatched(autoTimeModeAvailable && lookupResult != null && lookupResult.isPeakMatched());
        if (unlockResult != null) {
            vo.setAutoTimeModeUnlockAttempted(true);
            vo.setAutoTimeModeUnlockMessage(StringUtils.defaultIfBlank(
                    unlockResult.message(),
                    autoTimeModeAvailable
                            ? MtHistoricalOrderBackfillService.UNLOCK_SUCCESS_MESSAGE
                            : MtHistoricalOrderBackfillService.UNLOCK_INSUFFICIENT_MESSAGE
            ));
            vo.setAutoTimeModeUnavailableReason(unlockResult.unavailableReason());
        } else {
            vo.setAutoTimeModeUnlockAttempted(false);
        }
        return vo;
    }

    private void validateAutoTimeModeAvailability(Long shopId, StrategyConfig strategyConfig) {
        if (strategyConfig == null || !"auto".equals(strategyConfig.getTimeMode())) {
            return;
        }
        if (!isAutoTimeModeAvailable(shopId)) {
            throw new BusinessException(400, "当前门店暂无可信高峰期配置，请先使用按营业时长");
        }
    }

    private boolean isAutoTimeModeAvailable(Long shopId) {
        if (shopId == null) {
            return false;
        }
        return isAutoTimeModeAvailable(lookupAutoTimeMode(shopId));
    }

    private ShopPeakWindowProfileLookupResult lookupAutoTimeMode(Long shopId) {
        if (shopId == null) {
            return null;
        }
        return shopPeakWindowProfileService.lookup(shopId, LocalDateTime.now());
    }

    private boolean isAutoTimeModeAvailable(ShopPeakWindowProfileLookupResult lookupResult) {
        return lookupResult != null
                && StringUtils.equals(lookupResult.getProfileStatus(), ShopPeakWindowProfileLookupResult.STATUS_USABLE);
    }

    private List<String> resolvePeakWindowTexts(
            ShopPeakWindowProfileLookupResult lookupResult,
            boolean autoTimeModeAvailable
    ) {
        if (!autoTimeModeAvailable || lookupResult == null || lookupResult.getPeakWindows() == null) {
            return List.of();
        }
        return lookupResult.getPeakWindows().stream()
                .filter(Objects::nonNull)
                .map(ShopPeakWindow::toDisplayText)
                .filter(StringUtils::isNotBlank)
                .toList();
    }

    private String resolvePeakDayTypeText(ShopPeakWindowProfileLookupResult lookupResult) {
        if (lookupResult == null || lookupResult.getDayType() == null) {
            return null;
        }
        return switch (lookupResult.getDayType()) {
            case WEEKDAY -> "工作日";
            case WEEKEND -> "周末";
            case HOLIDAY -> StringUtils.defaultIfBlank(lookupResult.getHolidayName(), "节假日");
        };
    }

    private MtPromotionSetting normalizeDefaults(MtPromotionSetting setting) {
        boolean shouldPersist = false;

        if (setting.getPromotionEnabled() == null) {
            setting.setPromotionEnabled(DEFAULT_PROMOTION_ENABLED);
        }
        if (setting.getBalanceReminderEnabled() == null) {
            setting.setBalanceReminderEnabled(DEFAULT_BALANCE_REMINDER_ENABLED);
        }
        if (StringUtils.isBlank(setting.getStrategyConfigJson())) {
            setting.setStrategyConfigJson(null);
            if (setting.getStrategyConfigured() == null || Boolean.TRUE.equals(setting.getStrategyConfigured())) {
                setting.setStrategyConfigured(DEFAULT_STRATEGY_CONFIGURED);
                shouldPersist = true;
            }
        } else if (setting.getStrategyConfigured() == null) {
            setting.setStrategyConfigured(true);
            shouldPersist = true;
        }

        if (shouldPersist && setting.getId() != null) {
            setting.setUpdateTime(LocalDateTime.now());
            mtPromotionSettingMapper.updateById(setting);
        }
        return setting;
    }

    private boolean resolveBalanceReminderEnabled(Boolean balanceReminderEnabled) {
        return balanceReminderEnabled == null ? DEFAULT_BALANCE_REMINDER_ENABLED : balanceReminderEnabled;
    }

    private StrategyConfig parseAndValidateStrategyConfig(String strategyConfigJson) {
        try {
            StrategyConfig strategyConfig = objectMapper.readValue(strategyConfigJson, StrategyConfig.class);
            return normalizeForExecution(strategyConfig);
        } catch (JsonProcessingException e) {
            throw new BusinessException(400, "推广策略格式不正确");
        }
    }

    /**
     * 归一化策略配置，供保存与执行共用
     *
     * @param strategyConfig 策略配置
     * @return 归一化后的策略配置
     */
    public StrategyConfig normalizeForExecution(StrategyConfig strategyConfig) {
        if (strategyConfig != null && StringUtils.isBlank(strategyConfig.getDecisionMode())) {
            strategyConfig.setDecisionMode(DEFAULT_STRATEGY_DECISION_MODE);
        }
        validateStrategyConfig(strategyConfig);
        return strategyConfig;
    }

    private void validateStrategyConfig(StrategyConfig strategyConfig) {
        if (strategyConfig == null) {
            throw new BusinessException(400, "推广策略不能为空");
        }
        if (!"rate".equals(strategyConfig.getPriority()) && !"exposure".equals(strategyConfig.getPriority())) {
            throw new BusinessException(400, "推广策略优先级不正确");
        }
        if (!"auto".equals(strategyConfig.getTimeMode())
                && !"business".equals(strategyConfig.getTimeMode())
                && !"custom".equals(strategyConfig.getTimeMode())) {
            throw new BusinessException(400, "推广时段类型不正确");
        }
        if (!"control".equals(strategyConfig.getRateMode())) {
            throw new BusinessException(400, "推广费率模式不正确");
        }
        if (!"rule".equals(strategyConfig.getDecisionMode()) && !"llm".equals(strategyConfig.getDecisionMode())) {
            throw new BusinessException(400, "推广决策模式不正确");
        }

        int minRate = parseRateValue(strategyConfig.getRateMin(), "最小费率");
        int maxRate = parseRateValue(strategyConfig.getRateMax(), "最大费率");
        if (minRate > maxRate) {
            throw new BusinessException(400, "推广费率区间不正确");
        }
        normalizeAndValidateBidRange(strategyConfig);

        if ("custom".equals(strategyConfig.getTimeMode())) {
            strategyConfig.setCustomSlots(normalizeCustomSlots(strategyConfig.getCustomSlots()));
        }
    }

    private void normalizeAndValidateBidRange(StrategyConfig strategyConfig) {
        BigDecimal bidMin = normalizeBidAmount(strategyConfig.getBidMin() == null
                ? DEFAULT_BID_MIN
                : strategyConfig.getBidMin(), "AI最小出价");
        BigDecimal bidMax = normalizeBidAmount(strategyConfig.getBidMax() == null
                ? DEFAULT_BID_MAX
                : strategyConfig.getBidMax(), "AI最大出价");
        if (bidMin.compareTo(DEFAULT_BID_MIN) < 0 || bidMin.compareTo(DEFAULT_BID_MAX) > 0) {
            throw new BusinessException(400, "AI最小出价超出范围");
        }
        if (bidMax.compareTo(DEFAULT_BID_MIN) < 0 || bidMax.compareTo(DEFAULT_BID_MAX) > 0) {
            throw new BusinessException(400, "AI最大出价超出范围");
        }
        if (bidMin.compareTo(bidMax) > 0) {
            throw new BusinessException(400, "AI出价范围不正确");
        }
        strategyConfig.setBidMin(bidMin);
        strategyConfig.setBidMax(bidMax);
    }

    private BigDecimal normalizeBidAmount(BigDecimal bidAmount, String fieldName) {
        if (bidAmount == null) {
            throw new BusinessException(400, fieldName + "不能为空");
        }
        BigDecimal normalized = bidAmount.setScale(2, RoundingMode.HALF_UP);
        if (normalized.remainder(BID_STEP).compareTo(BigDecimal.ZERO.setScale(2)) != 0) {
            throw new BusinessException(400, fieldName + "最小修改单位为0.1元");
        }
        return normalized;
    }

    private int parseRateValue(String value, String fieldName) {
        if (StringUtils.isBlank(value) || !value.endsWith("%")) {
            throw new BusinessException(400, fieldName + "格式不正确");
        }

        String numberText = StringUtils.removeEnd(value, "%");
        try {
            int rate = Integer.parseInt(numberText);
            if (rate < 1 || rate > 20) {
                throw new BusinessException(400, fieldName + "超出范围");
            }
            return rate;
        } catch (NumberFormatException e) {
            throw new BusinessException(400, fieldName + "格式不正确");
        }
    }

    private List<StrategyTimeSlot> normalizeCustomSlots(List<StrategyTimeSlot> customSlots) {
        if (customSlots == null || customSlots.isEmpty()) {
            throw new BusinessException(400, "自定义时段不能为空");
        }

        List<MinuteSegment> segments = new ArrayList<>();
        for (StrategyTimeSlot slot : customSlots) {
            if (slot == null || StringUtils.isBlank(slot.getStart()) || StringUtils.isBlank(slot.getEnd())) {
                throw new BusinessException(400, "自定义时段不完整");
            }
            try {
                LocalTime start = LocalTime.parse(slot.getStart());
                LocalTime end = LocalTime.parse(slot.getEnd());
                if (start.equals(end)) {
                    throw new BusinessException(400, "自定义时段范围不正确");
                }
                appendSegments(segments, start, end);
            } catch (DateTimeParseException e) {
                throw new BusinessException(400, "自定义时段格式不正确");
            }
        }

        List<MinuteSegment> mergedSegments = mergeAdjacentSegments(segments);
        if (mergedSegments.size() == 1
                && mergedSegments.get(0).getStartMinute() == 0
                && mergedSegments.get(0).getEndMinute() == MINUTES_PER_DAY) {
            throw new BusinessException(400, "自定义时段不能覆盖全天，请使用让AI自动管理");
        }
        return buildNormalizedSlots(mergedSegments);
    }

    private String writeStrategyConfig(StrategyConfig strategyConfig) {
        try {
            return objectMapper.writeValueAsString(strategyConfig);
        } catch (JsonProcessingException e) {
            throw new BusinessException(500, "推广策略保存失败");
        }
    }

    /**
     * 初始化指定门店的默认推广配置
     *
     * @param shopId 门店 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void initDefaultForShopId(Long shopId) {
        if (shopId == null) {
            return;
        }
        mtPromotionSettingMapper.initDefaultIfAbsent(
                shopId,
                null,
                DEFAULT_STRATEGY_CONFIGURED,
                LocalDateTime.now()
        );
    }

    /**
     * 推广策略配置
     */
    @lombok.Data
    public static class StrategyConfig {

        /**
         * 优先级
         */
        private String priority;

        /**
         * 时段模式
         */
        private String timeMode;

        /**
         * 费率模式
         */
        private String rateMode;

        /**
         * 自定义时段
         */
        private List<StrategyTimeSlot> customSlots;

        /**
         * 最小费率
         */
        private String rateMin;

        /**
         * 最大费率
         */
        private String rateMax;

        /**
         * AI 自动定价最小出价，单位元
         */
        private BigDecimal bidMin;

        /**
         * AI 自动定价最大出价，单位元
         */
        private BigDecimal bidMax;

        /**
         * 调价决策模式
         */
        private String decisionMode;
    }

    /**
     * 推广时段
     */
    @lombok.Data
    public static class StrategyTimeSlot {

        /**
         * 开始时间
         */
        private String start;

        /**
         * 结束时间
         */
        private String end;
    }

    @lombok.Data
    private static class MinuteSegment {

        private int startMinute;

        private int endMinute;

        private MinuteSegment(int startMinute, int endMinute) {
            this.startMinute = startMinute;
            this.endMinute = endMinute;
        }
    }

    private void appendSegments(List<MinuteSegment> segments, LocalTime start, LocalTime end) {
        int startMinute = toMinute(start);
        int endMinute = toMinute(end);
        if (startMinute < endMinute) {
            segments.add(new MinuteSegment(startMinute, endMinute));
            return;
        }
        segments.add(new MinuteSegment(startMinute, MINUTES_PER_DAY));
        segments.add(new MinuteSegment(0, endMinute));
    }

    private List<MinuteSegment> mergeAdjacentSegments(List<MinuteSegment> segments) {
        List<MinuteSegment> sortedSegments = new ArrayList<>(segments);
        sortedSegments.sort(Comparator
                .comparingInt(MinuteSegment::getStartMinute)
                .thenComparingInt(MinuteSegment::getEndMinute));

        List<MinuteSegment> mergedSegments = new ArrayList<>();
        for (MinuteSegment segment : sortedSegments) {
            if (mergedSegments.isEmpty()) {
                mergedSegments.add(new MinuteSegment(segment.getStartMinute(), segment.getEndMinute()));
                continue;
            }

            MinuteSegment lastSegment = mergedSegments.get(mergedSegments.size() - 1);
            if (segment.getStartMinute() < lastSegment.getEndMinute()) {
                throw new BusinessException(400, "自定义时段不能重叠");
            }
            if (segment.getStartMinute() == lastSegment.getEndMinute()) {
                lastSegment.setEndMinute(segment.getEndMinute());
                continue;
            }
            mergedSegments.add(new MinuteSegment(segment.getStartMinute(), segment.getEndMinute()));
        }
        return mergedSegments;
    }

    private List<StrategyTimeSlot> buildNormalizedSlots(List<MinuteSegment> mergedSegments) {
        List<StrategyTimeSlot> normalizedSlots = new ArrayList<>();
        MinuteSegment leadingSegment = null;
        MinuteSegment trailingSegment = null;
        if (mergedSegments.size() >= 2
                && mergedSegments.get(0).getStartMinute() == 0
                && mergedSegments.get(mergedSegments.size() - 1).getEndMinute() == MINUTES_PER_DAY) {
            leadingSegment = mergedSegments.get(0);
            trailingSegment = mergedSegments.get(mergedSegments.size() - 1);
        }

        for (MinuteSegment segment : mergedSegments) {
            if (segment == leadingSegment || segment == trailingSegment) {
                continue;
            }
            normalizedSlots.add(buildSlot(segment.getStartMinute(), segment.getEndMinute()));
        }

        if (leadingSegment != null && trailingSegment != null) {
            normalizedSlots.add(buildSlot(trailingSegment.getStartMinute(), leadingSegment.getEndMinute()));
        }

        normalizedSlots.sort(Comparator.comparingInt(slot -> toMinute(LocalTime.parse(slot.getStart()))));
        return normalizedSlots;
    }

    private StrategyTimeSlot buildSlot(int startMinute, int endMinute) {
        StrategyTimeSlot slot = new StrategyTimeSlot();
        slot.setStart(formatMinute(startMinute));
        slot.setEnd(formatMinute(endMinute));
        return slot;
    }

    private int toMinute(LocalTime time) {
        return time.getHour() * 60 + time.getMinute();
    }

    private String formatMinute(int minute) {
        int normalizedMinute = ((minute % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY;
        int hour = normalizedMinute / 60;
        int minutePart = normalizedMinute % 60;
        return String.format("%02d:%02d", hour, minutePart);
    }
}
