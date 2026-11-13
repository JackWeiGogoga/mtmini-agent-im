package com.gogoga.backend.task;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.gogoga.backend.service.RedisDistributedLockService;
import com.gogoga.backend.service.sms.notification.SmsAppExpiryPlanningResult;
import com.gogoga.backend.service.sms.notification.SmsAppExpiryPlannerService;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SmsAppExpiryPlannerTaskTest {

    @Test
    void shouldLogOnePlanningSummaryWithOutcomeCounts() {
        SmsAppExpiryPlannerService plannerService = mock(SmsAppExpiryPlannerService.class);
        RedisDistributedLockService lockService = mock(RedisDistributedLockService.class);
        when(plannerService.planActiveRights())
                .thenReturn(new SmsAppExpiryPlanningResult(294, 0, 294, 0));
        doAnswer(invocation -> {
            invocation.getArgument(2, Runnable.class).run();
            return true;
        }).when(lockService).tryRun(
                eq("SmsAppExpiryPlannerTask"),
                any(Duration.class),
                any(Runnable.class)
        );
        SmsAppExpiryPlannerTask task = new SmsAppExpiryPlannerTask(plannerService, lockService);
        Logger logger = (Logger) LoggerFactory.getLogger(SmsAppExpiryPlannerTask.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            task.plan();
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }

        String message = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .filter(item -> item.contains("应用到期短信事件规划完成"))
                .findFirst()
                .orElseThrow();
        assertTrue(message.contains("candidateCount=294"));
        assertTrue(message.contains("createdCount=0"));
        assertTrue(message.contains("duplicateCount=294"));
        assertTrue(message.contains("failedCount=0"));
    }
}
