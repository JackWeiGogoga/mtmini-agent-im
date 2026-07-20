package com.gogoga.backend.service;

import com.gogoga.backend.client.MtImMessageClient;
import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.entity.mt.MtAccessToken;
import com.gogoga.backend.entity.mt.MtImBroadcastRecord;
import com.gogoga.backend.entity.mt.MtImBroadcastTask;
import com.gogoga.backend.enums.ImBroadcastRecordStatusEnum;
import com.gogoga.backend.enums.ImBroadcastTaskStatusEnum;
import com.gogoga.backend.enums.ImMessageTypeEnum;
import com.gogoga.backend.mapper.MtImBroadcastRecordMapper;
import com.gogoga.backend.mapper.MtImBroadcastSettingMapper;
import com.gogoga.backend.mapper.MtImBroadcastTaskMapper;
import com.gogoga.backend.service.async.MtAsyncTaskDryRunException;
import com.gogoga.backend.service.async.MtAsyncTaskTerminalFailureException;
import com.gogoga.backend.service.async.MtImBroadcastSendTaskPayload;
import com.gogoga.backend.util.MeituanAuthFailureUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 群发分片发送：分片内串行发送、复用同一 access token、受全局 QPS 与单店并发约束。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MtImBroadcastSendService {

    private static final int ERROR_SUMMARY_MAX_LENGTH = 500;
    private static final long SHOP_SLOT_LEASE_MINUTES = 30L;

    private final MtProperties mtProperties;
    private final MtImBroadcastTaskMapper taskMapper;
    private final MtImBroadcastRecordMapper recordMapper;
    private final MtImBroadcastSettingMapper settingMapper;
    private final MtAccessTokenService accessTokenService;
    private final MtMeituanAuthorizationFailureHandler authorizationFailureHandler;
    private final MtImMessageClient messageClient;
    private final ImBroadcastRateLimiter rateLimiter;
    private final MtImBroadcastTimelineService timelineService;

    public void execute(MtImBroadcastSendTaskPayload payload) {
        if (payload == null || payload.getBroadcastTaskId() == null || payload.getShardNo() == null) {
            throw new MtAsyncTaskTerminalFailureException("IM_BROADCAST_SEND_PAYLOAD_INVALID");
        }
        MtImBroadcastTask task = taskMapper.selectById(payload.getBroadcastTaskId());
        if (task == null) {
            throw new MtAsyncTaskTerminalFailureException("IM_BROADCAST_TASK_NOT_FOUND");
        }
        if (ImBroadcastTaskStatusEnum.SKIPPED.name().equals(task.getStatus())) {
            return;
        }

        try (ImBroadcastRateLimiter.SlotHandle slot =
                     rateLimiter.tryAcquireShopSlot(task.getShopId(), Duration.ofMinutes(SHOP_SLOT_LEASE_MINUTES))) {
            if (slot == null) {
                // 同门店已有分片在跑，稍后重投，避免单店对美团的并发超限。
                throw new MtAsyncTaskDryRunException("IM_BROADCAST_SHOP_BUSY", 30L);
            }
            sendShard(task, payload.getShardNo());
        }
    }

    private void sendShard(MtImBroadcastTask task, int shardNo) {
        List<MtImBroadcastRecord> pending = recordMapper.selectPendingByShard(
                task.getId(), shardNo, Math.max(mtProperties.getImBroadcast().getShardSize(), 1));
        if (pending == null || pending.isEmpty()) {
            // 分片已全部发完；仍需结算一次，让批次终态可以收敛。
            settleBatch(task, 0);
            return;
        }

        MtAccessToken token = resolveUsableToken(task.getShopId());
        if (token == null) {
            authorizationFailureHandler.handleConfirmedAuthFailure(
                    task.getShopId(), "IM_BROADCAST_SEND", "TOKEN_UNAVAILABLE", null);
            throw new MtAsyncTaskTerminalFailureException("AUTH_REQUIRED");
        }
        taskMapper.markSending(task.getId(), LocalDateTime.now());

        int newlySent = 0;
        String accessToken = token.getAccessToken();
        for (MtImBroadcastRecord record : pending) {
            if (!rateLimiter.acquireGlobalPermit(mtProperties.getImBroadcast().getThrottleBackoffMillis())) {
                // 拿不到全局配额说明整体已打满，剩余明细留给下一次重试，先结算已完成部分。
                log.info("群发全局限流已打满，提前结束分片，broadcastTaskId={}, shardNo={}", task.getId(), shardNo);
                break;
            }
            SendOutcome outcome = sendOne(accessToken, task, record);
            if (outcome.refreshedToken() != null) {
                accessToken = outcome.refreshedToken();
            }
            newlySent += outcome.newlySent();
        }
        settleBatch(task, newlySent);
    }

    private SendOutcome sendOne(String accessToken, MtImBroadcastTask task, MtImBroadcastRecord record) {
        MtImMessageClient.SendResult result = sendOnce(accessToken, task, record);
        String refreshedToken = null;

        if (!result.success() && MeituanAuthFailureUtil.isAuthFailure(result.errorCode(), result.responseSummary())) {
            MtAccessTokenService.TokenRefreshResult refresh =
                    accessTokenService.refreshTokenForShopWithResult(task.getShopId());
            MtAccessToken refreshed = refresh == null ? null : refresh.token();
            if (isUsableToken(refreshed)) {
                refreshedToken = refreshed.getAccessToken();
                result = sendOnce(refreshedToken, task, record);
            }
            if (!result.success()
                    && MeituanAuthFailureUtil.isAuthFailure(result.errorCode(), result.responseSummary())) {
                authorizationFailureHandler.handleConfirmedAuthFailure(
                        task.getShopId(), "IM_BROADCAST_SEND", result.errorCode(), result.responseSummary());
            }
        }

        if (result.success()) {
            // markSent 带 status <> 'SENT' 条件，返回 0 说明已被并发置为已发送，不能重复计入累计条数。
            int updated = recordMapper.markSent(record.getId(), LocalDateTime.now());
            if (updated > 0) {
                try {
                    timelineService.persistSentMessage(task, record);
                } catch (RuntimeException e) {
                    // 消息已投递成功，时间线补写失败只降级记录，不影响发送结果。
                    log.warn("群发消息写入会话时间线失败，broadcastTaskId={}, openUserId={}",
                            task.getId(), record.getOpenUserId(), e);
                }
            }
            return new SendOutcome(updated, refreshedToken);
        }
        recordMapper.markFailed(record.getId(),
                StringUtils.abbreviate(StringUtils.defaultIfBlank(result.errorCode(), "IM_SEND_FAILED"), 64),
                StringUtils.abbreviate(result.responseSummary(), ERROR_SUMMARY_MAX_LENGTH));
        return new SendOutcome(0, refreshedToken);
    }

    /**
     * 群发不依附任何订单，orderId 传 0；美团对两方会话消息并不强制要求订单号。
     */
    private MtImMessageClient.SendResult sendOnce(String accessToken,
                                                  MtImBroadcastTask task,
                                                  MtImBroadcastRecord record) {
        long cts = LocalDateTime.now().atZone(ZoneId.systemDefault()).toEpochSecond();
        return messageClient.sendTwoPartyMessage(accessToken, new MtImMessageClient.TwoPartyMessageCommand(
                record.getOutboundMessageId(),
                task.getContent(),
                ImMessageTypeEnum.TEXT.getCode(),
                cts,
                0L,
                record.getOpenUserId(),
                null,
                null,
                null
        ));
    }

    /**
     * 用明细表重算批次进度，避免分片超时回收或崩溃重跑造成重复计数。
     *
     * <p>门店累计条数只按本次真正新发出的条数递增，重跑时 {@code newlySent} 为 0，同样不会翻倍。</p>
     */
    private void settleBatch(MtImBroadcastTask task, int newlySent) {
        long sent = recordMapper.countByTask(task.getId(), ImBroadcastRecordStatusEnum.SENT.name());
        long failed = recordMapper.countByTask(task.getId(), ImBroadcastRecordStatusEnum.FAILED.name());
        int target = task.getTargetCount() == null ? 0 : task.getTargetCount();

        boolean settled = sent + failed >= target;
        String status;
        if (!settled) {
            status = ImBroadcastTaskStatusEnum.SENDING.name();
        } else if (failed == 0) {
            status = ImBroadcastTaskStatusEnum.SUCCESS.name();
        } else if (sent == 0) {
            status = ImBroadcastTaskStatusEnum.FAILED.name();
        } else {
            status = ImBroadcastTaskStatusEnum.PARTIAL.name();
        }
        taskMapper.updateProgress(task.getId(), sent, failed, status, settled ? LocalDateTime.now() : null);

        if (newlySent > 0) {
            settingMapper.increaseTotalSentCount(task.getSettingId(), newlySent);
        }
        log.info("群发分片结算完成，broadcastTaskId={}, shopId={}, sent={}, failed={}, newlySent={}, status={}",
                task.getId(), task.getShopId(), sent, failed, newlySent, status);
    }

    private MtAccessToken resolveUsableToken(Long shopId) {
        MtAccessToken token = accessTokenService.findByShopId(shopId);
        if (isUsableToken(token)) {
            return token;
        }
        MtAccessTokenService.TokenRefreshResult refresh = accessTokenService.refreshTokenForShopWithResult(shopId);
        MtAccessToken refreshed = refresh == null ? null : refresh.token();
        return isUsableToken(refreshed) ? refreshed : null;
    }

    private boolean isUsableToken(MtAccessToken token) {
        return token != null
                && Integer.valueOf(com.gogoga.backend.enums.MtAccessTokenAuthStatusEnum.VALID.getCode())
                        .equals(token.getAuthStatus())
                && StringUtils.isNotBlank(token.getAccessToken())
                && token.getExpiresAt() != null
                && token.getExpiresAt().isAfter(LocalDateTime.now());
    }

    /**
     * @param newlySent      本次真正新置为 SENT 的条数（0 或 1），用于安全递增门店累计条数
     * @param refreshedToken 本次发送中刷新出的新 token，供分片内后续消息复用；未刷新时为 null
     */
    private record SendOutcome(int newlySent, String refreshedToken) {
    }
}
