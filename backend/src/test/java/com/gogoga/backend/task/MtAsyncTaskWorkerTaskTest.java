package com.gogoga.backend.task;

import com.gogoga.backend.config.LlmRateLimitProperties;
import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.entity.mt.MtAsyncTask;
import com.gogoga.backend.enums.MtAsyncTaskTypeEnum;
import com.gogoga.backend.service.CommentLogAnalysisService;
import com.gogoga.backend.service.CommentReplyKnowledgeDocumentService;
import com.gogoga.backend.service.MtAsyncTaskService;
import com.gogoga.backend.service.MtAuthAsyncTaskService;
import com.gogoga.backend.service.MtCommentAutoReplyService;
import com.gogoga.backend.service.MtCommentComplaintService;
import com.gogoga.backend.service.MtCommentImFollowUpService;
import com.gogoga.backend.service.RedisDistributedLockService;
import com.gogoga.backend.service.llm.LlmCapacityDeferredException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MtAsyncTaskWorkerTaskTest {

    @Mock
    private MtAsyncTaskService asyncTaskService;
    @Mock
    private MtAuthAsyncTaskService authAsyncTaskService;
    @Mock
    private MtCommentAutoReplyService autoReplyService;
    @Mock
    private MtCommentComplaintService complaintService;
    @Mock
    private MtCommentImFollowUpService imFollowUpService;
    @Mock
    private CommentLogAnalysisService logAnalysisService;
    @Mock
    private CommentReplyKnowledgeDocumentService knowledgeDocumentService;
    @Mock
    private RedisDistributedLockService lockService;
    @Mock
    private Executor ordinaryExecutor;
    @Mock
    private ThreadPoolTaskExecutor llmExecutor;

    private MtAsyncTaskWorkerTask worker;

    @BeforeEach
    void setUp() {
        MtProperties mtProperties = new MtProperties();
        LlmRateLimitProperties rateLimitProperties = new LlmRateLimitProperties();
        rateLimitProperties.setRescheduleJitterMillis(0L);
        worker = new MtAsyncTaskWorkerTask(
                asyncTaskService,
                authAsyncTaskService,
                autoReplyService,
                complaintService,
                imFollowUpService,
                logAnalysisService,
                knowledgeDocumentService,
                lockService,
                mtProperties,
                rateLimitProperties,
                ordinaryExecutor,
                llmExecutor
        );
    }

    @Test
    void shouldBuildConfiguredDefaultWeightedSchedule() {
        List<MtAsyncTaskTypeEnum> schedule = worker.weightedLlmSchedule();

        assertEquals(5, count(schedule, MtAsyncTaskTypeEnum.COMMENT_AUTO_REPLY));
        assertEquals(2, count(schedule, MtAsyncTaskTypeEnum.COMMENT_AUTO_IM_FOLLOW_UP));
        assertEquals(2, count(schedule, MtAsyncTaskTypeEnum.COMMENT_AUTO_COMPLAINT));
        assertEquals(1, count(schedule, MtAsyncTaskTypeEnum.COMMENT_LOG_ANALYSIS));
        assertEquals(1, count(schedule, MtAsyncTaskTypeEnum.KNOWLEDGE_DOCUMENT_PARSE));
    }

    @Test
    void shouldRescheduleCapacityWaitWithoutBusinessRetry() {
        MtAsyncTask task = new MtAsyncTask();
        task.setId(9L);
        task.setTaskType(MtAsyncTaskTypeEnum.COMMENT_LOG_ANALYSIS.name());
        task.setRetryCount(2);
        task.setPayloadJson("{\"shopId\":100,\"commentId\":542543250001}");
        doThrow(new LlmCapacityDeferredException(1200L))
                .when(logAnalysisService)
                .executeCommentLogAnalysisTask(any());

        worker.executeClaimedTask(MtAsyncTaskTypeEnum.COMMENT_LOG_ANALYSIS, task);

        ArgumentCaptor<LocalDateTime> availableAt = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> updatedAt = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(asyncTaskService).rescheduleWithoutRetry(
                eq(task),
                availableAt.capture(),
                eq("LLM_CAPACITY_DEFERRED"),
                updatedAt.capture()
        );
        assertEquals(1200L, Duration.between(updatedAt.getValue(), availableAt.getValue()).toMillis());
    }

    private long count(List<MtAsyncTaskTypeEnum> schedule, MtAsyncTaskTypeEnum taskType) {
        return schedule.stream().filter(taskType::equals).count();
    }
}
