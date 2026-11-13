package com.gogoga.backend.task;

import com.gogoga.backend.config.LlmRateLimitProperties;
import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.entity.mt.MtAsyncTask;
import com.gogoga.backend.enums.MtAsyncTaskTypeEnum;
import com.gogoga.backend.service.KnowledgeDocumentService;
import com.gogoga.backend.service.MtAsyncTaskService;
import com.gogoga.backend.service.MtAuthAsyncTaskService;
import com.gogoga.backend.service.MtImAutoReplyOrchestrationService;
import com.gogoga.backend.service.MtImBroadcastPlanService;
import com.gogoga.backend.service.MtImBroadcastSendService;
import com.gogoga.backend.service.RedisDistributedLockService;
import com.gogoga.backend.service.async.MtApiDryRunException;
import com.gogoga.backend.service.async.MtImAutoReplyTaskPayload;
import com.gogoga.backend.util.JsonUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.List;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MtAsyncTaskWorkerTaskTest {

    @Mock private MtAsyncTaskService asyncTaskService;
    @Mock private MtAuthAsyncTaskService authAsyncTaskService;
    @Mock private KnowledgeDocumentService knowledgeDocumentService;
    @Mock private MtImAutoReplyOrchestrationService imAutoReplyOrchestrationService;
    @Mock private MtImBroadcastPlanService imBroadcastPlanService;
    @Mock private MtImBroadcastSendService imBroadcastSendService;
    @Mock private RedisDistributedLockService lockService;
    @Mock private Executor ordinaryExecutor;
    @Mock private ThreadPoolTaskExecutor llmExecutor;
    @Mock private ThreadPoolTaskExecutor broadcastExecutor;

    private MtAsyncTaskWorkerTask worker;

    @BeforeEach
    void setUp() {
        worker = new MtAsyncTaskWorkerTask(
                asyncTaskService,
                authAsyncTaskService,
                knowledgeDocumentService,
                imAutoReplyOrchestrationService,
                imBroadcastPlanService,
                imBroadcastSendService,
                lockService,
                new MtProperties(),
                new LlmRateLimitProperties(),
                ordinaryExecutor,
                llmExecutor,
                broadcastExecutor
        );
    }

    @Test
    void weightedScheduleIncludesKnowledgeAndWeightedImAutoReply() {
        List<MtAsyncTaskTypeEnum> schedule = worker.weightedLlmSchedule();

        assertEquals(6, schedule.size());
        assertEquals(MtAsyncTaskTypeEnum.KNOWLEDGE_DOCUMENT_PARSE, schedule.get(0));
        assertEquals(5, schedule.stream().filter(type -> type == MtAsyncTaskTypeEnum.IM_AUTO_REPLY).count());
    }

    @Test
    void marksAutoReplyDeadWhenTaskRetryBudgetIsExhausted() {
        MtImAutoReplyTaskPayload payload = new MtImAutoReplyTaskPayload();
        payload.setAutoReplyId(99L);
        MtAsyncTask task = new MtAsyncTask();
        task.setId(7L);
        task.setTaskType(MtAsyncTaskTypeEnum.IM_AUTO_REPLY.name());
        task.setPayloadJson(JsonUtil.toJson(payload));
        doThrow(new IllegalStateException("send failed")).when(imAutoReplyOrchestrationService).execute(any());
        when(asyncTaskService.handleFailure(
                eq(task), any(), eq("IM_AUTO_REPLY_EXECUTION_FAILED:IllegalStateException"), any())).thenReturn(true);

        worker.executeClaimedTask(MtAsyncTaskTypeEnum.IM_AUTO_REPLY, task);

        verify(imAutoReplyOrchestrationService).markDeadAfterRetryExhausted(any());
    }

    @Test
    void reschedulesMeituanApiDryRunWithoutConsumingRetry() {
        MtAsyncTask task = new MtAsyncTask();
        task.setId(8L);
        task.setTaskType(MtAsyncTaskTypeEnum.IM_AUTO_REPLY.name());
        task.setRetryCount(2);
        task.setPayloadJson(JsonUtil.toJson(new MtImAutoReplyTaskPayload()));
        doThrow(new MtApiDryRunException("invokeApiWithToken:SendImMsgWmoperRequest", 17L))
                .when(imAutoReplyOrchestrationService).execute(any());

        worker.executeClaimedTask(MtAsyncTaskTypeEnum.IM_AUTO_REPLY, task);

        verify(asyncTaskService).rescheduleWithoutRetry(
                eq(task),
                any(),
                eq("MT_API_DRY_RUN:invokeApiWithToken:SendImMsgWmoperRequest"),
                any()
        );
        verify(asyncTaskService, never()).handleFailure(eq(task), any(), any(), any());
        verify(imAutoReplyOrchestrationService, never()).markDeadAfterRetryExhausted(any());
    }
}
