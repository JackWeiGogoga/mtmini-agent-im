package com.gogoga.backend.task;

import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.entity.mt.MtAsyncTask;
import com.gogoga.backend.enums.MtAsyncTaskTypeEnum;
import com.gogoga.backend.service.MtAsyncTaskService;
import com.gogoga.backend.service.MtAuthAsyncTaskService;
import com.gogoga.backend.service.MtOrderAsyncTaskService;
import com.gogoga.backend.service.MtPromotionAutoBidService;
import com.gogoga.backend.service.RedisDistributedLockService;
import com.gogoga.backend.service.async.MtAuthPostProcessTaskPayload;
import com.gogoga.backend.service.async.MtOrderNotifyTaskPayload;
import com.gogoga.backend.service.async.MtPromotionAutoBidTaskPayload;
import com.gogoga.backend.service.async.MtPromotionLogMarkdownTaskPayload;
import com.gogoga.backend.util.JsonUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MtPromotionAsyncTaskWorkerTaskTest {

    @Mock
    private MtAsyncTaskService mtAsyncTaskService;

    @Mock
    private MtPromotionAutoBidService mtPromotionAutoBidService;

    @Mock
    private MtAuthAsyncTaskService mtAuthAsyncTaskService;

    @Mock
    private MtOrderAsyncTaskService mtOrderAsyncTaskService;

    @Mock
    private RedisDistributedLockService redisDistributedLockService;

    private MtPromotionAsyncTaskWorkerTask workerTask;

    @BeforeEach
    void setUp() {
        MtProperties mtProperties = new MtProperties();
        mtProperties.getPromotion().getAsyncTask().setEnabled(true);
        mtProperties.getPromotion().getAsyncTask().setWorkerEnabled(true);
        mtProperties.getPromotion().getAsyncTask().setAutoBidShopLockSeconds(300);
        mtProperties.getPromotion().getAsyncTask().setOrderNotifyLockSeconds(60);
        mtProperties.getPromotion().getAsyncTask().setAutoBidBusyBackoffSeconds(30);
        mtProperties.getPromotion().getAsyncTask().setOrderNotifyBusyBackoffSeconds(5);
        mtProperties.getPromotion().getAsyncTask().getCleanup().setEnabled(true);
        mtProperties.getPromotion().getAsyncTask().getCleanup().setBatchSize(1000);
        mtProperties.getPromotion().getAsyncTask().getCleanup().setSuccessRetentionDays(7);
        mtProperties.getPromotion().getAsyncTask().getCleanup().setDeadRetentionDays(30);
        mtProperties.getPromotion().getAsyncTask().getCleanup().setLockSeconds(300);
        Executor directExecutor = Runnable::run;
        workerTask = new MtPromotionAsyncTaskWorkerTask(
                mtAsyncTaskService,
                mtPromotionAutoBidService,
                mtAuthAsyncTaskService,
                mtOrderAsyncTaskService,
                redisDistributedLockService,
                mtProperties,
                directExecutor
        );
        lenient().when(mtAsyncTaskService.selectExecutableTaskIds(any(), eq(20), any())).thenReturn(List.of());
    }

    @Test
    void shouldExecuteClaimedOrderNotifyTaskAndMarkSuccess() {
        MtAsyncTask task = buildOrderNotifyTask(10L, "order-001");
        when(mtAsyncTaskService.selectExecutableTaskIds(eq(MtAsyncTaskTypeEnum.ORDER_NOTIFY), eq(20), any()))
                .thenReturn(List.of(10L));
        when(mtAsyncTaskService.claimTask(eq(MtAsyncTaskTypeEnum.ORDER_NOTIFY), eq(10L), any(), any()))
                .thenReturn(task);
        doAnswer(invocation -> {
            Runnable action = invocation.getArgument(2);
            action.run();
            return true;
        }).when(redisDistributedLockService).tryRun(any(), any(Duration.class), any(Runnable.class));

        workerTask.pollAsyncTasks();

        verify(mtOrderAsyncTaskService).executeOrderNotifyTask(any(MtOrderNotifyTaskPayload.class));
        verify(mtAsyncTaskService).markSuccess(eq(MtAsyncTaskTypeEnum.ORDER_NOTIFY), eq(10L), any());
    }

    @Test
    void shouldRescheduleOrderNotifyTaskWhenOrderLockBusy() {
        MtAsyncTask task = buildOrderNotifyTask(13L, "order-001");
        when(mtAsyncTaskService.selectExecutableTaskIds(eq(MtAsyncTaskTypeEnum.ORDER_NOTIFY), eq(20), any()))
                .thenReturn(List.of(13L));
        when(mtAsyncTaskService.claimTask(eq(MtAsyncTaskTypeEnum.ORDER_NOTIFY), eq(13L), any(), any()))
                .thenReturn(task);
        when(redisDistributedLockService.tryRun(any(), any(Duration.class), any(Runnable.class))).thenReturn(false);

        workerTask.pollAsyncTasks();

        verify(mtAsyncTaskService).rescheduleWithoutRetry(eq(task), any(), eq("ORDER_EXECUTION_BUSY"), any());
        verify(mtAsyncTaskService, never()).markSuccess(any(), any(), any());
    }

    @Test
    void shouldExecuteClaimedAutoBidTaskAndMarkSuccess() {
        MtAsyncTask task = buildAutoBidTask(11L, 1L);
        when(mtAsyncTaskService.selectExecutableTaskIds(eq(MtAsyncTaskTypeEnum.PROMOTION_AUTO_BID), eq(20), any()))
                .thenReturn(List.of(11L));
        when(mtAsyncTaskService.claimTask(eq(MtAsyncTaskTypeEnum.PROMOTION_AUTO_BID), eq(11L), any(), any()))
                .thenReturn(task);
        doAnswer(invocation -> {
            Runnable action = invocation.getArgument(2);
            action.run();
            return true;
        }).when(redisDistributedLockService).tryRun(any(), any(Duration.class), any(Runnable.class));

        workerTask.pollAsyncTasks();

        verify(mtPromotionAutoBidService).executeAutoBidTask(any(MtPromotionAutoBidTaskPayload.class));
        verify(mtAsyncTaskService).markSuccess(eq(MtAsyncTaskTypeEnum.PROMOTION_AUTO_BID), eq(11L), any());
        verify(mtAsyncTaskService, never()).handleFailure(any(), any(), any(), any());
    }

    @Test
    void shouldRescheduleAutoBidTaskWhenShopLockBusy() {
        MtAsyncTask task = buildAutoBidTask(12L, 1L);
        when(mtAsyncTaskService.selectExecutableTaskIds(eq(MtAsyncTaskTypeEnum.PROMOTION_AUTO_BID), eq(20), any()))
                .thenReturn(List.of(12L));
        when(mtAsyncTaskService.claimTask(eq(MtAsyncTaskTypeEnum.PROMOTION_AUTO_BID), eq(12L), any(), any()))
                .thenReturn(task);
        when(redisDistributedLockService.tryRun(any(), any(Duration.class), any(Runnable.class))).thenReturn(false);

        workerTask.pollAsyncTasks();

        verify(mtAsyncTaskService).rescheduleWithoutRetry(eq(task), any(), eq("SHOP_EXECUTION_BUSY"), any());
        verify(mtAsyncTaskService, never()).markSuccess(any(), any(), any());
        verify(mtAsyncTaskService, never()).handleFailure(any(), any(), any(), any());
    }

    @Test
    void shouldSendMarkdownTaskFailureToRetryHandler() {
        MtAsyncTask task = buildMarkdownTask(21L, 7L);
        when(mtAsyncTaskService.selectExecutableTaskIds(eq(MtAsyncTaskTypeEnum.PROMOTION_LOG_MARKDOWN), eq(20), any()))
                .thenReturn(List.of(21L));
        when(mtAsyncTaskService.claimTask(eq(MtAsyncTaskTypeEnum.PROMOTION_LOG_MARKDOWN), eq(21L), any(), any()))
                .thenReturn(task);
        org.mockito.Mockito.doThrow(new IllegalStateException("LLM_FAILED"))
                .when(mtPromotionAutoBidService).executePromotionLogMarkdownTask(any(MtPromotionLogMarkdownTaskPayload.class));

        workerTask.pollAsyncTasks();

        verify(mtAsyncTaskService).handleFailure(eq(task), any(), eq("LLM_FAILED"), any());
        verify(mtAsyncTaskService, never()).markSuccess(any(), any(), any());
    }

    @Test
    void shouldRecoverStaleTasksForAllTaskTypes() {
        workerTask.recoverStaleTasks();

        verify(mtAsyncTaskService).recoverStaleTasks(eq(MtAsyncTaskTypeEnum.ORDER_NOTIFY), any(), eq(20), any());
        verify(mtAsyncTaskService).recoverStaleTasks(eq(MtAsyncTaskTypeEnum.PROMOTION_AUTO_BID), any(), eq(20), any());
        verify(mtAsyncTaskService).recoverStaleTasks(eq(MtAsyncTaskTypeEnum.PROMOTION_LOG_MARKDOWN), any(), eq(20), any());
        verify(mtAsyncTaskService).recoverStaleTasks(eq(MtAsyncTaskTypeEnum.AUTH_SYNC_RIGHTS), any(), eq(20), any());
        verify(mtAsyncTaskService).recoverStaleTasks(eq(MtAsyncTaskTypeEnum.AUTH_BACKFILL_POI_ID), any(), eq(20), any());
        verify(mtAsyncTaskService).recoverStaleTasks(eq(MtAsyncTaskTypeEnum.AUTH_SYNC_RECENT_AD_DATA), any(), eq(20), any());
        verify(mtAsyncTaskService).recoverStaleTasks(eq(MtAsyncTaskTypeEnum.AUTH_HISTORICAL_ORDER_BACKFILL), any(), eq(20), any());
        verify(mtAsyncTaskService).recoverStaleTasks(eq(MtAsyncTaskTypeEnum.AUTH_BLOCK_ORDER_PUSH), any(), eq(20), any());
    }

    @Test
    void shouldExecuteClaimedAuthSyncRightsTaskAndMarkSuccess() {
        MtAsyncTask task = buildAuthTask(31L, MtAsyncTaskTypeEnum.AUTH_SYNC_RIGHTS, 1L);
        when(mtAsyncTaskService.selectExecutableTaskIds(eq(MtAsyncTaskTypeEnum.AUTH_SYNC_RIGHTS), eq(20), any()))
                .thenReturn(List.of(31L));
        when(mtAsyncTaskService.claimTask(eq(MtAsyncTaskTypeEnum.AUTH_SYNC_RIGHTS), eq(31L), any(), any()))
                .thenReturn(task);

        workerTask.pollAsyncTasks();

        verify(mtAuthAsyncTaskService).executeAuthSyncRightsTask(any(MtAuthPostProcessTaskPayload.class));
        verify(mtAsyncTaskService).markSuccess(eq(MtAsyncTaskTypeEnum.AUTH_SYNC_RIGHTS), eq(31L), any());
    }

    @Test
    void shouldExecuteClaimedAuthBlockOrderPushTaskAndMarkSuccess() {
        MtAsyncTask task = buildAuthTask(32L, MtAsyncTaskTypeEnum.AUTH_BLOCK_ORDER_PUSH, 1L);
        when(mtAsyncTaskService.selectExecutableTaskIds(eq(MtAsyncTaskTypeEnum.AUTH_BLOCK_ORDER_PUSH), eq(20), any()))
                .thenReturn(List.of(32L));
        when(mtAsyncTaskService.claimTask(eq(MtAsyncTaskTypeEnum.AUTH_BLOCK_ORDER_PUSH), eq(32L), any(), any()))
                .thenReturn(task);

        workerTask.pollAsyncTasks();

        verify(mtAuthAsyncTaskService).executeAuthBlockOrderPushTask(any(MtAuthPostProcessTaskPayload.class));
        verify(mtAsyncTaskService).markSuccess(eq(MtAsyncTaskTypeEnum.AUTH_BLOCK_ORDER_PUSH), eq(32L), any());
    }

    @Test
    void shouldCleanupHistoricalTerminalTasksUnderDistributedLock() {
        doAnswer(invocation -> {
            Runnable action = invocation.getArgument(2);
            action.run();
            return true;
        }).when(redisDistributedLockService).tryRun(any(), any(Duration.class), any(Runnable.class));

        workerTask.cleanupHistoricalTasks();

        verify(mtAsyncTaskService).cleanupHistoricalTerminalTasks(any(), any(), eq(1000));
    }

    private MtAsyncTask buildAutoBidTask(Long taskId, Long shopId) {
        MtPromotionAutoBidTaskPayload payload = new MtPromotionAutoBidTaskPayload();
        payload.setShopId(shopId);
        payload.setSlotTime(LocalDateTime.of(2026, 4, 18, 12, 0));
        MtAsyncTask task = new MtAsyncTask();
        task.setId(taskId);
        task.setTaskType(MtAsyncTaskTypeEnum.PROMOTION_AUTO_BID.name());
        task.setPayloadJson(JsonUtil.toJson(payload));
        return task;
    }

    private MtAsyncTask buildOrderNotifyTask(Long taskId, String orderId) {
        MtOrderNotifyTaskPayload payload = new MtOrderNotifyTaskPayload();
        payload.setMsgId("msg-" + taskId);
        payload.setOrderId(orderId);
        payload.setEPoiId("shop-001");
        payload.setMessage("{\"order_id\":\"" + orderId + "\",\"status\":\"2\"}");
        MtAsyncTask task = new MtAsyncTask();
        task.setId(taskId);
        task.setTaskType(MtAsyncTaskTypeEnum.ORDER_NOTIFY.name());
        task.setPayloadJson(JsonUtil.toJson(payload));
        return task;
    }

    private MtAsyncTask buildMarkdownTask(Long taskId, Long recordId) {
        MtPromotionLogMarkdownTaskPayload payload = new MtPromotionLogMarkdownTaskPayload();
        payload.setRecordId(recordId);
        MtAsyncTask task = new MtAsyncTask();
        task.setId(taskId);
        task.setTaskType(MtAsyncTaskTypeEnum.PROMOTION_LOG_MARKDOWN.name());
        task.setPayloadJson(JsonUtil.toJson(payload));
        return task;
    }

    private MtAsyncTask buildAuthTask(Long taskId, MtAsyncTaskTypeEnum taskType, Long shopId) {
        MtAuthPostProcessTaskPayload payload = new MtAuthPostProcessTaskPayload();
        payload.setShopId(shopId);
        MtAsyncTask task = new MtAsyncTask();
        task.setId(taskId);
        task.setTaskType(taskType.name());
        task.setPayloadJson(JsonUtil.toJson(payload));
        return task;
    }
}
