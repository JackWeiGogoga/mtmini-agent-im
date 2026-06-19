package com.gogoga.backend.service;

import com.gogoga.backend.entity.mt.MtAsyncTask;
import com.gogoga.backend.enums.MtAsyncTaskTypeEnum;
import com.gogoga.backend.mapper.MtAsyncTaskMapper;
import com.gogoga.backend.service.async.MtAsyncTaskEnqueueResult;
import com.gogoga.backend.service.async.MtAsyncTaskExecutionPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MtAsyncTaskServiceTest {

    @Mock
    private MtAsyncTaskMapper mtAsyncTaskMapper;

    @Mock
    private MtAsyncTaskMetricsService mtAsyncTaskMetricsService;

    @InjectMocks
    private MtAsyncTaskService mtAsyncTaskService;

    @Test
    void shouldSuppressDuplicateEnqueueByBizKey() {
        doThrow(new DuplicateKeyException("duplicate")).when(mtAsyncTaskMapper).insert(any(MtAsyncTask.class));
        MtAsyncTask existingTask = new MtAsyncTask();
        existingTask.setId(99L);
        when(mtAsyncTaskMapper.selectByTaskTypeAndBizKey(MtAsyncTaskTypeEnum.ORDER_NOTIFY.name(), "1#2026-04-18T12:00"))
                .thenReturn(existingTask);

        MtAsyncTaskEnqueueResult enqueueResult = mtAsyncTaskService.enqueueTask(
                MtAsyncTaskTypeEnum.ORDER_NOTIFY,
                "1#2026-04-18T12:00",
                "1",
                java.util.Map.of("shopId", 1L),
                LocalDateTime.of(2026, 4, 18, 12, 0),
                3
        );

        assertFalse(enqueueResult.isCreated());
        assertEquals(99L, enqueueResult.getTaskId());
    }

    @Test
    void shouldRescheduleFailedTaskWithIncrementedRetry() {
        MtAsyncTask task = buildProcessingTask(1L, 1, 3);
        MtAsyncTaskExecutionPolicy policy = MtAsyncTaskExecutionPolicy.builder()
                .maxRetryCount(3)
                .timeout(Duration.ofMinutes(5))
                .initialBackoff(Duration.ofSeconds(60))
                .maxBackoff(Duration.ofSeconds(600))
                .build();
        LocalDateTime now = LocalDateTime.of(2026, 4, 18, 12, 0);

        mtAsyncTaskService.handleFailure(task, policy, "REMOTE_TIMEOUT", now);

        ArgumentCaptor<Integer> retryCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<LocalDateTime> availableAtCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(mtAsyncTaskMapper).rescheduleFromProcessing(
                eq(1L),
                eq(MtAsyncTaskTypeEnum.ORDER_NOTIFY.name()),
                retryCaptor.capture(),
                availableAtCaptor.capture(),
                eq("REMOTE_TIMEOUT"),
                eq(now)
        );
        assertEquals(2, retryCaptor.getValue());
        assertEquals(now.plusSeconds(120), availableAtCaptor.getValue());
        verify(mtAsyncTaskMapper, never()).markDead(any(), any(), any(), any(), any());
    }

    @Test
    void shouldMarkTaskDeadWhenRetryBudgetExceeded() {
        MtAsyncTask task = buildProcessingTask(1L, 3, 3);
        MtAsyncTaskExecutionPolicy policy = MtAsyncTaskExecutionPolicy.builder()
                .maxRetryCount(3)
                .timeout(Duration.ofMinutes(5))
                .initialBackoff(Duration.ofSeconds(60))
                .maxBackoff(Duration.ofSeconds(600))
                .build();
        LocalDateTime now = LocalDateTime.of(2026, 4, 18, 12, 0);

        mtAsyncTaskService.handleFailure(task, policy, "STALE_TASK_TIMEOUT", now);

        verify(mtAsyncTaskMapper).markDead(
                1L,
                MtAsyncTaskTypeEnum.ORDER_NOTIFY.name(),
                4,
                now,
                "STALE_TASK_TIMEOUT"
        );
        verify(mtAsyncTaskMapper, never()).rescheduleFromProcessing(any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldRescheduleWithoutIncrementingRetryCount() {
        MtAsyncTask task = buildProcessingTask(2L, 2, 3);
        LocalDateTime now = LocalDateTime.of(2026, 4, 18, 12, 0);
        LocalDateTime nextAvailableAt = now.plusSeconds(30);

        mtAsyncTaskService.rescheduleWithoutRetry(task, nextAvailableAt, "SHOP_EXECUTION_BUSY", now);

        verify(mtAsyncTaskMapper).rescheduleFromProcessing(
                2L,
                MtAsyncTaskTypeEnum.ORDER_NOTIFY.name(),
                2,
                nextAvailableAt,
                "SHOP_EXECUTION_BUSY",
                now
        );
    }

    @Test
    void shouldRecoverStaleTasksForRetry() {
        MtAsyncTask task = buildProcessingTask(5L, 0, 3);
        task.setTaskType(MtAsyncTaskTypeEnum.AUTH_SYNC_RIGHTS.name());
        task.setClaimedAt(LocalDateTime.of(2026, 4, 18, 11, 40));
        when(mtAsyncTaskMapper.selectStaleProcessingTasks(
                eq(MtAsyncTaskTypeEnum.AUTH_SYNC_RIGHTS.name()),
                eq(LocalDateTime.of(2026, 4, 18, 11, 55)),
                eq(10)
        )).thenReturn(java.util.List.of(task));

        MtAsyncTaskExecutionPolicy policy = MtAsyncTaskExecutionPolicy.builder()
                .maxRetryCount(3)
                .timeout(Duration.ofMinutes(5))
                .initialBackoff(Duration.ofSeconds(60))
                .maxBackoff(Duration.ofSeconds(600))
                .build();

        int recovered = mtAsyncTaskService.recoverStaleTasks(
                MtAsyncTaskTypeEnum.AUTH_SYNC_RIGHTS,
                policy,
                10,
                LocalDateTime.of(2026, 4, 18, 12, 0)
        );

        assertEquals(1, recovered);
        verify(mtAsyncTaskMapper).rescheduleFromProcessing(
                5L,
                MtAsyncTaskTypeEnum.AUTH_SYNC_RIGHTS.name(),
                1,
                LocalDateTime.of(2026, 4, 18, 12, 1),
                "STALE_TASK_TIMEOUT",
                LocalDateTime.of(2026, 4, 18, 12, 0)
        );
    }

    @Test
    void shouldEnqueueTaskWithPendingStatus() {
        MtAsyncTaskEnqueueResult enqueueResult = mtAsyncTaskService.enqueueTask(
                MtAsyncTaskTypeEnum.AUTH_SYNC_RIGHTS,
                "record#1",
                "1",
                java.util.Map.of("recordId", 1L),
                LocalDateTime.of(2026, 4, 18, 12, 0),
                2
        );

        assertTrue(enqueueResult.isCreated());
        ArgumentCaptor<MtAsyncTask> taskCaptor = ArgumentCaptor.forClass(MtAsyncTask.class);
        verify(mtAsyncTaskMapper).insert(taskCaptor.capture());
        assertEquals("PENDING", taskCaptor.getValue().getStatus());
        assertEquals("record#1", taskCaptor.getValue().getBizKey());
    }

    @Test
    void shouldCleanupHistoricalSuccessAndDeadTasks() {
        LocalDateTime successFinishedBefore = LocalDateTime.of(2026, 4, 11, 0, 0);
        LocalDateTime deadFinishedBefore = LocalDateTime.of(2026, 3, 19, 0, 0);
        when(mtAsyncTaskMapper.deleteHistoricalTerminalTasks(eq("SUCCESS"), eq(successFinishedBefore), eq(500)))
                .thenReturn(12);
        when(mtAsyncTaskMapper.deleteHistoricalTerminalTasks(eq("DEAD"), eq(deadFinishedBefore), eq(500)))
                .thenReturn(3);

        int deleted = mtAsyncTaskService.cleanupHistoricalTerminalTasks(
                successFinishedBefore,
                deadFinishedBefore,
                500
        );

        assertEquals(15, deleted);
    }

    private MtAsyncTask buildProcessingTask(Long id, int retryCount, int maxRetryCount) {
        MtAsyncTask task = new MtAsyncTask();
        task.setId(id);
        task.setTaskType(MtAsyncTaskTypeEnum.ORDER_NOTIFY.name());
        task.setStatus("PROCESSING");
        task.setRetryCount(retryCount);
        task.setMaxRetryCount(maxRetryCount);
        return task;
    }
}
