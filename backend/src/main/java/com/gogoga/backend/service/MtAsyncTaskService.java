package com.gogoga.backend.service;

import com.gogoga.backend.entity.mt.MtAsyncTask;
import com.gogoga.backend.entity.mt.MtAsyncTaskBacklogSummary;
import com.gogoga.backend.enums.MtAsyncTaskStatusEnum;
import com.gogoga.backend.enums.MtAsyncTaskTypeEnum;
import com.gogoga.backend.exception.BusinessException;
import com.gogoga.backend.mapper.MtAsyncTaskMapper;
import com.gogoga.backend.service.async.MtAsyncTaskEnqueueResult;
import com.gogoga.backend.service.async.MtAsyncTaskExecutionPolicy;
import com.gogoga.backend.util.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * 数据库异步任务服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MtAsyncTaskService {

    private static final int LAST_ERROR_MAX_LENGTH = 1000;

    private final MtAsyncTaskMapper mtAsyncTaskMapper;
    private final MtAsyncTaskMetricsService mtAsyncTaskMetricsService;

    public MtAsyncTaskEnqueueResult enqueueTask(
            MtAsyncTaskTypeEnum taskType,
            String bizKey,
            String scopeKey,
            Object payload,
            LocalDateTime availableAt,
            int maxRetryCount
    ) {
        if (taskType == null) {
            throw new IllegalArgumentException("taskType 不能为空");
        }
        if (StringUtils.isBlank(bizKey)) {
            throw new IllegalArgumentException("bizKey 不能为空");
        }
        LocalDateTime now = LocalDateTime.now();
        MtAsyncTask task = new MtAsyncTask();
        task.setTaskType(taskType.name());
        task.setBizKey(bizKey);
        task.setScopeKey(scopeKey);
        task.setPayloadJson(payload == null ? null : JsonUtil.toJson(payload));
        task.setStatus(MtAsyncTaskStatusEnum.PENDING.name());
        task.setRetryCount(0);
        task.setMaxRetryCount(Math.max(maxRetryCount, 0));
        task.setAvailableAt(availableAt == null ? now : availableAt);
        task.setCreateTime(now);
        task.setUpdateTime(now);
        try {
            mtAsyncTaskMapper.insert(task);
            mtAsyncTaskMetricsService.increment(taskType.name(), "enqueue");
            log.info("数据库异步任务入队成功，taskType={}, bizKey={}, scopeKey={}, taskId={}",
                    taskType.name(), bizKey, scopeKey, task.getId());
            return new MtAsyncTaskEnqueueResult(task.getId(), true);
        } catch (DuplicateKeyException e) {
            mtAsyncTaskMetricsService.increment(taskType.name(), "enqueue_duplicate");
            log.info("数据库异步任务重复入队，taskType={}, bizKey={}, scopeKey={}",
                    taskType.name(), bizKey, scopeKey);
            MtAsyncTask existingTask = mtAsyncTaskMapper.selectByTaskTypeAndBizKey(taskType.name(), bizKey);
            return new MtAsyncTaskEnqueueResult(existingTask == null ? null : existingTask.getId(), false);
        }
    }

    public List<Long> selectExecutableTaskIds(MtAsyncTaskTypeEnum taskType, int limit, LocalDateTime now) {
        if (taskType == null || limit <= 0) {
            return Collections.emptyList();
        }
        return mtAsyncTaskMapper.selectExecutableIds(taskType.name(), now == null ? LocalDateTime.now() : now, limit);
    }

    public List<Long> selectExecutableFairTaskIds(
            MtAsyncTaskTypeEnum taskType,
            String afterScopeKey,
            int limit,
            LocalDateTime now
    ) {
        if (taskType == null || limit <= 0) {
            return Collections.emptyList();
        }
        return mtAsyncTaskMapper.selectExecutableFairIds(
                taskType.name(),
                now == null ? LocalDateTime.now() : now,
                StringUtils.defaultString(afterScopeKey),
                limit
        );
    }

    public MtAsyncTask selectByTaskTypeAndBizKey(MtAsyncTaskTypeEnum taskType, String bizKey) {
        if (taskType == null || StringUtils.isBlank(bizKey)) {
            return null;
        }
        return mtAsyncTaskMapper.selectByTaskTypeAndBizKey(taskType.name(), bizKey);
    }

    public MtAsyncTaskEnqueueResult enqueueOrResetForManualRetry(
            MtAsyncTaskTypeEnum taskType,
            String bizKey,
            String scopeKey,
            Object payload,
            int maxRetryCount
    ) {
        MtAsyncTask existingTask = selectByTaskTypeAndBizKey(taskType, bizKey);
        if (existingTask == null) {
            return enqueueTask(taskType, bizKey, scopeKey, payload, LocalDateTime.now(), maxRetryCount);
        }
        if (StringUtils.equals(existingTask.getStatus(), MtAsyncTaskStatusEnum.PROCESSING.name())) {
            throw new BusinessException(409, "任务正在处理中，请稍后再试");
        }
        LocalDateTime now = LocalDateTime.now();
        int updated = mtAsyncTaskMapper.resetForManualRetry(
                existingTask.getId(),
                taskType.name(),
                Math.max(maxRetryCount, 0),
                now
        );
        if (updated != 1) {
            throw new BusinessException(409, "任务状态已变化，请刷新后重试");
        }
        mtAsyncTaskMetricsService.increment(taskType.name(), "manual_retry");
        log.info("数据库异步任务已由管理员重新入队，taskType={}, bizKey={}, scopeKey={}, taskId={}",
                taskType.name(), bizKey, scopeKey, existingTask.getId());
        return new MtAsyncTaskEnqueueResult(existingTask.getId(), false);
    }

    public MtAsyncTask claimTask(MtAsyncTaskTypeEnum taskType, Long taskId, String workerId, LocalDateTime claimedAt) {
        if (taskType == null || taskId == null || StringUtils.isBlank(workerId)) {
            return null;
        }
        LocalDateTime now = claimedAt == null ? LocalDateTime.now() : claimedAt;
        int updated = mtAsyncTaskMapper.claimPendingById(taskId, taskType.name(), workerId, now);
        if (updated <= 0) {
            return null;
        }
        MtAsyncTask task = mtAsyncTaskMapper.selectById(taskId);
        mtAsyncTaskMetricsService.increment(taskType.name(), "claim");
        log.info("数据库异步任务领取成功，taskType={}, taskId={}, workerId={}", taskType.name(), taskId, workerId);
        return task;
    }

    public void markSuccess(MtAsyncTaskTypeEnum taskType, Long taskId, LocalDateTime finishedAt) {
        if (taskType == null || taskId == null) {
            return;
        }
        LocalDateTime now = finishedAt == null ? LocalDateTime.now() : finishedAt;
        mtAsyncTaskMapper.markSuccess(taskId, taskType.name(), now);
        mtAsyncTaskMetricsService.increment(taskType.name(), "success");
        log.info("数据库异步任务执行成功，taskType={}, taskId={}", taskType.name(), taskId);
    }

    public void rescheduleWithoutRetry(MtAsyncTask task, LocalDateTime availableAt, String reason, LocalDateTime now) {
        if (task == null) {
            return;
        }
        LocalDateTime currentTime = now == null ? LocalDateTime.now() : now;
        LocalDateTime nextAvailableAt = availableAt == null ? currentTime : availableAt;
        String safeErrorSummary = StringUtils.left(StringUtils.defaultIfBlank(reason, "RESCHEDULED"), LAST_ERROR_MAX_LENGTH);
        mtAsyncTaskMapper.rescheduleFromProcessing(
                task.getId(),
                task.getTaskType(),
                defaultRetryCount(task),
                nextAvailableAt,
                safeErrorSummary,
                currentTime
        );
        mtAsyncTaskMetricsService.increment(task.getTaskType(), "reschedule");
        log.info("数据库异步任务无损重排，taskType={}, taskId={}, nextAvailableAt={}, reason={}",
                task.getTaskType(), task.getId(), nextAvailableAt, safeErrorSummary);
    }

    public boolean handleFailure(
            MtAsyncTask task,
            MtAsyncTaskExecutionPolicy policy,
            String errorSummary,
            LocalDateTime now
    ) {
        if (task == null || policy == null) {
            return false;
        }
        LocalDateTime currentTime = now == null ? LocalDateTime.now() : now;
        String safeErrorSummary = StringUtils.left(StringUtils.defaultIfBlank(errorSummary, "UNKNOWN_ERROR"), LAST_ERROR_MAX_LENGTH);
        int nextRetryCount = defaultRetryCount(task) + 1;
        if (nextRetryCount > defaultMaxRetryCount(task, policy.getMaxRetryCount())) {
            mtAsyncTaskMapper.markDead(task.getId(), task.getTaskType(), nextRetryCount, currentTime, safeErrorSummary);
            mtAsyncTaskMetricsService.increment(task.getTaskType(), "dead");
            log.warn("数据库异步任务进入终态失败，taskType={}, taskId={}, retryCount={}, error={}",
                    task.getTaskType(), task.getId(), nextRetryCount, safeErrorSummary);
            return true;
        }
        LocalDateTime nextAvailableAt = currentTime.plus(resolveBackoff(policy, nextRetryCount));
        mtAsyncTaskMapper.rescheduleFromProcessing(
                task.getId(),
                task.getTaskType(),
                nextRetryCount,
                nextAvailableAt,
                safeErrorSummary,
                currentTime
        );
        mtAsyncTaskMetricsService.increment(task.getTaskType(), "retry");
        log.warn("数据库异步任务重试回退，taskType={}, taskId={}, retryCount={}, nextAvailableAt={}, error={}",
                task.getTaskType(), task.getId(), nextRetryCount, nextAvailableAt, safeErrorSummary);
        return false;
    }

    public void markTerminalFailure(MtAsyncTask task, String errorSummary, LocalDateTime now) {
        if (task == null) {
            return;
        }
        LocalDateTime currentTime = now == null ? LocalDateTime.now() : now;
        String safeErrorSummary = StringUtils.left(
                StringUtils.defaultIfBlank(errorSummary, "TERMINAL_BUSINESS_FAILURE"),
                LAST_ERROR_MAX_LENGTH
        );
        int nextRetryCount = defaultRetryCount(task) + 1;
        mtAsyncTaskMapper.markDead(
                task.getId(),
                task.getTaskType(),
                nextRetryCount,
                currentTime,
                safeErrorSummary
        );
        mtAsyncTaskMetricsService.increment(task.getTaskType(), "dead");
        log.warn("数据库异步任务业务失败并进入终态，taskType={}, taskId={}, retryCount={}, error={}",
                task.getTaskType(), task.getId(), nextRetryCount, safeErrorSummary);
    }

    public int recoverStaleTasks(
            MtAsyncTaskTypeEnum taskType,
            MtAsyncTaskExecutionPolicy policy,
            int limit,
            LocalDateTime now
    ) {
        return recoverStaleTasks(taskType, policy, limit, now, null);
    }

    public int recoverStaleTasks(
            MtAsyncTaskTypeEnum taskType,
            MtAsyncTaskExecutionPolicy policy,
            int limit,
            LocalDateTime now,
            Consumer<MtAsyncTask> terminalListener
    ) {
        if (taskType == null || policy == null || limit <= 0) {
            return 0;
        }
        LocalDateTime currentTime = now == null ? LocalDateTime.now() : now;
        LocalDateTime claimedBefore = currentTime.minus(policy.getTimeout());
        List<MtAsyncTask> staleTasks = mtAsyncTaskMapper.selectStaleProcessingTasks(taskType.name(), claimedBefore, limit);
        for (MtAsyncTask task : staleTasks) {
            boolean dead = handleFailure(task, policy, "STALE_TASK_TIMEOUT", currentTime);
            if (dead && terminalListener != null) {
                terminalListener.accept(task);
            }
            mtAsyncTaskMetricsService.increment(taskType.name(), "stale_recovered");
            log.warn("数据库异步任务回收超时任务，taskType={}, taskId={}, claimedAt={}",
                    taskType.name(), task.getId(), task.getClaimedAt());
        }
        return staleTasks.size();
    }

    public List<MtAsyncTaskBacklogSummary> selectBacklogSummary(LocalDateTime currentTime) {
        return mtAsyncTaskMapper.selectBacklogSummary(currentTime == null ? LocalDateTime.now() : currentTime);
    }

    public int cleanupHistoricalTerminalTasks(
            LocalDateTime successFinishedBefore,
            LocalDateTime deadFinishedBefore,
            int batchSize
    ) {
        if (batchSize <= 0) {
            return 0;
        }
        int deletedCount = 0;
        if (successFinishedBefore != null) {
            deletedCount += mtAsyncTaskMapper.deleteHistoricalTerminalTasks(
                    MtAsyncTaskStatusEnum.SUCCESS.name(),
                    successFinishedBefore,
                    batchSize
            );
        }
        if (deadFinishedBefore != null) {
            deletedCount += mtAsyncTaskMapper.deleteHistoricalTerminalTasks(
                    MtAsyncTaskStatusEnum.DEAD.name(),
                    deadFinishedBefore,
                    batchSize
            );
        }
        return deletedCount;
    }

    private int defaultRetryCount(MtAsyncTask task) {
        return task.getRetryCount() == null ? 0 : task.getRetryCount();
    }

    private int defaultMaxRetryCount(MtAsyncTask task, int fallback) {
        return task.getMaxRetryCount() == null ? fallback : task.getMaxRetryCount();
    }

    private java.time.Duration resolveBackoff(MtAsyncTaskExecutionPolicy policy, int retryCount) {
        long initialSeconds = Math.max(policy.getInitialBackoff().getSeconds(), 1L);
        long multiplier = 1L << Math.min(Math.max(retryCount - 1, 0), 10);
        long backoffSeconds = Math.min(initialSeconds * multiplier, Math.max(policy.getMaxBackoff().getSeconds(), initialSeconds));
        return java.time.Duration.ofSeconds(backoffSeconds);
    }
}
