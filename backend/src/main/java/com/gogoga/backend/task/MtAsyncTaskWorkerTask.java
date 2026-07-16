package com.gogoga.backend.task;

import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.entity.mt.MtAsyncTask;
import com.gogoga.backend.enums.MtAsyncTaskTypeEnum;
import com.gogoga.backend.service.MtAsyncTaskService;
import com.gogoga.backend.service.MtAuthAsyncTaskService;
import com.gogoga.backend.service.MtCommentAutoReplyService;
import com.gogoga.backend.service.MtCommentComplaintService;
import com.gogoga.backend.service.MtCommentImFollowUpService;
import com.gogoga.backend.service.CommentReplyKnowledgeDocumentService;
import com.gogoga.backend.service.RedisDistributedLockService;
import com.gogoga.backend.service.async.MtAsyncTaskDryRunException;
import com.gogoga.backend.service.async.MtAsyncTaskExecutionPolicy;
import com.gogoga.backend.service.async.MtAsyncTaskTerminalFailureException;
import com.gogoga.backend.service.async.MtAuthPostProcessTaskPayload;
import com.gogoga.backend.service.async.MtCommentAutoComplaintTaskPayload;
import com.gogoga.backend.service.async.MtCommentAutoImFollowUpTaskPayload;
import com.gogoga.backend.service.async.MtCommentAutoReplyTaskPayload;
import com.gogoga.backend.service.async.MtKnowledgeDocumentParseTaskPayload;
import com.gogoga.backend.util.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * 数据库异步任务 worker。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = {"mt.async-task.enabled", "mt.async-task.worker-enabled"}, havingValue = "true")
public class MtAsyncTaskWorkerTask {

    private static final List<MtAsyncTaskTypeEnum> POLL_TASK_TYPES = List.of(
            MtAsyncTaskTypeEnum.AUTH_SYNC_RIGHTS,
            MtAsyncTaskTypeEnum.AUTH_BACKFILL_POI_ID,
            MtAsyncTaskTypeEnum.AUTH_BLOCK_ORDER_PUSH,
            MtAsyncTaskTypeEnum.AUTH_COMMENT_BACKFILL,
            MtAsyncTaskTypeEnum.COMMENT_AUTO_REPLY,
            MtAsyncTaskTypeEnum.COMMENT_AUTO_COMPLAINT,
            MtAsyncTaskTypeEnum.COMMENT_AUTO_IM_FOLLOW_UP,
            MtAsyncTaskTypeEnum.KNOWLEDGE_DOCUMENT_PARSE
    );

    private final MtAsyncTaskService mtAsyncTaskService;
    private final MtAuthAsyncTaskService mtAuthAsyncTaskService;
    private final MtCommentAutoReplyService mtCommentAutoReplyService;
    private final MtCommentComplaintService mtCommentComplaintService;
    private final MtCommentImFollowUpService mtCommentImFollowUpService;
    private final CommentReplyKnowledgeDocumentService commentReplyKnowledgeDocumentService;
    private final RedisDistributedLockService redisDistributedLockService;
    private final MtProperties mtProperties;

    @Qualifier("mtAsyncTaskExecutor")
    private final Executor mtAsyncTaskExecutor;

    private final String workerInstanceId = "mt-async-" + UUID.randomUUID();

    public MtAsyncTaskWorkerTask(
            MtAsyncTaskService mtAsyncTaskService,
            MtAuthAsyncTaskService mtAuthAsyncTaskService,
            MtCommentAutoReplyService mtCommentAutoReplyService,
            MtCommentComplaintService mtCommentComplaintService,
            MtCommentImFollowUpService mtCommentImFollowUpService,
            CommentReplyKnowledgeDocumentService commentReplyKnowledgeDocumentService,
            RedisDistributedLockService redisDistributedLockService,
            MtProperties mtProperties,
            @Qualifier("mtAsyncTaskExecutor") Executor mtAsyncTaskExecutor
    ) {
        this.mtAsyncTaskService = mtAsyncTaskService;
        this.mtAuthAsyncTaskService = mtAuthAsyncTaskService;
        this.mtCommentAutoReplyService = mtCommentAutoReplyService;
        this.mtCommentComplaintService = mtCommentComplaintService;
        this.mtCommentImFollowUpService = mtCommentImFollowUpService;
        this.commentReplyKnowledgeDocumentService = commentReplyKnowledgeDocumentService;
        this.redisDistributedLockService = redisDistributedLockService;
        this.mtProperties = mtProperties;
        this.mtAsyncTaskExecutor = mtAsyncTaskExecutor;
    }

    @Scheduled(fixedDelayString = "${mt.async-task.poll-delay-ms:1000}")
    public void pollAsyncTasks() {
        POLL_TASK_TYPES.forEach(this::submitExecutableTasks);
    }

    @Scheduled(fixedDelayString = "${mt.async-task.recovery-delay-ms:30000}")
    public void recoverStaleTasks() {
        MtProperties.AsyncTask asyncTask = mtProperties.getAsyncTask();
        int recoveredCount = 0;
        for (MtAsyncTaskTypeEnum taskType : POLL_TASK_TYPES) {
            recoveredCount += mtAsyncTaskService.recoverStaleTasks(
                    taskType,
                    resolvePolicy(taskType),
                    asyncTask.getRecoveryLimit(),
                    LocalDateTime.now()
            );
        }
        if (recoveredCount > 0) {
            log.warn("数据库异步任务完成超时回收，recoveredCount={}", recoveredCount);
        }
    }

    @Scheduled(fixedDelayString = "${mt.async-task.cleanup.delay-ms:3600000}")
    public void cleanupHistoricalTasks() {
        MtProperties.Cleanup cleanup = mtProperties.getAsyncTask().getCleanup();
        if (cleanup == null || !cleanup.isEnabled()) {
            return;
        }
        redisDistributedLockService.tryRun(
                "MtAsyncTaskCleanupTask",
                Duration.ofSeconds(cleanup.getLockSeconds()),
                () -> {
                    LocalDateTime now = LocalDateTime.now();
                    int deletedCount = mtAsyncTaskService.cleanupHistoricalTerminalTasks(
                            now.minusDays(cleanup.getSuccessRetentionDays()),
                            now.minusDays(cleanup.getDeadRetentionDays()),
                            cleanup.getBatchSize()
                    );
                    if (deletedCount > 0) {
                    log.info("数据库异步任务历史清理完成，deletedCount={}", deletedCount);
                    }
                }
        );
    }

    private void submitExecutableTasks(MtAsyncTaskTypeEnum taskType) {
        MtProperties.AsyncTask asyncTask = mtProperties.getAsyncTask();
        List<Long> taskIds = mtAsyncTaskService.selectExecutableTaskIds(taskType, asyncTask.getPollSize(), LocalDateTime.now());
        for (Long taskId : taskIds) {
            mtAsyncTaskExecutor.execute(() -> executeClaimedTask(taskType, taskId));
        }
    }

    private void executeClaimedTask(MtAsyncTaskTypeEnum taskType, Long taskId) {
        String workerId = workerInstanceId + "-" + Thread.currentThread().getName();
        MtAsyncTask task = mtAsyncTaskService.claimTask(taskType, taskId, workerId, LocalDateTime.now());
        if (task == null) {
            return;
        }
        try {
            switch (taskType) {
                case AUTH_SYNC_RIGHTS -> executeAuthSyncRightsTask(task);
                case AUTH_BACKFILL_POI_ID -> executeAuthBackfillPoiIdTask(task);
                case AUTH_BLOCK_ORDER_PUSH -> executeAuthBlockOrderPushTask(task);
                case AUTH_COMMENT_BACKFILL -> executeAuthCommentBackfillTask(task);
                case COMMENT_AUTO_REPLY -> executeCommentAutoReplyTask(task);
                case COMMENT_AUTO_COMPLAINT -> executeCommentAutoComplaintTask(task);
                case COMMENT_AUTO_IM_FOLLOW_UP -> executeCommentAutoImFollowUpTask(task);
                case KNOWLEDGE_DOCUMENT_PARSE -> executeKnowledgeDocumentParseTask(task);
                default -> throw new IllegalStateException("不支持的任务类型: " + taskType.name());
            }
            mtAsyncTaskService.markSuccess(taskType, taskId, LocalDateTime.now());
        } catch (MtAsyncTaskDryRunException e) {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime nextAvailableAt = now.plusSeconds(e.getRescheduleSeconds());
            mtAsyncTaskService.rescheduleWithoutRetry(task, nextAvailableAt, e.getMessage(), now);
            log.warn("数据库异步任务 dry run 跳过执行，taskType={}, taskId={}, nextAvailableAt={}",
                    taskType.name(), taskId, nextAvailableAt);
        } catch (MtAsyncTaskTerminalFailureException e) {
            log.warn("数据库异步任务业务终态失败，taskType={}, taskId={}, error={}",
                    taskType.name(), taskId, e.getMessage());
            mtAsyncTaskService.markTerminalFailure(task, e.getMessage(), LocalDateTime.now());
        } catch (Exception e) {
            log.error("数据库异步任务执行失败，taskType={}, taskId={}", taskType.name(), taskId, e);
            mtAsyncTaskService.handleFailure(task, resolvePolicy(taskType), e.getMessage(), LocalDateTime.now());
        }
    }

    private void executeAuthSyncRightsTask(MtAsyncTask task) {
        mtAuthAsyncTaskService.executeAuthSyncRightsTask(parseAuthPayload(task));
    }

    private void executeAuthBackfillPoiIdTask(MtAsyncTask task) {
        mtAuthAsyncTaskService.executeAuthBackfillPoiIdTask(parseAuthPayload(task));
    }

    private void executeAuthBlockOrderPushTask(MtAsyncTask task) {
        mtAuthAsyncTaskService.executeAuthBlockOrderPushTask(parseAuthPayload(task));
    }

    private void executeAuthCommentBackfillTask(MtAsyncTask task) {
        mtCommentAutoReplyService.executeAuthCommentBackfillTask(parseAuthPayload(task));
    }

    private void executeCommentAutoReplyTask(MtAsyncTask task) {
        MtCommentAutoReplyTaskPayload payload = JsonUtil.toBean(task.getPayloadJson(), MtCommentAutoReplyTaskPayload.class);
        mtCommentAutoReplyService.executeCommentAutoReplyTask(payload);
    }

    private void executeCommentAutoComplaintTask(MtAsyncTask task) {
        MtCommentAutoComplaintTaskPayload payload = JsonUtil.toBean(task.getPayloadJson(), MtCommentAutoComplaintTaskPayload.class);
        mtCommentComplaintService.executeCommentAutoComplaintTask(payload);
    }

    private void executeCommentAutoImFollowUpTask(MtAsyncTask task) {
        MtCommentAutoImFollowUpTaskPayload payload = JsonUtil.toBean(
                task.getPayloadJson(), MtCommentAutoImFollowUpTaskPayload.class);
        mtCommentImFollowUpService.executeCommentAutoImFollowUpTask(payload);
    }

    private void executeKnowledgeDocumentParseTask(MtAsyncTask task) {
        MtKnowledgeDocumentParseTaskPayload payload = JsonUtil.toBean(task.getPayloadJson(), MtKnowledgeDocumentParseTaskPayload.class);
        commentReplyKnowledgeDocumentService.executeDocumentParseTask(payload);
    }

    private MtAuthPostProcessTaskPayload parseAuthPayload(MtAsyncTask task) {
        MtAuthPostProcessTaskPayload payload = JsonUtil.toBean(task.getPayloadJson(), MtAuthPostProcessTaskPayload.class);
        if (payload == null || payload.getShopId() == null) {
            throw new IllegalArgumentException("AUTH_TASK_PAYLOAD_INVALID");
        }
        return payload;
    }

    private MtAsyncTaskExecutionPolicy resolvePolicy(MtAsyncTaskTypeEnum taskType) {
        MtProperties.AsyncTask asyncTask = mtProperties.getAsyncTask();
        return switch (taskType) {
            case AUTH_SYNC_RIGHTS -> resolvePolicy(asyncTask.getAuthSyncRights());
            case AUTH_BACKFILL_POI_ID -> resolvePolicy(asyncTask.getAuthBackfillPoiId());
            case AUTH_COMMENT_BACKFILL -> resolvePolicy(asyncTask.getAuthCommentBackfill());
            case AUTH_BLOCK_ORDER_PUSH -> MtAsyncTaskExecutionPolicy.builder()
                    .maxRetryCount(0)
                    .timeout(Duration.ofSeconds(180))
                    .initialBackoff(Duration.ofSeconds(60))
                    .maxBackoff(Duration.ofSeconds(60))
                    .build();
            case COMMENT_AUTO_REPLY -> resolvePolicy(asyncTask.getCommentAutoReply());
            case COMMENT_AUTO_COMPLAINT -> resolvePolicy(asyncTask.getCommentAutoComplaint());
            case COMMENT_AUTO_IM_FOLLOW_UP -> resolvePolicy(asyncTask.getCommentAutoImFollowUp());
            case KNOWLEDGE_DOCUMENT_PARSE -> resolvePolicy(asyncTask.getKnowledgeDocumentParse());
        };
    }

    private MtAsyncTaskExecutionPolicy resolvePolicy(MtProperties.TaskPolicy taskPolicy) {
        return MtAsyncTaskExecutionPolicy.builder()
                .maxRetryCount(taskPolicy.getMaxRetryCount())
                .timeout(Duration.ofSeconds(taskPolicy.getTimeoutSeconds()))
                .initialBackoff(Duration.ofSeconds(taskPolicy.getInitialBackoffSeconds()))
                .maxBackoff(Duration.ofSeconds(taskPolicy.getMaxBackoffSeconds()))
                .build();
    }

}
