package com.gogoga.backend.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.gogoga.backend.entity.mt.MtAccessToken;
import com.gogoga.backend.entity.mt.MtAsyncTask;
import com.gogoga.backend.entity.mt.MtPromotionAdRealtimeSnapshot;
import com.gogoga.backend.entity.mt.MtPromotionBidConstraint;
import com.gogoga.backend.entity.mt.MtPromotionBidRecord;
import com.gogoga.backend.entity.mt.MtPromotionMetricSnapshot;
import com.gogoga.backend.entity.mt.MtPromotionSetting;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.enums.AdminDataScopeTypeEnum;
import com.gogoga.backend.enums.BackendPortalType;
import com.gogoga.backend.enums.MtAccessTokenAuthStatusEnum;
import com.gogoga.backend.enums.MtAsyncTaskTypeEnum;
import com.gogoga.backend.enums.MtOrderStatusEnum;
import com.gogoga.backend.exception.BusinessException;
import com.gogoga.backend.mapper.MtAsyncTaskMapper;
import com.gogoga.backend.mapper.MtOrderMapper;
import com.gogoga.backend.mapper.MtPowerConsumeMapper;
import com.gogoga.backend.mapper.MtPromotionAdRealtimeSnapshotMapper;
import com.gogoga.backend.mapper.MtPromotionBidConstraintMapper;
import com.gogoga.backend.mapper.MtPromotionBidRecordMapper;
import com.gogoga.backend.mapper.MtPromotionMetricSnapshotMapper;
import com.gogoga.backend.mapper.MtPromotionSettingMapper;
import com.gogoga.backend.mapper.MtShopMapper;
import com.gogoga.backend.mapper.MtShopPeakWindowProfileMapper;
import com.gogoga.backend.mapper.MtShopRightMapper;
import com.gogoga.backend.param.AdminPricingAnalysisQueryParam;
import com.gogoga.backend.service.promotion.PromotionDecisionDetail;
import com.gogoga.backend.service.promotion.PromotionLogMessageBuilder;
import com.gogoga.backend.service.promotion.ShopPeakDayType;
import com.gogoga.backend.util.JsonUtil;
import com.gogoga.backend.util.UserContext;
import com.gogoga.backend.vo.AdminPricingAnalysisDetailVO;
import com.gogoga.backend.vo.AdminPricingAnalysisRowVO;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 后台定价分析服务。
 */
@Service
@RequiredArgsConstructor
public class AdminPricingAnalysisService {

    private static final ZoneId DEFAULT_ZONE_ID = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter POWER_SOURCE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    private final MtPromotionBidRecordMapper mtPromotionBidRecordMapper;
    private final MtShopMapper mtShopMapper;
    private final MtPromotionSettingMapper mtPromotionSettingMapper;
    private final MtPromotionSettingHistoryService mtPromotionSettingHistoryService;
    private final MtAccessTokenService mtAccessTokenService;
    private final MtShopRightMapper mtShopRightMapper;
    private final MtPromotionMetricSnapshotMapper mtPromotionMetricSnapshotMapper;
    private final MtPromotionAdRealtimeSnapshotMapper mtPromotionAdRealtimeSnapshotMapper;
    private final MtShopPeakWindowProfileMapper mtShopPeakWindowProfileMapper;
    private final MtPromotionBidConstraintMapper mtPromotionBidConstraintMapper;
    private final MtOrderMapper mtOrderMapper;
    private final MtAsyncTaskMapper mtAsyncTaskMapper;
    private final MtPowerConsumeMapper mtPowerConsumeMapper;
    private final AdminDataScopeService adminDataScopeService;
    private final UserShopBindingService userShopBindingService;
    private final PromotionLogMessageBuilder promotionLogMessageBuilder;

    public PageInfo<AdminPricingAnalysisRowVO> listRows(AdminPricingAnalysisQueryParam param) {
        requireAdminPortal();
        AdminPricingAnalysisQueryParam query = param == null ? new AdminPricingAnalysisQueryParam() : param;
        validateTimeRange(query.getSlotStartTime(), query.getSlotEndTime());
        AnalysisScope scope = resolveScope();
        if (scope.isEmptyTeamScope()) {
            return new PageInfo<>(List.of());
        }
        int pageNum = query.getPageNum() == null || query.getPageNum() < 1 ? 1 : query.getPageNum();
        int pageSize = query.getPageSize() == null || query.getPageSize() < 1 ? 10 : Math.min(query.getPageSize(), 100);
        PageHelper.startPage(pageNum, pageSize);
        List<AdminPricingAnalysisRowVO> rows = mtPromotionBidRecordMapper.selectAdminAnalysisRows(
                query.getShopId(),
                StringUtils.trimToNull(query.getPoiId()),
                StringUtils.trimToNull(query.getShopName()),
                StringUtils.trimToNull(query.getKeyword()),
                StringUtils.trimToNull(query.getExecuteStatus()),
                StringUtils.trimToNull(query.getDecisionAction()),
                StringUtils.trimToNull(query.getDecisionReason()),
                StringUtils.trimToNull(query.getLogMarkdownStatus()),
                query.getSlotStartTime(),
                query.getSlotEndTime(),
                scope.scopeTypeName(),
                scope.currentUserId(),
                scope.visibleUserIds()
        );
        rows.forEach(this::fillDecisionReasonText);
        return new PageInfo<>(rows);
    }

    public AdminPricingAnalysisDetailVO getDetail(Long recordId) {
        requireAdminPortal();
        if (recordId == null) {
            throw new BusinessException(400, "记录ID不能为空");
        }
        MtPromotionBidRecord record = mtPromotionBidRecordMapper.selectById(recordId);
        if (record == null) {
            throw new BusinessException(404, "定价记录不存在");
        }
        requireCanViewShop(record.getShopId());

        MtShop shop = record.getShopId() == null ? null : mtShopMapper.selectById(record.getShopId());
        MtPromotionSetting setting = record.getShopId() == null ? null : mtPromotionSettingMapper.selectByShopId(record.getShopId());
        PromotionDecisionDetail detail = JsonUtil.toBean(record.getDecisionDetailJson(), PromotionDecisionDetail.class);

        AdminPricingAnalysisDetailVO result = new AdminPricingAnalysisDetailVO();
        AdminPricingAnalysisDetailVO.MetricsContext metricsContext = buildMetricsContext(record);
        result.setRecordSummary(buildRecordSummary(record));
        result.setShopContext(buildShopContext(record, shop));
        result.setStrategyContext(buildStrategyContext(record, setting, detail));
        result.setMetricsContext(metricsContext);
        result.setPeakProfileContext(buildPeakProfileContext(record, detail));
        result.setStatusCheckContext(buildStatusCheckContext(record));
        result.setBidConstraintContext(buildBidConstraintContext(record));
        result.setDecisionContext(buildDecisionContext(detail));
        result.setExecutionContext(buildExecutionContext(record));
        result.setRawPayloads(buildRawPayloads(record, metricsContext));
        return result;
    }

    private AdminPricingAnalysisDetailVO.RecordSummary buildRecordSummary(MtPromotionBidRecord record) {
        AdminPricingAnalysisDetailVO.RecordSummary summary = new AdminPricingAnalysisDetailVO.RecordSummary();
        summary.setId(record.getId());
        summary.setShopId(record.getShopId());
        summary.setSlotTime(record.getSlotTime());
        summary.setRecordSource(record.getRecordSource());
        summary.setAttributedSlotTime(record.getAttributedSlotTime());
        summary.setOperatedAt(record.getOperatedAt());
        summary.setWindowKey(record.getWindowKey());
        summary.setWindowStartAt(record.getWindowStartAt());
        summary.setWindowEndAt(record.getWindowEndAt());
        summary.setExecuteStatus(record.getExecuteStatus());
        summary.setDecisionAction(record.getDecisionAction());
        summary.setDecisionReason(record.getDecisionReason());
        summary.setDecisionReasonText(resolveDecisionReasonText(record.getDecisionReason()));
        summary.setPreviousBidAmount(record.getPreviousBidAmount());
        summary.setNextBidAmount(record.getNextBidAmount());
        summary.setTotalCost(record.getTotalCost());
        summary.setTotalIncome(record.getTotalIncome());
        summary.setTotalCostRatio(record.getTotalCostRatio());
        summary.setCostDelta(record.getCostDelta());
        summary.setCostCounterValue(record.getCostCounterValue());
        summary.setRequestNo(record.getRequestNo());
        summary.setErrorMsg(record.getErrorMsg());
        summary.setMarkdownMessage(record.getMarkdownMessage());
        summary.setLogMarkdownStatus(record.getLogMarkdownStatus());
        summary.setLogTaskId(record.getLogTaskId());
        summary.setCreateTime(record.getCreateTime());
        return summary;
    }

    private void fillDecisionReasonText(AdminPricingAnalysisRowVO row) {
        if (row == null) {
            return;
        }
        row.setDecisionReasonText(resolveDecisionReasonText(row.getDecisionReason()));
    }

    private String resolveDecisionReasonText(String decisionReason) {
        return promotionLogMessageBuilder.resolveDecisionReasonText(decisionReason);
    }

    private AdminPricingAnalysisDetailVO.ShopContext buildShopContext(MtPromotionBidRecord record, MtShop shop) {
        AdminPricingAnalysisDetailVO.ShopContext context = new AdminPricingAnalysisDetailVO.ShopContext();
        context.setShopId(record.getShopId());
        if (shop != null) {
            context.setPoiId(shop.getPoiId());
            context.setEpoiId(shop.getEpoiId());
            context.setShopName(StringUtils.defaultIfBlank(shop.getPoiName(), shop.getName()));
            context.setAddress(shop.getAddress());
            context.setShippingTime(shop.getShippingTime());
        }
        MtAccessToken token = record.getShopId() == null ? null : mtAccessTokenService.findByShopId(record.getShopId());
        context.setHasAccessToken(token != null && StringUtils.isNotBlank(token.getAccessToken()));
        context.setTokenExpiresAt(token == null ? null : token.getExpiresAt());
        context.setTokenExpired(token != null && token.getExpiresAt() != null && token.getExpiresAt().isBefore(LocalDateTime.now()));
        Integer authStatus = token == null || token.getAuthStatus() == null
                ? MtAccessTokenAuthStatusEnum.VALID.getCode()
                : token.getAuthStatus();
        boolean reauthRequired = MtAccessTokenAuthStatusEnum.REAUTH_REQUIRED.getCode() == authStatus;
        context.setAuthStatus(authStatus);
        context.setAuthStatusText(reauthRequired ? "需重新授权" : "有效");
        context.setReauthRequired(reauthRequired);
        context.setAuthStatusChangedAt(token == null ? null : token.getAuthStatusChangedAt());
        context.setLastAuthFailureAt(token == null ? null : token.getLastAuthFailureAt());
        context.setRights(record.getShopId() == null ? List.of() : mtShopRightMapper.selectByShopId(record.getShopId()));
        return context;
    }

    private AdminPricingAnalysisDetailVO.StrategyContext buildStrategyContext(
            MtPromotionBidRecord record,
            MtPromotionSetting setting,
            PromotionDecisionDetail detail
    ) {
        AdminPricingAnalysisDetailVO.StrategyContext context = new AdminPricingAnalysisDetailVO.StrategyContext();
        String executionStrategyJson = detail == null ? null : detail.getStrategyConfigSnapshotJson();
        String executionHash = detail == null ? null : detail.getStrategyConfigHash();
        String currentStrategyJson = setting == null ? null : setting.getStrategyConfigJson();
        String currentHash = MtPromotionSettingHistoryService.strategyConfigHash(currentStrategyJson);
        context.setExecutionStrategyConfigJson(executionStrategyJson);
        context.setExecutionStrategyConfigHash(executionHash);
        context.setExecutionStrategySnapshotMissing(StringUtils.isBlank(executionStrategyJson));
        context.setCurrentStrategyConfigJson(currentStrategyJson);
        context.setCurrentStrategyConfigHash(currentHash);
        context.setCurrentPromotionEnabled(setting == null ? null : Boolean.TRUE.equals(setting.getPromotionEnabled()));
        context.setCurrentStrategyConfigured(setting == null ? null : Boolean.TRUE.equals(setting.getStrategyConfigured()));
        context.setSameAsCurrent(StringUtils.isNotBlank(executionHash) && StringUtils.equals(executionHash, currentHash));
        context.setHistories(mtPromotionSettingHistoryService.listLatestByShopId(record.getShopId(), 20));
        return context;
    }

    private AdminPricingAnalysisDetailVO.MetricsContext buildMetricsContext(MtPromotionBidRecord record) {
        AdminPricingAnalysisDetailVO.MetricsContext context = new AdminPricingAnalysisDetailVO.MetricsContext();
        MtPromotionMetricSnapshot metricSnapshot = record.getShopId() == null || record.getSlotTime() == null
                ? null
                : mtPromotionMetricSnapshotMapper.selectByShopIdAndBizDateAndSlotTime(
                record.getShopId(), record.getSlotTime().toLocalDate(), record.getSlotTime());
        MtPromotionAdRealtimeSnapshot realtimeSnapshot = record.getShopId() == null || record.getSlotTime() == null
                ? null
                : mtPromotionAdRealtimeSnapshotMapper.selectByShopIdAndBizDateAndSlotTime(
                record.getShopId(), record.getSlotTime().toLocalDate(), record.getSlotTime());
        context.setMetricSnapshot(metricSnapshot);
        context.setRealtimeSnapshot(realtimeSnapshot);
        context.setWindowOrderCount(countOrders(record.getShopId(), record.getWindowStartAt(), record.getSlotTime()));
        context.setWindowIncome(sumIncome(record.getShopId(), record.getWindowStartAt(), record.getSlotTime()));
        LocalDateTime hourStart = record.getSlotTime() == null ? null : record.getSlotTime().withMinute(0).withSecond(0).withNano(0);
        context.setCurrentHourOrderCount(countOrders(record.getShopId(), hourStart, record.getSlotTime()));
        context.setCurrentHourIncome(sumIncome(record.getShopId(), hourStart, record.getSlotTime()));
        return context;
    }

    private AdminPricingAnalysisDetailVO.PeakProfileContext buildPeakProfileContext(
            MtPromotionBidRecord record,
            PromotionDecisionDetail detail
    ) {
        AdminPricingAnalysisDetailVO.PeakProfileContext context = new AdminPricingAnalysisDetailVO.PeakProfileContext();
        context.setSnapshot(buildPeakProfileSnapshot(detail));
        if (record.getShopId() == null) {
            context.setProfiles(List.of());
            return context;
        }
        List<com.gogoga.backend.entity.mt.MtShopPeakWindowProfile> profiles = new ArrayList<>();
        for (ShopPeakDayType dayType : ShopPeakDayType.values()) {
            com.gogoga.backend.entity.mt.MtShopPeakWindowProfile profile =
                    mtShopPeakWindowProfileMapper.selectByShopIdAndDayType(record.getShopId(), dayType.name());
            if (profile != null) {
                profiles.add(profile);
            }
        }
        context.setProfiles(profiles);
        return context;
    }

    private AdminPricingAnalysisDetailVO.PeakProfileSnapshot buildPeakProfileSnapshot(PromotionDecisionDetail detail) {
        AdminPricingAnalysisDetailVO.PeakProfileSnapshot snapshot = new AdminPricingAnalysisDetailVO.PeakProfileSnapshot();
        if (detail == null) {
            return snapshot;
        }
        snapshot.setDayType(detail.getPeakWindowDayType());
        snapshot.setProfileDayType(detail.getPeakWindowProfileDayType());
        snapshot.setCalendarDayType(detail.getPeakWindowCalendarDayType());
        snapshot.setHolidayName(detail.getPeakWindowHolidayName());
        snapshot.setProfileStatus(detail.getPeakWindowProfileStatus());
        snapshot.setGatingEnabled(detail.getPeakWindowGatingEnabled());
        snapshot.setPeakMatched(detail.getPeakWindowMatched());
        snapshot.setMatchedRange(detail.getPeakWindowMatchedRange());
        snapshot.setSampleDayCount(detail.getPeakWindowSampleDayCount());
        snapshot.setSampleOrderCount(detail.getPeakWindowSampleOrderCount());
        snapshot.setUnavailableReason(detail.getPeakWindowUnavailableReason());
        snapshot.setPeakBidPhase(detail.getPeakBidPhase());
        snapshot.setPeakWindowKey(detail.getPeakWindowKey());
        snapshot.setNextPeakWindowKey(detail.getNextPeakWindowKey());
        return snapshot;
    }

    private AdminPricingAnalysisDetailVO.DecisionContext buildDecisionContext(PromotionDecisionDetail detail) {
        AdminPricingAnalysisDetailVO.DecisionContext context = new AdminPricingAnalysisDetailVO.DecisionContext();
        if (detail == null) {
            return context;
        }
        context.setDecisionMode(detail.getDecisionMode());
        context.setDecisionSource(detail.getDecisionSource());
        context.setPricingBranch(detail.getPricingBranch());
        context.setFeeRateBranchAction(detail.getFeeRateBranchAction());
        context.setFeeRateBranchReason(detail.getFeeRateBranchReason());
        context.setExposureBranchStatus(detail.getExposureBranchStatus());
        context.setExposureBranchAction(detail.getExposureBranchAction());
        context.setExposureBranchReason(detail.getExposureBranchReason());
        context.setFinalAggregationMode(detail.getFinalAggregationMode());
        context.setFinalAggregationSource(detail.getFinalAggregationSource());
        context.setPeakBidPhase(detail.getPeakBidPhase());
        context.setPeakWindowKey(detail.getPeakWindowKey());
        context.setNextPeakWindowKey(detail.getNextPeakWindowKey());
        context.setPeakWindowDayType(detail.getPeakWindowDayType());
        context.setPeakWindowProfileDayType(detail.getPeakWindowProfileDayType());
        context.setPeakWindowMatched(detail.getPeakWindowMatched());
        context.setPeakWindowGatingEnabled(detail.getPeakWindowGatingEnabled());
        context.setPeakWindowMatchedRange(detail.getPeakWindowMatchedRange());
        context.setPeakWindowProfileStatus(detail.getPeakWindowProfileStatus());
        context.setPeakWindowSampleDayCount(detail.getPeakWindowSampleDayCount());
        context.setPeakWindowSampleOrderCount(detail.getPeakWindowSampleOrderCount());
        context.setPeakWindowUnavailableReason(detail.getPeakWindowUnavailableReason());
        context.setLowPeakBaselineBidAmount(detail.getLowPeakBaselineBidAmount());
        context.setLowPeakBaselineSource(detail.getLowPeakBaselineSource());
        context.setPeakEntryBidAmount(detail.getPeakEntryBidAmount());
        context.setPeakEntryBidSource(detail.getPeakEntryBidSource());
        context.setSlotsUntilPeakStart(detail.getSlotsUntilPeakStart());
        context.setSlotsSincePeakEnd(detail.getSlotsSincePeakEnd());
        context.setPeakRampTargetBidAmount(detail.getPeakRampTargetBidAmount());
        context.setPeakRampReason(detail.getPeakRampReason());
        context.setPeerAverageBidAmount(detail.getPeerAverageBidAmount());
        context.setMetricsBaselineType(detail.getMetricsBaselineType());
        context.setMetricsEffectiveWindowStartAt(detail.getMetricsEffectiveWindowStartAt());
        context.setMetricsBusinessWindowStartAt(detail.getMetricsBusinessWindowStartAt());
        context.setTemporaryRecoveryWindow(detail.getTemporaryRecoveryWindow());
        context.setRecentOrderStatisticBizDate(detail.getRecentOrderStatisticBizDate());
        context.setRecentOrderLookbackStartDate(detail.getRecentOrderLookbackStartDate());
        context.setRecentOrderLookbackEndDate(detail.getRecentOrderLookbackEndDate());
        context.setRecentOrderSampleDates(detail.getRecentOrderSampleDates());
        context.setRecentOrderStatisticTrusted(detail.getRecentOrderStatisticTrusted());
        context.setRecentOrderStatisticUntrustedReason(detail.getRecentOrderStatisticUntrustedReason());
        context.setRecentOrderTotalOrderCount(detail.getRecentOrderTotalOrderCount());
        context.setRecentOrderAverageDailyOrderCount(detail.getRecentOrderAverageDailyOrderCount());
        context.setHighVolumeRampDownEligible(detail.getHighVolumeRampDownEligible());
        context.setPeakRampDecreaseStepAmount(detail.getPeakRampDecreaseStepAmount());
        context.setLowPeakOrderCount(detail.getLowPeakOrderCount());
        context.setLowPeakOrderShare(detail.getLowPeakOrderShare());
        context.setLowPeakShareBaselineBidAmount(detail.getLowPeakShareBaselineBidAmount());
        context.setLatestHighPeakBidAmount(detail.getLatestHighPeakBidAmount());
        context.setLatestHighPeakBidSource(detail.getLatestHighPeakBidSource());
        context.setLowPeakShareBaselineApplied(detail.getLowPeakShareBaselineApplied());
        context.setRatePriorityFastCorrectionApplied(detail.getRatePriorityFastCorrectionApplied());
        context.setRatePriorityCurrentRate(detail.getRatePriorityCurrentRate());
        context.setRatePriorityMaxRate(detail.getRatePriorityMaxRate());
        context.setRatePriorityFastThreshold(detail.getRatePriorityFastThreshold());
        context.setRatePriorityFastDecreaseStepAmount(detail.getRatePriorityFastDecreaseStepAmount());
        context.setRatePriorityRecoveredToMainAlgorithm(detail.getRatePriorityRecoveredToMainAlgorithm());
        context.setExposureWeakGrowthRollbackApplied(detail.getExposureWeakGrowthRollbackApplied());
        context.setExposureWeakGrowthConsecutiveCount(detail.getExposureWeakGrowthConsecutiveCount());
        context.setExposureWeakGrowthRollbackUnavailableReason(detail.getExposureWeakGrowthRollbackUnavailableReason());
        context.setExposureWeakGrowthBestSlotTime(detail.getExposureWeakGrowthBestSlotTime());
        context.setExposureWeakGrowthBestFollowupExposure(detail.getExposureWeakGrowthBestFollowupExposure());
        context.setExposureWeakGrowthBestPreviousBidAmount(detail.getExposureWeakGrowthBestPreviousBidAmount());
        context.setExposureWeakGrowthRollbackBidAmount(detail.getExposureWeakGrowthRollbackBidAmount());
        context.setUserBidMinAmount(detail.getUserBidMinAmount());
        context.setUserBidMaxAmount(detail.getUserBidMaxAmount());
        context.setEffectiveMaxBidAmount(detail.getEffectiveMaxBidAmount());
        context.setBidBeforeUserRangeClamp(detail.getBidBeforeUserRangeClamp());
        context.setBidAfterUserRangeClamp(detail.getBidAfterUserRangeClamp());
        return context;
    }

    @SuppressWarnings("unchecked")
    private AdminPricingAnalysisDetailVO.StatusCheckContext buildStatusCheckContext(MtPromotionBidRecord record) {
        AdminPricingAnalysisDetailVO.StatusCheckContext context = new AdminPricingAnalysisDetailVO.StatusCheckContext();
        if (record == null || StringUtils.isBlank(record.getUpdatePayloadJson())) {
            return context;
        }
        Map<String, Object> payload = JsonUtil.toMap(record.getUpdatePayloadJson());
        if (payload == null || payload.isEmpty()) {
            return context;
        }
        Object statusCheckObject = payload.get("statusCheck");
        if (!(statusCheckObject instanceof Map<?, ?>)) {
            return context;
        }
        Map<String, Object> statusCheck = (Map<String, Object>) statusCheckObject;
        context.setStatus(toInteger(statusCheck.get("status")));
        context.setBid(toInteger(statusCheck.get("bid")));
        context.setBudget(toBigDecimal(statusCheck.get("budget")));
        context.setBudgetUsed(toBigDecimal(statusCheck.get("budgetUsed")));
        context.setAvailableBudget(toBigDecimal(statusCheck.get("availableBudget")));
        context.setResponseCode(toStringValue(statusCheck.get("responseCode")));
        context.setResponseMsg(toStringValue(statusCheck.get("responseMsg")));
        context.setResponseDataErrMsg(resolveResponseDataErrMsg(statusCheck.get("responseData")));
        return context;
    }

    private AdminPricingAnalysisDetailVO.BidConstraintContext buildBidConstraintContext(MtPromotionBidRecord record) {
        AdminPricingAnalysisDetailVO.BidConstraintContext context = new AdminPricingAnalysisDetailVO.BidConstraintContext();
        BigDecimal globalMaxBidAmount = MtPromotionBidConstraintService.GLOBAL_MAX_BID_AMOUNT;
        context.setGlobalMaxBidAmount(globalMaxBidAmount);
        context.setEffectiveMaxBidAmount(globalMaxBidAmount);
        context.setActive(false);
        if (record == null || record.getShopId() == null) {
            return context;
        }
        MtPromotionBidConstraint constraint = mtPromotionBidConstraintMapper.selectActiveByShopId(
                record.getShopId(),
                LocalDateTime.now()
        );
        if (constraint == null || constraint.getMaxBidAmount() == null) {
            return context;
        }
        context.setActive(true);
        context.setShopMaxBidAmount(constraint.getMaxBidAmount());
        context.setEffectiveMaxBidAmount(
                constraint.getMaxBidAmount().compareTo(globalMaxBidAmount) < 0
                        ? constraint.getMaxBidAmount()
                        : globalMaxBidAmount
        );
        context.setSource(constraint.getSource());
        context.setRawMessage(constraint.getRawMessage());
        context.setObservedAt(constraint.getObservedAt());
        context.setExpireAt(constraint.getExpireAt());
        return context;
    }

    private AdminPricingAnalysisDetailVO.ExecutionContext buildExecutionContext(MtPromotionBidRecord record) {
        AdminPricingAnalysisDetailVO.ExecutionContext context = new AdminPricingAnalysisDetailVO.ExecutionContext();
        context.setAutoBidTask(mtAsyncTaskMapper.selectByTaskTypeAndBizKey(
                MtAsyncTaskTypeEnum.PROMOTION_AUTO_BID.name(),
                buildAutoBidTaskBizKey(record.getShopId(), record.getSlotTime())
        ));
        MtAsyncTask logTask = record.getLogTaskId() == null ? null : mtAsyncTaskMapper.selectById(record.getLogTaskId());
        context.setLogTask(logTask);
        context.setPowerConsumes(record.getShopId() == null || record.getSlotTime() == null
                ? List.of()
                : mtPowerConsumeMapper.selectByShopIdAndSourceId(record.getShopId(), buildPowerSourceId(record.getShopId(), record.getSlotTime())));
        return context;
    }

    private AdminPricingAnalysisDetailVO.RawPayloads buildRawPayloads(
            MtPromotionBidRecord record,
            AdminPricingAnalysisDetailVO.MetricsContext metricsContext
    ) {
        AdminPricingAnalysisDetailVO.RawPayloads payloads = new AdminPricingAnalysisDetailVO.RawPayloads();
        payloads.setMetricsPayloadJson(record.getMetricsPayloadJson());
        payloads.setMetricSnapshotPayloadJson(metricsContext == null || metricsContext.getMetricSnapshot() == null
                ? null
                : metricsContext.getMetricSnapshot().getPayloadJson());
        payloads.setRealtimeSnapshotPayloadJson(metricsContext == null || metricsContext.getRealtimeSnapshot() == null
                ? null
                : metricsContext.getRealtimeSnapshot().getPayloadJson());
        payloads.setUpdatePayloadJson(record.getUpdatePayloadJson());
        payloads.setDecisionDetailJson(record.getDecisionDetailJson());
        return payloads;
    }

    private Integer countOrders(Long shopId, LocalDateTime startAt, LocalDateTime endAt) {
        if (shopId == null || startAt == null || endAt == null) {
            return 0;
        }
        return mtOrderMapper.countOrdersByShopIdAndOrderSendTimeBetweenExcludingStatus(
                shopId,
                MtOrderStatusEnum.CANCELED.getCode(),
                toEpochSecond(startAt),
                toEpochSecond(endAt)
        );
    }

    private BigDecimal sumIncome(Long shopId, LocalDateTime startAt, LocalDateTime endAt) {
        if (shopId == null || startAt == null || endAt == null) {
            return BigDecimal.ZERO;
        }
        return mtOrderMapper.sumIncomeByShopIdAndOrderSendTimeBetweenExcludingStatus(
                shopId,
                MtOrderStatusEnum.CANCELED.getCode(),
                toEpochSecond(startAt),
                toEpochSecond(endAt)
        );
    }

    private Integer toInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && StringUtils.isNotBlank(text)) {
            try {
                return Integer.valueOf(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        if (value instanceof String text && StringUtils.isNotBlank(text)) {
            try {
                return new BigDecimal(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String toStringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String resolveResponseDataErrMsg(Object responseData) {
        if (!(responseData instanceof Map<?, ?> data)) {
            return null;
        }
        return toStringValue(data.get("errMsg"));
    }

    private Long toEpochSecond(LocalDateTime value) {
        return value == null ? null : value.atZone(DEFAULT_ZONE_ID).toEpochSecond();
    }

    private String buildAutoBidTaskBizKey(Long shopId, LocalDateTime slotTime) {
        return shopId == null || slotTime == null ? null : shopId + "#" + slotTime;
    }

    private String buildPowerSourceId(Long shopId, LocalDateTime slotTime) {
        return shopId + "-" + slotTime.format(POWER_SOURCE_TIME_FORMATTER);
    }

    private void requireAdminPortal() {
        if (UserContext.getActivePortal() != BackendPortalType.ADMIN) {
            throw new BusinessException(403, "仅管理员工作台可访问定价分析");
        }
    }

    private void requireCanViewShop(Long shopId) {
        if (shopId == null) {
            throw new BusinessException(400, "门店ID不能为空");
        }
        if (UserContext.isSuperAdmin()) {
            return;
        }
        userShopBindingService.requireCurrentUserVisibleShop(shopId, null);
    }

    private AnalysisScope resolveScope() {
        AdminDataScopeTypeEnum scopeType = adminDataScopeService.getCurrentScopeType();
        if (scopeType == AdminDataScopeTypeEnum.SELF) {
            return new AnalysisScope(scopeType.name(), UserContext.getUserId(), List.of());
        }
        if (scopeType == AdminDataScopeTypeEnum.TEAM) {
            return new AnalysisScope(scopeType.name(), null, new ArrayList<>(adminDataScopeService.getCurrentVisibleUserIds()));
        }
        return new AnalysisScope(AdminDataScopeTypeEnum.ALL.name(), null, List.of());
    }

    private void validateTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime != null && endTime != null && startTime.isAfter(endTime)) {
            throw new BusinessException(400, "开始时间不能晚于结束时间");
        }
    }

    private record AnalysisScope(String scopeTypeName, String currentUserId, List<String> visibleUserIds) {

        private boolean isEmptyTeamScope() {
            return AdminDataScopeTypeEnum.TEAM.name().equals(scopeTypeName)
                    && (visibleUserIds == null || visibleUserIds.isEmpty());
        }
    }
}
