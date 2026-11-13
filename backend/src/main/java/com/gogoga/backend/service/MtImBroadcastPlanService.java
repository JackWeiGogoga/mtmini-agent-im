package com.gogoga.backend.service;

import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.dto.ImBroadcastTargetDTO;
import com.gogoga.backend.dto.SensitiveWordHitDTO;
import com.gogoga.backend.entity.mt.MtImBroadcastRecord;
import com.gogoga.backend.entity.mt.MtImBroadcastTask;
import com.gogoga.backend.enums.ImBroadcastRecordStatusEnum;
import com.gogoga.backend.enums.ImBroadcastTaskStatusEnum;
import com.gogoga.backend.enums.MtAsyncTaskTypeEnum;
import com.gogoga.backend.mapper.MtImBroadcastRecordMapper;
import com.gogoga.backend.mapper.MtImBroadcastTaskMapper;
import com.gogoga.backend.mapper.MtImConversationMapper;
import com.gogoga.backend.service.async.MtAsyncTaskTerminalFailureException;
import com.gogoga.backend.service.async.MtImBroadcastPlanTaskPayload;
import com.gogoga.backend.service.async.MtImBroadcastSendTaskPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 群发目标人群编排：游标翻页物化发送明细，并按分片投递发送任务。
 *
 * <p>全程只在内存中保留一页目标，门店联系人再多也不会撑爆内存。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MtImBroadcastPlanService {

    static final String SKIP_NO_TARGET = "NO_TARGET";
    static final String SKIP_SENSITIVE_WORD = "SENSITIVE_WORD_HIT";

    private final MtProperties mtProperties;
    private final MtImBroadcastTaskMapper taskMapper;
    private final MtImBroadcastRecordMapper recordMapper;
    private final MtImConversationMapper conversationMapper;
    private final MtImBroadcastReadinessService readinessService;
    private final SensitiveWordService sensitiveWordService;
    private final ImOutboundMessageIdGenerator outboundMessageIdGenerator;
    private final MtAsyncTaskService asyncTaskService;

    public void execute(MtImBroadcastPlanTaskPayload payload) {
        if (payload == null || payload.getBroadcastTaskId() == null) {
            throw new MtAsyncTaskTerminalFailureException("IM_BROADCAST_PLAN_PAYLOAD_INVALID");
        }
        MtImBroadcastTask task = taskMapper.selectById(payload.getBroadcastTaskId());
        if (task == null) {
            throw new MtAsyncTaskTerminalFailureException("IM_BROADCAST_TASK_NOT_FOUND");
        }
        if (!ImBroadcastTaskStatusEnum.PENDING.name().equals(task.getStatus())) {
            // 已编排过，重试时直接幂等返回，避免重复物化明细。
            return;
        }

        String notReadyReason = readinessService.checkReadiness(task.getShopId());
        if (notReadyReason != null) {
            skip(task, notReadyReason);
            return;
        }
        SensitiveWordHitDTO hit = sensitiveWordService.detect(task.getContent());
        if (hit != null && hit.isHit()) {
            // 文案级检测只做一次，命中即整批不发，不必等到逐条发送时反复检测同一段固定文案。
            skip(task, SKIP_SENSITIVE_WORD);
            return;
        }

        PlanResult result = materializeTargets(task);
        if (result.targetCount() == 0) {
            skip(task, SKIP_NO_TARGET);
            return;
        }
        taskMapper.markPlanned(task.getId(), result.targetCount(), result.truncatedCount(),
                result.shardCount(), LocalDateTime.now());
        for (int shardNo = 0; shardNo < result.shardCount(); shardNo++) {
            asyncTaskService.enqueueTask(
                    MtAsyncTaskTypeEnum.IM_BROADCAST_SEND,
                    sendBizKey(task.getId(), shardNo),
                    String.valueOf(task.getShopId()),
                    new MtImBroadcastSendTaskPayload(task.getId(), task.getShopId(), shardNo),
                    LocalDateTime.now(),
                    mtProperties.getImBroadcast().getSendTask().getMaxRetryCount()
            );
        }
        log.info("群发目标编排完成，broadcastTaskId={}, shopId={}, targetCount={}, truncatedCount={}, shardCount={}",
                task.getId(), task.getShopId(), result.targetCount(), result.truncatedCount(), result.shardCount());
    }

    public static String sendBizKey(Long broadcastTaskId, int shardNo) {
        return "IM_BROADCAST_SEND:" + broadcastTaskId + ":" + shardNo;
    }

    private PlanResult materializeTargets(MtImBroadcastTask task) {
        MtProperties.ImBroadcast config = mtProperties.getImBroadcast();
        int maxTargets = Math.max(config.getMaxTargetsPerBatch(), 1);
        int pageSize = Math.max(config.getPlanPageSize(), 1);
        int shardSize = Math.max(config.getShardSize(), 1);
        LocalDateTime activeSince = LocalDateTime.now().minusDays(Math.max(config.getActiveWithinDays(), 1));
        LocalDateTime now = LocalDateTime.now();

        LocalDateTime cursorTime = null;
        Long cursorId = null;
        int planned = 0;
        List<MtImBroadcastRecord> buffer = new ArrayList<>(pageSize);

        while (planned < maxTargets) {
            int limit = Math.min(pageSize, maxTargets - planned);
            List<ImBroadcastTargetDTO> page = conversationMapper.selectBroadcastTargets(
                    task.getShopId(), activeSince, cursorTime, cursorId, limit);
            if (page == null || page.isEmpty()) {
                break;
            }
            for (ImBroadcastTargetDTO target : page) {
                MtImBroadcastRecord record = new MtImBroadcastRecord();
                record.setTaskId(task.getId());
                record.setShopId(task.getShopId());
                record.setConversationId(target.getConversationId());
                record.setOpenUserId(target.getOpenUserId());
                record.setShardNo(planned / shardSize);
                // 出站消息 ID 在编排阶段就固化，分片重试时复用同一个，天然对上游幂等。
                record.setOutboundMessageId(outboundMessageIdGenerator.nextId());
                record.setStatus(ImBroadcastRecordStatusEnum.PENDING.name());
                record.setCreateTime(now);
                record.setUpdateTime(now);
                buffer.add(record);
                planned++;
            }
            recordMapper.batchInsertIgnore(buffer);
            buffer.clear();

            ImBroadcastTargetDTO last = page.get(page.size() - 1);
            cursorTime = last.getLastUserMessageAt();
            cursorId = last.getConversationId();
            if (page.size() < limit) {
                break;
            }
        }

        int truncated = 0;
        if (planned >= maxTargets) {
            // 只有触顶时才付出一次索引内 COUNT 的代价，用于如实回填被截断人数。
            long total = conversationMapper.countBroadcastTargets(task.getShopId(), activeSince);
            truncated = (int) Math.max(0L, total - planned);
        }
        int shardCount = (planned + shardSize - 1) / shardSize;
        return new PlanResult(planned, truncated, shardCount);
    }

    private void skip(MtImBroadcastTask task, String reason) {
        taskMapper.markSkipped(task.getId(), ImBroadcastTaskStatusEnum.SKIPPED.name(), reason, LocalDateTime.now());
        log.info("群发批次跳过，broadcastTaskId={}, shopId={}, reason={}", task.getId(), task.getShopId(), reason);
    }

    private record PlanResult(int targetCount, int truncatedCount, int shardCount) {
    }
}
