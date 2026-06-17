package com.gogoga.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gogoga.backend.constant.PromotionPowerThresholdConstant;
import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.client.result.PeerBidPriceOpenApiResult;
import com.gogoga.backend.client.result.PromotionBidOpenApiResult;
import com.gogoga.backend.client.result.PromotionPlanStatusOpenApiResult;
import com.gogoga.backend.client.MtShopClient;
import com.gogoga.backend.client.MtPromotionOpenApiClient;
import com.gogoga.backend.entity.mt.MtPromotionAutoBidCandidate;
import com.gogoga.backend.entity.mt.MtPromotionAdRealtimeSnapshot;
import com.gogoga.backend.entity.mt.MtPromotionBidRecord;
import com.gogoga.backend.entity.mt.MtPromotionRecentOrderStatistic;
import com.gogoga.backend.entity.mt.MtPowerAccount;
import com.gogoga.backend.entity.mt.MtPromotionSetting;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.entity.mt.MtAccessToken;
import com.gogoga.backend.enums.MtAsyncTaskTypeEnum;
import com.gogoga.backend.enums.MtPromotionLogMarkdownStatusEnum;
import com.gogoga.backend.exception.BusinessException;
import com.gogoga.backend.mapper.MtPromotionBidRecordMapper;
import com.gogoga.backend.mapper.MtPromotionSettingMapper;
import com.gogoga.backend.service.promotion.PromotionBidDecisionService;
import com.gogoga.backend.service.promotion.PromotionDecisionEngine;
import com.gogoga.backend.service.promotion.PromotionDecisionDetail;
import com.gogoga.backend.service.promotion.PromotionDecisionResult;
import com.gogoga.backend.service.promotion.PromotionExposureDecisionResult;
import com.gogoga.backend.service.promotion.PromotionExposureDecisionService;
import com.gogoga.backend.service.promotion.PromotionExecutionWindow;
import com.gogoga.backend.service.promotion.PromotionLogAsyncTaskService;
import com.gogoga.backend.service.promotion.PromotionLogMarkdownResult;
import com.gogoga.backend.service.promotion.PromotionLogMarkdownService;
import com.gogoga.backend.service.promotion.PromotionLogMessageBuilder;
import com.gogoga.backend.service.promotion.PromotionMetricsProvider;
import com.gogoga.backend.service.promotion.PromotionMetricsSnapshot;
import com.gogoga.backend.service.promotion.PromotionRecentOrderStatisticService;
import com.gogoga.backend.service.promotion.PromotionTimeWindowEvaluator;
import com.gogoga.backend.service.promotion.PromotionTimeWindowResult;
import com.gogoga.backend.service.promotion.ShopPeakWindowProfileLookupResult;
import com.gogoga.backend.service.promotion.ShopPeakWindowProfileService;
import com.gogoga.backend.service.async.MtAsyncTaskEnqueueResult;
import com.gogoga.backend.service.async.MtPromotionAutoBidTaskPayload;
import com.gogoga.backend.service.async.MtPromotionLogMarkdownTaskPayload;
import com.gogoga.backend.util.JsonUtil;
import com.gogoga.backend.vo.MtH5ManualPromotionBidVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 门店自动调价服务
 *
 * @author system
 * @since 2026-03-26
 */
@Slf4j
@Service
public class MtPromotionAutoBidService {

    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_SKIPPED = "SKIPPED";
    private static final String STATUS_FAILED = "FAILED";
    private static final String SKIP_REASON_PROMOTION_DISABLED_DURING_TASK = "PROMOTION_DISABLED_DURING_TASK";
    private static final String SKIP_REASON_STRATEGY_CHANGED_DURING_TASK = "STRATEGY_CHANGED_DURING_TASK";
    private static final String SKIP_REASON_REMOTE_PLAN_STATUS_CLOSED = "REMOTE_PLAN_STATUS_CLOSED";
    private static final String SKIP_REASON_PROMOTION_DAILY_BUDGET_EXHAUSTED = "PROMOTION_DAILY_BUDGET_EXHAUSTED";
    private static final String DECISION_REASON_WINDOW_END_BID_RESET = "WINDOW_END_BID_RESET_TO_FLOOR";
    private static final String DECISION_REASON_SETTINGS_SAVE_OUT_OF_WINDOW_BID_RESET = "SETTINGS_SAVE_OUT_OF_WINDOW_BID_RESET";
    private static final String SKIP_REASON_UNTRUSTED_PROMOTION_METRICS = "UNTRUSTED_PROMOTION_METRICS";
    private static final String SKIP_REASON_POWER_BELOW_EXECUTION_THRESHOLD = "POWER_BELOW_EXECUTION_THRESHOLD_AUTO_DISABLED";
    private static final String SKIP_REASON_POWER_BELOW_WARNING_THRESHOLD = "POWER_BELOW_WARNING_THRESHOLD_REMINDER";
    private static final String SKIP_REASON_PROMOTION_BALANCE_BELOW_EXECUTION_THRESHOLD = "PROMOTION_BALANCE_BELOW_EXECUTION_THRESHOLD_AUTO_DISABLED";
    private static final String SKIP_REASON_PROMOTION_BALANCE_BELOW_WARNING_THRESHOLD = "PROMOTION_BALANCE_BELOW_WARNING_THRESHOLD_REMINDER";
    private static final String CHANGE_REASON_AUTH_FAILURE = MtMeituanAuthorizationFailureHandler.CHANGE_REASON_AUTH_FAILURE;
    private static final String INITIAL_BID_TRIGGER_PEER_AVG = "INITIAL_BID_FROM_PEER_AVG";
    private static final String LOW_PEAK_BID_TRIGGER_PEER_AVG = "LOW_PEAK_BASELINE_FROM_PEER_AVG";
    private static final String DECISION_REASON_LOW_PEAK_BASELINE_BID = "LOW_PEAK_BASELINE_BID";
    private static final String DECISION_REASON_PRE_PEAK_RAMP_UP = "PRE_PEAK_RAMP_UP";
    private static final String DECISION_REASON_POST_PEAK_RAMP_DOWN = "POST_PEAK_RAMP_DOWN";
    private static final String DECISION_REASON_EXPOSURE_GROWTH_EFFECTIVE_KEEP_BID = "EXPOSURE_GROWTH_EFFECTIVE_KEEP_BID";
    private static final String DECISION_REASON_EXPOSURE_GROWTH_WEAK_INCREASE_BID = "EXPOSURE_GROWTH_WEAK_INCREASE_BID";
    private static final String DECISION_REASON_EXPOSURE_WEAK_GROWTH_ROLLBACK_BID = "EXPOSURE_WEAK_GROWTH_ROLLBACK_BID";
    private static final String DECISION_REASON_RATE_EXPOSURE_CONFLICT_KEEP_BID = "RATE_EXPOSURE_CONFLICT_KEEP_BID";
    private static final String DECISION_REASON_MAX_BID_REACHED_KEEP = "MAX_BID_REACHED_KEEP";
    private static final String EXPOSURE_REASON_DECISION_CONTEXT_NOT_DYNAMIC = "DECISION_CONTEXT_NOT_DYNAMIC";
    private static final String FINAL_AGGREGATION_MODE_RATE_ONLY = "RATE_ONLY";
    private static final String FINAL_AGGREGATION_MODE_RATE_WITH_EXPOSURE = "RATE_WITH_EXPOSURE";
    private static final String FINAL_AGGREGATION_SOURCE_FEE_RATE = "FEE_RATE_BRANCH";
    private static final String FINAL_AGGREGATION_SOURCE_EXPOSURE_KEEP = "EXPOSURE_KEEP";
    private static final String FINAL_AGGREGATION_SOURCE_EXPOSURE_INCREASE = "EXPOSURE_INCREASE";
    private static final String FINAL_AGGREGATION_SOURCE_EXPOSURE_WEAK_GROWTH_ROLLBACK = "EXPOSURE_WEAK_GROWTH_ROLLBACK";
    private static final String FINAL_AGGREGATION_SOURCE_RATE_EXPOSURE_CONFLICT = "RATE_EXPOSURE_CONFLICT";
    private static final String RECORD_SOURCE_AUTO = "AUTO";
    private static final String RECORD_SOURCE_MANUAL = "MANUAL";
    private static final String DECISION_REASON_MANUAL_BID_UPDATE = "MANUAL_BID_UPDATE";
    private static final String PRICING_BRANCH_PEAK_RULE = "PEAK_RULE";
    private static final String PRICING_BRANCH_LOW_PEAK_BASELINE = "LOW_PEAK_BASELINE";
    private static final String PRICING_BRANCH_SMOOTH_PEAK_RAMP = "SMOOTH_PEAK_RAMP";
    private static final String BID_SOURCE_INHERITED = "INHERITED";
    private static final String BID_SOURCE_PEER_AVG = "PEER_AVG";
    private static final String BID_SOURCE_HISTORY = "HISTORY";
    private static final String BID_SOURCE_CURRENT_PLAN = "CURRENT_PLAN";
    private static final String BID_SOURCE_DEFAULT = "DEFAULT";
    private static final String BID_SOURCE_DYNAMIC_INITIAL = "DYNAMIC_INITIAL";
    private static final String BID_SOURCE_LOW_PEAK_ORDER_SHARE = "LOW_PEAK_ORDER_SHARE";
    private static final int POWER_EXECUTION_THRESHOLD = PromotionPowerThresholdConstant.MIN_PROMOTION_EXECUTION_POWER;
    private static final int POWER_WARNING_THRESHOLD = PromotionPowerThresholdConstant.PROMOTION_POWER_WARNING_THRESHOLD;
    private static final int SLOT_MINUTES = 10;
    private static final int PRE_PEAK_WARMUP_SLOTS = 4;
    private static final int SUCCESS_HISTORY_LIMIT = 3;
    private static final int EXPOSURE_WEAK_GROWTH_ROLLBACK_STREAK = 3;
    private static final BigDecimal WINDOW_END_RESET_BID = new BigDecimal("0.10");
    private static final BigDecimal BID_STEP = new BigDecimal("0.10");
    private static final BigDecimal DEFAULT_INITIAL_BID = new BigDecimal("0.40");
    private static final BigDecimal HIGH_VOLUME_AVERAGE_ORDER_THRESHOLD = new BigDecimal("30.00");
    private static final BigDecimal FAST_DECREASE_BID_STEP = new BigDecimal("0.20");
    private static final BigDecimal RATE_PRIORITY_FAST_THRESHOLD_DELTA = new BigDecimal("0.050000");
    private static final BigDecimal MAX_SYNC_BID_AMOUNT = MtPromotionBidConstraintService.GLOBAL_MAX_BID_AMOUNT;
    private static final BigDecimal BID_UNIT = new BigDecimal("0.10");
    private static final BigDecimal PEER_AVG_INITIAL_BID_MIN = new BigDecimal("0.10");
    private static final BigDecimal PEER_AVG_INITIAL_BID_MAX = new BigDecimal("1.50");
    private static final BigDecimal PEER_AVG_INITIAL_BID_SUBTRACT = new BigDecimal("0.70");
    private static final BigDecimal PEER_AVG_INITIAL_BID_FACTOR = new BigDecimal("0.50");
    private static final BigDecimal PEER_AVG_INITIAL_BID_OFFSET = new BigDecimal("0.10");
    private static final BigDecimal PROMOTION_BALANCE_EXECUTION_THRESHOLD = new BigDecimal("1.00");
    private static final BigDecimal PROMOTION_BALANCE_WARNING_THRESHOLD = new BigDecimal("5.00");

    private final MtPromotionSettingMapper mtPromotionSettingMapper;
    private final MtPromotionBidRecordMapper mtPromotionBidRecordMapper;
    private final PromotionTimeWindowEvaluator promotionTimeWindowEvaluator;
    private final PromotionMetricsProvider promotionMetricsProvider;
    private final MtPromotionOpenApiClient mtPromotionOpenApiClient;
    private final PromotionBidDecisionService promotionBidDecisionService;
    private final PromotionExposureDecisionService promotionExposureDecisionService;
    private final PromotionPowerConsumeService promotionPowerConsumeService;
    private final MtPromotionSettingService mtPromotionSettingService;
    private final MtPowerAccountService mtPowerAccountService;
    private final MtShopClient mtShopClient;
    private final MtShopService mtShopService;
    private final MtPromotionAdRealtimeSnapshotService mtPromotionAdRealtimeSnapshotService;
    private final MtPromotionAdDataService mtPromotionAdDataService;
    private final PromotionLogMarkdownService promotionLogMarkdownService;
    private final PromotionLogAsyncTaskService promotionLogAsyncTaskService;
    private final PromotionLogMessageBuilder promotionLogMessageBuilder;
    private final ShopPeakWindowProfileService shopPeakWindowProfileService;
    private final MtPromotionBidConstraintService mtPromotionBidConstraintService;
    private final ObjectMapper objectMapper;
    private final MtAsyncTaskService mtAsyncTaskService;
    private final MtProperties mtProperties;
    private MtAccessTokenService mtAccessTokenService;
    private MtMeituanAuthorizationFailureHandler mtMeituanAuthorizationFailureHandler;
    private PromotionRecentOrderStatisticService promotionRecentOrderStatisticService;

    @Autowired
    public MtPromotionAutoBidService(
            MtPromotionSettingMapper mtPromotionSettingMapper,
            MtPromotionBidRecordMapper mtPromotionBidRecordMapper,
            PromotionTimeWindowEvaluator promotionTimeWindowEvaluator,
            PromotionMetricsProvider promotionMetricsProvider,
            MtPromotionOpenApiClient mtPromotionOpenApiClient,
            PromotionBidDecisionService promotionBidDecisionService,
            PromotionExposureDecisionService promotionExposureDecisionService,
            PromotionPowerConsumeService promotionPowerConsumeService,
            MtPromotionSettingService mtPromotionSettingService,
            MtPowerAccountService mtPowerAccountService,
            MtShopClient mtShopClient,
            MtShopService mtShopService,
            MtPromotionAdRealtimeSnapshotService mtPromotionAdRealtimeSnapshotService,
            MtPromotionAdDataService mtPromotionAdDataService,
            PromotionLogMarkdownService promotionLogMarkdownService,
            PromotionLogAsyncTaskService promotionLogAsyncTaskService,
            PromotionLogMessageBuilder promotionLogMessageBuilder,
            ShopPeakWindowProfileService shopPeakWindowProfileService,
            MtPromotionBidConstraintService mtPromotionBidConstraintService,
            ObjectMapper objectMapper,
            MtAsyncTaskService mtAsyncTaskService,
            MtProperties mtProperties
    ) {
        this.mtPromotionSettingMapper = mtPromotionSettingMapper;
        this.mtPromotionBidRecordMapper = mtPromotionBidRecordMapper;
        this.promotionTimeWindowEvaluator = promotionTimeWindowEvaluator;
        this.promotionMetricsProvider = promotionMetricsProvider;
        this.mtPromotionOpenApiClient = mtPromotionOpenApiClient;
        this.promotionBidDecisionService = promotionBidDecisionService;
        this.promotionExposureDecisionService = promotionExposureDecisionService;
        this.promotionPowerConsumeService = promotionPowerConsumeService;
        this.mtPromotionSettingService = mtPromotionSettingService;
        this.mtPowerAccountService = mtPowerAccountService;
        this.mtShopClient = mtShopClient;
        this.mtShopService = mtShopService;
        this.mtPromotionAdRealtimeSnapshotService = mtPromotionAdRealtimeSnapshotService;
        this.mtPromotionAdDataService = mtPromotionAdDataService;
        this.promotionLogMarkdownService = promotionLogMarkdownService;
        this.promotionLogAsyncTaskService = promotionLogAsyncTaskService;
        this.promotionLogMessageBuilder = promotionLogMessageBuilder;
        this.shopPeakWindowProfileService = shopPeakWindowProfileService;
        this.mtPromotionBidConstraintService = mtPromotionBidConstraintService;
        this.objectMapper = objectMapper;
        this.mtAsyncTaskService = mtAsyncTaskService;
        this.mtProperties = mtProperties;
    }

    public MtPromotionAutoBidService(
            MtPromotionSettingMapper mtPromotionSettingMapper,
            MtPromotionBidRecordMapper mtPromotionBidRecordMapper,
            PromotionTimeWindowEvaluator promotionTimeWindowEvaluator,
            PromotionMetricsProvider promotionMetricsProvider,
            MtPromotionOpenApiClient mtPromotionOpenApiClient,
            PromotionBidDecisionService promotionBidDecisionService,
            PromotionExposureDecisionService promotionExposureDecisionService,
            PromotionPowerConsumeService promotionPowerConsumeService,
            MtPromotionSettingService mtPromotionSettingService,
            MtPowerAccountService mtPowerAccountService,
            MtShopClient mtShopClient,
            MtShopService mtShopService,
            MtPromotionAdRealtimeSnapshotService mtPromotionAdRealtimeSnapshotService,
            MtPromotionAdDataService mtPromotionAdDataService,
            PromotionLogMarkdownService promotionLogMarkdownService,
            PromotionLogAsyncTaskService promotionLogAsyncTaskService,
            PromotionLogMessageBuilder promotionLogMessageBuilder,
            ShopPeakWindowProfileService shopPeakWindowProfileService,
            ObjectMapper objectMapper,
            MtAsyncTaskService mtAsyncTaskService,
            MtProperties mtProperties
    ) {
        this(
                mtPromotionSettingMapper,
                mtPromotionBidRecordMapper,
                promotionTimeWindowEvaluator,
                promotionMetricsProvider,
                mtPromotionOpenApiClient,
                promotionBidDecisionService,
                promotionExposureDecisionService,
                promotionPowerConsumeService,
                mtPromotionSettingService,
                mtPowerAccountService,
                mtShopClient,
                mtShopService,
                mtPromotionAdRealtimeSnapshotService,
                mtPromotionAdDataService,
                promotionLogMarkdownService,
                promotionLogAsyncTaskService,
                promotionLogMessageBuilder,
                shopPeakWindowProfileService,
                null,
                objectMapper,
                mtAsyncTaskService,
                mtProperties
        );
    }

    public MtPromotionAutoBidService(
            MtPromotionSettingMapper mtPromotionSettingMapper,
            MtPromotionBidRecordMapper mtPromotionBidRecordMapper,
            PromotionTimeWindowEvaluator promotionTimeWindowEvaluator,
            PromotionMetricsProvider promotionMetricsProvider,
            MtPromotionOpenApiClient mtPromotionOpenApiClient,
            PromotionBidDecisionService promotionBidDecisionService,
            PromotionExposureDecisionService promotionExposureDecisionService,
            PromotionPowerConsumeService promotionPowerConsumeService,
            MtPromotionSettingService mtPromotionSettingService,
            MtPowerAccountService mtPowerAccountService,
            MtShopClient mtShopClient,
            MtShopService mtShopService,
            MtPromotionAdRealtimeSnapshotService mtPromotionAdRealtimeSnapshotService,
            MtPromotionAdDataService mtPromotionAdDataService,
            PromotionLogMarkdownService promotionLogMarkdownService,
            PromotionLogMessageBuilder promotionLogMessageBuilder,
            ShopPeakWindowProfileService shopPeakWindowProfileService,
            ObjectMapper objectMapper
    ) {
        this(
                mtPromotionSettingMapper,
                mtPromotionBidRecordMapper,
                promotionTimeWindowEvaluator,
                promotionMetricsProvider,
                mtPromotionOpenApiClient,
                promotionBidDecisionService,
                promotionExposureDecisionService,
                promotionPowerConsumeService,
                mtPromotionSettingService,
                mtPowerAccountService,
                mtShopClient,
                mtShopService,
                mtPromotionAdRealtimeSnapshotService,
                mtPromotionAdDataService,
                promotionLogMarkdownService,
                null,
                promotionLogMessageBuilder,
                shopPeakWindowProfileService,
                null,
                objectMapper,
                null,
                null
        );
    }

    @Autowired(required = false)
    public void setMtAccessTokenService(MtAccessTokenService mtAccessTokenService) {
        this.mtAccessTokenService = mtAccessTokenService;
    }

    @Autowired(required = false)
    public void setMtMeituanAuthorizationFailureHandler(MtMeituanAuthorizationFailureHandler mtMeituanAuthorizationFailureHandler) {
        this.mtMeituanAuthorizationFailureHandler = mtMeituanAuthorizationFailureHandler;
    }

    @Autowired(required = false)
    public void setPromotionRecentOrderStatisticService(PromotionRecentOrderStatisticService promotionRecentOrderStatisticService) {
        this.promotionRecentOrderStatisticService = promotionRecentOrderStatisticService;
    }

    public MtPromotionAutoBidService(
            MtPromotionSettingMapper mtPromotionSettingMapper,
            MtPromotionBidRecordMapper mtPromotionBidRecordMapper,
            PromotionTimeWindowEvaluator promotionTimeWindowEvaluator,
            PromotionMetricsProvider promotionMetricsProvider,
            MtPromotionOpenApiClient mtPromotionOpenApiClient,
            PromotionBidDecisionService promotionBidDecisionService,
            PromotionExposureDecisionService promotionExposureDecisionService,
            PromotionPowerConsumeService promotionPowerConsumeService,
            MtPromotionSettingService mtPromotionSettingService,
            MtPowerAccountService mtPowerAccountService,
            MtShopClient mtShopClient,
            MtShopService mtShopService,
            ObjectMapper objectMapper
    ) {
        this(
                mtPromotionSettingMapper,
                mtPromotionBidRecordMapper,
                promotionTimeWindowEvaluator,
                promotionMetricsProvider,
                mtPromotionOpenApiClient,
                promotionBidDecisionService,
                promotionExposureDecisionService,
                promotionPowerConsumeService,
                mtPromotionSettingService,
                mtPowerAccountService,
                mtShopClient,
                mtShopService,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                objectMapper,
                null,
                null
        );
    }

    /**
     * 执行当前 10 分钟时段的自动调价
     */
    public void executeCurrentSlot() {
        LocalDateTime slotTime = resolveCurrentSlotTime(LocalDateTime.now());
        if (isDatabaseAsyncTaskEnabled()) {
            dispatchSlot(slotTime);
            return;
        }
        executeSlot(slotTime);
    }

    /**
     * 为指定时段分发自动调价异步任务。
     *
     * @param slotTime 时段时间
     */
    public void dispatchSlot(LocalDateTime slotTime) {
        if (!isDatabaseAsyncTaskEnabled() || mtAsyncTaskService == null || slotTime == null) {
            executeSlot(slotTime);
            return;
        }
        int pageSize = resolveAsyncTaskConfig().getDispatchPageSize();
        Long lastShopId = null;
        int dispatchedCount = 0;
        while (true) {
            List<MtPromotionAutoBidCandidate> candidates = mtPromotionSettingMapper.selectAutoBidCandidatesByCursor(
                    slotTime,
                    lastShopId,
                    pageSize
            );
            if (candidates == null || candidates.isEmpty()) {
                break;
            }
            for (MtPromotionAutoBidCandidate candidate : candidates) {
                lastShopId = candidate.getShopId();
                if (enqueueAutoBidTask(candidate, slotTime)) {
                    dispatchedCount++;
                }
            }
            if (candidates.size() < pageSize) {
                break;
            }
        }
        log.info("自动调价任务分发完成，slotTime={}, dispatchedCount={}", slotTime, dispatchedCount);
    }

    /**
     * 执行指定时段自动调价
     *
     * @param slotTime 时段时间
     */
    public void executeSlot(LocalDateTime slotTime) {
        List<MtPromotionAutoBidCandidate> candidates = mtPromotionSettingMapper.selectAutoBidCandidates(slotTime);
        log.info("自动调价任务开始，slotTime={}, candidateCount={}", slotTime, candidates.size());
        for (MtPromotionAutoBidCandidate candidate : candidates) {
            try {
                executeCandidate(candidate, slotTime);
            } catch (Exception e) {
                log.error("自动调价执行异常，shopId={}, slotTime={}", candidate.getShopId(), slotTime, e);
                persistFailedRecord(candidate, slotTime, null, null, null, null, "UNEXPECTED_EXCEPTION:" + e.getMessage());
            }
        }
        log.info("自动调价任务结束，slotTime={}, candidateCount={}", slotTime, candidates.size());
    }

    /**
     * 执行已分发的自动调价任务。
     *
     * @param payload 调价任务载荷
     */
    public void executeAutoBidTask(MtPromotionAutoBidTaskPayload payload) {
        if (payload == null || payload.getCandidate() == null || payload.getSlotTime() == null) {
            return;
        }
        executeCandidate(payload.getCandidate(), payload.getSlotTime());
    }

    /**
     * 执行已分发的日志 Markdown 任务。
     *
     * @param payload 日志任务载荷
     */
    public void executePromotionLogMarkdownTask(MtPromotionLogMarkdownTaskPayload payload) {
        if (payload == null || promotionLogAsyncTaskService == null) {
            return;
        }
        promotionLogAsyncTaskService.generateAndPersistMarkdown(payload.getRecordId(), payload.getAccessToken());
    }

    /**
     * H5 手动修改当前推广窗口出价。
     *
     * @param shop 当前门店
     * @param bidAmount 手动出价，单位元
     * @return 生效结果
     */
    public MtH5ManualPromotionBidVO updateCurrentWindowBidManually(MtShop shop, BigDecimal bidAmount) {
        if (shop == null || shop.getId() == null) {
            throw new BusinessException(403, "当前门店无权修改推广出价");
        }
        BigDecimal normalizedBidAmount = normalizeBidAmountWithFloor(bidAmount);
        if (normalizedBidAmount == null
                || normalizedBidAmount.compareTo(WINDOW_END_RESET_BID) < 0
                || normalizedBidAmount.compareTo(MAX_SYNC_BID_AMOUNT) > 0) {
            throw new BusinessException(400, "出价范围为0.1-4元");
        }
        LocalDateTime now = LocalDateTime.now().withNano(0);
        LocalDateTime attributedSlotTime = resolveCurrentSlotTime(now);
        MtPromotionSetting setting = mtPromotionSettingService.getOrCreateByShopId(shop.getId());
        if (!Boolean.TRUE.equals(setting.getPromotionEnabled())
                || !Boolean.TRUE.equals(setting.getStrategyConfigured())
                || StringUtils.isBlank(setting.getStrategyConfigJson())) {
            throw new BusinessException(400, "当前门店未开启推广或未配置推广策略");
        }
        MtAccessToken accessToken = mtAccessTokenService == null ? null : mtAccessTokenService.findByShopId(shop.getId());
        if (accessToken == null || StringUtils.isBlank(accessToken.getAccessToken())) {
            throw new BusinessException(400, "当前门店缺少美团授权，无法同步出价");
        }

        MtPromotionAutoBidCandidate candidate = buildManualBidCandidate(shop, setting, accessToken);
        MtPromotionSettingService.StrategyConfig strategyConfig = readStrategyConfig(setting.getStrategyConfigJson());
        PromotionTimeWindowResult timeWindowResult = promotionTimeWindowEvaluator.evaluate(
                strategyConfig,
                shop.getShippingTime(),
                now
        );
        if (timeWindowResult == null || !timeWindowResult.isExecutable()) {
            throw new BusinessException(400, "非推广时段");
        }
        PromotionExecutionWindow executionWindow = timeWindowResult.getExecutionWindow();
        BigDecimal effectiveMaxBidAmount = resolveEffectiveMaxBidAmount(candidate, now);
        if (normalizedBidAmount.compareTo(normalizeEffectiveMaxBidAmount(effectiveMaxBidAmount)) > 0) {
            throw new BusinessException(400, "出价超过当前门店允许上限");
        }

        MtPromotionBidRecord latestSuccess = findLatestSuccess(candidate.getShopId(), executionWindow, 1);
        if (latestSuccess != null && latestSuccess.getSlotTime() != null) {
            attributedSlotTime = resolveCurrentSlotTime(latestSuccess.getSlotTime());
        }

        PromotionBidOpenApiResult updateResult = updatePlanBid(
                candidate,
                normalizedBidAmount,
                now,
                executionWindow,
                "MANUAL_BID_UPDATE",
                effectiveMaxBidAmount
        );
        String bidUpdatePayloadJson = updateResult == null ? null : updateResult.getPayloadJson();
        if (updateResult == null || !updateResult.isSuccess()) {
            PromotionDecisionResult retryDecision = buildManualDecisionResult(latestSuccess, normalizedBidAmount);
            BidLimitRetryResult retryResult = retryUpdatePlanBidWithObservedMaxBid(
                    candidate,
                    retryDecision,
                    updateResult,
                    now,
                    executionWindow,
                    effectiveMaxBidAmount
            );
            if (retryResult != null && retryResult.updateResult().isSuccess()) {
                updateResult = retryResult.updateResult();
                normalizedBidAmount = retryDecision.getNextBidAmount();
                bidUpdatePayloadJson = buildBidUpdateRetryPayloadJson(
                        bidUpdatePayloadJson,
                        updateResult.getPayloadJson(),
                        retryResult.observedMaxBidAmount()
                );
            } else {
                String errorMsg = retryResult == null
                        ? (updateResult == null ? null : updateResult.getErrorMsg())
                        : retryResult.updateResult().getErrorMsg();
                throw new BusinessException(500, StringUtils.defaultIfBlank(errorMsg, "同步美团出价失败"));
            }
        }

        MtPromotionBidRecord record = buildBaseRecord(candidate, now, executionWindow, null);
        record.setRecordSource(RECORD_SOURCE_MANUAL);
        record.setAttributedSlotTime(attributedSlotTime);
        record.setOperatedAt(now);
        record.setPreviousBidAmount(latestSuccess == null ? null : latestSuccess.getNextBidAmount());
        record.setNextBidAmount(normalizedBidAmount);
        record.setDecisionAction(resolveRuleDecisionAction(record.getPreviousBidAmount(), normalizedBidAmount));
        record.setDecisionReason(DECISION_REASON_MANUAL_BID_UPDATE);
        record.setExecuteStatus(STATUS_SUCCESS);
        record.setUpdatePayloadJson(mergeUpdatePayloadJson(null, bidUpdatePayloadJson));
        record.setDecisionDetailJson(buildManualDecisionDetailJson(record, executionWindow));
        if (!insertRecord(record, candidate)) {
            log.error("手动出价远端已成功但本地记录失败，shopId={}, bid={}, slotTime={}, attributedSlotTime={}",
                    shop.getId(), normalizedBidAmount, now, attributedSlotTime);
            throw new BusinessException(500, "手动出价记录保存失败");
        }
        log.info("H5手动修改推广出价成功，shopId={}, poiId={}, previousBidAmount={}, effectiveBidAmount={}, slotTime={}, attributedSlotTime={}, recordId={}, windowKey={}",
                shop.getId(),
                shop.getPoiId(),
                record.getPreviousBidAmount(),
                record.getNextBidAmount(),
                now,
                record.getAttributedSlotTime(),
                record.getId(),
                executionWindow == null ? null : executionWindow.getWindowKey());

        MtH5ManualPromotionBidVO vo = new MtH5ManualPromotionBidVO();
        vo.setRecordId(record.getId());
        vo.setPreviousBidAmount(record.getPreviousBidAmount());
        vo.setEffectiveBidAmount(record.getNextBidAmount());
        vo.setWindowKey(executionWindow == null ? null : executionWindow.getWindowKey());
        vo.setWindowStartAt(executionWindow == null ? null : executionWindow.getWindowStartAt());
        vo.setWindowEndAt(executionWindow == null ? null : executionWindow.getWindowEndAt());
        vo.setAttributedSlotTime(record.getAttributedSlotTime());
        vo.setOperatedAt(record.getOperatedAt());
        vo.setRemoteSynced(Boolean.TRUE);
        return vo;
    }

    /**
     * 保存推广策略后，如果当前时间按新策略已不在推广窗口内，立即把远端出价回落到底价。
     *
     * <p>该补偿不关闭本地推广开关，也不关闭美团计划；它只修正美团侧仍保留上一窗口高出价的问题。</p>
     */
    public void resetBidToFloorIfOutOfPromotionWindowAfterSettingsSave(MtShop shop) {
        if (shop == null || shop.getId() == null) {
            return;
        }
        MtPromotionSetting setting = mtPromotionSettingService.getOrCreateByShopId(shop.getId());
        if (setting == null
                || !Boolean.TRUE.equals(setting.getPromotionEnabled())
                || !Boolean.TRUE.equals(setting.getStrategyConfigured())
                || StringUtils.isBlank(setting.getStrategyConfigJson())) {
            return;
        }
        MtAccessToken accessToken = mtAccessTokenService == null ? null : mtAccessTokenService.findByShopId(shop.getId());
        if (accessToken == null || StringUtils.isBlank(accessToken.getAccessToken())) {
            log.info("策略保存后跳过非推广时段出价回落，原因=缺少美团授权, shopId={}", shop.getId());
            return;
        }

        MtPromotionAutoBidCandidate candidate = buildManualBidCandidate(shop, setting, accessToken);
        refreshShippingTimeIfMissing(candidate);
        LocalDateTime now = LocalDateTime.now().withNano(0);
        PromotionTimeWindowResult timeWindowResult;
        try {
            timeWindowResult = promotionTimeWindowEvaluator.evaluate(
                    readStrategyConfig(candidate.getStrategyConfigJson()),
                    candidate.getShippingTime(),
                    now
            );
        } catch (RuntimeException e) {
            log.warn("策略保存后判断推广时段失败，跳过出价回落，shopId={}", shop.getId(), e);
            return;
        }
        if (timeWindowResult != null && timeWindowResult.isExecutable()) {
            log.info("策略保存后仍在推广时段，跳过出价回落，shopId={}, windowKey={}",
                    shop.getId(),
                    timeWindowResult.getExecutionWindow() == null ? null : timeWindowResult.getExecutionWindow().getWindowKey());
            return;
        }
        if (!shouldSilentlySkipOutOfPromotionWindow(timeWindowResult)) {
            log.info("策略保存后未命中有效非推广时段，跳过出价回落，shopId={}, reason={}",
                    shop.getId(), timeWindowResult == null ? null : timeWindowResult.getReason());
            return;
        }

        MtPromotionBidRecord latestSuccess = mtPromotionBidRecordMapper.selectLatestSuccessByShopId(shop.getId());
        BigDecimal previousBidAmount = latestSuccess == null ? null : latestSuccess.getNextBidAmount();
        if (previousBidAmount != null && previousBidAmount.compareTo(WINDOW_END_RESET_BID) <= 0) {
            log.info("策略保存后非推广时段出价已在底价，无需重复回落，shopId={}, reason={}, currentBid={}",
                    shop.getId(),
                    timeWindowResult.getReason(),
                    previousBidAmount);
            return;
        }
        PromotionDecisionResult decisionResult = new PromotionDecisionResult();
        decisionResult.setDecisionAction(PromotionDecisionEngine.ACTION_DECREASE);
        decisionResult.setDecisionReason(DECISION_REASON_SETTINGS_SAVE_OUT_OF_WINDOW_BID_RESET);
        decisionResult.setPreviousBidAmount(previousBidAmount);
        decisionResult.setNextBidAmount(WINDOW_END_RESET_BID);

        PromotionBidOpenApiResult updateResult = updatePlanBid(
                candidate,
                WINDOW_END_RESET_BID,
                now,
                null,
                "SETTINGS_SAVE_OUT_OF_WINDOW_BID_RESET"
        );
        String bidUpdatePayloadJson = updateResult == null ? null : updateResult.getPayloadJson();
        if (updateResult == null || !updateResult.isSuccess()) {
            persistFailedRecord(
                    candidate,
                    now,
                    null,
                    null,
                    decisionResult,
                    mergeUpdatePayloadJson(null, bidUpdatePayloadJson),
                    updateResult == null ? null : updateResult.getErrorMsg()
            );
            log.warn("策略保存后非推广时段出价回落失败，shopId={}, reason={}, errorMsg={}",
                    shop.getId(),
                    timeWindowResult.getReason(),
                    updateResult == null ? null : updateResult.getErrorMsg());
            return;
        }

        MtPromotionBidRecord record = buildBaseRecord(candidate, now, null, null);
        record.setPreviousBidAmount(decisionResult.getPreviousBidAmount());
        record.setNextBidAmount(decisionResult.getNextBidAmount());
        record.setDecisionAction(decisionResult.getDecisionAction());
        record.setDecisionReason(decisionResult.getDecisionReason());
        record.setExecuteStatus(STATUS_SUCCESS);
        record.setUpdatePayloadJson(mergeUpdatePayloadJson(null, bidUpdatePayloadJson));
        insertRecord(record, candidate);
        log.info("策略保存后非推广时段出价已回落到底价，shopId={}, reason={}, previousBid={}, nextBid={}",
                shop.getId(),
                timeWindowResult.getReason(),
                decisionResult.getPreviousBidAmount(),
                decisionResult.getNextBidAmount());
    }

    private void executeCandidate(MtPromotionAutoBidCandidate candidate, LocalDateTime slotTime) {
        log.info("开始处理自动调价候选门店，shopId={}, settingId={}, poiId={}, slotTime={}, accessTokenPresent={}",
                candidate.getShopId(),
                candidate.getSettingId(),
                candidate.getPoiId(),
                slotTime,
                StringUtils.isNotBlank(candidate.getAccessToken()));
        LocalDate bizDate = slotTime.toLocalDate();
        if (mtPromotionBidRecordMapper.selectByShopIdAndBizDateAndSlotTime(candidate.getShopId(), bizDate, slotTime) != null) {
            log.info("自动调价已存在同槽位记录，跳过执行，shopId={}, slotTime={}", candidate.getShopId(), slotTime);
            return;
        }

        String staleConfigSkipReason = refreshCandidateWithLatestSetting(candidate);
        if (staleConfigSkipReason != null) {
            persistSkippedRecord(candidate, slotTime, null, null, staleConfigSkipReason, null);
            return;
        }

        MtPromotionSettingService.StrategyConfig strategyConfig = readStrategyConfig(candidate.getStrategyConfigJson());
        refreshShippingTimeIfMissing(candidate);
        PromotionTimeWindowResult timeWindowResult = promotionTimeWindowEvaluator.evaluate(
                strategyConfig,
                candidate.getShippingTime(),
                slotTime
        );
        PromotionTimeWindowResult previousTimeWindowResult = promotionTimeWindowEvaluator.evaluate(
                strategyConfig,
                candidate.getShippingTime(),
                slotTime.minusMinutes(10)
        );
        if (handleWindowEndBidReset(candidate, slotTime, previousTimeWindowResult, timeWindowResult)) {
            return;
        }
        if (!timeWindowResult.isExecutable()) {
            if (shouldSilentlySkipOutOfPromotionWindow(timeWindowResult)) {
                log.info("自动调价当前不在推广时段，跳过且不记录日志，shopId={}, slotTime={}, reason={}",
                        candidate.getShopId(), slotTime, timeWindowResult.getReason());
                return;
            }
            persistSkippedRecord(
                    candidate,
                    slotTime,
                    resolveRecordWindow(timeWindowResult, previousTimeWindowResult),
                    null,
                    timeWindowResult.getReason(),
                    null
            );
            return;
        }
        ensureRealtimeSnapshotForExecutableSlot(candidate, slotTime);
        int balanceBeforeExecution = getCurrentPowerBalance(candidate.getShopId());
        log.info("自动调价执行前算力余额，shopId={}, slotTime={}, balance={}",
                candidate.getShopId(), slotTime, balanceBeforeExecution);
        if (balanceBeforeExecution < POWER_EXECUTION_THRESHOLD) {
            mtPromotionSettingService.disablePromotionByShopId(candidate.getShopId());
            persistSkippedRecord(candidate, slotTime, timeWindowResult.getExecutionWindow(), null, SKIP_REASON_POWER_BELOW_EXECUTION_THRESHOLD, null);
            return;
        }
        if (balanceBeforeExecution < POWER_WARNING_THRESHOLD) {
            persistPowerNoticeRecord(candidate, slotTime.plusSeconds(1), SKIP_REASON_POWER_BELOW_WARNING_THRESHOLD);
        }

        PromotionPlanStatusOpenApiResult remoteStatusResult = ensureRemotePlanStillEnabled(
                candidate,
                slotTime,
                timeWindowResult
        );
        if (remoteStatusResult != null && !remoteStatusResult.isSuccess()) {
            if (remoteStatusResult.isAuthFailure()) {
                handleConfirmedAuthFailure(candidate, remoteStatusResult.getErrorMsg());
            }
            persistFailedRecord(
                    candidate,
                    slotTime,
                    timeWindowResult.getExecutionWindow(),
                    null,
                    null,
                    mergeUpdatePayloadJson(remoteStatusResult.getPayloadJson(), null),
                    remoteStatusResult.getErrorMsg()
            );
            return;
        }
        logRemoteBudgetStatus(candidate, slotTime, timeWindowResult.getExecutionWindow(), remoteStatusResult);
        if (remoteStatusResult != null && Integer.valueOf(MtPromotionOpenApiClient.STATUS_CLOSE).equals(remoteStatusResult.getStatus())) {
            mtPromotionSettingService.disablePromotionByShopId(candidate.getShopId());
            persistSkippedRecord(
                    candidate,
                    slotTime,
                    timeWindowResult.getExecutionWindow(),
                    null,
                    SKIP_REASON_REMOTE_PLAN_STATUS_CLOSED,
                    mergeUpdatePayloadJson(remoteStatusResult.getPayloadJson(), null, null)
            );
            return;
        }
        if (remoteStatusResult != null
                && remoteStatusResult.getAvailableBudget() != null
                && remoteStatusResult.getAvailableBudget().compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("自动调价检测到美团日预算余额不足，shopId={}, poiId={}, slotTime={}, windowKey={}, budget={}, budgetUsed={}, availableBudget={}, status={}",
                    candidate.getShopId(),
                    candidate.getPoiId(),
                    slotTime,
                    timeWindowResult.getExecutionWindow() == null ? null : timeWindowResult.getExecutionWindow().getWindowKey(),
                    remoteStatusResult.getBudget(),
                    remoteStatusResult.getBudgetUsed(),
                    remoteStatusResult.getAvailableBudget(),
                    remoteStatusResult.getStatus());
            if (hasDailyBudgetExhaustedNotice(candidate.getShopId(), slotTime.toLocalDate())) {
                log.info("自动调价同业务日预算已提醒过，跳过重复记录，shopId={}, slotTime={}",
                        candidate.getShopId(), slotTime);
                return;
            }
            persistSkippedRecord(
                    candidate,
                    slotTime,
                    timeWindowResult.getExecutionWindow(),
                    null,
                    SKIP_REASON_PROMOTION_DAILY_BUDGET_EXHAUSTED,
                    mergeUpdatePayloadJson(remoteStatusResult.getPayloadJson(), null, null)
            );
            return;
        }

        PromotionExecutionWindow executionWindow = timeWindowResult.getExecutionWindow();
        PromotionMetricsSnapshot metricsSnapshot = promotionMetricsProvider.queryMetrics(candidate, executionWindow, slotTime);
        BigDecimal promotionBalance = metricsSnapshot == null ? null : metricsSnapshot.getPromotionBalance();
        if (promotionBalance != null && promotionBalance.compareTo(PROMOTION_BALANCE_EXECUTION_THRESHOLD) < 0) {
            mtPromotionSettingService.disablePromotionByShopId(candidate.getShopId());
            persistSkippedRecord(
                    candidate,
                    slotTime,
                    timeWindowResult.getExecutionWindow(),
                    metricsSnapshot,
                    SKIP_REASON_PROMOTION_BALANCE_BELOW_EXECUTION_THRESHOLD,
                    mergeUpdatePayloadJson(remoteStatusResult == null ? null : remoteStatusResult.getPayloadJson(), null)
            );
            return;
        }
        if (promotionBalance != null && promotionBalance.compareTo(PROMOTION_BALANCE_WARNING_THRESHOLD) < 0) {
            persistPromotionBalanceNoticeRecord(
                    candidate,
                    slotTime.plusSeconds(1),
                    metricsSnapshot,
                    SKIP_REASON_PROMOTION_BALANCE_BELOW_WARNING_THRESHOLD
            );
        }
        if (metricsSnapshot == null || !metricsSnapshot.isMetricsTrusted()) {
            persistSkippedRecord(
                    candidate,
                    slotTime,
                    timeWindowResult.getExecutionWindow(),
                    metricsSnapshot,
                    buildMetricsUntrustedReason(metricsSnapshot),
                    mergeUpdatePayloadJson(remoteStatusResult == null ? null : remoteStatusResult.getPayloadJson(), null)
            );
            return;
        }
        List<MtPromotionBidRecord> successHistory = mtPromotionBidRecordMapper.selectLatestSuccessByShopIdAndWindowKey(
                candidate.getShopId(),
                executionWindow == null ? null : executionWindow.getWindowKey(),
                SUCCESS_HISTORY_LIMIT
        );
        ShopPeakWindowProfileLookupResult peakLookupResult = resolvePeakWindowLookup(candidate, strategyConfig, slotTime);
        MtPromotionRecentOrderStatistic recentOrderStatistic = resolveRecentOrderStatistic(
                candidate,
                slotTime,
                peakLookupResult
        );
        PeakBidPhaseResolution peakBidPhaseResolution = resolvePeakBidPhase(
                candidate,
                slotTime,
                executionWindow,
                previousTimeWindowResult,
                successHistory,
                remoteStatusResult,
                strategyConfig,
                peakLookupResult,
                recentOrderStatistic
        );
        InitialBidResolutionResult initialBidResolution = peakBidPhaseResolution != null && peakBidPhaseResolution.isRulePhase()
                ? new InitialBidResolutionResult(peakBidPhaseResolution.lowPeakBaselineBidAmount(), peakBidPhaseResolution.peerBidResult())
                : resolveInitialBidAmount(
                candidate,
                slotTime,
                executionWindow,
                previousTimeWindowResult,
                successHistory,
                remoteStatusResult,
                strategyConfig,
                peakBidPhaseResolution == null ? peakLookupResult : peakBidPhaseResolution.initialBidPeakLookupResult()
        );
        BigDecimal initialBidAmount = initialBidResolution.initialBidAmount();
        BigDecimal effectiveMaxBidAmount = resolveEffectiveMaxBidAmount(candidate, slotTime);
        peakBidPhaseResolution = enrichPeakPhaseWithDynamicInitialBid(
                peakBidPhaseResolution,
                initialBidAmount,
                initialBidResolution.peerBidResult()
        );
        PromotionDecisionResult decisionResult = buildDecisionResult(
                strategyConfig,
                metricsSnapshot,
                successHistory,
                slotTime,
                initialBidAmount,
                peakLookupResult,
                peakBidPhaseResolution
        );
        enforceMaxBidLimit(decisionResult, effectiveMaxBidAmount);
        decisionResult.setDecisionDetailJson(mergeDecisionDetailWithBaselineContext(
                decisionResult.getDecisionDetailJson(),
                resolveDecisionBaselineBidAmount(initialBidAmount, peakBidPhaseResolution),
                resolvePricingBranch(strategyConfig, peakLookupResult, peakBidPhaseResolution)
        ));
        decisionResult.setDecisionDetailJson(mergeDecisionDetailWithFeeRateBranchContext(
                decisionResult.getDecisionDetailJson(),
                decisionResult.getDecisionAction(),
                decisionResult.getDecisionReason()
        ));
        PromotionExposureDecisionResult exposureDecisionResult = resolveExposureDecisionResult(
                candidate,
                strategyConfig,
                metricsSnapshot,
                successHistory,
                decisionResult
        );
        decisionResult = aggregateDecisionWithExposure(
                candidate == null ? null : candidate.getShopId(),
                slotTime,
                successHistory,
                decisionResult,
                exposureDecisionResult
        );
        enforceMaxBidLimit(decisionResult, effectiveMaxBidAmount);
        BigDecimal bidBeforeUserRangeClamp = normalizeBidAmountWithFloor(decisionResult.getNextBidAmount());
        enforceUserBidRangeLimit(decisionResult, strategyConfig, effectiveMaxBidAmount);
        decisionResult.setDecisionDetailJson(mergeDecisionDetailWithPeerBidContext(
                decisionResult.getDecisionDetailJson(),
                choosePeerBidResult(initialBidResolution.peerBidResult(), peakBidPhaseResolution)
        ));
        decisionResult.setDecisionDetailJson(mergeDecisionDetailWithExposureContext(
                decisionResult.getDecisionDetailJson(),
                exposureDecisionResult
        ));
        decisionResult.setDecisionDetailJson(mergeDecisionDetailWithPeakWindowContext(
                decisionResult.getDecisionDetailJson(),
                peakLookupResult
        ));
        decisionResult.setDecisionDetailJson(mergeDecisionDetailWithPeakRampContext(
                decisionResult.getDecisionDetailJson(),
                peakBidPhaseResolution,
                buildStrategyConfigHash(candidate.getStrategyConfigJson())
        ));
        decisionResult.setDecisionDetailJson(mergeDecisionDetailWithRecentOrderStatisticContext(
                decisionResult.getDecisionDetailJson(),
                recentOrderStatistic,
                peakBidPhaseResolution,
                strategyConfig,
                metricsSnapshot,
                decisionResult
        ));
        decisionResult.setDecisionDetailJson(mergeDecisionDetailWithBidRangeContext(
                decisionResult.getDecisionDetailJson(),
                strategyConfig,
                effectiveMaxBidAmount,
                bidBeforeUserRangeClamp,
                decisionResult.getNextBidAmount()
        ));
        decisionResult.setDecisionDetailJson(mergeDecisionDetailWithStrategySnapshot(
                decisionResult.getDecisionDetailJson(),
                candidate.getStrategyConfigJson()
        ));
        BigDecimal syncedBidAmount = clampBidAmountForSync(decisionResult.getNextBidAmount(), candidate, slotTime, executionWindow, effectiveMaxBidAmount);
        decisionResult.setNextBidAmount(syncedBidAmount);
        log.info("自动调价决策完成，shopId={}, slotTime={}, previousBid={}, nextBid={}, action={}",
                candidate.getShopId(),
                slotTime,
                decisionResult.getPreviousBidAmount(),
                decisionResult.getNextBidAmount(),
                decisionResult.getDecisionAction());
        PromotionBidOpenApiResult updateResult = updatePlanBid(
                candidate,
                decisionResult.getNextBidAmount(),
                slotTime,
                executionWindow,
                effectiveMaxBidAmount
        );
        String bidUpdatePayloadJson = updateResult.getPayloadJson();

        if (!updateResult.isSuccess()) {
            BidLimitRetryResult retryResult = retryUpdatePlanBidWithObservedMaxBid(
                    candidate,
                    decisionResult,
                    updateResult,
                    slotTime,
                    executionWindow,
                    effectiveMaxBidAmount
            );
            if (retryResult != null && retryResult.updateResult().isSuccess()) {
                updateResult = retryResult.updateResult();
                bidUpdatePayloadJson = buildBidUpdateRetryPayloadJson(
                        bidUpdatePayloadJson,
                        updateResult.getPayloadJson(),
                        retryResult.observedMaxBidAmount()
                );
            } else {
                String failedBidUpdatePayloadJson = retryResult == null
                        ? updateResult.getPayloadJson()
                        : buildBidUpdateRetryPayloadJson(updateResult.getPayloadJson(), retryResult.updateResult().getPayloadJson(), retryResult.observedMaxBidAmount());
                String errorMsg = retryResult == null ? updateResult.getErrorMsg() : retryResult.updateResult().getErrorMsg();
                persistFailedRecord(
                        candidate,
                        slotTime,
                        executionWindow,
                        metricsSnapshot,
                        decisionResult,
                        mergeUpdatePayloadJson(remoteStatusResult == null ? null : remoteStatusResult.getPayloadJson(), failedBidUpdatePayloadJson),
                        errorMsg
                );
                return;
            }
        }

        MtPromotionBidRecord record = buildBaseRecord(candidate, slotTime, executionWindow, metricsSnapshot);
        record.setPreviousBidAmount(decisionResult.getPreviousBidAmount());
        record.setNextBidAmount(decisionResult.getNextBidAmount());
        record.setDecisionAction(decisionResult.getDecisionAction());
        record.setDecisionReason(decisionResult.getDecisionReason());
        record.setDecisionDetailJson(mergeDecisionDetailWithMetricsContext(
                decisionResult.getDecisionDetailJson(),
                metricsSnapshot,
                executionWindow
        ));
        record.setExecuteStatus(STATUS_SUCCESS);
        record.setUpdatePayloadJson(mergeUpdatePayloadJson(
                remoteStatusResult == null ? null : remoteStatusResult.getPayloadJson(),
                bidUpdatePayloadJson
        ));
        insertRecord(record, candidate);

        log.info(
                "自动调价执行成功，shopId={}, slotTime={}, action={}, nextBid={}",
                candidate.getShopId(),
                slotTime,
                decisionResult.getDecisionAction(),
                decisionResult.getNextBidAmount()
        );
    }

    private void refreshShippingTimeIfMissing(MtPromotionAutoBidCandidate candidate) {
        if (candidate == null || StringUtils.isNotBlank(candidate.getShippingTime()) || StringUtils.isBlank(candidate.getAccessToken())) {
            return;
        }
        MtShop shop = mtShopService.findById(candidate.getShopId());
        if (shop == null) {
            return;
        }
        mtShopClient.queryAuthorizedShop(candidate.getShopId(), candidate.getAccessToken(), "AUTO_BID_FILL_SHIPPING_TIME").ifPresent(detail -> {
            MtShop updatedShop = mtShopService.updateShippingTimeFromAuthorizedDetail(shop, detail);
            candidate.setShippingTime(updatedShop == null ? null : updatedShop.getShippingTime());
            if (StringUtils.isNotBlank(candidate.getShippingTime())) {
                log.info("自动调价补齐门店营业时间成功，shopId={}, shippingTime={}", candidate.getShopId(), candidate.getShippingTime());
            }
        });
    }

    private MtPromotionBidRecord buildBaseRecord(
            MtPromotionAutoBidCandidate candidate,
            LocalDateTime slotTime,
            PromotionExecutionWindow executionWindow,
            PromotionMetricsSnapshot metricsSnapshot
    ) {
        LocalDateTime now = LocalDateTime.now();
        MtPromotionBidRecord record = new MtPromotionBidRecord();
        record.setShopId(candidate.getShopId());
        record.setBizDate(slotTime.toLocalDate());
        record.setSlotTime(slotTime);
        record.setRecordSource(RECORD_SOURCE_AUTO);
        record.setAttributedSlotTime(resolveCurrentSlotTime(slotTime));
        record.setOperatedAt(null);
        record.setWindowKey(executionWindow == null ? null : executionWindow.getWindowKey());
        record.setWindowStartAt(metricsSnapshot != null && metricsSnapshot.getEffectiveWindowStartAt() != null
                ? metricsSnapshot.getEffectiveWindowStartAt()
                : executionWindow == null ? null : executionWindow.getWindowStartAt());
        record.setWindowEndAt(executionWindow == null ? null : executionWindow.getWindowEndAt());
        record.setRequestNo(buildRequestNo(candidate.getShopId(), slotTime));
        record.setCostCounterValue(metricsSnapshot == null ? null : normalizeMoney(metricsSnapshot.getCostCounterValue()));
        record.setTotalCost(metricsSnapshot == null ? null : normalizeMoney(metricsSnapshot.getWindowCost()));
        record.setTotalIncome(metricsSnapshot == null ? null : normalizeMoney(metricsSnapshot.getWindowIncome()));
        record.setTotalCostRatio(metricsSnapshot == null ? null : normalizeRatio(metricsSnapshot.getWindowCostRatio()));
        record.setCostDelta(metricsSnapshot == null ? null : normalizeMoney(metricsSnapshot.getSlotCostDelta()));
        record.setMetricsPayloadJson(metricsSnapshot == null ? null : metricsSnapshot.getPayloadJson());
        record.setDecisionDetailJson(mergeDecisionDetailWithMetricsContext(null, metricsSnapshot, executionWindow));
        record.setCreateTime(now);
        record.setUpdateTime(now);
        return record;
    }

    private void persistSkippedRecord(
            MtPromotionAutoBidCandidate candidate,
            LocalDateTime slotTime,
            PromotionExecutionWindow executionWindow,
            PromotionMetricsSnapshot metricsSnapshot,
            String reason,
            String updatePayloadJson
    ) {
        MtPromotionBidRecord record = buildBaseRecord(candidate, slotTime, executionWindow, metricsSnapshot);
        record.setDecisionAction(PromotionDecisionEngine.ACTION_KEEP);
        record.setDecisionReason(reason);
        record.setExecuteStatus(STATUS_SKIPPED);
        record.setUpdatePayloadJson(updatePayloadJson);
        insertRecord(record, candidate);
        log.info("自动调价跳过，shopId={}, slotTime={}, reason={}", candidate.getShopId(), slotTime, reason);
    }

    private void persistFailedRecord(
            MtPromotionAutoBidCandidate candidate,
            LocalDateTime slotTime,
            PromotionExecutionWindow executionWindow,
            PromotionMetricsSnapshot metricsSnapshot,
            PromotionDecisionResult decisionResult,
            String updatePayloadJson,
            String errorMsg
    ) {
        MtPromotionBidRecord record = buildBaseRecord(candidate, slotTime, executionWindow, metricsSnapshot);
        if (decisionResult != null) {
            record.setPreviousBidAmount(decisionResult.getPreviousBidAmount());
            record.setNextBidAmount(decisionResult.getNextBidAmount());
            record.setDecisionAction(decisionResult.getDecisionAction());
            record.setDecisionReason(decisionResult.getDecisionReason());
            record.setDecisionDetailJson(decisionResult.getDecisionDetailJson());
        }
        record.setUpdatePayloadJson(updatePayloadJson);
        record.setExecuteStatus(STATUS_FAILED);
        record.setErrorMsg(StringUtils.left(StringUtils.defaultIfBlank(errorMsg, "UNKNOWN_ERROR"), 1000));
        insertRecord(record, candidate);
    }

    private boolean handleWindowEndBidReset(
            MtPromotionAutoBidCandidate candidate,
            LocalDateTime slotTime,
            PromotionTimeWindowResult previousTimeWindowResult,
            PromotionTimeWindowResult currentTimeWindowResult
    ) {
        if (!shouldCloseWindow(previousTimeWindowResult, currentTimeWindowResult)) {
            return false;
        }
        PromotionExecutionWindow previousWindow = previousTimeWindowResult == null ? null : previousTimeWindowResult.getExecutionWindow();
        PromotionDecisionResult decisionResult = new PromotionDecisionResult();
        decisionResult.setDecisionAction(PromotionDecisionEngine.ACTION_DECREASE);
        decisionResult.setDecisionReason(DECISION_REASON_WINDOW_END_BID_RESET);
        decisionResult.setPreviousBidAmount(resolveLatestSuccessBid(candidate.getShopId(), previousWindow));
        decisionResult.setNextBidAmount(WINDOW_END_RESET_BID);

        PromotionBidOpenApiResult updateResult = updatePlanBid(
                candidate,
                WINDOW_END_RESET_BID,
                slotTime,
                previousWindow,
                "WINDOW_END_BID_RESET"
        );
        if (!updateResult.isSuccess()) {
            persistFailedRecord(
                    candidate,
                    slotTime,
                    previousWindow,
                    null,
                    decisionResult,
                    mergeUpdatePayloadJson(null, updateResult.getPayloadJson()),
                    updateResult.getErrorMsg()
            );
            return true;
        }

        MtPromotionBidRecord record = buildBaseRecord(candidate, slotTime, previousWindow, null);
        record.setPreviousBidAmount(decisionResult.getPreviousBidAmount());
        record.setNextBidAmount(decisionResult.getNextBidAmount());
        record.setDecisionAction(decisionResult.getDecisionAction());
        record.setDecisionReason(decisionResult.getDecisionReason());
        record.setExecuteStatus(STATUS_SUCCESS);
        record.setUpdatePayloadJson(mergeUpdatePayloadJson(null, updateResult.getPayloadJson()));
        insertRecord(record, candidate);
        return true;
    }

    private PromotionBidOpenApiResult updatePlanBid(
            MtPromotionAutoBidCandidate candidate,
            BigDecimal nextBidAmount,
            LocalDateTime slotTime,
            PromotionExecutionWindow executionWindow,
            BigDecimal effectiveMaxBidAmount
    ) {
        return updatePlanBid(candidate, nextBidAmount, slotTime, executionWindow, "SLOT_BID_UPDATE", effectiveMaxBidAmount);
    }

    private PromotionBidOpenApiResult updatePlanBid(
            MtPromotionAutoBidCandidate candidate,
            BigDecimal nextBidAmount,
            LocalDateTime slotTime,
            PromotionExecutionWindow executionWindow,
            String trigger
    ) {
        return updatePlanBid(candidate, nextBidAmount, slotTime, executionWindow, trigger, MAX_SYNC_BID_AMOUNT);
    }

    private PromotionBidOpenApiResult updatePlanBid(
            MtPromotionAutoBidCandidate candidate,
            BigDecimal nextBidAmount,
            LocalDateTime slotTime,
            PromotionExecutionWindow executionWindow,
            String trigger,
            BigDecimal effectiveMaxBidAmount
    ) {
        BigDecimal syncedBidAmount = clampBidAmountForSync(nextBidAmount, candidate, slotTime, executionWindow, effectiveMaxBidAmount);
        Integer bidCents = toBidCents(syncedBidAmount);
        PromotionBidOpenApiResult result = new PromotionBidOpenApiResult();
        result.setBid(bidCents);
        if (bidCents == null) {
            result.setErrorMsg("下一时段出价格式不正确");
            return result;
        }
        return mtPromotionOpenApiClient.updatePlanBid(
                candidate,
                bidCents,
                slotTime,
                executionWindow,
                trigger
        );
    }

    private BidLimitRetryResult retryUpdatePlanBidWithObservedMaxBid(
            MtPromotionAutoBidCandidate candidate,
            PromotionDecisionResult decisionResult,
            PromotionBidOpenApiResult firstUpdateResult,
            LocalDateTime slotTime,
            PromotionExecutionWindow executionWindow,
            BigDecimal currentEffectiveMaxBidAmount
    ) {
        if (mtPromotionBidConstraintService == null || firstUpdateResult == null || firstUpdateResult.isSuccess()) {
            return null;
        }
        Optional<BigDecimal> observedMaxBidAmount = mtPromotionBidConstraintService.observeMaxBidLimitFromFailure(
                candidate,
                firstUpdateResult,
                slotTime
        );
        if (observedMaxBidAmount == null || observedMaxBidAmount.isEmpty()) {
            return null;
        }
        BigDecimal retryMaxBidAmount = minBidAmount(currentEffectiveMaxBidAmount, observedMaxBidAmount.get());
        BigDecimal originalNextBidAmount = decisionResult == null ? null : decisionResult.getNextBidAmount();
        if (originalNextBidAmount != null && normalizeBidAmountWithFloor(originalNextBidAmount).compareTo(retryMaxBidAmount) <= 0) {
            return null;
        }
        enforceMaxBidLimit(decisionResult, retryMaxBidAmount);
        PromotionBidOpenApiResult retryUpdateResult = updatePlanBid(
                candidate,
                decisionResult == null ? retryMaxBidAmount : decisionResult.getNextBidAmount(),
                slotTime,
                executionWindow,
                "SLOT_BID_UPDATE_RETRY_WITH_OBSERVED_LIMIT",
                retryMaxBidAmount
        );
        return new BidLimitRetryResult(retryUpdateResult, retryMaxBidAmount);
    }

    private String refreshCandidateWithLatestSetting(MtPromotionAutoBidCandidate candidate) {
        MtPromotionSetting latestSetting = mtPromotionSettingService.getOrCreateByShopId(candidate.getShopId());
        if (!Boolean.TRUE.equals(latestSetting.getPromotionEnabled())) {
            return SKIP_REASON_PROMOTION_DISABLED_DURING_TASK;
        }
        if (!Boolean.TRUE.equals(latestSetting.getStrategyConfigured()) || StringUtils.isBlank(latestSetting.getStrategyConfigJson())) {
            return SKIP_REASON_STRATEGY_CHANGED_DURING_TASK;
        }
        candidate.setStrategyConfigJson(latestSetting.getStrategyConfigJson());
        return null;
    }

    private PromotionPlanStatusOpenApiResult ensureRemotePlanStillEnabled(
            MtPromotionAutoBidCandidate candidate,
            LocalDateTime slotTime,
            PromotionTimeWindowResult currentTimeWindowResult
    ) {
        if (currentTimeWindowResult == null || !currentTimeWindowResult.isExecutable()) {
            return null;
        }
        PromotionPlanStatusOpenApiResult result = mtPromotionOpenApiClient.queryPlanStatus(
                candidate,
                slotTime,
                currentTimeWindowResult.getExecutionWindow(),
                "WINDOW_STATUS_CHECK"
        );
        if (result == null || !result.isAuthFailure() || result.isSuccess() || mtAccessTokenService == null) {
            return result;
        }
        String firstPayloadJson = result.getPayloadJson();
        log.warn("查询美团推广计划状态鉴权失败，准备刷新 token 后重试一次，shopId={}, slotTime={}, responseCode={}, traceId={}",
                candidate == null ? null : candidate.getShopId(),
                slotTime,
                result.getResponseCode(),
                result.getResponseTraceId());
        MtAccessTokenService.TokenRefreshResult tokenRefreshResult = mtAccessTokenService.refreshTokenForShopWithResult(candidate.getShopId());
        MtAccessToken refreshedToken = tokenRefreshResult == null ? null : tokenRefreshResult.token();
        if (refreshedToken == null || StringUtils.isBlank(refreshedToken.getAccessToken())) {
            result.setPayloadJson(appendTokenRefreshRetryPayload(
                    firstPayloadJson,
                    tokenRefreshResult,
                    null
            ));
            return result;
        }
        candidate.setAccessToken(refreshedToken.getAccessToken());
        candidate.setTokenExpiresAt(refreshedToken.getExpiresAt());
        PromotionPlanStatusOpenApiResult retryResult = mtPromotionOpenApiClient.queryPlanStatus(
                candidate,
                slotTime,
                currentTimeWindowResult.getExecutionWindow(),
                "WINDOW_STATUS_CHECK_AFTER_TOKEN_REFRESH"
        );
        if (retryResult != null) {
            retryResult.setPayloadJson(appendTokenRefreshRetryPayload(
                    retryResult.getPayloadJson(),
                    tokenRefreshResult,
                    firstPayloadJson
            ));
        }
        return retryResult;
    }

    private void handleConfirmedAuthFailure(MtPromotionAutoBidCandidate candidate, String errorMsg) {
        if (candidate == null || candidate.getShopId() == null) {
            return;
        }
        if (mtMeituanAuthorizationFailureHandler != null) {
            mtMeituanAuthorizationFailureHandler.handleConfirmedAuthFailure(
                    candidate.getShopId(),
                    "MT_PROMOTION_AUTO_BID_STATUS_CHECK",
                    null,
                    errorMsg
            );
        } else if (mtAccessTokenService != null) {
            mtAccessTokenService.markReauthRequired(candidate.getShopId());
            mtPromotionSettingService.disablePromotionByShopId(candidate.getShopId(), CHANGE_REASON_AUTH_FAILURE);
        } else {
            mtPromotionSettingService.disablePromotionByShopId(candidate.getShopId(), CHANGE_REASON_AUTH_FAILURE);
        }
        log.warn("自动调价确认美团鉴权失败，已标记重新授权并关闭推广，shopId={}, poiId={}, errorMsg={}",
                candidate.getShopId(), candidate.getPoiId(), errorMsg);
    }

    private String appendTokenRefreshRetryPayload(
            String payloadJson,
            MtAccessTokenService.TokenRefreshResult tokenRefreshResult,
            String firstAttemptPayloadJson
    ) {
        Map<String, Object> payload = JsonUtil.toMap(payloadJson);
        if (payload == null) {
            payload = new LinkedHashMap<>();
        }
        MtAccessToken refreshedToken = tokenRefreshResult == null ? null : tokenRefreshResult.token();
        Map<String, Object> tokenRefreshRetry = new LinkedHashMap<>();
        tokenRefreshRetry.put("attempted", true);
        tokenRefreshRetry.put("refreshStatus", tokenRefreshResult != null && tokenRefreshResult.success() ? "REFRESH_SUCCESS" : "REFRESH_FAILED");
        tokenRefreshRetry.put("refreshResponseCode", tokenRefreshResult == null ? null : tokenRefreshResult.responseCode());
        tokenRefreshRetry.put("refreshResponseMsg", tokenRefreshResult == null ? null : tokenRefreshResult.responseMsg());
        tokenRefreshRetry.put("refreshError", tokenRefreshResult == null ? "REFRESH_RESULT_MISSING" : tokenRefreshResult.error());
        tokenRefreshRetry.put("refreshedExpiresAt", refreshedToken == null || refreshedToken.getExpiresAt() == null
                ? null : refreshedToken.getExpiresAt().toString());
        Map<String, Object> firstAttemptPayload = JsonUtil.toMap(firstAttemptPayloadJson);
        if (firstAttemptPayload != null) {
            tokenRefreshRetry.put("firstAttempt", firstAttemptPayload);
        }
        payload.put("tokenRefreshRetry", tokenRefreshRetry);
        return JsonUtil.toJson(payload);
    }

    private void ensureRealtimeSnapshotForExecutableSlot(
            MtPromotionAutoBidCandidate candidate,
            LocalDateTime slotTime
    ) {
        if (candidate == null
                || candidate.getShopId() == null
                || slotTime == null
                || StringUtils.isBlank(candidate.getAccessToken())
                || mtPromotionAdRealtimeSnapshotService == null) {
            return;
        }
        MtPromotionAdRealtimeSnapshot existingSnapshot = mtPromotionAdRealtimeSnapshotService.selectByShopIdAndBizDateAndSlotTime(
                candidate.getShopId(),
                slotTime.toLocalDate(),
                slotTime
        );
        if (existingSnapshot != null) {
            return;
        }
        try {
            mtPromotionAdRealtimeSnapshotService.captureCurrentSlotSnapshot(
                    candidate.getShopId(),
                    candidate.getAccessToken(),
                    slotTime
            );
            log.info("自动调价执行前补齐实时推广快照成功，shopId={}, slotTime={}",
                    candidate.getShopId(), slotTime);
        } catch (RuntimeException e) {
            log.warn("自动调价执行前补齐实时推广快照失败，shopId={}, slotTime={}, error={}",
                    candidate.getShopId(), slotTime, e.getMessage());
        }
    }

    private void logRemoteBudgetStatus(
            MtPromotionAutoBidCandidate candidate,
            LocalDateTime slotTime,
            PromotionExecutionWindow executionWindow,
            PromotionPlanStatusOpenApiResult remoteStatusResult
    ) {
        if (candidate == null || remoteStatusResult == null || !remoteStatusResult.isSuccess()) {
            return;
        }
        log.info("自动调价预算状态检查完成，shopId={}, poiId={}, slotTime={}, windowKey={}, status={}, budget={}, budgetUsed={}, availableBudget={}",
                candidate.getShopId(),
                candidate.getPoiId(),
                slotTime,
                executionWindow == null ? null : executionWindow.getWindowKey(),
                remoteStatusResult.getStatus(),
                remoteStatusResult.getBudget(),
                remoteStatusResult.getBudgetUsed(),
                remoteStatusResult.getAvailableBudget());
    }

    private boolean shouldCloseWindow(
            PromotionTimeWindowResult previousTimeWindowResult,
            PromotionTimeWindowResult currentTimeWindowResult
    ) {
        if (previousTimeWindowResult == null || !previousTimeWindowResult.isExecutable()) {
            return false;
        }
        if (currentTimeWindowResult == null || !currentTimeWindowResult.isExecutable()) {
            return true;
        }
        return !sameWindow(previousTimeWindowResult.getExecutionWindow(), currentTimeWindowResult.getExecutionWindow());
    }

    private boolean sameWindow(PromotionExecutionWindow left, PromotionExecutionWindow right) {
        if (left == null || right == null) {
            return false;
        }
        return StringUtils.equals(left.getWindowKey(), right.getWindowKey());
    }

    private PromotionExecutionWindow resolveRecordWindow(
            PromotionTimeWindowResult currentTimeWindowResult,
            PromotionTimeWindowResult previousTimeWindowResult
    ) {
        if (currentTimeWindowResult != null && currentTimeWindowResult.getExecutionWindow() != null) {
            return currentTimeWindowResult.getExecutionWindow();
        }
        return previousTimeWindowResult == null ? null : previousTimeWindowResult.getExecutionWindow();
    }

    private boolean shouldSilentlySkipOutOfPromotionWindow(PromotionTimeWindowResult timeWindowResult) {
        if (timeWindowResult == null) {
            return false;
        }
        return StringUtils.equalsAny(
                timeWindowResult.getReason(),
                "OUT_OF_BUSINESS_WINDOW",
                "OUT_OF_CUSTOM_WINDOW"
        );
    }

    private String mergeUpdatePayloadJson(String statusCheckPayloadJson, String planStatusPayloadJson, String bidUpdatePayloadJson) {
        if (StringUtils.isBlank(statusCheckPayloadJson)
                && StringUtils.isBlank(planStatusPayloadJson)
                && StringUtils.isBlank(bidUpdatePayloadJson)) {
            return null;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        if (StringUtils.isNotBlank(statusCheckPayloadJson)) {
            payload.put("statusCheck", JsonUtil.toMap(statusCheckPayloadJson));
        }
        if (StringUtils.isNotBlank(planStatusPayloadJson)) {
            payload.put("planStatus", JsonUtil.toMap(planStatusPayloadJson));
        }
        if (StringUtils.isNotBlank(bidUpdatePayloadJson)) {
            payload.put("bidUpdate", JsonUtil.toMap(bidUpdatePayloadJson));
        }
        return JsonUtil.toJson(payload);
    }

    private String mergeUpdatePayloadJson(String statusCheckPayloadJson, String bidUpdatePayloadJson) {
        return mergeUpdatePayloadJson(statusCheckPayloadJson, null, bidUpdatePayloadJson);
    }

    private MtPromotionAutoBidCandidate buildManualBidCandidate(
            MtShop shop,
            MtPromotionSetting setting,
            MtAccessToken accessToken
    ) {
        MtPromotionAutoBidCandidate candidate = new MtPromotionAutoBidCandidate();
        candidate.setSettingId(setting == null ? null : setting.getId());
        candidate.setShopId(shop.getId());
        candidate.setShopName(StringUtils.firstNonBlank(shop.getPoiName(), shop.getName(), "美团门店"));
        candidate.setPoiId(shop.getPoiId());
        candidate.setShippingTime(shop.getShippingTime());
        candidate.setStrategyConfigJson(setting == null ? null : setting.getStrategyConfigJson());
        candidate.setAccessToken(accessToken == null ? null : accessToken.getAccessToken());
        candidate.setTokenExpiresAt(accessToken == null ? null : accessToken.getExpiresAt());
        return candidate;
    }

    private MtPromotionBidRecord findLatestSuccess(Long shopId, PromotionExecutionWindow executionWindow, int limit) {
        if (shopId == null || executionWindow == null || StringUtils.isBlank(executionWindow.getWindowKey())) {
            return null;
        }
        List<MtPromotionBidRecord> history = mtPromotionBidRecordMapper.selectLatestSuccessByShopIdAndWindowKey(
                shopId,
                executionWindow.getWindowKey(),
                Math.max(1, limit)
        );
        return history == null || history.isEmpty() ? null : history.get(0);
    }

    private PromotionDecisionResult buildManualDecisionResult(MtPromotionBidRecord latestSuccess, BigDecimal nextBidAmount) {
        PromotionDecisionResult decisionResult = new PromotionDecisionResult();
        BigDecimal previousBidAmount = latestSuccess == null ? null : latestSuccess.getNextBidAmount();
        decisionResult.setPreviousBidAmount(previousBidAmount);
        decisionResult.setNextBidAmount(nextBidAmount);
        decisionResult.setDecisionAction(resolveRuleDecisionAction(previousBidAmount, nextBidAmount));
        decisionResult.setDecisionReason(DECISION_REASON_MANUAL_BID_UPDATE);
        decisionResult.setDecisionSource(PromotionBidDecisionService.DECISION_SOURCE_RULE);
        decisionResult.setPowerConsumeMode(PromotionPowerConsumeService.POWER_MODE_RULE);
        return decisionResult;
    }

    private String buildManualDecisionDetailJson(MtPromotionBidRecord record, PromotionExecutionWindow executionWindow) {
        PromotionDecisionDetail detail = new PromotionDecisionDetail();
        detail.setDecisionMode(PromotionBidDecisionService.DECISION_MODE_RULE);
        detail.setDecisionSource(PromotionBidDecisionService.DECISION_SOURCE_RULE);
        detail.setWindowBaselineBidAmount(normalizeBidAmountWithFloor(record == null ? null : record.getNextBidAmount()));
        detail.setPricingBranch("MANUAL");
        detail.setManualBidSource(RECORD_SOURCE_MANUAL);
        detail.setManualBidOperatedAt(record == null ? null : record.getOperatedAt());
        detail.setManualBidAttributedSlotTime(record == null ? null : record.getAttributedSlotTime());
        detail.setMetricsBusinessWindowStartAt(executionWindow == null ? null : executionWindow.getWindowStartAt());
        return JsonUtil.toJson(detail);
    }

    private BigDecimal resolveLatestSuccessBid(Long shopId, PromotionExecutionWindow executionWindow) {
        if (shopId == null || executionWindow == null || StringUtils.isBlank(executionWindow.getWindowKey())) {
            return null;
        }
        List<MtPromotionBidRecord> successHistory = mtPromotionBidRecordMapper.selectLatestSuccessByShopIdAndWindowKey(
                shopId,
                executionWindow.getWindowKey(),
                1
        );
        if (successHistory == null || successHistory.isEmpty()) {
            return null;
        }
        MtPromotionBidRecord latestRecord = successHistory.get(0);
        return latestRecord == null ? null : latestRecord.getNextBidAmount();
    }

    private String buildMetricsUntrustedReason(PromotionMetricsSnapshot metricsSnapshot) {
        if (metricsSnapshot == null || StringUtils.isBlank(metricsSnapshot.getUntrustedReason())) {
            return SKIP_REASON_UNTRUSTED_PROMOTION_METRICS;
        }
        return SKIP_REASON_UNTRUSTED_PROMOTION_METRICS + ":" + metricsSnapshot.getUntrustedReason();
    }

    private InitialBidResolutionResult resolveInitialBidAmount(
            MtPromotionAutoBidCandidate candidate,
            LocalDateTime slotTime,
            PromotionExecutionWindow executionWindow,
            PromotionTimeWindowResult previousTimeWindowResult,
            List<MtPromotionBidRecord> successHistory,
            PromotionPlanStatusOpenApiResult remoteStatusResult,
            MtPromotionSettingService.StrategyConfig strategyConfig,
            ShopPeakWindowProfileLookupResult peakLookupResult
    ) {
        if (shouldRefreshLowPeakBaselineBid(strategyConfig, peakLookupResult)) {
            return resolveLiveLowPeakBaselineBidAmount(
                    candidate,
                    slotTime,
                    executionWindow,
                    previousTimeWindowResult,
                    successHistory,
                    remoteStatusResult
            );
        }
        if (successHistory != null && !successHistory.isEmpty()) {
            return new InitialBidResolutionResult(
                    defaultIfNull(resolveWindowBaselineBidFromHistory(successHistory), DEFAULT_INITIAL_BID),
                    null
            );
        }
        if (shouldUseCurrentPlanBidAsInitialBid(previousTimeWindowResult, executionWindow)) {
            BigDecimal currentPlanBidAmount = resolveCurrentPlanBidAmount(candidate, slotTime, executionWindow, remoteStatusResult);
            if (currentPlanBidAmount != null) {
                return new InitialBidResolutionResult(currentPlanBidAmount, null);
            }
        }
        return resolvePeerAverageInitialBid(
                candidate,
                slotTime,
                executionWindow,
                INITIAL_BID_TRIGGER_PEER_AVG,
                "默认初始出价",
                DEFAULT_INITIAL_BID
        );
    }

    private InitialBidResolutionResult resolveLiveLowPeakBaselineBidAmount(
            MtPromotionAutoBidCandidate candidate,
            LocalDateTime slotTime,
            PromotionExecutionWindow executionWindow,
            PromotionTimeWindowResult previousTimeWindowResult,
            List<MtPromotionBidRecord> successHistory,
            PromotionPlanStatusOpenApiResult remoteStatusResult
    ) {
        InitialBidResolutionResult liveBaselineResult = resolvePeerAverageInitialBid(
                candidate,
                slotTime,
                executionWindow,
                LOW_PEAK_BID_TRIGGER_PEER_AVG,
                "低峰基准价",
                null
        );
        if (liveBaselineResult.initialBidAmount() != null) {
            return liveBaselineResult;
        }
        BigDecimal historyBaselineBid = resolveWindowBaselineBidFromHistory(successHistory);
        if (historyBaselineBid != null) {
            log.warn("低峰实时基准价查询失败，回退最近窗口基准价，shopId={}, slotTime={}, baselineBid={}",
                    candidate == null ? null : candidate.getShopId(),
                    slotTime,
                    historyBaselineBid);
            return new InitialBidResolutionResult(historyBaselineBid, liveBaselineResult.peerBidResult());
        }
        if (shouldUseCurrentPlanBidAsInitialBid(previousTimeWindowResult, executionWindow)) {
            BigDecimal currentPlanBidAmount = resolveCurrentPlanBidAmount(candidate, slotTime, executionWindow, remoteStatusResult);
            if (currentPlanBidAmount != null) {
                log.warn("低峰实时基准价查询失败，回退当前计划出价，shopId={}, slotTime={}, bid={}",
                        candidate == null ? null : candidate.getShopId(),
                        slotTime,
                        currentPlanBidAmount);
                return new InitialBidResolutionResult(currentPlanBidAmount, liveBaselineResult.peerBidResult());
            }
        }
        log.warn("低峰实时基准价查询失败，回退默认初始出价，shopId={}, slotTime={}, defaultBid={}",
                candidate == null ? null : candidate.getShopId(),
                slotTime,
                DEFAULT_INITIAL_BID);
        return new InitialBidResolutionResult(DEFAULT_INITIAL_BID, liveBaselineResult.peerBidResult());
    }

    private InitialBidResolutionResult resolvePeerAverageInitialBid(
            MtPromotionAutoBidCandidate candidate,
            LocalDateTime slotTime,
            PromotionExecutionWindow executionWindow,
            String trigger,
            String fallbackDescription,
            BigDecimal fallbackBidAmount
    ) {
        PeerBidPriceOpenApiResult peerBidResult = mtPromotionOpenApiClient.queryPeerAverageBid(
                candidate,
                slotTime,
                executionWindow,
                trigger
        );
        if (peerBidResult == null
                || !peerBidResult.isSuccess()
                || peerBidResult.getAverageBidAmount() == null
                || peerBidResult.getAverageBidAmount().signum() <= 0) {
            log.warn("查询附近商家平均出价失败，回退{}，shopId={}, slotTime={}, trigger={}, errorMsg={}",
                    fallbackDescription,
                    candidate == null ? null : candidate.getShopId(),
                    slotTime,
                    trigger,
                    peerBidResult == null ? null : peerBidResult.getErrorMsg());
            return new InitialBidResolutionResult(fallbackBidAmount, peerBidResult);
        }
        BigDecimal peerAverageBidAmount = peerBidResult.getAverageBidAmount();
        if (peerAverageBidAmount.compareTo(PEER_AVG_INITIAL_BID_MIN) < 0
                || peerAverageBidAmount.compareTo(PEER_AVG_INITIAL_BID_MAX) > 0) {
            log.warn("附近商家平均出价超出允许范围，回退{}，shopId={}, slotTime={}, trigger={}, averageBidAmount={}",
                    fallbackDescription,
                    candidate == null ? null : candidate.getShopId(),
                    slotTime,
                    trigger,
                    peerAverageBidAmount);
            return new InitialBidResolutionResult(fallbackBidAmount, peerBidResult);
        }
        BigDecimal initialBidAmount = peerAverageBidAmount
                .subtract(PEER_AVG_INITIAL_BID_SUBTRACT)
                .multiply(PEER_AVG_INITIAL_BID_FACTOR)
                .subtract(PEER_AVG_INITIAL_BID_OFFSET)
                .multiply(BigDecimal.TEN)
                .setScale(0, RoundingMode.DOWN)
                .divide(BigDecimal.TEN)
                .setScale(2, RoundingMode.UNNECESSARY);
        return new InitialBidResolutionResult(normalizeBidAmountWithFloor(initialBidAmount), peerBidResult);
    }

    private BigDecimal resolveWindowBaselineBidFromHistory(List<MtPromotionBidRecord> successHistory) {
        if (successHistory == null || successHistory.isEmpty()) {
            return null;
        }
        for (MtPromotionBidRecord history : successHistory) {
            if (history == null || StringUtils.isBlank(history.getDecisionDetailJson())) {
                continue;
            }
            PromotionDecisionDetail detail = JsonUtil.toBean(history.getDecisionDetailJson(), PromotionDecisionDetail.class);
            if (detail != null && detail.getWindowBaselineBidAmount() != null) {
                return normalizeMoney(detail.getWindowBaselineBidAmount());
            }
        }
        return null;
    }

    private BaselineBidResolution resolveLowPeakBaselineBid(
            MtPromotionAutoBidCandidate candidate,
            LocalDateTime slotTime,
            PromotionExecutionWindow executionWindow,
            PromotionTimeWindowResult previousTimeWindowResult,
            List<MtPromotionBidRecord> successHistory,
            PromotionPlanStatusOpenApiResult remoteStatusResult,
            String strategyConfigHash,
            MtPromotionRecentOrderStatistic recentOrderStatistic,
            ShopPeakWindowProfileLookupResult peakLookupResult
    ) {
        BigDecimal inheritedBid = resolveLowPeakBaselineBidFromHistory(successHistory, executionWindow, strategyConfigHash);
        if (inheritedBid != null) {
            return applyLowPeakShareBaseline(
                    candidate,
                    slotTime,
                    new BaselineBidResolution(inheritedBid, BID_SOURCE_INHERITED, null, null, null, null, false),
                    recentOrderStatistic,
                    peakLookupResult
            );
        }
        InitialBidResolutionResult peerResult = resolvePeerAverageInitialBid(
                candidate,
                slotTime,
                executionWindow,
                LOW_PEAK_BID_TRIGGER_PEER_AVG,
                "低峰基准价",
                null
        );
        if (peerResult.initialBidAmount() != null) {
            return applyLowPeakShareBaseline(
                    candidate,
                    slotTime,
                    new BaselineBidResolution(peerResult.initialBidAmount(), BID_SOURCE_PEER_AVG, peerResult.peerBidResult(), null, null, null, false),
                    recentOrderStatistic,
                    peakLookupResult
            );
        }
        BigDecimal historyBaselineBid = resolveWindowBaselineBidFromHistory(successHistory);
        if (historyBaselineBid != null) {
            return applyLowPeakShareBaseline(
                    candidate,
                    slotTime,
                    new BaselineBidResolution(historyBaselineBid, BID_SOURCE_HISTORY, peerResult.peerBidResult(), null, null, null, false),
                    recentOrderStatistic,
                    peakLookupResult
            );
        }
        if (shouldUseCurrentPlanBidAsInitialBid(previousTimeWindowResult, executionWindow)) {
            BigDecimal currentPlanBidAmount = resolveCurrentPlanBidAmount(candidate, slotTime, executionWindow, remoteStatusResult);
            if (currentPlanBidAmount != null) {
                return applyLowPeakShareBaseline(
                        candidate,
                        slotTime,
                        new BaselineBidResolution(currentPlanBidAmount, BID_SOURCE_CURRENT_PLAN, peerResult.peerBidResult(), null, null, null, false),
                        recentOrderStatistic,
                        peakLookupResult
                );
            }
        }
        return applyLowPeakShareBaseline(
                candidate,
                slotTime,
                new BaselineBidResolution(DEFAULT_INITIAL_BID, BID_SOURCE_DEFAULT, peerResult.peerBidResult(), null, null, null, false),
                recentOrderStatistic,
                peakLookupResult
        );
    }

    private BaselineBidResolution applyLowPeakShareBaseline(
            MtPromotionAutoBidCandidate candidate,
            LocalDateTime slotTime,
            BaselineBidResolution baseResolution,
            MtPromotionRecentOrderStatistic recentOrderStatistic,
            ShopPeakWindowProfileLookupResult peakLookupResult
    ) {
        if (baseResolution == null || baseResolution.bidAmount() == null) {
            return baseResolution;
        }
        if (!isTrustedRecentOrderStatistic(recentOrderStatistic)
                || !isTrustedPeakProfile(peakLookupResult)
                || recentOrderStatistic.getLowPeakOrderShare() == null) {
            return baseResolution;
        }
        BigDecimal latestHighPeakBid = resolveLatestHighPeakBid(candidate == null ? null : candidate.getShopId(), slotTime);
        if (latestHighPeakBid == null) {
            return baseResolution;
        }
        BigDecimal shareBaseline = normalizeBaselineBidAmount(latestHighPeakBid.multiply(recentOrderStatistic.getLowPeakOrderShare()));
        if (shareBaseline == null || baseResolution.bidAmount().compareTo(shareBaseline) >= 0) {
            return new BaselineBidResolution(
                    baseResolution.bidAmount(),
                    baseResolution.source(),
                    baseResolution.peerBidResult(),
                    latestHighPeakBid,
                    shareBaseline,
                    BID_SOURCE_LOW_PEAK_ORDER_SHARE,
                    false
            );
        }
        return new BaselineBidResolution(
                shareBaseline,
                BID_SOURCE_LOW_PEAK_ORDER_SHARE,
                baseResolution.peerBidResult(),
                latestHighPeakBid,
                shareBaseline,
                BID_SOURCE_LOW_PEAK_ORDER_SHARE,
                true
        );
    }

    private BigDecimal resolveLowPeakBaselineBidFromHistory(
            List<MtPromotionBidRecord> successHistory,
            PromotionExecutionWindow executionWindow,
            String strategyConfigHash
    ) {
        if (successHistory == null || successHistory.isEmpty()) {
            return null;
        }
        for (MtPromotionBidRecord history : successHistory) {
            if (history == null || StringUtils.isBlank(history.getDecisionDetailJson())) {
                continue;
            }
            if (!sameWindowKey(history.getWindowKey(), executionWindow == null ? null : executionWindow.getWindowKey())) {
                continue;
            }
            PromotionDecisionDetail detail = JsonUtil.toBean(history.getDecisionDetailJson(), PromotionDecisionDetail.class);
            if (!sameStrategyConfigHash(detail, strategyConfigHash)) {
                continue;
            }
            BigDecimal baselineBid = detail == null ? null : detail.getLowPeakBaselineBidAmount();
            if (baselineBid == null && detail != null) {
                baselineBid = detail.getWindowBaselineBidAmount();
            }
            BigDecimal normalized = normalizeBaselineBidAmount(baselineBid);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private BigDecimal resolvePeakEntryBidFromHistory(
            List<MtPromotionBidRecord> successHistory,
            String peakWindowKey,
            String strategyConfigHash
    ) {
        if (successHistory == null || successHistory.isEmpty() || StringUtils.isBlank(peakWindowKey)) {
            return null;
        }
        for (MtPromotionBidRecord history : successHistory) {
            if (history == null || StringUtils.isBlank(history.getDecisionDetailJson())) {
                continue;
            }
            PromotionDecisionDetail detail = JsonUtil.toBean(history.getDecisionDetailJson(), PromotionDecisionDetail.class);
            if (detail == null
                    || !StringUtils.equals(detail.getPeakWindowKey(), peakWindowKey)
                    || !sameStrategyConfigHash(detail, strategyConfigHash)) {
                continue;
            }
            BigDecimal normalized = normalizeBaselineBidAmount(detail.getPeakEntryBidAmount());
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private BigDecimal resolveLatestHighPeakBid(Long shopId, LocalDateTime slotTime) {
        if (shopId == null || slotTime == null) {
            return null;
        }
        List<MtPromotionBidRecord> recentSuccessRecords = mtPromotionBidRecordMapper.selectLatestSuccessByShopIdSince(
                shopId,
                slotTime.toLocalDate().minusDays(15),
                50
        );
        if (recentSuccessRecords == null || recentSuccessRecords.isEmpty()) {
            return null;
        }
        for (MtPromotionBidRecord record : recentSuccessRecords) {
            if (record == null || StringUtils.isBlank(record.getDecisionDetailJson())) {
                continue;
            }
            PromotionDecisionDetail detail = JsonUtil.toBean(record.getDecisionDetailJson(), PromotionDecisionDetail.class);
            if (detail == null) {
                continue;
            }
            boolean highPeak = StringUtils.equals(detail.getPeakBidPhase(), PeakBidPhase.PEAK.name())
                    || (Boolean.TRUE.equals(detail.getPeakWindowMatched())
                    && StringUtils.equals(detail.getPricingBranch(), PRICING_BRANCH_PEAK_RULE));
            if (highPeak && record.getNextBidAmount() != null) {
                return normalizeBaselineBidAmount(record.getNextBidAmount());
            }
        }
        return null;
    }

    private boolean isTrustedRecentOrderStatistic(MtPromotionRecentOrderStatistic statistic) {
        return statistic != null && Boolean.TRUE.equals(statistic.getTrusted());
    }

    private boolean isTrustedPeakProfile(ShopPeakWindowProfileLookupResult peakLookupResult) {
        return peakLookupResult != null
                && peakLookupResult.isGatingEnabled()
                && StringUtils.equals(peakLookupResult.getProfileStatus(), ShopPeakWindowProfileLookupResult.STATUS_USABLE)
                && CollectionUtils.isNotEmpty(peakLookupResult.getPeakWindows());
    }

    private boolean sameWindowKey(String left, String right) {
        return StringUtils.isNotBlank(left) && StringUtils.equals(left, right);
    }

    private boolean sameStrategyConfigHash(PromotionDecisionDetail detail, String strategyConfigHash) {
        return detail != null
                && StringUtils.isNotBlank(strategyConfigHash)
                && StringUtils.equals(detail.getStrategyConfigHash(), strategyConfigHash);
    }

    private PeakBidPhaseResolution resolvePeakBidPhase(
            MtPromotionAutoBidCandidate candidate,
            LocalDateTime slotTime,
            PromotionExecutionWindow executionWindow,
            PromotionTimeWindowResult previousTimeWindowResult,
            List<MtPromotionBidRecord> successHistory,
            PromotionPlanStatusOpenApiResult remoteStatusResult,
            MtPromotionSettingService.StrategyConfig strategyConfig,
            ShopPeakWindowProfileLookupResult peakLookupResult,
            MtPromotionRecentOrderStatistic recentOrderStatistic
    ) {
        if (!shouldApplyPeakWindowGating(strategyConfig) || peakLookupResult == null) {
            return PeakBidPhaseResolution.normal(null);
        }
        if (!peakLookupResult.isGatingEnabled()) {
            return PeakBidPhaseResolution.unavailable(peakLookupResult);
        }
        String strategyConfigHash = buildStrategyConfigHash(candidate == null ? null : candidate.getStrategyConfigJson());
        List<PeakWindowSlot> peakWindows = resolvePeakWindowSlots(peakLookupResult, slotTime);
        PeakWindowSlot currentPeakWindow = findCurrentPeakWindow(peakWindows, slotTime);
        if (currentPeakWindow != null || peakLookupResult.isPeakMatched()) {
            PeakWindowSlot matchedWindow = currentPeakWindow == null
                    ? findWindowByDisplayText(peakWindows, peakLookupResult.getMatchedWindow())
                    : currentPeakWindow;
            PeakEntryBidResolution inheritedPeakEntry = matchedWindow == null
                    ? null
                    : resolveInheritedPeakEntryBid(successHistory, matchedWindow.windowKey(), strategyConfigHash);
            return PeakBidPhaseResolution.peak(peakLookupResult, matchedWindow, inheritedPeakEntry);
        }

        BaselineBidResolution lowBaselineResolution = resolveLowPeakBaselineBid(
                candidate,
                slotTime,
                executionWindow,
                previousTimeWindowResult,
                successHistory,
                remoteStatusResult,
                strategyConfigHash,
                recentOrderStatistic,
                peakLookupResult
        );
        BigDecimal lowPeakBaselineBid = normalizeBaselineBidAmount(lowBaselineResolution == null ? null : lowBaselineResolution.bidAmount());
        if (lowPeakBaselineBid == null) {
            lowPeakBaselineBid = normalizeBaselineBidAmount(DEFAULT_INITIAL_BID);
        }

        PeakWindowSlot previousPeakWindow = findPreviousPeakWindow(peakWindows, slotTime);
        PeakWindowSlot nextPeakWindow = findNextPeakWindow(peakWindows, slotTime);
        int slotsUntilNextPeak = slotsUntil(slotTime, nextPeakWindow == null ? null : nextPeakWindow.startAt());

        BigDecimal previousBid = resolveLatestSuccessBidFromHistory(successHistory);
        BigDecimal nextWarmupBid = calculatePrePeakTargetBid(
                lowPeakBaselineBid,
                slotsUntilNextPeak,
                successHistory,
                nextPeakWindow == null ? null : nextPeakWindow.windowKey(),
                strategyConfigHash,
                slotTime
        );
        if (nextPeakWindow != null && nextWarmupBid != null) {
            BigDecimal decayTarget = calculatePostPeakDecayTarget(previousBid, lowPeakBaselineBid, recentOrderStatistic);
            boolean previousPeakEnded = previousPeakWindow != null;
            BigDecimal targetBid = previousPeakEnded ? maxBid(decayTarget, nextWarmupBid, lowPeakBaselineBid) : nextWarmupBid;
            PeakBidPhase phase = previousPeakEnded && targetBid.compareTo(nextWarmupBid) > 0
                    ? PeakBidPhase.POST_PEAK_RAMP_DOWN
                    : PeakBidPhase.PRE_PEAK_RAMP_UP;
            return PeakBidPhaseResolution.rule(
                    peakLookupResult,
                    phase,
                    previousPeakWindow,
                    nextPeakWindow,
                    lowBaselineResolution,
                    null,
                    targetBid,
                    slotsUntilNextPeak,
                    slotsSince(slotTime, previousPeakWindow == null ? null : previousPeakWindow.endAt()),
                    "FIXED_NEXT_PEAK_WARMUP",
                    recentOrderStatistic,
                    resolvePostPeakDecreaseStep(recentOrderStatistic)
            );
        }

        if (previousPeakWindow != null) {
            BigDecimal decayTarget = calculatePostPeakDecayTarget(previousBid, lowPeakBaselineBid, recentOrderStatistic);
            if (previousBid != null && previousBid.compareTo(lowPeakBaselineBid) > 0) {
                return PeakBidPhaseResolution.rule(
                        peakLookupResult,
                        PeakBidPhase.POST_PEAK_RAMP_DOWN,
                        previousPeakWindow,
                        nextPeakWindow,
                        lowBaselineResolution,
                        null,
                        decayTarget,
                        slotsUntilNextPeak < Integer.MAX_VALUE ? slotsUntilNextPeak : null,
                        slotsSince(slotTime, previousPeakWindow.endAt()),
                        "PREVIOUS_PEAK_DECAY",
                        recentOrderStatistic,
                        resolvePostPeakDecreaseStep(recentOrderStatistic)
                );
            }
        }

        return PeakBidPhaseResolution.rule(
                peakLookupResult,
                PeakBidPhase.LOW_BASELINE_HOLD,
                previousPeakWindow,
                nextPeakWindow,
                lowBaselineResolution,
                null,
                lowPeakBaselineBid,
                slotsUntilNextPeak < Integer.MAX_VALUE ? slotsUntilNextPeak : null,
                slotsSince(slotTime, previousPeakWindow == null ? null : previousPeakWindow.endAt()),
                "LOW_BASELINE",
                recentOrderStatistic,
                BID_STEP
        );
    }

    private PeakEntryBidResolution resolveInheritedPeakEntryBid(
            List<MtPromotionBidRecord> successHistory,
            String peakWindowKey,
            String strategyConfigHash
    ) {
        BigDecimal inherited = resolvePeakEntryBidFromHistory(successHistory, peakWindowKey, strategyConfigHash);
        return inherited == null ? null : new PeakEntryBidResolution(inherited, BID_SOURCE_INHERITED, null);
    }

    private PeakBidPhaseResolution enrichPeakPhaseWithDynamicInitialBid(
            PeakBidPhaseResolution phaseResolution,
            BigDecimal initialBidAmount,
            PeerBidPriceOpenApiResult peerBidResult
    ) {
        if (phaseResolution == null
                || phaseResolution.phase() != PeakBidPhase.PEAK
                || phaseResolution.peakEntryBidAmount() != null
                || initialBidAmount == null) {
            return phaseResolution;
        }
        String source = peerBidResult != null && peerBidResult.isSuccess() ? BID_SOURCE_PEER_AVG : BID_SOURCE_DYNAMIC_INITIAL;
        return phaseResolution.withPeakEntry(new PeakEntryBidResolution(initialBidAmount, source, peerBidResult));
    }

    private BigDecimal resolveDecisionBaselineBidAmount(BigDecimal initialBidAmount, PeakBidPhaseResolution phaseResolution) {
        if (phaseResolution != null && phaseResolution.lowPeakBaselineBidAmount() != null) {
            return phaseResolution.lowPeakBaselineBidAmount();
        }
        return initialBidAmount;
    }

    private PeerBidPriceOpenApiResult choosePeerBidResult(
            PeerBidPriceOpenApiResult initialPeerBidResult,
            PeakBidPhaseResolution phaseResolution
    ) {
        if (phaseResolution != null && phaseResolution.peerBidResult() != null) {
            return phaseResolution.peerBidResult();
        }
        return initialPeerBidResult;
    }

    private List<PeakWindowSlot> resolvePeakWindowSlots(ShopPeakWindowProfileLookupResult lookupResult, LocalDateTime slotTime) {
        if (lookupResult == null || lookupResult.getPeakWindows() == null || slotTime == null) {
            return List.of();
        }
        return lookupResult.getPeakWindows().stream()
                .map(window -> toPeakWindowSlot(window, lookupResult, slotTime))
                .filter(window -> window != null)
                .toList();
    }

    private PeakWindowSlot toPeakWindowSlot(
            com.gogoga.backend.service.promotion.ShopPeakWindow window,
            ShopPeakWindowProfileLookupResult lookupResult,
            LocalDateTime slotTime
    ) {
        if (window == null || window.getStartHour() == null || window.getEndHour() == null || slotTime == null) {
            return null;
        }
        LocalDate date = slotTime.toLocalDate();
        LocalDateTime startAt = date.atTime(normalizeHour(window.getStartHour()), 0);
        LocalDateTime endAt = window.getEndHour() >= 24
                ? date.plusDays(1L).atStartOfDay()
                : date.atTime(normalizeHour(window.getEndHour()), 0);
        String displayText = window.toDisplayText();
        String dayType = lookupResult.getProfileDayType() == null ? null : lookupResult.getProfileDayType().name();
        String generatedAt = lookupResult.getGeneratedAt() == null ? "unknown" : lookupResult.getGeneratedAt().toString();
        String windowKey = (dayType == null ? "UNKNOWN" : dayType) + ":" + displayText + ":" + generatedAt;
        return new PeakWindowSlot(windowKey, displayText, startAt, endAt);
    }

    private int normalizeHour(Integer hour) {
        if (hour == null) {
            return 0;
        }
        return Math.max(0, Math.min(hour, 23));
    }

    private PeakWindowSlot findCurrentPeakWindow(List<PeakWindowSlot> windows, LocalDateTime slotTime) {
        if (windows == null || slotTime == null) {
            return null;
        }
        for (PeakWindowSlot window : windows) {
            if (!slotTime.isBefore(window.startAt()) && slotTime.isBefore(window.endAt())) {
                return window;
            }
        }
        return null;
    }

    private PeakWindowSlot findWindowByDisplayText(List<PeakWindowSlot> windows, String displayText) {
        if (windows == null || StringUtils.isBlank(displayText)) {
            return null;
        }
        for (PeakWindowSlot window : windows) {
            if (StringUtils.equals(window.displayText(), displayText)) {
                return window;
            }
        }
        return null;
    }

    private PeakWindowSlot findPreviousPeakWindow(List<PeakWindowSlot> windows, LocalDateTime slotTime) {
        if (windows == null || slotTime == null) {
            return null;
        }
        PeakWindowSlot previous = null;
        for (PeakWindowSlot window : windows) {
            if (!window.endAt().isAfter(slotTime)) {
                previous = window;
            }
        }
        return previous;
    }

    private PeakWindowSlot findNextPeakWindow(List<PeakWindowSlot> windows, LocalDateTime slotTime) {
        if (windows == null || slotTime == null) {
            return null;
        }
        for (PeakWindowSlot window : windows) {
            if (window.startAt().isAfter(slotTime)) {
                return window;
            }
        }
        return null;
    }

    private BigDecimal calculatePrePeakTargetBid(
            BigDecimal lowPeakBaselineBid,
            int slotsUntilPeak,
            List<MtPromotionBidRecord> successHistory,
            String nextPeakWindowKey,
            String strategyConfigHash,
            LocalDateTime slotTime
    ) {
        if (lowPeakBaselineBid == null || slotsUntilPeak < 1 || slotsUntilPeak > PRE_PEAK_WARMUP_SLOTS) {
            return null;
        }
        BigDecimal previousWarmupBid = resolvePreviousPrePeakWarmupBid(
                successHistory,
                nextPeakWindowKey,
                strategyConfigHash,
                slotTime
        );
        BigDecimal target = (previousWarmupBid == null ? lowPeakBaselineBid : previousWarmupBid).add(BID_STEP);
        BigDecimal maxWarmupBid = lowPeakBaselineBid.add(BID_STEP.multiply(BigDecimal.valueOf(PRE_PEAK_WARMUP_SLOTS)));
        if (target.compareTo(maxWarmupBid) > 0) {
            target = maxWarmupBid;
        }
        return normalizeBaselineBidAmount(target);
    }

    private BigDecimal resolvePreviousPrePeakWarmupBid(
            List<MtPromotionBidRecord> successHistory,
            String nextPeakWindowKey,
            String strategyConfigHash,
            LocalDateTime slotTime
    ) {
        if (successHistory == null || successHistory.isEmpty()
                || StringUtils.isBlank(nextPeakWindowKey)
                || slotTime == null) {
            return null;
        }
        LocalDateTime previousSlotTime = slotTime.minusMinutes(SLOT_MINUTES);
        for (MtPromotionBidRecord history : successHistory) {
            if (history == null
                    || history.getSlotTime() == null
                    || !history.getSlotTime().isEqual(previousSlotTime)
                    || StringUtils.isBlank(history.getDecisionDetailJson())) {
                continue;
            }
            PromotionDecisionDetail detail = JsonUtil.toBean(history.getDecisionDetailJson(), PromotionDecisionDetail.class);
            if (detail == null
                    || !StringUtils.equals(detail.getPeakBidPhase(), PeakBidPhase.PRE_PEAK_RAMP_UP.name())
                    || !StringUtils.equals(detail.getNextPeakWindowKey(), nextPeakWindowKey)
                    || !sameStrategyConfigHash(detail, strategyConfigHash)) {
                continue;
            }
            BigDecimal previousWarmupBid = normalizeBaselineBidAmount(detail.getPeakRampTargetBidAmount());
            if (previousWarmupBid != null) {
                return previousWarmupBid;
            }
            return normalizeBaselineBidAmount(history.getNextBidAmount());
        }
        return null;
    }

    private BigDecimal calculatePostPeakDecayTarget(
            BigDecimal previousBid,
            BigDecimal lowPeakBaselineBid,
            MtPromotionRecentOrderStatistic recentOrderStatistic
    ) {
        BigDecimal floor = normalizeBaselineBidAmount(lowPeakBaselineBid == null ? DEFAULT_INITIAL_BID : lowPeakBaselineBid);
        if (previousBid == null) {
            return floor;
        }
        BigDecimal target = normalizeMoney(previousBid).subtract(resolvePostPeakDecreaseStep(recentOrderStatistic));
        return target.compareTo(floor) < 0 ? floor : normalizeBaselineBidAmount(target);
    }

    private BigDecimal resolvePostPeakDecreaseStep(MtPromotionRecentOrderStatistic recentOrderStatistic) {
        if (isHighVolumeStatistic(recentOrderStatistic)) {
            return FAST_DECREASE_BID_STEP;
        }
        return BID_STEP;
    }

    private boolean isHighVolumeStatistic(MtPromotionRecentOrderStatistic recentOrderStatistic) {
        return isTrustedRecentOrderStatistic(recentOrderStatistic)
                && recentOrderStatistic.getAverageDailyOrderCount() != null
                && recentOrderStatistic.getAverageDailyOrderCount().compareTo(HIGH_VOLUME_AVERAGE_ORDER_THRESHOLD) >= 0;
    }

    private BigDecimal maxBid(BigDecimal first, BigDecimal second, BigDecimal third) {
        BigDecimal result = first == null ? second : first;
        if (second != null && (result == null || second.compareTo(result) > 0)) {
            result = second;
        }
        if (third != null && (result == null || third.compareTo(result) > 0)) {
            result = third;
        }
        return normalizeBaselineBidAmount(result);
    }

    private int slotsUntil(LocalDateTime slotTime, LocalDateTime targetTime) {
        if (slotTime == null || targetTime == null || !targetTime.isAfter(slotTime)) {
            return Integer.MAX_VALUE;
        }
        long minutes = ChronoUnit.MINUTES.between(slotTime, targetTime);
        return (int) Math.ceil((double) minutes / SLOT_MINUTES);
    }

    private Integer slotsSince(LocalDateTime slotTime, LocalDateTime targetTime) {
        if (slotTime == null || targetTime == null || slotTime.isBefore(targetTime)) {
            return null;
        }
        long minutes = ChronoUnit.MINUTES.between(targetTime, slotTime);
        return (int) (minutes / SLOT_MINUTES);
    }

    private BigDecimal resolveLatestSuccessBidFromHistory(List<MtPromotionBidRecord> successHistory) {
        if (successHistory == null || successHistory.isEmpty() || successHistory.get(0) == null) {
            return null;
        }
        return normalizeMoney(successHistory.get(0).getNextBidAmount());
    }

    private boolean shouldRefreshLowPeakBaselineBid(
            MtPromotionSettingService.StrategyConfig strategyConfig,
            ShopPeakWindowProfileLookupResult peakLookupResult
    ) {
        return shouldUseLowPeakBaseline(strategyConfig, peakLookupResult);
    }

    private BigDecimal defaultIfNull(BigDecimal value, BigDecimal fallbackValue) {
        return value == null ? fallbackValue : value;
    }

    private boolean shouldUseCurrentPlanBidAsInitialBid(
            PromotionTimeWindowResult previousTimeWindowResult,
            PromotionExecutionWindow executionWindow
    ) {
        return previousTimeWindowResult != null
                && previousTimeWindowResult.isExecutable()
                && sameWindow(previousTimeWindowResult.getExecutionWindow(), executionWindow);
    }

    private BigDecimal resolveCurrentPlanBidAmount(
            MtPromotionAutoBidCandidate candidate,
            LocalDateTime slotTime,
            PromotionExecutionWindow executionWindow,
            PromotionPlanStatusOpenApiResult remoteStatusResult
    ) {
        if (remoteStatusResult == null || !remoteStatusResult.isSuccess()) {
            log.warn("中途开启推广时读取当前计划出价失败，回退初始出价逻辑，shopId={}, poiId={}, slotTime={}, windowKey={}, errorMsg={}",
                    candidate == null ? null : candidate.getShopId(),
                    candidate == null ? null : candidate.getPoiId(),
                    slotTime,
                    executionWindow == null ? null : executionWindow.getWindowKey(),
                    remoteStatusResult == null ? null : remoteStatusResult.getErrorMsg());
            return null;
        }
        Integer currentBid = remoteStatusResult.getBid();
        if (currentBid == null || currentBid <= 0) {
            log.warn("中途开启推广时当前计划出价为空，回退初始出价逻辑，shopId={}, poiId={}, slotTime={}, windowKey={}, bid={}",
                    candidate == null ? null : candidate.getShopId(),
                    candidate == null ? null : candidate.getPoiId(),
                    slotTime,
                    executionWindow == null ? null : executionWindow.getWindowKey(),
                    currentBid);
            return null;
        }
        BigDecimal currentPlanBidAmount = BigDecimal.valueOf(currentBid.longValue(), 2);
        currentPlanBidAmount = normalizeBidAmountWithFloor(currentPlanBidAmount);
        log.info("中途开启推广时使用当前计划出价作为初始出价，shopId={}, poiId={}, slotTime={}, windowKey={}, currentBidAmount={}",
                candidate == null ? null : candidate.getShopId(),
                candidate == null ? null : candidate.getPoiId(),
                slotTime,
                executionWindow == null ? null : executionWindow.getWindowKey(),
                currentPlanBidAmount);
        return currentPlanBidAmount;
    }

    private Integer toBidCents(BigDecimal nextBidAmount) {
        BigDecimal normalizedBidAmount = normalizeBidAmountWithFloor(nextBidAmount);
        if (normalizedBidAmount == null || normalizedBidAmount.signum() <= 0) {
            return null;
        }
        return normalizedBidAmount.movePointRight(2).intValueExact();
    }

    private BigDecimal clampBidAmountForSync(
            BigDecimal nextBidAmount,
            MtPromotionAutoBidCandidate candidate,
            LocalDateTime slotTime,
            PromotionExecutionWindow executionWindow,
            BigDecimal effectiveMaxBidAmount
    ) {
        if (nextBidAmount == null) {
            return null;
        }
        BigDecimal maxBidAmount = normalizeEffectiveMaxBidAmount(effectiveMaxBidAmount);
        BigDecimal normalizedBidAmount = normalizeBidAmountWithFloor(nextBidAmount);
        if (normalizedBidAmount.compareTo(maxBidAmount) <= 0) {
            return normalizedBidAmount;
        }
        log.warn("同步美团出价触发上限保护，shopId={}, poiId={}, slotTime={}, windowKey={}, originalBid={}, maxBid={}",
                candidate == null ? null : candidate.getShopId(),
                candidate == null ? null : candidate.getPoiId(),
                slotTime,
                executionWindow == null ? null : executionWindow.getWindowKey(),
                normalizedBidAmount,
                maxBidAmount);
        return maxBidAmount;
    }

    private void enforceUserBidRangeLimit(
            PromotionDecisionResult decisionResult,
            MtPromotionSettingService.StrategyConfig strategyConfig,
            BigDecimal effectiveMaxBidAmount
    ) {
        if (decisionResult == null || decisionResult.getNextBidAmount() == null) {
            return;
        }
        BigDecimal bidMin = normalizeUserBidMinAmount(strategyConfig);
        BigDecimal bidMax = normalizeUserBidMaxAmount(strategyConfig);
        BigDecimal effectiveMax = normalizeEffectiveMaxBidAmount(effectiveMaxBidAmount);
        BigDecimal effectiveUpperBound = bidMax.compareTo(effectiveMax) < 0 ? bidMax : effectiveMax;
        BigDecimal originalNextBid = normalizeBidAmountWithFloor(decisionResult.getNextBidAmount());
        BigDecimal adjustedNextBid = originalNextBid;
        if (adjustedNextBid.compareTo(effectiveUpperBound) > 0) {
            adjustedNextBid = effectiveUpperBound;
        } else if (bidMin.compareTo(effectiveUpperBound) <= 0 && adjustedNextBid.compareTo(bidMin) < 0) {
            adjustedNextBid = bidMin;
        }
        decisionResult.setNextBidAmount(adjustedNextBid);
        if (adjustedNextBid.compareTo(originalNextBid) != 0) {
            adjustDecisionActionAfterBidClamp(decisionResult, adjustedNextBid);
        }
    }

    private void adjustDecisionActionAfterBidClamp(PromotionDecisionResult decisionResult, BigDecimal adjustedNextBid) {
        BigDecimal previousBid = normalizeBidAmountWithFloor(decisionResult.getPreviousBidAmount());
        if (previousBid == null) {
            return;
        }
        int comparison = adjustedNextBid.compareTo(previousBid);
        if (comparison > 0) {
            decisionResult.setDecisionAction(PromotionDecisionEngine.ACTION_INCREASE);
            return;
        }
        if (comparison < 0) {
            decisionResult.setDecisionAction(PromotionDecisionEngine.ACTION_DECREASE);
            return;
        }
        decisionResult.setDecisionAction(PromotionDecisionEngine.ACTION_KEEP);
    }

    private BigDecimal normalizeUserBidMinAmount(MtPromotionSettingService.StrategyConfig strategyConfig) {
        BigDecimal bidMin = normalizeBidAmountWithFloor(strategyConfig == null ? null : strategyConfig.getBidMin());
        if (bidMin == null || bidMin.compareTo(MtPromotionSettingService.DEFAULT_BID_MIN) < 0) {
            return MtPromotionSettingService.DEFAULT_BID_MIN;
        }
        if (bidMin.compareTo(MtPromotionSettingService.DEFAULT_BID_MAX) > 0) {
            return MtPromotionSettingService.DEFAULT_BID_MAX;
        }
        return bidMin;
    }

    private BigDecimal normalizeUserBidMaxAmount(MtPromotionSettingService.StrategyConfig strategyConfig) {
        BigDecimal bidMax = normalizeBidAmountWithFloor(strategyConfig == null ? null : strategyConfig.getBidMax());
        if (bidMax == null || bidMax.compareTo(MtPromotionSettingService.DEFAULT_BID_MIN) < 0) {
            return MtPromotionSettingService.DEFAULT_BID_MAX;
        }
        if (bidMax.compareTo(MtPromotionSettingService.DEFAULT_BID_MAX) > 0) {
            return MtPromotionSettingService.DEFAULT_BID_MAX;
        }
        return bidMax;
    }

    private void enforceMaxBidLimit(PromotionDecisionResult decisionResult, BigDecimal effectiveMaxBidAmount) {
        if (decisionResult == null || decisionResult.getNextBidAmount() == null) {
            return;
        }
        BigDecimal maxBidAmount = normalizeEffectiveMaxBidAmount(effectiveMaxBidAmount);
        BigDecimal normalizedNextBid = normalizeBidAmountWithFloor(decisionResult.getNextBidAmount());
        if (normalizedNextBid.compareTo(maxBidAmount) <= 0) {
            decisionResult.setNextBidAmount(normalizedNextBid);
            return;
        }
        BigDecimal previousBid = normalizeBidAmountWithFloor(decisionResult.getPreviousBidAmount());
        decisionResult.setNextBidAmount(maxBidAmount);
        if (previousBid == null) {
            return;
        }
        int comparison = maxBidAmount.compareTo(previousBid);
        if (comparison > 0) {
            decisionResult.setDecisionAction(PromotionDecisionEngine.ACTION_INCREASE);
            return;
        }
        if (comparison < 0) {
            decisionResult.setDecisionAction(PromotionDecisionEngine.ACTION_DECREASE);
            return;
        }
        if (StringUtils.equals(decisionResult.getDecisionAction(), PromotionDecisionEngine.ACTION_INCREASE)) {
            decisionResult.setDecisionAction(PromotionDecisionEngine.ACTION_KEEP);
            decisionResult.setDecisionReason(DECISION_REASON_MAX_BID_REACHED_KEEP);
        }
    }

    private BigDecimal resolveEffectiveMaxBidAmount(MtPromotionAutoBidCandidate candidate, LocalDateTime slotTime) {
        if (mtPromotionBidConstraintService == null) {
            return MAX_SYNC_BID_AMOUNT;
        }
        return mtPromotionBidConstraintService.resolveEffectiveMaxBidAmount(
                candidate == null ? null : candidate.getShopId(),
                MAX_SYNC_BID_AMOUNT,
                slotTime
        );
    }

    private BigDecimal normalizeEffectiveMaxBidAmount(BigDecimal effectiveMaxBidAmount) {
        BigDecimal normalizedMaxBidAmount = normalizeBidAmountWithFloor(effectiveMaxBidAmount);
        if (normalizedMaxBidAmount == null || normalizedMaxBidAmount.signum() <= 0) {
            return MAX_SYNC_BID_AMOUNT;
        }
        return normalizedMaxBidAmount.compareTo(MAX_SYNC_BID_AMOUNT) < 0 ? normalizedMaxBidAmount : MAX_SYNC_BID_AMOUNT;
    }

    private BigDecimal minBidAmount(BigDecimal left, BigDecimal right) {
        BigDecimal normalizedLeft = normalizeEffectiveMaxBidAmount(left);
        BigDecimal normalizedRight = normalizeEffectiveMaxBidAmount(right);
        return normalizedLeft.compareTo(normalizedRight) < 0 ? normalizedLeft : normalizedRight;
    }

    private String buildBidUpdateRetryPayloadJson(String firstPayloadJson, String retryPayloadJson, BigDecimal observedMaxBidAmount) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (StringUtils.isNotBlank(firstPayloadJson)) {
            payload.put("firstAttempt", JsonUtil.toMap(firstPayloadJson));
        }
        if (StringUtils.isNotBlank(retryPayloadJson)) {
            payload.put("retryAttempt", JsonUtil.toMap(retryPayloadJson));
        }
        payload.put("observedMaxBidAmount", observedMaxBidAmount == null ? null : normalizeEffectiveMaxBidAmount(observedMaxBidAmount));
        return JsonUtil.toJson(payload);
    }

    private record BidLimitRetryResult(PromotionBidOpenApiResult updateResult, BigDecimal observedMaxBidAmount) {
    }

    private boolean insertRecord(MtPromotionBidRecord record, MtPromotionAutoBidCandidate candidate) {
        boolean manualRecord = isManualRecord(record);
        boolean fixedMarkdownRecord = manualRecord || shouldUseFixedMarkdownWithoutLlm(record);
        if (manualRecord) {
            enrichRecordWithFixedMarkdown(record, "manual");
        } else if (fixedMarkdownRecord) {
            enrichRecordWithFixedMarkdown(record, "settings-save");
        } else if (!isDatabaseAsyncTaskEnabled()) {
            enrichRecordWithMarkdown(record, candidate);
        } else if (record != null) {
            record.setLogMarkdownStatus(MtPromotionLogMarkdownStatusEnum.PENDING.name());
        }
        try {
            mtPromotionBidRecordMapper.insert(record);
            if (!fixedMarkdownRecord && isDatabaseAsyncTaskEnabled()) {
                enqueuePromotionLogMarkdownTask(record, candidate);
            }
            return true;
        } catch (DuplicateKeyException e) {
            log.warn("自动调价记录重复，shopId={}, bizDate={}, slotTime={}", record.getShopId(), record.getBizDate(), record.getSlotTime());
            return false;
        }
    }

    private boolean isManualRecord(MtPromotionBidRecord record) {
        return record != null && RECORD_SOURCE_MANUAL.equals(record.getRecordSource());
    }

    private boolean shouldUseFixedMarkdownWithoutLlm(MtPromotionBidRecord record) {
        return record != null
                && DECISION_REASON_SETTINGS_SAVE_OUT_OF_WINDOW_BID_RESET.equals(record.getDecisionReason());
    }

    private void enrichRecordWithFixedMarkdown(MtPromotionBidRecord record, String source) {
        if (record == null) {
            return;
        }
        if (promotionLogMessageBuilder != null) {
            record.setMarkdownMessage(promotionLogMessageBuilder.buildFallbackMarkdown(record));
        }
        record.setLogMarkdownStatus(MtPromotionLogMarkdownStatusEnum.READY.name());
        PromotionDecisionDetail detail = JsonUtil.toBean(record.getDecisionDetailJson(), PromotionDecisionDetail.class);
        if (detail == null) {
            detail = new PromotionDecisionDetail();
        }
        detail.setLogMarkdownSource(source);
        detail.setLogLlmFailureSummary(null);
        detail.setLogLlmRawResponseSummary(null);
        detail.setLogLlmPromptTokens(null);
        detail.setLogLlmCompletionTokens(null);
        detail.setLogLlmTotalTokens(null);
        record.setDecisionDetailJson(JsonUtil.toJson(detail));
    }

    private boolean enqueueAutoBidTask(MtPromotionAutoBidCandidate candidate, LocalDateTime slotTime) {
        if (candidate == null || candidate.getShopId() == null || slotTime == null || mtAsyncTaskService == null) {
            return false;
        }
        MtPromotionAutoBidTaskPayload payload = new MtPromotionAutoBidTaskPayload();
        payload.setShopId(candidate.getShopId());
        payload.setSlotTime(slotTime);
        payload.setCandidate(candidate);
        MtAsyncTaskEnqueueResult enqueueResult = mtAsyncTaskService.enqueueTask(
                MtAsyncTaskTypeEnum.PROMOTION_AUTO_BID,
                buildAutoBidTaskBizKey(candidate.getShopId(), slotTime),
                String.valueOf(candidate.getShopId()),
                payload,
                slotTime,
                resolveAsyncTaskConfig().getAutoBid().getMaxRetryCount()
        );
        return enqueueResult != null && enqueueResult.isCreated();
    }

    private void enqueuePromotionLogMarkdownTask(MtPromotionBidRecord record, MtPromotionAutoBidCandidate candidate) {
        if (record == null || record.getId() == null || mtAsyncTaskService == null) {
            return;
        }
        String bizKey = buildPromotionLogTaskBizKey(record.getId());
        MtPromotionLogMarkdownTaskPayload payload = new MtPromotionLogMarkdownTaskPayload();
        payload.setRecordId(record.getId());
        payload.setShopId(record.getShopId());
        payload.setAccessToken(candidate == null ? null : candidate.getAccessToken());
        MtAsyncTaskEnqueueResult enqueueResult = mtAsyncTaskService.enqueueTask(
                MtAsyncTaskTypeEnum.PROMOTION_LOG_MARKDOWN,
                bizKey,
                record.getShopId() == null ? null : String.valueOf(record.getShopId()),
                payload,
                LocalDateTime.now(),
                resolveAsyncTaskConfig().getLogMarkdown().getMaxRetryCount()
        );
        mtPromotionBidRecordMapper.updateLogTaskMetadata(
                record.getId(),
                enqueueResult == null ? null : enqueueResult.getTaskId(),
                MtPromotionLogMarkdownStatusEnum.PENDING.name(),
                LocalDateTime.now()
        );
    }

    private String buildAutoBidTaskBizKey(Long shopId, LocalDateTime slotTime) {
        return shopId + "#" + slotTime;
    }

    private String buildPromotionLogTaskBizKey(Long recordId) {
        return "record#" + recordId;
    }

    private boolean isDatabaseAsyncTaskEnabled() {
        return mtProperties != null
                && mtProperties.getPromotion() != null
                && mtProperties.getPromotion().getAsyncTask() != null
                && mtProperties.getPromotion().getAsyncTask().isEnabled();
    }

    private MtProperties.AsyncTask resolveAsyncTaskConfig() {
        return mtProperties == null || mtProperties.getPromotion() == null || mtProperties.getPromotion().getAsyncTask() == null
                ? new MtProperties.AsyncTask()
                : mtProperties.getPromotion().getAsyncTask();
    }

    private int getCurrentPowerBalance(Long shopId) {
        MtPowerAccount account = mtPowerAccountService.getOrCreateAccount(shopId);
        return account == null || account.getBalance() == null ? 0 : account.getBalance();
    }

    private void persistPowerNoticeRecord(
            MtPromotionAutoBidCandidate candidate,
            LocalDateTime noticeTime,
            String reason
    ) {
        MtPromotionBidRecord record = buildBaseRecord(candidate, noticeTime, null, null);
        record.setDecisionAction(PromotionDecisionEngine.ACTION_KEEP);
        record.setDecisionReason(reason);
        record.setExecuteStatus(STATUS_SKIPPED);
        insertRecord(record, candidate);
    }

    private void persistPromotionBalanceNoticeRecord(
            MtPromotionAutoBidCandidate candidate,
            LocalDateTime noticeTime,
            PromotionMetricsSnapshot metricsSnapshot,
            String reason
    ) {
        MtPromotionBidRecord record = buildBaseRecord(candidate, noticeTime, null, metricsSnapshot);
        record.setDecisionAction(PromotionDecisionEngine.ACTION_KEEP);
        record.setDecisionReason(reason);
        record.setExecuteStatus(STATUS_SKIPPED);
        insertRecord(record, candidate);
    }

    private MtPromotionSettingService.StrategyConfig readStrategyConfig(String strategyConfigJson) {
        try {
            MtPromotionSettingService.StrategyConfig strategyConfig = objectMapper.readValue(
                    strategyConfigJson,
                    MtPromotionSettingService.StrategyConfig.class
            );
            if (strategyConfig == null || StringUtils.isBlank(strategyConfig.getTimeMode())) {
                throw new IllegalArgumentException("推广策略缺少时段配置");
            }
            return mtPromotionSettingService.normalizeForExecution(strategyConfig);
        } catch (Exception e) {
            throw new IllegalArgumentException("推广策略解析失败", e);
        }
    }

    private LocalDateTime resolveCurrentSlotTime(LocalDateTime now) {
        int minute = now.getMinute();
        int normalizedMinute = minute - minute % 10;
        return now.withMinute(normalizedMinute).withSecond(0).withNano(0);
    }

    private String buildRequestNo(Long shopId, LocalDateTime slotTime) {
        return "PB" + shopId + slotTime.toLocalDate() + slotTime.toLocalTime().toString().replace(":", "") + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 8);
    }

    private BigDecimal normalizeMoney(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal normalizeRatio(BigDecimal value) {
        return value == null ? null : value.setScale(6, RoundingMode.HALF_UP);
    }

    private void enrichRecordWithMarkdown(MtPromotionBidRecord record, MtPromotionAutoBidCandidate candidate) {
        if (record == null) {
            return;
        }
        PromotionDecisionDetail detail = JsonUtil.toBean(record.getDecisionDetailJson(), PromotionDecisionDetail.class);
        if (detail == null) {
            detail = new PromotionDecisionDetail();
        }
        if (!STATUS_SUCCESS.equals(record.getExecuteStatus())) {
            if (promotionLogMessageBuilder != null) {
                record.setMarkdownMessage(promotionLogMessageBuilder.buildFallbackMarkdown(record));
            }
            record.setLogMarkdownStatus(MtPromotionLogMarkdownStatusEnum.FALLBACK.name());
            detail.setLogMarkdownSource("fallback");
            record.setDecisionDetailJson(JsonUtil.toJson(detail));
            return;
        }
        if (candidate != null && mtPromotionAdDataService != null) {
            mtPromotionAdDataService.ensureRecentAdDataBackfillBeforeLogGeneration(
                    record.getShopId(),
                    candidate.getAccessToken()
            );
        }
        if (promotionLogMarkdownService == null) {
            return;
        }

        PromotionLogMarkdownResult logMarkdownResult = promotionLogMarkdownService.generate(record);
        record.setMarkdownMessage(logMarkdownResult.getMarkdownMessage());
        record.setLogMarkdownStatus(logMarkdownResult.isFallback()
                ? MtPromotionLogMarkdownStatusEnum.FALLBACK.name()
                : MtPromotionLogMarkdownStatusEnum.READY.name());
        detail.setLogMarkdownSource(logMarkdownResult.isFallback() ? "fallback" : "llm");
        detail.setLogLlmFailureSummary(logMarkdownResult.getFailureSummary());
        detail.setLogLlmRawResponseSummary(logMarkdownResult.getRawResponseSummary());
        detail.setLogLlmPromptTokens(logMarkdownResult.getPromptTokens());
        detail.setLogLlmCompletionTokens(logMarkdownResult.getCompletionTokens());
        detail.setLogLlmTotalTokens(logMarkdownResult.getTotalTokens());
        record.setDecisionDetailJson(JsonUtil.toJson(detail));

        if (logMarkdownResult.getTotalTokens() == null || logMarkdownResult.getTotalTokens() <= 0) {
            return;
        }
        try {
            promotionPowerConsumeService.consumeForLogGeneration(
                    record.getShopId(),
                    record.getSlotTime(),
                    logMarkdownResult.getPromptTokens(),
                    logMarkdownResult.getCompletionTokens()
            );
        } catch (Exception e) {
            detail.setLogPowerConsumeFailureSummary(StringUtils.left(e.getMessage(), 300));
            record.setDecisionDetailJson(JsonUtil.toJson(detail));
            log.warn("推广日志算力扣减失败，shopId={}, slotTime={}, reason={}",
                    record.getShopId(), record.getSlotTime(), e.getMessage());
        }
    }

    private String mergeDecisionDetailWithMetricsContext(
            String baseDecisionDetailJson,
            PromotionMetricsSnapshot metricsSnapshot,
            PromotionExecutionWindow executionWindow
    ) {
        if (metricsSnapshot == null && StringUtils.isBlank(baseDecisionDetailJson)) {
            return null;
        }
        PromotionDecisionDetail detail = JsonUtil.toBean(baseDecisionDetailJson, PromotionDecisionDetail.class);
        if (detail == null) {
            detail = new PromotionDecisionDetail();
        }
        if (metricsSnapshot != null) {
            detail.setMetricsBaselineType(metricsSnapshot.getBaselineType());
            detail.setMetricsEffectiveWindowStartAt(metricsSnapshot.getEffectiveWindowStartAt());
            detail.setMetricsBusinessWindowStartAt(
                    metricsSnapshot.getBusinessWindowStartAt() != null
                            ? metricsSnapshot.getBusinessWindowStartAt()
                            : executionWindow == null ? null : executionWindow.getWindowStartAt()
            );
            detail.setTemporaryRecoveryWindow(metricsSnapshot.isTemporaryRecoveryWindow());
            detail.setRecoveryWindowInitialization(metricsSnapshot.isRecoveryWindowInitializationRequired());
        }
        return JsonUtil.toJson(detail);
    }

    private String mergeDecisionDetailWithBaselineContext(
            String baseDecisionDetailJson,
            BigDecimal windowBaselineBidAmount,
            String pricingBranch
    ) {
        PromotionDecisionDetail detail = JsonUtil.toBean(baseDecisionDetailJson, PromotionDecisionDetail.class);
        if (detail == null) {
            detail = new PromotionDecisionDetail();
        }
        detail.setWindowBaselineBidAmount(normalizeBaselineBidAmount(windowBaselineBidAmount));
        detail.setPricingBranch(pricingBranch);
        return JsonUtil.toJson(detail);
    }

    private BigDecimal normalizeBaselineBidAmount(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return normalizeBidAmountWithFloor(value);
    }

    private BigDecimal normalizeBidAmountWithFloor(BigDecimal value) {
        if (value == null) {
            return null;
        }
        BigDecimal normalized = normalizeMoney(value);
        BigDecimal floored = normalized.divide(BID_UNIT, 0, RoundingMode.DOWN)
                .multiply(BID_UNIT)
                .setScale(2, RoundingMode.UNNECESSARY);
        return floored.compareTo(WINDOW_END_RESET_BID) < 0 ? WINDOW_END_RESET_BID : floored;
    }

    private String mergeDecisionDetailWithPeerBidContext(
            String baseDecisionDetailJson,
            PeerBidPriceOpenApiResult peerBidResult
    ) {
        if (peerBidResult == null && StringUtils.isBlank(baseDecisionDetailJson)) {
            return null;
        }
        PromotionDecisionDetail detail = JsonUtil.toBean(baseDecisionDetailJson, PromotionDecisionDetail.class);
        if (detail == null) {
            detail = new PromotionDecisionDetail();
        }
        if (peerBidResult != null) {
            detail.setPeerAverageBidAmount(peerBidResult.getAverageBidAmount());
            detail.setPeerAverageBidPayloadJson(peerBidResult.getPayloadJson());
            detail.setPeerAverageBidErrorMsg(peerBidResult.getErrorMsg());
        }
        return JsonUtil.toJson(detail);
    }

    private String mergeDecisionDetailWithFeeRateBranchContext(
            String baseDecisionDetailJson,
            String feeRateAction,
            String feeRateReason
    ) {
        PromotionDecisionDetail detail = JsonUtil.toBean(baseDecisionDetailJson, PromotionDecisionDetail.class);
        if (detail == null) {
            detail = new PromotionDecisionDetail();
        }
        detail.setFeeRateBranchAction(feeRateAction);
        detail.setFeeRateBranchReason(feeRateReason);
        return JsonUtil.toJson(detail);
    }

    private String mergeDecisionDetailWithExposureContext(
            String baseDecisionDetailJson,
            PromotionExposureDecisionResult exposureDecisionResult
    ) {
        if (exposureDecisionResult == null && StringUtils.isBlank(baseDecisionDetailJson)) {
            return null;
        }
        PromotionDecisionDetail detail = JsonUtil.toBean(baseDecisionDetailJson, PromotionDecisionDetail.class);
        if (detail == null) {
            detail = new PromotionDecisionDetail();
        }
        if (exposureDecisionResult != null) {
            detail.setExposureBranchStatus(exposureDecisionResult.getStatus());
            detail.setExposureBranchAction(exposureDecisionResult.getAction());
            detail.setExposureBranchReason(exposureDecisionResult.getReason());
            detail.setExposurePreviousFollowupExposure(exposureDecisionResult.getPreviousFollowupExposure());
            detail.setExposureLatestFollowupExposure(exposureDecisionResult.getLatestFollowupExposure());
            detail.setExposureGrowthRate(exposureDecisionResult.getGrowthRate());
        }
        if (StringUtils.isBlank(detail.getFinalAggregationMode())) {
            detail.setFinalAggregationMode(FINAL_AGGREGATION_MODE_RATE_ONLY);
        }
        if (StringUtils.isBlank(detail.getFinalAggregationSource())) {
            detail.setFinalAggregationSource(FINAL_AGGREGATION_SOURCE_FEE_RATE);
        }
        return JsonUtil.toJson(detail);
    }

    private String mergeDecisionDetailWithPeakWindowContext(
            String baseDecisionDetailJson,
            ShopPeakWindowProfileLookupResult peakLookupResult
    ) {
        if (peakLookupResult == null && StringUtils.isBlank(baseDecisionDetailJson)) {
            return null;
        }
        PromotionDecisionDetail detail = JsonUtil.toBean(baseDecisionDetailJson, PromotionDecisionDetail.class);
        if (detail == null) {
            detail = new PromotionDecisionDetail();
        }
        if (peakLookupResult != null) {
            detail.setPeakWindowDayType(peakLookupResult.getDayType() == null ? null : peakLookupResult.getDayType().name());
            detail.setPeakWindowProfileDayType(peakLookupResult.getProfileDayType() == null ? null : peakLookupResult.getProfileDayType().name());
            detail.setPeakWindowCalendarDayType(peakLookupResult.getCalendarDayType() == null ? null : peakLookupResult.getCalendarDayType().name());
            detail.setPeakWindowHolidayName(peakLookupResult.getHolidayName());
            detail.setPeakWindowMatched(peakLookupResult.isPeakMatched());
            detail.setPeakWindowGatingEnabled(peakLookupResult.isGatingEnabled());
            detail.setPeakWindowMatchedRange(peakLookupResult.getMatchedWindow());
            detail.setPeakWindowProfileStatus(peakLookupResult.getProfileStatus());
            detail.setPeakWindowSampleDayCount(peakLookupResult.getSampleDayCount());
            detail.setPeakWindowSampleOrderCount(peakLookupResult.getSampleOrderCount());
            detail.setPeakWindowUnavailableReason(peakLookupResult.getUnavailableReason());
        }
        return JsonUtil.toJson(detail);
    }

    private String mergeDecisionDetailWithPeakRampContext(
            String baseDecisionDetailJson,
            PeakBidPhaseResolution phaseResolution,
            String strategyConfigHash
    ) {
        if (phaseResolution == null && StringUtils.isBlank(baseDecisionDetailJson)) {
            return null;
        }
        PromotionDecisionDetail detail = JsonUtil.toBean(baseDecisionDetailJson, PromotionDecisionDetail.class);
        if (detail == null) {
            detail = new PromotionDecisionDetail();
        }
        detail.setStrategyConfigHash(strategyConfigHash);
        if (phaseResolution != null) {
            detail.setPeakBidPhase(phaseResolution.phaseName());
            detail.setPeakWindowKey(phaseResolution.currentPeakWindowKey());
            detail.setNextPeakWindowKey(phaseResolution.nextPeakWindowKey());
            detail.setLowPeakBaselineBidAmount(normalizeBaselineBidAmount(phaseResolution.lowPeakBaselineBidAmount()));
            detail.setLowPeakBaselineSource(phaseResolution.lowPeakBaselineSource());
            detail.setPeakEntryBidAmount(normalizeBaselineBidAmount(phaseResolution.peakEntryBidAmount()));
            detail.setPeakEntryBidSource(phaseResolution.peakEntryBidSource());
            detail.setSlotsUntilPeakStart(phaseResolution.slotsUntilPeakStart());
            detail.setSlotsSincePeakEnd(phaseResolution.slotsSincePeakEnd());
            detail.setPeakRampTargetBidAmount(normalizeBaselineBidAmount(phaseResolution.targetBidAmount()));
            detail.setPeakRampReason(phaseResolution.reason());
        }
        return JsonUtil.toJson(detail);
    }

    private String mergeDecisionDetailWithRecentOrderStatisticContext(
            String baseDecisionDetailJson,
            MtPromotionRecentOrderStatistic recentOrderStatistic,
            PeakBidPhaseResolution phaseResolution,
            MtPromotionSettingService.StrategyConfig strategyConfig,
            PromotionMetricsSnapshot metricsSnapshot,
            PromotionDecisionResult decisionResult
    ) {
        if (recentOrderStatistic == null
                && phaseResolution == null
                && metricsSnapshot == null
                && StringUtils.isBlank(baseDecisionDetailJson)) {
            return null;
        }
        PromotionDecisionDetail detail = JsonUtil.toBean(baseDecisionDetailJson, PromotionDecisionDetail.class);
        if (detail == null) {
            detail = new PromotionDecisionDetail();
        }
        if (recentOrderStatistic != null) {
            detail.setRecentOrderStatisticBizDate(recentOrderStatistic.getBizDate());
            detail.setRecentOrderLookbackStartDate(recentOrderStatistic.getLookbackStartDate());
            detail.setRecentOrderLookbackEndDate(recentOrderStatistic.getLookbackEndDate());
            detail.setRecentOrderSampleDates(parseSampleDates(recentOrderStatistic.getSampleDatesJson()));
            detail.setRecentOrderStatisticTrusted(recentOrderStatistic.getTrusted());
            detail.setRecentOrderStatisticUntrustedReason(recentOrderStatistic.getUntrustedReason());
            detail.setRecentOrderTotalOrderCount(recentOrderStatistic.getTotalOrderCount());
            detail.setRecentOrderAverageDailyOrderCount(recentOrderStatistic.getAverageDailyOrderCount());
            detail.setHighVolumeRampDownEligible(isHighVolumeStatistic(recentOrderStatistic));
            detail.setLowPeakOrderCount(recentOrderStatistic.getLowPeakOrderCount());
            detail.setLowPeakOrderShare(recentOrderStatistic.getLowPeakOrderShare());
        }
        if (phaseResolution != null) {
            detail.setPeakRampDecreaseStepAmount(phaseResolution.postPeakDecreaseStepAmount());
            BaselineBidResolution lowPeakBaseline = phaseResolution.lowPeakBaseline();
            if (lowPeakBaseline != null) {
                detail.setLatestHighPeakBidAmount(lowPeakBaseline.latestHighPeakBidAmount());
                detail.setLatestHighPeakBidSource(lowPeakBaseline.latestHighPeakBidSource());
                detail.setLowPeakShareBaselineBidAmount(lowPeakBaseline.lowPeakShareBaselineBidAmount());
                detail.setLowPeakShareBaselineApplied(lowPeakBaseline.lowPeakShareBaselineApplied());
            }
        }
        BigDecimal maxRate = parseRateToDecimal(strategyConfig == null ? null : strategyConfig.getRateMax());
        BigDecimal currentRate = metricsSnapshot == null ? null : metricsSnapshot.getWindowCostRatio();
        boolean ratePriority = strategyConfig != null && StringUtils.equals(strategyConfig.getPriority(), "rate");
        boolean fastCorrectionApplied = decisionResult != null
                && StringUtils.equals(decisionResult.getDecisionReason(), PromotionDecisionEngine.REASON_RATE_PRIORITY_FAST_CORRECTION);
        detail.setRatePriorityCurrentRate(normalizeRatio(currentRate));
        detail.setRatePriorityMaxRate(normalizeRatio(maxRate));
        detail.setRatePriorityFastThreshold(maxRate == null ? null : normalizeRatio(maxRate.add(RATE_PRIORITY_FAST_THRESHOLD_DELTA)));
        detail.setRatePriorityFastCorrectionApplied(fastCorrectionApplied);
        detail.setRatePriorityFastDecreaseStepAmount(fastCorrectionApplied ? FAST_DECREASE_BID_STEP : null);
        detail.setRatePriorityRecoveredToMainAlgorithm(ratePriority
                && maxRate != null
                && currentRate != null
                && currentRate.compareTo(maxRate) <= 0);
        return JsonUtil.toJson(detail);
    }

    private List<LocalDate> parseSampleDates(String sampleDatesJson) {
        if (StringUtils.isBlank(sampleDatesJson)) {
            return Collections.emptyList();
        }
        return JsonUtil.toList(sampleDatesJson, LocalDate.class);
    }

    private String buildStrategyConfigHash(String strategyConfigJson) {
        return MtPromotionSettingHistoryService.strategyConfigHash(strategyConfigJson);
    }

    private String mergeDecisionDetailWithStrategySnapshot(
            String baseDecisionDetailJson,
            String strategyConfigJson
    ) {
        if (StringUtils.isBlank(strategyConfigJson) && StringUtils.isBlank(baseDecisionDetailJson)) {
            return null;
        }
        PromotionDecisionDetail detail = JsonUtil.toBean(baseDecisionDetailJson, PromotionDecisionDetail.class);
        if (detail == null) {
            detail = new PromotionDecisionDetail();
        }
        if (StringUtils.isNotBlank(strategyConfigJson)) {
            detail.setStrategyConfigSnapshotJson(strategyConfigJson);
            detail.setStrategyConfigHash(buildStrategyConfigHash(strategyConfigJson));
        }
        return JsonUtil.toJson(detail);
    }

    private String mergeDecisionDetailWithBidRangeContext(
            String baseDecisionDetailJson,
            MtPromotionSettingService.StrategyConfig strategyConfig,
            BigDecimal effectiveMaxBidAmount,
            BigDecimal bidBeforeUserRangeClamp,
            BigDecimal bidAfterUserRangeClamp
    ) {
        PromotionDecisionDetail detail = JsonUtil.toBean(baseDecisionDetailJson, PromotionDecisionDetail.class);
        if (detail == null) {
            detail = new PromotionDecisionDetail();
        }
        detail.setUserBidMinAmount(normalizeUserBidMinAmount(strategyConfig));
        detail.setUserBidMaxAmount(normalizeUserBidMaxAmount(strategyConfig));
        detail.setEffectiveMaxBidAmount(normalizeEffectiveMaxBidAmount(effectiveMaxBidAmount));
        detail.setBidBeforeUserRangeClamp(normalizeBidAmountWithFloor(bidBeforeUserRangeClamp));
        detail.setBidAfterUserRangeClamp(normalizeBidAmountWithFloor(bidAfterUserRangeClamp));
        return JsonUtil.toJson(detail);
    }

    private boolean hasDailyBudgetExhaustedNotice(Long shopId, LocalDate bizDate) {
        if (shopId == null || bizDate == null) {
            return false;
        }
        return mtPromotionBidRecordMapper.selectLatestByShopIdAndBizDateAndDecisionReason(
                shopId,
                bizDate,
                SKIP_REASON_PROMOTION_DAILY_BUDGET_EXHAUSTED
        ) != null;
    }

    private String resolvePricingBranch(
            MtPromotionSettingService.StrategyConfig strategyConfig,
            ShopPeakWindowProfileLookupResult peakLookupResult,
            PeakBidPhaseResolution peakBidPhaseResolution
    ) {
        if (peakBidPhaseResolution != null && peakBidPhaseResolution.isRulePhase()) {
            return StringUtils.equals(peakBidPhaseResolution.phaseName(), PeakBidPhase.LOW_BASELINE_HOLD.name())
                    ? PRICING_BRANCH_LOW_PEAK_BASELINE
                    : PRICING_BRANCH_SMOOTH_PEAK_RAMP;
        }
        if (shouldUseLowPeakBaseline(strategyConfig, peakLookupResult)) {
            return PRICING_BRANCH_LOW_PEAK_BASELINE;
        }
        return PRICING_BRANCH_PEAK_RULE;
    }

    private ShopPeakWindowProfileLookupResult resolvePeakWindowLookup(
            MtPromotionAutoBidCandidate candidate,
            MtPromotionSettingService.StrategyConfig strategyConfig,
            LocalDateTime slotTime
    ) {
        if (shopPeakWindowProfileService == null
                || candidate == null
                || candidate.getShopId() == null
                || !shouldApplyPeakWindowGating(strategyConfig)) {
            return null;
        }
        ShopPeakWindowProfileLookupResult result = shopPeakWindowProfileService.lookup(candidate.getShopId(), slotTime);
        log.info("自动调价高峰画像判定完成，shopId={}, slotTime={}, dayType={}, gatingEnabled={}, peakMatched={}, matchedWindow={}, unavailableReason={}",
                candidate.getShopId(),
                slotTime,
                result == null || result.getDayType() == null ? null : result.getDayType().name(),
                result != null && result.isGatingEnabled(),
                result != null && result.isPeakMatched(),
                result == null ? null : result.getMatchedWindow(),
                result == null ? null : result.getUnavailableReason());
        return result;
    }

    private MtPromotionRecentOrderStatistic resolveRecentOrderStatistic(
            MtPromotionAutoBidCandidate candidate,
            LocalDateTime slotTime,
            ShopPeakWindowProfileLookupResult peakLookupResult
    ) {
        if (promotionRecentOrderStatisticService == null
                || candidate == null
                || candidate.getShopId() == null
                || slotTime == null) {
            return null;
        }
        return promotionRecentOrderStatisticService.resolve(candidate.getShopId(), slotTime.toLocalDate(), peakLookupResult);
    }

    private PromotionDecisionResult buildDecisionResult(
            MtPromotionSettingService.StrategyConfig strategyConfig,
            PromotionMetricsSnapshot metricsSnapshot,
            List<MtPromotionBidRecord> successHistory,
            LocalDateTime slotTime,
            BigDecimal initialBidAmount,
            ShopPeakWindowProfileLookupResult peakLookupResult,
            PeakBidPhaseResolution peakBidPhaseResolution
    ) {
        if (peakBidPhaseResolution != null && peakBidPhaseResolution.isRulePhase()) {
            return buildPeakRampRuleDecision(successHistory, peakBidPhaseResolution);
        }
        if (shouldUseLowPeakBaseline(strategyConfig, peakLookupResult)) {
            return buildNonPeakKeepDecision(successHistory, initialBidAmount);
        }
        return promotionBidDecisionService.decide(
                strategyConfig,
                metricsSnapshot,
                successHistory,
                slotTime,
                initialBidAmount
        );
    }

    private boolean shouldUseLowPeakBaseline(
            MtPromotionSettingService.StrategyConfig strategyConfig,
            ShopPeakWindowProfileLookupResult peakLookupResult
    ) {
        return shouldApplyPeakWindowGating(strategyConfig)
                && peakLookupResult != null
                && peakLookupResult.isGatingEnabled()
                && !peakLookupResult.isPeakMatched();
    }

    private boolean shouldApplyPeakWindowGating(MtPromotionSettingService.StrategyConfig strategyConfig) {
        return strategyConfig != null && StringUtils.equals(strategyConfig.getTimeMode(), "auto");
    }

    private PromotionDecisionResult buildNonPeakKeepDecision(
            List<MtPromotionBidRecord> successHistory,
            BigDecimal baselineBidAmount
    ) {
        MtPromotionBidRecord latestSuccess = successHistory == null || successHistory.isEmpty() ? null : successHistory.get(0);
        BigDecimal previousBid = latestSuccess == null ? null : normalizeMoney(latestSuccess.getNextBidAmount());
        BigDecimal baselineBid = normalizeBaselineBidAmount(baselineBidAmount == null ? DEFAULT_INITIAL_BID : baselineBidAmount);
        PromotionDecisionResult result = new PromotionDecisionResult();
        result.setPreviousBidAmount(previousBid);
        result.setNextBidAmount(baselineBid);
        result.setDecisionAction(latestSuccess == null ? PromotionDecisionEngine.ACTION_INIT : PromotionDecisionEngine.ACTION_KEEP);
        result.setDecisionReason(DECISION_REASON_LOW_PEAK_BASELINE_BID);
        result.setDecisionSource(PromotionBidDecisionService.DECISION_SOURCE_RULE);
        result.setPowerConsumeMode(PromotionPowerConsumeService.POWER_MODE_RULE);
        PromotionDecisionDetail detail = new PromotionDecisionDetail();
        detail.setDecisionMode(PromotionBidDecisionService.DECISION_MODE_RULE);
        detail.setDecisionSource(PromotionBidDecisionService.DECISION_SOURCE_RULE);
        result.setDecisionDetailJson(JsonUtil.toJson(detail));
        return result;
    }

    private PromotionDecisionResult buildPeakRampRuleDecision(
            List<MtPromotionBidRecord> successHistory,
            PeakBidPhaseResolution phaseResolution
    ) {
        MtPromotionBidRecord latestSuccess = successHistory == null || successHistory.isEmpty() ? null : successHistory.get(0);
        BigDecimal previousBid = latestSuccess == null ? null : normalizeMoney(latestSuccess.getNextBidAmount());
        BigDecimal targetBid = normalizeBaselineBidAmount(phaseResolution == null ? null : phaseResolution.targetBidAmount());
        if (targetBid == null) {
            targetBid = normalizeBaselineBidAmount(DEFAULT_INITIAL_BID);
        }
        PromotionDecisionResult result = new PromotionDecisionResult();
        result.setPreviousBidAmount(previousBid);
        result.setNextBidAmount(targetBid);
        result.setDecisionAction(resolveRuleDecisionAction(previousBid, targetBid));
        result.setDecisionReason(resolvePeakRampDecisionReason(phaseResolution));
        result.setDecisionSource(PromotionBidDecisionService.DECISION_SOURCE_RULE);
        result.setPowerConsumeMode(PromotionPowerConsumeService.POWER_MODE_RULE);
        PromotionDecisionDetail detail = new PromotionDecisionDetail();
        detail.setDecisionMode(PromotionBidDecisionService.DECISION_MODE_RULE);
        detail.setDecisionSource(PromotionBidDecisionService.DECISION_SOURCE_RULE);
        result.setDecisionDetailJson(JsonUtil.toJson(detail));
        return result;
    }

    private String resolveRuleDecisionAction(BigDecimal previousBid, BigDecimal targetBid) {
        if (previousBid == null || targetBid == null) {
            return PromotionDecisionEngine.ACTION_INIT;
        }
        int comparison = targetBid.compareTo(previousBid);
        if (comparison > 0) {
            return PromotionDecisionEngine.ACTION_INCREASE;
        }
        if (comparison < 0) {
            return PromotionDecisionEngine.ACTION_DECREASE;
        }
        return PromotionDecisionEngine.ACTION_KEEP;
    }

    private String resolvePeakRampDecisionReason(PeakBidPhaseResolution phaseResolution) {
        if (phaseResolution == null || phaseResolution.phase() == null) {
            return DECISION_REASON_LOW_PEAK_BASELINE_BID;
        }
        return switch (phaseResolution.phase()) {
            case PRE_PEAK_RAMP_UP -> DECISION_REASON_PRE_PEAK_RAMP_UP;
            case POST_PEAK_RAMP_DOWN -> DECISION_REASON_POST_PEAK_RAMP_DOWN;
            case LOW_BASELINE_HOLD -> DECISION_REASON_LOW_PEAK_BASELINE_BID;
            default -> DECISION_REASON_LOW_PEAK_BASELINE_BID;
        };
    }

    private PromotionExposureDecisionResult resolveExposureDecisionResult(
            MtPromotionAutoBidCandidate candidate,
            MtPromotionSettingService.StrategyConfig strategyConfig,
            PromotionMetricsSnapshot metricsSnapshot,
            List<MtPromotionBidRecord> successHistory,
            PromotionDecisionResult feeRateDecisionResult
    ) {
        if (feeRateDecisionResult == null) {
            return PromotionExposureDecisionResult.notApplicable(EXPOSURE_REASON_DECISION_CONTEXT_NOT_DYNAMIC);
        }
        if (StringUtils.equals(feeRateDecisionResult.getDecisionReason(), DECISION_REASON_LOW_PEAK_BASELINE_BID)
                || StringUtils.equals(feeRateDecisionResult.getDecisionAction(), PromotionDecisionEngine.ACTION_INIT)) {
            return PromotionExposureDecisionResult.notApplicable(EXPOSURE_REASON_DECISION_CONTEXT_NOT_DYNAMIC);
        }
        BigDecimal currentRatio = metricsSnapshot == null ? null : metricsSnapshot.getWindowCostRatio();
        BigDecimal maxRate = parseRateToDecimal(strategyConfig == null ? null : strategyConfig.getRateMax());
        if (currentRatio != null && maxRate != null && currentRatio.compareTo(maxRate) > 0) {
            return PromotionExposureDecisionResult.skippedByRateCeiling("RATE_ABOVE_MAX_SKIP_EXPOSURE");
        }
        if (promotionExposureDecisionService == null) {
            return PromotionExposureDecisionResult.unavailable("EXPOSURE_SERVICE_UNAVAILABLE");
        }
        return promotionExposureDecisionService.analyze(candidate == null ? null : candidate.getShopId(), successHistory);
    }

    private PromotionDecisionResult aggregateDecisionWithExposure(
            Long shopId,
            LocalDateTime slotTime,
            List<MtPromotionBidRecord> successHistory,
            PromotionDecisionResult feeRateDecisionResult,
            PromotionExposureDecisionResult exposureDecisionResult
    ) {
        if (feeRateDecisionResult == null) {
            return null;
        }
        if (exposureDecisionResult == null || !exposureDecisionResult.isApplied()) {
            feeRateDecisionResult.setDecisionDetailJson(setFinalAggregationContext(
                    feeRateDecisionResult.getDecisionDetailJson(),
                    FINAL_AGGREGATION_MODE_RATE_ONLY,
                    FINAL_AGGREGATION_SOURCE_FEE_RATE
            ));
            return feeRateDecisionResult;
        }
        int feeRateActionValue = mapActionToStep(feeRateDecisionResult.getDecisionAction());
        int exposureActionValue = mapActionToStep(exposureDecisionResult.getAction());
        int finalActionValue = Math.max(-1, Math.min(1, feeRateActionValue + exposureActionValue));
        ExposureWeakGrowthRollbackResult rollbackResult = resolveExposureWeakGrowthRollback(
                shopId,
                successHistory,
                exposureDecisionResult,
                feeRateActionValue
        );
        if (rollbackResult.applied()) {
            feeRateDecisionResult.setDecisionAction(PromotionDecisionEngine.ACTION_DECREASE);
            feeRateDecisionResult.setDecisionReason(DECISION_REASON_EXPOSURE_WEAK_GROWTH_ROLLBACK_BID);
            feeRateDecisionResult.setNextBidAmount(rollbackResult.rollbackBidAmount());
            feeRateDecisionResult.setDecisionDetailJson(setFinalAggregationContext(
                    feeRateDecisionResult.getDecisionDetailJson(),
                    FINAL_AGGREGATION_MODE_RATE_WITH_EXPOSURE,
                    FINAL_AGGREGATION_SOURCE_EXPOSURE_WEAK_GROWTH_ROLLBACK
            ));
            feeRateDecisionResult.setDecisionDetailJson(mergeDecisionDetailWithExposureWeakGrowthRollbackContext(
                    feeRateDecisionResult.getDecisionDetailJson(),
                    rollbackResult
            ));
            log.info(
                    "自动调价触发连续曝光弱增长回退，shopId={}, slotTime={}, consecutiveCount={}, referenceSlotTime={}, rollbackBid={}, previousBid={}, feeRateActionValue={}, exposureAction={}",
                    shopId,
                    slotTime,
                    rollbackResult.consecutiveCount(),
                    rollbackResult.bestSlotTime(),
                    rollbackResult.rollbackBidAmount(),
                    feeRateDecisionResult.getPreviousBidAmount(),
                    feeRateActionValue,
                    exposureDecisionResult.getAction()
            );
            return feeRateDecisionResult;
        }
        feeRateDecisionResult.setDecisionAction(mapStepToAction(finalActionValue));
        feeRateDecisionResult.setDecisionReason(resolveAggregatedReason(
                feeRateDecisionResult.getDecisionReason(),
                feeRateActionValue,
                exposureActionValue,
                finalActionValue
        ));
        feeRateDecisionResult.setNextBidAmount(resolveAggregatedBidAmount(
                feeRateDecisionResult.getPreviousBidAmount(),
                feeRateDecisionResult.getNextBidAmount(),
                finalActionValue
        ));
        feeRateDecisionResult.setDecisionDetailJson(setFinalAggregationContext(
                feeRateDecisionResult.getDecisionDetailJson(),
                FINAL_AGGREGATION_MODE_RATE_WITH_EXPOSURE,
                resolveFinalAggregationSource(feeRateActionValue, exposureActionValue, finalActionValue)
        ));
        feeRateDecisionResult.setDecisionDetailJson(mergeDecisionDetailWithExposureWeakGrowthRollbackContext(
                feeRateDecisionResult.getDecisionDetailJson(),
                rollbackResult
        ));
        return feeRateDecisionResult;
    }

    private ExposureWeakGrowthRollbackResult resolveExposureWeakGrowthRollback(
            Long shopId,
            List<MtPromotionBidRecord> successHistory,
            PromotionExposureDecisionResult exposureDecisionResult,
            int feeRateActionValue
    ) {
        if (shopId == null) {
            return ExposureWeakGrowthRollbackResult.unavailable("SHOP_ID_MISSING", 0);
        }
        if (promotionExposureDecisionService == null) {
            return ExposureWeakGrowthRollbackResult.unavailable("EXPOSURE_SERVICE_UNAVAILABLE", 0);
        }
        if (exposureDecisionResult == null || !exposureDecisionResult.isApplied()
                || !StringUtils.equals(exposureDecisionResult.getAction(), PromotionDecisionEngine.ACTION_INCREASE)) {
            return ExposureWeakGrowthRollbackResult.notApplicable();
        }
        if (feeRateActionValue < 0) {
            return ExposureWeakGrowthRollbackResult.notApplicable();
        }
        if (CollectionUtils.size(successHistory) < EXPOSURE_WEAK_GROWTH_ROLLBACK_STREAK) {
            return ExposureWeakGrowthRollbackResult.unavailable("SUCCESS_HISTORY_STREAK_INSUFFICIENT", CollectionUtils.size(successHistory));
        }

        MtPromotionBidRecord rollbackReferenceRecord = null;
        int consecutiveCount = 0;
        for (MtPromotionBidRecord history : successHistory) {
            if (consecutiveCount >= EXPOSURE_WEAK_GROWTH_ROLLBACK_STREAK) {
                break;
            }
            if (!isExposureWeakGrowthIncreaseRecord(history)) {
                return ExposureWeakGrowthRollbackResult.unavailable("EXPOSURE_INCREASE_STREAK_INTERRUPTED", consecutiveCount);
            }
            consecutiveCount++;
            rollbackReferenceRecord = history;
        }
        if (consecutiveCount < EXPOSURE_WEAK_GROWTH_ROLLBACK_STREAK
                || rollbackReferenceRecord == null
                || rollbackReferenceRecord.getPreviousBidAmount() == null) {
            return ExposureWeakGrowthRollbackResult.unavailable("ROLLBACK_BID_UNAVAILABLE", consecutiveCount);
        }
        BigDecimal rollbackBidAmount = normalizeMoney(rollbackReferenceRecord.getPreviousBidAmount());
        if (rollbackBidAmount != null && rollbackBidAmount.compareTo(WINDOW_END_RESET_BID) < 0) {
            rollbackBidAmount = WINDOW_END_RESET_BID;
        }
        return ExposureWeakGrowthRollbackResult.applied(
                consecutiveCount,
                rollbackReferenceRecord.getSlotTime(),
                null,
                rollbackBidAmount
        );
    }

    private boolean isExposureWeakGrowthIncreaseRecord(MtPromotionBidRecord record) {
        if (record == null) {
            return false;
        }
        PromotionDecisionDetail detail = JsonUtil.toBean(record.getDecisionDetailJson(), PromotionDecisionDetail.class);
        return detail != null && StringUtils.equals(detail.getFinalAggregationSource(), FINAL_AGGREGATION_SOURCE_EXPOSURE_INCREASE)
                || StringUtils.equals(record.getDecisionReason(), DECISION_REASON_EXPOSURE_GROWTH_WEAK_INCREASE_BID);
    }

    private String mergeDecisionDetailWithExposureWeakGrowthRollbackContext(
            String baseDecisionDetailJson,
            ExposureWeakGrowthRollbackResult rollbackResult
    ) {
        if (rollbackResult == null || rollbackResult.skipped()) {
            return baseDecisionDetailJson;
        }
        PromotionDecisionDetail detail = JsonUtil.toBean(baseDecisionDetailJson, PromotionDecisionDetail.class);
        if (detail == null) {
            detail = new PromotionDecisionDetail();
        }
        detail.setExposureWeakGrowthRollbackApplied(rollbackResult.applied());
        detail.setExposureWeakGrowthConsecutiveCount(rollbackResult.consecutiveCount());
        detail.setExposureWeakGrowthRollbackUnavailableReason(rollbackResult.unavailableReason());
        detail.setExposureWeakGrowthBestSlotTime(rollbackResult.bestSlotTime());
        detail.setExposureWeakGrowthBestFollowupExposure(rollbackResult.bestFollowupExposure());
        detail.setExposureWeakGrowthBestPreviousBidAmount(rollbackResult.bestPreviousBidAmount());
        detail.setExposureWeakGrowthRollbackBidAmount(rollbackResult.rollbackBidAmount());
        return JsonUtil.toJson(detail);
    }

    private String setFinalAggregationContext(
            String baseDecisionDetailJson,
            String finalAggregationMode,
            String finalAggregationSource
    ) {
        PromotionDecisionDetail detail = JsonUtil.toBean(baseDecisionDetailJson, PromotionDecisionDetail.class);
        if (detail == null) {
            detail = new PromotionDecisionDetail();
        }
        detail.setFinalAggregationMode(finalAggregationMode);
        detail.setFinalAggregationSource(finalAggregationSource);
        return JsonUtil.toJson(detail);
    }

    private String resolveAggregatedReason(
            String originalReason,
            int feeRateActionValue,
            int exposureActionValue,
            int finalActionValue
    ) {
        if (feeRateActionValue < 0 && exposureActionValue > 0 && finalActionValue == 0) {
            return DECISION_REASON_RATE_EXPOSURE_CONFLICT_KEEP_BID;
        }
        if (exposureActionValue > 0) {
            return DECISION_REASON_EXPOSURE_GROWTH_WEAK_INCREASE_BID;
        }
        if (exposureActionValue == 0 && finalActionValue == 0) {
            return DECISION_REASON_EXPOSURE_GROWTH_EFFECTIVE_KEEP_BID;
        }
        return originalReason;
    }

    private String resolveFinalAggregationSource(
            int feeRateActionValue,
            int exposureActionValue,
            int finalActionValue
    ) {
        if (feeRateActionValue < 0 && exposureActionValue > 0 && finalActionValue == 0) {
            return FINAL_AGGREGATION_SOURCE_RATE_EXPOSURE_CONFLICT;
        }
        if (exposureActionValue > 0) {
            return FINAL_AGGREGATION_SOURCE_EXPOSURE_INCREASE;
        }
        if (exposureActionValue == 0 && finalActionValue == 0) {
            return FINAL_AGGREGATION_SOURCE_EXPOSURE_KEEP;
        }
        return FINAL_AGGREGATION_SOURCE_FEE_RATE;
    }

    private BigDecimal resolveAggregatedBidAmount(
            BigDecimal previousBidAmount,
            BigDecimal fallbackBidAmount,
            int finalActionValue
    ) {
        if (previousBidAmount == null) {
            return fallbackBidAmount;
        }
        BigDecimal aggregated = previousBidAmount.add(BID_STEP.multiply(BigDecimal.valueOf(finalActionValue)));
        return aggregated.compareTo(WINDOW_END_RESET_BID) < 0 ? WINDOW_END_RESET_BID : normalizeMoney(aggregated);
    }

    private int mapActionToStep(String action) {
        if (StringUtils.equals(action, PromotionDecisionEngine.ACTION_INCREASE)) {
            return 1;
        }
        if (StringUtils.equals(action, PromotionDecisionEngine.ACTION_DECREASE)) {
            return -1;
        }
        return 0;
    }

    private String mapStepToAction(int step) {
        if (step > 0) {
            return PromotionDecisionEngine.ACTION_INCREASE;
        }
        if (step < 0) {
            return PromotionDecisionEngine.ACTION_DECREASE;
        }
        return PromotionDecisionEngine.ACTION_KEEP;
    }

    private BigDecimal parseRateToDecimal(String value) {
        if (StringUtils.isBlank(value) || !value.endsWith("%")) {
            return null;
        }
        String number = StringUtils.removeEnd(value.trim(), "%");
        return new BigDecimal(number).divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP);
    }

    private record ExposureWeakGrowthRollbackResult(
            boolean applied,
            boolean skipped,
            int consecutiveCount,
            String unavailableReason,
            LocalDateTime bestSlotTime,
            Integer bestFollowupExposure,
            BigDecimal bestPreviousBidAmount,
            BigDecimal rollbackBidAmount
    ) {

        static ExposureWeakGrowthRollbackResult notApplicable() {
            return new ExposureWeakGrowthRollbackResult(false, true, 0, null, null, null, null, null);
        }

        static ExposureWeakGrowthRollbackResult unavailable(String reason, int consecutiveCount) {
            return new ExposureWeakGrowthRollbackResult(false, false, consecutiveCount, reason, null, null, null, null);
        }

        static ExposureWeakGrowthRollbackResult applied(
                int consecutiveCount,
                LocalDateTime bestSlotTime,
                Integer bestFollowupExposure,
                BigDecimal rollbackBidAmount
        ) {
            return new ExposureWeakGrowthRollbackResult(
                    true,
                    false,
                    consecutiveCount,
                    null,
                    bestSlotTime,
                    bestFollowupExposure,
                    rollbackBidAmount,
                    rollbackBidAmount
            );
        }
    }

    private enum PeakBidPhase {
        PEAK,
        PRE_PEAK_RAMP_UP,
        POST_PEAK_RAMP_DOWN,
        LOW_BASELINE_HOLD,
        UNAVAILABLE_PROFILE,
        NOT_APPLICABLE
    }

    private record PeakWindowSlot(
            String windowKey,
            String displayText,
            LocalDateTime startAt,
            LocalDateTime endAt
    ) {
    }

    private record BaselineBidResolution(
            BigDecimal bidAmount,
            String source,
            PeerBidPriceOpenApiResult peerBidResult,
            BigDecimal latestHighPeakBidAmount,
            BigDecimal lowPeakShareBaselineBidAmount,
            String latestHighPeakBidSource,
            Boolean lowPeakShareBaselineApplied
    ) {
    }

    private record PeakEntryBidResolution(
            BigDecimal bidAmount,
            String source,
            PeerBidPriceOpenApiResult peerBidResult
    ) {
    }

    private record PeakBidPhaseResolution(
            PeakBidPhase phase,
            ShopPeakWindowProfileLookupResult peakLookupResult,
            PeakWindowSlot currentPeakWindow,
            PeakWindowSlot previousPeakWindow,
            PeakWindowSlot nextPeakWindow,
            BaselineBidResolution lowPeakBaseline,
            PeakEntryBidResolution peakEntry,
            BigDecimal targetBidAmount,
            Integer slotsUntilPeakStart,
            Integer slotsSincePeakEnd,
            String reason,
            MtPromotionRecentOrderStatistic recentOrderStatistic,
            BigDecimal postPeakDecreaseStepAmount
    ) {

        static PeakBidPhaseResolution normal(ShopPeakWindowProfileLookupResult lookupResult) {
            return new PeakBidPhaseResolution(PeakBidPhase.NOT_APPLICABLE, lookupResult, null, null, null,
                    null, null, null, null, null, null, null, null);
        }

        static PeakBidPhaseResolution unavailable(ShopPeakWindowProfileLookupResult lookupResult) {
            return new PeakBidPhaseResolution(PeakBidPhase.UNAVAILABLE_PROFILE, lookupResult, null, null, null,
                    null, null, null, null, null, "PROFILE_UNAVAILABLE", null, null);
        }

        static PeakBidPhaseResolution peak(
                ShopPeakWindowProfileLookupResult lookupResult,
                PeakWindowSlot currentPeakWindow,
                PeakEntryBidResolution peakEntry
        ) {
            return new PeakBidPhaseResolution(PeakBidPhase.PEAK, lookupResult, currentPeakWindow, null, null,
                    null, peakEntry, null, 0, null, "PEAK_MATCHED", null, null);
        }

        static PeakBidPhaseResolution rule(
                ShopPeakWindowProfileLookupResult lookupResult,
                PeakBidPhase phase,
                PeakWindowSlot previousPeakWindow,
                PeakWindowSlot nextPeakWindow,
                BaselineBidResolution lowPeakBaseline,
                PeakEntryBidResolution peakEntry,
                BigDecimal targetBidAmount,
                Integer slotsUntilPeakStart,
                Integer slotsSincePeakEnd,
                String reason,
                MtPromotionRecentOrderStatistic recentOrderStatistic,
                BigDecimal postPeakDecreaseStepAmount
        ) {
            return new PeakBidPhaseResolution(phase, lookupResult, null, previousPeakWindow, nextPeakWindow,
                    lowPeakBaseline, peakEntry, targetBidAmount, slotsUntilPeakStart, slotsSincePeakEnd, reason,
                    recentOrderStatistic, postPeakDecreaseStepAmount);
        }

        boolean isRulePhase() {
            return phase == PeakBidPhase.PRE_PEAK_RAMP_UP
                    || phase == PeakBidPhase.POST_PEAK_RAMP_DOWN
                    || phase == PeakBidPhase.LOW_BASELINE_HOLD;
        }

        String phaseName() {
            return phase == null ? null : phase.name();
        }

        BigDecimal lowPeakBaselineBidAmount() {
            return lowPeakBaseline == null ? null : lowPeakBaseline.bidAmount();
        }

        String lowPeakBaselineSource() {
            return lowPeakBaseline == null ? null : lowPeakBaseline.source();
        }

        BigDecimal peakEntryBidAmount() {
            return peakEntry == null ? null : peakEntry.bidAmount();
        }

        String peakEntryBidSource() {
            return peakEntry == null ? null : peakEntry.source();
        }

        String currentPeakWindowKey() {
            if (currentPeakWindow != null) {
                return currentPeakWindow.windowKey();
            }
            if (nextPeakWindow != null) {
                return nextPeakWindow.windowKey();
            }
            return previousPeakWindow == null ? null : previousPeakWindow.windowKey();
        }

        String nextPeakWindowKey() {
            return nextPeakWindow == null ? null : nextPeakWindow.windowKey();
        }

        PeerBidPriceOpenApiResult peerBidResult() {
            if (peakEntry != null && peakEntry.peerBidResult() != null) {
                return peakEntry.peerBidResult();
            }
            return lowPeakBaseline == null ? null : lowPeakBaseline.peerBidResult();
        }

        ShopPeakWindowProfileLookupResult initialBidPeakLookupResult() {
            if (isRulePhase()) {
                return null;
            }
            return peakLookupResult;
        }

        PeakBidPhaseResolution withPeakEntry(PeakEntryBidResolution newPeakEntry) {
            return new PeakBidPhaseResolution(phase, peakLookupResult, currentPeakWindow, previousPeakWindow, nextPeakWindow,
                    lowPeakBaseline, newPeakEntry, targetBidAmount, slotsUntilPeakStart, slotsSincePeakEnd, reason,
                    recentOrderStatistic, postPeakDecreaseStepAmount);
        }
    }

    private record InitialBidResolutionResult(
            BigDecimal initialBidAmount,
            PeerBidPriceOpenApiResult peerBidResult
    ) {
    }
}
