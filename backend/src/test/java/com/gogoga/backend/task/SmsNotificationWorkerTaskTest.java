package com.gogoga.backend.task;

import com.gogoga.backend.config.SmsProperties;
import com.gogoga.backend.service.RedisDistributedLockService;
import com.gogoga.backend.service.sms.notification.SmsNotificationEventService;
import com.gogoga.backend.service.sms.notification.SmsNotificationProcessingService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SmsNotificationWorkerTaskTest {

    @Test
    void shouldUseConfiguredBusinessZoneForWorkerTimestamps() {
        SmsNotificationEventService eventService = mock(SmsNotificationEventService.class);
        SmsNotificationProcessingService processingService = mock(SmsNotificationProcessingService.class);
        RedisDistributedLockService lockService = mock(RedisDistributedLockService.class);
        SmsProperties smsProperties = new SmsProperties();
        String zoneId = "Pacific/Kiritimati";
        smsProperties.getBusiness().setZoneId(zoneId);
        when(processingService.recoverStale(any(LocalDateTime.class))).thenReturn(0);
        doAnswer(invocation -> {
            invocation.getArgument(2, Runnable.class).run();
            return true;
        }).when(lockService).tryRun(
                eq("SmsNotificationRecoveryWorker"),
                any(Duration.class),
                any(Runnable.class)
        );
        SmsNotificationWorkerTask task = new SmsNotificationWorkerTask(
                eventService,
                processingService,
                lockService,
                smsProperties
        );
        ZoneId businessZone = ZoneId.of(zoneId);
        LocalDateTime before = LocalDateTime.now(businessZone).minusSeconds(1);

        task.recoverStale();

        LocalDateTime after = LocalDateTime.now(businessZone).plusSeconds(1);
        ArgumentCaptor<LocalDateTime> nowCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(processingService).recoverStale(nowCaptor.capture());
        LocalDateTime workerNow = nowCaptor.getValue();
        assertFalse(workerNow.isBefore(before));
        assertFalse(workerNow.isAfter(after));
    }
}
