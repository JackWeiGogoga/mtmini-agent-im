package com.gogoga.backend.task;

import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.config.LlmRateLimitProperties;
import com.gogoga.backend.entity.mt.MtAsyncTask;
import com.gogoga.backend.enums.MtAsyncTaskTypeEnum;
import com.gogoga.backend.service.MtAsyncTaskService;
import com.gogoga.backend.service.MtAuthAsyncTaskService;
import com.gogoga.backend.service.MtImAutoReplyOrchestrationService;
import com.gogoga.backend.service.MtImBroadcastPlanService;
import com.gogoga.backend.service.MtImBroadcastSendService;
import com.gogoga.backend.service.KnowledgeDocumentService;
import com.gogoga.backend.service.RedisDistributedLockService;
import com.gogoga.backend.service.async.MtAsyncTaskDryRunException;
import com.gogoga.backend.service.async.MtAsyncTaskExecutionPolicy;
import com.gogoga.backend.service.async.MtAsyncTaskTerminalFailureException;
import com.gogoga.backend.service.async.MtAuthPostProcessTaskPayload;
import com.gogoga.backend.service.async.MtKnowledgeDocumentParseTaskPayload;
import com.gogoga.backend.service.async.MtImAutoReplyTaskPayload;
import com.gogoga.backend.service.async.MtImBroadcastPlanTaskPayload;
import com.gogoga.backend.service.async.MtImBroadcastSendTaskPayload;
import com.gogoga.backend.service.llm.LlmCapacityDeferredException;
import com.gogoga.backend.util.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 数据库异步任务 worker。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = {"mt.async-task.enabled", "mt.async-task.worker-enabled"}, havingValue = "true")
public class MtAsyncTaskWorkerTask {

    private static final List<MtAsyncTaskTypeEnum> ORDINARY_TASK_TYPES = List.of(
            MtAsyncTaskTypeEnum.AUTH_SYNC_RIGHTS,
            MtAsyncTaskTypeEnum.AUTH_BACKFILL_POI_ID,
            MtAsyncTaskTypeEnum.AUTH_BLOCK_ORDER_PUSH,
            MtAsyncTaskTypeEnum.AUTH_CONFIGURE_IM
    );

    private static final List<MtAsyncTaskTypeEnum> POLL_TASK_TYPES = List.of(
            MtAsyncTaskTypeEnum.AUTH_SYNC_RIGHTS,
            MtAsyncTaskTypeEnum.AUTH_BACKFILL_POI_ID,
            MtAsyncTaskTypeEnum.AUTH_BLOCK_ORDER_PUSH,
            MtAsyncTaskTypeEnum.AUTH_CONFIGURE_IM,
            MtAsyncTaskTypeEnum.KNOWLEDGE_DOCUMENT_PARSE,
            MtAsyncTaskTypeEnum.IM_AUTO_REPLY,
            MtAsyncTaskTypeEnum.IM_BROADCAST_PLAN,
            MtAsyncTaskTypeEnum.IM_BROADCAST_SEND
    );

    /**
     * 群发任务走独立线程池，并按 scopeKey（门店）轮转取任务，避免大商家饿死其他商家。
     */
    private static final List<MtAsyncTaskTypeEnum> BROADCAST_TASK_TYPES = List.of(
            MtAsyncTaskTypeEnum.IM_BROADCAST_PLAN,
            MtAsyncTaskTypeEnum.IM_BROADCAST_SEND
    );

    private final MtAsyncTaskService mtAsyncTaskService;
    private final MtAuthAsyncTaskService mtAuthAsyncTaskService;
    private final KnowledgeDocumentService knowledgeDocumentService;
    private final MtImAutoReplyOrchestrationService imAutoReplyOrchestrationService;
    private final MtImBroadcastPlanService imBroadcastPlanService;
    private final MtImBroadcastSendService imBroadcastSendService;
    private final RedisDistributedLockService redisDistributedLockService;
    private final MtProperties mtProperties;
    private final LlmRateLimitProperties llmRateLimitProperties;

    @Qualifier("mtAsyncTaskExecutor")
    private final Executor mtAsyncTaskExecutor;

    private final ThreadPoolTaskExecutor mtLlmTaskExecutor;
    private final ThreadPoolTaskExecutor mtBroadcastTaskExecutor;
    private final AtomicInteger llmScheduleCursor = new AtomicInteger();
    private final AtomicInteger broadcastScheduleCursor = new AtomicInteger();
    private final Map<MtAsyncTaskTypeEnum, String> llmScopeCursors = new ConcurrentHashMap<>();
    private final Map<MtAsyncTaskTypeEnum, String> broadcastScopeCursors = new ConcurrentHashMap<>();

    private final String workerInstanceId = "mt-async-" + UUID.randomUUID();

    public MtAsyncTaskWorkerTask(
            MtAsyncTaskService mtAsyncTaskService,
            MtAuthAsyncTaskService mtAuthAsyncTaskService,
            KnowledgeDocumentService knowledgeDocumentService,
            MtImAutoReplyOrchestrationService imAutoReplyOrchestrationService,
            MtImBroadcastPlanService imBroadcastPlanService,
            MtImBroadcastSendService imBroadcastSendService,
            RedisDistributedLockService redisDistributedLockService,
            MtProperties mtProperties,
            LlmRateLimitProperties llmRateLimitProperties,
            @Qualifier("mtAsyncTaskExecutor") Executor mtAsyncTaskExecutor,
            @Qualifier("mtLlmTaskExecutor") ThreadPoolTaskExecutor mtLlmTaskExecutor,
            @Qualifier("mtBroadcastTaskExecutor") ThreadPoolTaskExecutor mtBroadcastTaskExecutor
    ) {
        this.mtAsyncTaskService = mtAsyncTaskService;
        this.mtAuthAsyncTaskService = mtAuthAsyncTaskService;
        this.knowledgeDocumentService = knowledgeDocumentService;
        this.imAutoReplyOrchestrationService = imAutoReplyOrchestrationService;
        this.imBroadcastPlanService = imBroadcastPlanService;
        this.imBroadcastSendService = imBroadcastSendService;
        this.redisDistributedLockService = redisDistributedLockService;
        this.mtProperties = mtProperties;
        this.llmRateLimitProperties = llmRateLimitProperties;
        this.mtAsyncTaskExecutor = mtAsyncTaskExecutor;
        this.mtLlmTaskExecutor = mtLlmTaskExecutor;
        this.mtBroadcastTaskExecutor = mtBroadcastTaskExecutor;
    }

    @Scheduled(fixedDelayString = "${mt.async-task.poll-delay-ms:1000}")
    public void pollAsyncTasks() {
        ORDINARY_TASK_TYPES.forEach(this::submitExecutableTasks);
        submitAvailableLlmTasks();
        submitAvailableBroadcastTasks();
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
                    LocalDateTime.now(),
                    taskType == MtAsyncTaskTypeEnum.IM_AUTO_REPLY ? this::markImAutoReplyDead : null
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
            mtAsyncTaskExecutor.execute(() -> claimAndExecuteTask(taskType, taskId));
        }
    }

    private void submitAvailableLlmTasks() {
        int concurrency = Math.max(mtProperties.getAsyncTask().getLlmExecutorConcurrency(), 1);
        int availableSlots = Math.max(concurrency - mtLlmTaskExecutor.getActiveCount(), 0);
        for (int i = 0; i < availableSlots; i++) {
            ClaimedTask claimedTask = claimNextWeightedLlmTask();
            if (claimedTask == null) {
                return;
            }
            try {
                mtLlmTaskExecutor.execute(() -> executeClaimedTask(claimedTask.taskType(), claimedTask.task()));
            } catch (TaskRejectedException e) {
                LocalDateTime now = LocalDateTime.now();
                mtAsyncTaskService.rescheduleWithoutRetry(
                        claimedTask.task(),
                        now.plusSeconds(1),
                        "LLM_EXECUTOR_REJECTED",
                        now
                );
                log.warn("LLM任务执行器拒绝任务并已无损重排，taskType={}, taskId={}",
                        claimedTask.taskType().name(), claimedTask.task().getId());
                return;
            }
        }
    }

    private void submitAvailableBroadcastTasks() {
        if (!mtProperties.getImBroadcast().isEnabled()) {
            return;
        }
        int concurrency = Math.max(mtProperties.getImBroadcast().getExecutorConcurrency(), 1);
        int availableSlots = Math.max(concurrency - mtBroadcastTaskExecutor.getActiveCount(), 0);
        for (int i = 0; i < availableSlots; i++) {
            ClaimedTask claimedTask = claimNextBroadcastTask();
            if (claimedTask == null) {
                return;
            }
            try {
                mtBroadcastTaskExecutor.execute(() -> executeClaimedTask(claimedTask.taskType(), claimedTask.task()));
            } catch (TaskRejectedException e) {
                LocalDateTime now = LocalDateTime.now();
                mtAsyncTaskService.rescheduleWithoutRetry(
                        claimedTask.task(),
                        now.plusSeconds(1),
                        "BROADCAST_EXECUTOR_REJECTED",
                        now
                );
                log.warn("群发任务执行器拒绝任务并已无损重排，taskType={}, taskId={}",
                        claimedTask.taskType().name(), claimedTask.task().getId());
                return;
            }
        }
    }

    /**
     * 按门店 scopeKey 轮转领取，保证单个大商家的大量分片不会长期占满群发线程池。
     */
    private ClaimedTask claimNextBroadcastTask() {
        for (int i = 0; i < BROADCAST_TASK_TYPES.size(); i++) {
            int index = Math.floorMod(broadcastScheduleCursor.getAndIncrement(), BROADCAST_TASK_TYPES.size());
            MtAsyncTaskTypeEnum taskType = BROADCAST_TASK_TYPES.get(index);
            List<Long> taskIds = mtAsyncTaskService.selectExecutableFairTaskIds(
                    taskType,
                    broadcastScopeCursors.get(taskType),
                    1,
                    LocalDateTime.now()
            );
            if (taskIds.isEmpty()) {
                continue;
            }
            MtAsyncTask task = mtAsyncTaskService.claimTask(
                    taskType,
                    taskIds.get(0),
                    workerInstanceId + "-broadcast-dispatcher",
                    LocalDateTime.now()
            );
            if (task != null) {
                broadcastScopeCursors.put(taskType, fairScopeKey(task));
                return new ClaimedTask(taskType, task);
            }
        }
        return null;
    }

    private ClaimedTask claimNextWeightedLlmTask() {
        List<MtAsyncTaskTypeEnum> schedule = weightedLlmSchedule();
        if (schedule.isEmpty()) {
            return null;
        }
        for (int i = 0; i < schedule.size(); i++) {
            int index = Math.floorMod(llmScheduleCursor.getAndIncrement(), schedule.size());
            MtAsyncTaskTypeEnum taskType = schedule.get(index);
            List<Long> taskIds = mtAsyncTaskService.selectExecutableFairTaskIds(
                    taskType,
                    llmScopeCursors.get(taskType),
                    1,
                    LocalDateTime.now()
            );
            if (taskIds.isEmpty()) {
                continue;
            }
            Long taskId = taskIds.get(0);
            MtAsyncTask task = mtAsyncTaskService.claimTask(
                    taskType,
                    taskId,
                    workerInstanceId + "-llm-dispatcher",
                    LocalDateTime.now()
            );
            if (task != null) {
                llmScopeCursors.put(taskType, fairScopeKey(task));
                return new ClaimedTask(taskType, task);
            }
        }
        return null;
    }

    List<MtAsyncTaskTypeEnum> weightedLlmSchedule() {
        MtProperties.LlmTaskWeights weights = mtProperties.getAsyncTask().getLlmTaskWeights();
        List<MtAsyncTaskTypeEnum> schedule = new ArrayList<>();
        addWeight(schedule, MtAsyncTaskTypeEnum.KNOWLEDGE_DOCUMENT_PARSE, weights.getKnowledgeDocumentParse());
        addWeight(schedule, MtAsyncTaskTypeEnum.IM_AUTO_REPLY, weights.getImAutoReply());
        return schedule;
    }

    private void addWeight(List<MtAsyncTaskTypeEnum> schedule, MtAsyncTaskTypeEnum taskType, int weight) {
        for (int i = 0; i < Math.max(weight, 0); i++) {
            schedule.add(taskType);
        }
    }

    private void claimAndExecuteTask(MtAsyncTaskTypeEnum taskType, Long taskId) {
        String workerId = workerInstanceId + "-" + Thread.currentThread().getName();
        MtAsyncTask task = mtAsyncTaskService.claimTask(taskType, taskId, workerId, LocalDateTime.now());
        if (task == null) {
            return;
        }
        executeClaimedTask(taskType, task);
    }

    void executeClaimedTask(MtAsyncTaskTypeEnum taskType, MtAsyncTask task) {
        Long taskId = task.getId();
        try {
            switch (taskType) {
                case AUTH_SYNC_RIGHTS -> executeAuthSyncRightsTask(task);
                case AUTH_BACKFILL_POI_ID -> executeAuthBackfillPoiIdTask(task);
                case AUTH_BLOCK_ORDER_PUSH -> executeAuthBlockOrderPushTask(task);
                case AUTH_CONFIGURE_IM -> executeAuthConfigureImTask(task);
                case KNOWLEDGE_DOCUMENT_PARSE -> executeKnowledgeDocumentParseTask(task);
                case IM_AUTO_REPLY -> executeImAutoReplyTask(task);
                case IM_BROADCAST_PLAN -> executeImBroadcastPlanTask(task);
                case IM_BROADCAST_SEND -> executeImBroadcastSendTask(task);
                default -> throw new IllegalStateException("不支持的任务类型: " + taskType.name());
            }
            mtAsyncTaskService.markSuccess(taskType, taskId, LocalDateTime.now());
        } catch (LlmCapacityDeferredException e) {
            LocalDateTime now = LocalDateTime.now();
            long jitterMillis = randomJitterMillis();
            LocalDateTime nextAvailableAt = now.plusNanos(
                    Math.max(e.getRetryAfterMillis() + jitterMillis, 1L) * 1_000_000L);
            mtAsyncTaskService.rescheduleWithoutRetry(
                    task,
                    nextAvailableAt,
                    "LLM_CAPACITY_DEFERRED",
                    now
            );
            log.info("LLM任务容量等待并已无损重排，taskType={}, taskId={}, nextAvailableAt={}",
                    taskType.name(), taskId, nextAvailableAt);
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
            String errorSummary;
            if (taskType == MtAsyncTaskTypeEnum.IM_AUTO_REPLY) {
                errorSummary = "IM_AUTO_REPLY_EXECUTION_FAILED:" + e.getClass().getSimpleName();
                log.error("IM自动回复任务执行失败，taskId={}, errorType={}", taskId, e.getClass().getSimpleName());
            } else {
                errorSummary = e.getMessage();
                log.error("数据库异步任务执行失败，taskType={}, taskId={}", taskType.name(), taskId, e);
            }
            boolean dead = mtAsyncTaskService.handleFailure(
                    task, resolvePolicy(taskType), errorSummary, LocalDateTime.now());
            if (dead && taskType == MtAsyncTaskTypeEnum.IM_AUTO_REPLY) {
                markImAutoReplyDead(task);
            }
        }
    }

    private void markImAutoReplyDead(MtAsyncTask task) {
        if (task == null) {
            return;
        }
        MtImAutoReplyTaskPayload payload = JsonUtil.toBean(task.getPayloadJson(), MtImAutoReplyTaskPayload.class);
        imAutoReplyOrchestrationService.markDeadAfterRetryExhausted(payload);
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

    private void executeAuthConfigureImTask(MtAsyncTask task) {
        mtAuthAsyncTaskService.executeAuthConfigureImTask(parseAuthPayload(task));
    }

    private void executeKnowledgeDocumentParseTask(MtAsyncTask task) {
        MtKnowledgeDocumentParseTaskPayload payload = JsonUtil.toBean(task.getPayloadJson(), MtKnowledgeDocumentParseTaskPayload.class);
        knowledgeDocumentService.executeDocumentParseTask(payload);
    }

    private void executeImAutoReplyTask(MtAsyncTask task) {
        MtImAutoReplyTaskPayload payload = JsonUtil.toBean(task.getPayloadJson(), MtImAutoReplyTaskPayload.class);
        imAutoReplyOrchestrationService.execute(payload);
    }

    private void executeImBroadcastPlanTask(MtAsyncTask task) {
        MtImBroadcastPlanTaskPayload payload = JsonUtil.toBean(task.getPayloadJson(), MtImBroadcastPlanTaskPayload.class);
        imBroadcastPlanService.execute(payload);
    }

    private void executeImBroadcastSendTask(MtAsyncTask task) {
        MtImBroadcastSendTaskPayload payload = JsonUtil.toBean(task.getPayloadJson(), MtImBroadcastSendTaskPayload.class);
        imBroadcastSendService.execute(payload);
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
            case AUTH_CONFIGURE_IM -> resolvePolicy(asyncTask.getAuthConfigureIm());
            case AUTH_BLOCK_ORDER_PUSH -> MtAsyncTaskExecutionPolicy.builder()
                    .maxRetryCount(0)
                    .timeout(Duration.ofSeconds(180))
                    .initialBackoff(Duration.ofSeconds(60))
                    .maxBackoff(Duration.ofSeconds(60))
                    .build();
            case KNOWLEDGE_DOCUMENT_PARSE -> resolvePolicy(asyncTask.getKnowledgeDocumentParse());
            case IM_AUTO_REPLY -> resolvePolicy(asyncTask.getImAutoReply());
            case IM_BROADCAST_PLAN -> resolvePolicy(mtProperties.getImBroadcast().getPlanTask());
            case IM_BROADCAST_SEND -> resolvePolicy(mtProperties.getImBroadcast().getSendTask());
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

    private long randomJitterMillis() {
        long maxJitter = Math.max(llmRateLimitProperties.getRescheduleJitterMillis(), 0L);
        return maxJitter <= 0L ? 0L : ThreadLocalRandom.current().nextLong(maxJitter + 1L);
    }

    private String fairScopeKey(MtAsyncTask task) {
        if (task.getScopeKey() == null || task.getScopeKey().isBlank()) {
            return "__task__" + task.getId();
        }
        return task.getScopeKey();
    }

    private record ClaimedTask(MtAsyncTaskTypeEnum taskType, MtAsyncTask task) {
    }

}
