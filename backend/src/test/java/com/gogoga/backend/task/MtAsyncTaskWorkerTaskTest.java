package com.gogoga.backend.task;

import com.gogoga.backend.config.LlmRateLimitProperties;
import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.enums.MtAsyncTaskTypeEnum;
import com.gogoga.backend.service.KnowledgeDocumentService;
import com.gogoga.backend.service.MtAsyncTaskService;
import com.gogoga.backend.service.MtAuthAsyncTaskService;
import com.gogoga.backend.service.RedisDistributedLockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.List;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class MtAsyncTaskWorkerTaskTest {

    @Mock private MtAsyncTaskService asyncTaskService;
    @Mock private MtAuthAsyncTaskService authAsyncTaskService;
    @Mock private KnowledgeDocumentService knowledgeDocumentService;
    @Mock private RedisDistributedLockService lockService;
    @Mock private Executor ordinaryExecutor;
    @Mock private ThreadPoolTaskExecutor llmExecutor;

    private MtAsyncTaskWorkerTask worker;

    @BeforeEach
    void setUp() {
        worker = new MtAsyncTaskWorkerTask(
                asyncTaskService,
                authAsyncTaskService,
                knowledgeDocumentService,
                lockService,
                new MtProperties(),
                new LlmRateLimitProperties(),
                ordinaryExecutor,
                llmExecutor
        );
    }

    @Test
    void weightedScheduleOnlyContainsGenericKnowledgeParsing() {
        List<MtAsyncTaskTypeEnum> schedule = worker.weightedLlmSchedule();

        assertEquals(1, schedule.size());
        assertEquals(MtAsyncTaskTypeEnum.KNOWLEDGE_DOCUMENT_PARSE, schedule.get(0));
    }
}
