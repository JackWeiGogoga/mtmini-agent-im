package com.gogoga.backend.service;

import com.gogoga.backend.client.MtPromotionOpenApiClient;
import com.gogoga.backend.entity.mt.MtAccessToken;
import com.gogoga.backend.entity.mt.MtPromotionBidRecord;
import com.gogoga.backend.entity.mt.MtPromotionRightExpiryShutdownCandidate;
import com.gogoga.backend.mapper.MtPromotionBidRecordMapper;
import com.gogoga.backend.mapper.MtPromotionSettingMapper;
import com.gogoga.backend.mapper.MtShopRightMapper;
import com.gogoga.backend.service.promotion.PromotionPlanStatusUpdateResult;
import com.gogoga.backend.util.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 功能权益过期后的推广链路关停服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MtPromotionRightExpiryShutdownService {

    public static final String TRIGGER_PROMOTION_RIGHT_EXPIRED = "PROMOTION_RIGHT_EXPIRED";
    public static final String CHANGE_REASON_PROMOTION_RIGHT_EXPIRED = "PROMOTION_RIGHT_EXPIRED_SHUTDOWN";
    public static final String DECISION_REASON_PROMOTION_RIGHT_EXPIRED = "PROMOTION_RIGHT_EXPIRED_AUTO_DISABLED";

    private static final int DEFAULT_BATCH_SIZE = 100;
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String ACTION_KEEP = "KEEP";
    private static final String RECORD_SOURCE_AUTO = "AUTO";

    private final MtPromotionSettingMapper mtPromotionSettingMapper;
    private final MtPromotionBidRecordMapper mtPromotionBidRecordMapper;
    private final MtShopRightMapper mtShopRightMapper;
    private final MtPromotionOpenApiClient mtPromotionOpenApiClient;
    private final MtOrderPushControlService mtOrderPushControlService;
    private final MtPromotionSettingService mtPromotionSettingService;
    private final MtMeituanAuthorizationFailureHandler mtMeituanAuthorizationFailureHandler;

    public void processExpiredPromotionRights() {
        processExpiredPromotionRights(DEFAULT_BATCH_SIZE);
    }

    public void processExpiredPromotionRights(int batchSize) {
        int safeBatchSize = batchSize <= 0 ? DEFAULT_BATCH_SIZE : batchSize;
        LocalDateTime now = LocalDateTime.now();
        Long lastShopId = null;
        int processedCount = 0;
        while (true) {
            List<MtPromotionRightExpiryShutdownCandidate> candidates =
                    mtPromotionSettingMapper.selectRightExpiryShutdownCandidatesByCursor(now, lastShopId, safeBatchSize);
            if (CollectionUtils.isEmpty(candidates)) {
                break;
            }
            for (MtPromotionRightExpiryShutdownCandidate candidate : candidates) {
                lastShopId = candidate.getShopId();
                if (shutdownOne(candidate)) {
                    processedCount++;
                }
            }
            if (candidates.size() < safeBatchSize) {
                break;
            }
        }
        log.info("功能权益过期关停处理完成，processedCount={}, batchSize={}", processedCount, safeBatchSize);
    }

    public boolean shutdownOne(MtPromotionRightExpiryShutdownCandidate candidate) {
        if (!isValidCandidate(candidate)) {
            log.info("功能权益过期关停跳过，原因=候选缺少必要字段, shopId={}, poiId={}, hasToken={}, opBizCode={}",
                    candidate == null ? null : candidate.getShopId(),
                    candidate == null ? null : candidate.getPoiId(),
                    candidate != null && StringUtils.isNotBlank(candidate.getAccessToken()),
                    candidate == null ? null : candidate.getOpBizCode());
            return false;
        }
        if (mtShopRightMapper.existsActivePromotionRight(candidate.getShopId(), LocalDateTime.now())) {
            log.info("功能权益过期关停跳过，原因=权益已恢复, shopId={}", candidate.getShopId());
            return false;
        }

        PromotionPlanStatusUpdateResult planResult = mtPromotionOpenApiClient.updatePlanStatus(
                candidate.getShopId(),
                candidate.getPoiId(),
                candidate.getAccessToken(),
                MtPromotionOpenApiClient.STATUS_CLOSE,
                null,
                null,
                TRIGGER_PROMOTION_RIGHT_EXPIRED
        );
        if (planResult == null || !planResult.isSuccess()) {
            if (planResult != null && planResult.isAuthFailure()) {
                mtMeituanAuthorizationFailureHandler.handleConfirmedAuthFailure(
                        candidate.getShopId(),
                        TRIGGER_PROMOTION_RIGHT_EXPIRED,
                        planResult.getResponseCode(),
                        StringUtils.defaultIfBlank(planResult.getResponseMsg(), planResult.getErrorMsg())
                );
                log.warn("功能权益过期关闭美团推广计划确认鉴权失败，已标记重新授权并关闭本地推广，shopId={}, poiId={}, responseCode={}, responseMsg={}, traceId={}, error={}",
                        candidate.getShopId(),
                        candidate.getPoiId(),
                        planResult.getResponseCode(),
                        planResult.getResponseMsg(),
                        planResult.getResponseTraceId(),
                        planResult.getErrorMsg());
                return true;
            }
            log.warn("功能权益过期关闭美团推广计划失败，shopId={}, poiId={}, responseCode={}, responseMsg={}, traceId={}, error={}",
                    candidate.getShopId(),
                    candidate.getPoiId(),
                    planResult == null ? null : planResult.getResponseCode(),
                    planResult == null ? null : planResult.getResponseMsg(),
                    planResult == null ? null : planResult.getResponseTraceId(),
                    planResult == null ? null : planResult.getErrorMsg());
            return false;
        }

        if (!Boolean.TRUE.equals(candidate.getOrderPushBlocked())) {
            MtOrderPushControlService.OrderPushSyncResult pushResult = mtOrderPushControlService.blockOrderPush(
                    toAccessToken(candidate),
                    TRIGGER_PROMOTION_RIGHT_EXPIRED
            );
            if (pushResult == null || !pushResult.success()) {
                log.warn("功能权益过期停止订单推送失败，shopId={}, opBizCode={}, error={}",
                        candidate.getShopId(), candidate.getOpBizCode(), pushResult == null ? null : pushResult.errorMessage());
                return false;
            }
        }

        mtPromotionSettingService.disablePromotionByShopId(candidate.getShopId(), CHANGE_REASON_PROMOTION_RIGHT_EXPIRED);
        insertShutdownPromotionLog(candidate);
        log.info("功能权益过期关停完成，shopId={}, poiId={}, orderPushBlocked={}",
                candidate.getShopId(), candidate.getPoiId(), candidate.getOrderPushBlocked());
        return true;
    }

    private boolean isValidCandidate(MtPromotionRightExpiryShutdownCandidate candidate) {
        return candidate != null
                && candidate.getShopId() != null
                && StringUtils.isNotBlank(candidate.getPoiId())
                && StringUtils.isNotBlank(candidate.getAccessToken())
                && candidate.getAccessTokenId() != null
                && StringUtils.isNotBlank(candidate.getOpBizCode());
    }

    private MtAccessToken toAccessToken(MtPromotionRightExpiryShutdownCandidate candidate) {
        MtAccessToken token = new MtAccessToken();
        token.setId(candidate.getAccessTokenId());
        token.setShopId(candidate.getShopId());
        token.setAccessToken(candidate.getAccessToken());
        token.setOpBizCode(candidate.getOpBizCode());
        token.setExpiresAt(candidate.getTokenExpiresAt());
        token.setOrderPushBlocked(candidate.getOrderPushBlocked());
        return token;
    }

    private void insertShutdownPromotionLog(MtPromotionRightExpiryShutdownCandidate candidate) {
        try {
            LocalDateTime now = LocalDateTime.now();
            MtPromotionBidRecord record = new MtPromotionBidRecord();
            record.setShopId(candidate.getShopId());
            record.setBizDate(LocalDate.now());
            record.setSlotTime(now);
            record.setRecordSource(RECORD_SOURCE_AUTO);
            record.setWindowKey(TRIGGER_PROMOTION_RIGHT_EXPIRED);
            record.setRequestNo("right-expiry-shutdown-" + candidate.getShopId() + "-" + System.currentTimeMillis());
            record.setDecisionAction(ACTION_KEEP);
            record.setDecisionReason(DECISION_REASON_PROMOTION_RIGHT_EXPIRED);
            record.setExecuteStatus(STATUS_SUCCESS);
            record.setUpdatePayloadJson(JsonUtil.toJson(Map.of(
                    "planStatus", Map.of(
                            "status", MtPromotionOpenApiClient.STATUS_CLOSE,
                            "triggerSource", TRIGGER_PROMOTION_RIGHT_EXPIRED
                    ),
                    "orderPushBlocked", true
            )));
            record.setMarkdownMessage("**AI推广已自动关闭**\n- 原因：功能权益已过期\n- 操作：已关闭美团推广计划，并停止订单推送\n- 恢复方式：续费或核销权益后，需手动重新开启推广");
            record.setLogMarkdownStatus("READY");
            record.setCreateTime(now);
            record.setUpdateTime(now);
            mtPromotionBidRecordMapper.insert(record);
        } catch (RuntimeException e) {
            log.error("功能权益过期关停推广日志写入失败，shopId={}", candidate.getShopId(), e);
        }
    }
}
