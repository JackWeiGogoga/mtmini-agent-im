package com.gogoga.backend.service.sms.notification;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.gogoga.backend.config.SmsProperties;
import com.gogoga.backend.entity.sms.SmsNotificationEvent;
import com.gogoga.backend.enums.SmsNotificationSceneEnum;
import com.gogoga.backend.service.sms.BusinessSmsSendWindowPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MerchantLifecycleSmsServiceTest {

    private SmsNotificationEventService eventService;
    private MerchantLifecycleSmsService service;

    @BeforeEach
    void setUp() {
        eventService = mock(SmsNotificationEventService.class);
        SmsProperties properties = new SmsProperties();
        properties.getBusiness().setEnabled(true);
        service = new MerchantLifecycleSmsService(
                eventService,
                properties,
                new BusinessSmsSendWindowPolicy()
        );
    }

    @Test
    void shouldUseLatestPurchaseTemplateAndScheduleForNextDayAtTen() {
        Instant occurredAt = Instant.parse("2026-07-22T16:30:00Z");

        service.enqueuePurchaseGuide(10L, "order-1", 90, occurredAt);

        SmsNotificationEventEnqueueRequest request = captureRequest();
        assertEquals(SmsNotificationSceneEnum.PURCHASE_GUIDE, request.scene());
        assertEquals("2685915", request.templateId());
        assertEquals(List.of("AI智能体消息管理", "90天"), request.templateParams());
        assertEquals("order-1", request.bizKey());
        assertEquals(LocalDateTime.of(2026, 7, 24, 10, 0), request.availableAt());
    }

    @Test
    void shouldSkipUnsupportedDaysWithoutCreatingInvalidTemplateParameters() {
        service.enqueuePurchaseGuide(10L, "order-1", 15, Instant.now());

        verify(eventService, never()).enqueue(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldUseLatestPowerTemplateWithConsumeNumberAsBusinessKey() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 22, 12, 0);

        service.enqueuePowerExhausted(10L, "PC-1", now);

        SmsNotificationEventEnqueueRequest request = captureRequest();
        assertEquals(SmsNotificationSceneEnum.POWER_EXHAUSTED, request.scene());
        assertEquals("2693564", request.templateId());
        assertEquals(List.of("AI智能体消息管理"), request.templateParams());
        assertEquals("PC-1", request.bizKey());
        assertEquals(now, request.availableAt());
    }

    @Test
    void shouldScheduleEarlyMorningPowerEventForNextDayAtEight() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 24, 2, 0, 11);

        service.enqueuePowerExhausted(10L, "PC-1", now);

        SmsNotificationEventEnqueueRequest request = captureRequest();
        assertEquals(LocalDateTime.of(2026, 7, 25, 8, 0), request.availableAt());
    }

    @Test
    void shouldUseLatestExpiryTemplateAndExpectedExpiryInBusinessKey() {
        LocalDateTime expectedExpireAt = LocalDateTime.of(2026, 8, 1, 23, 59, 59);
        LocalDateTime scheduledAt = LocalDateTime.of(2026, 8, 2, 10, 0);

        service.enqueueAppExpired(10L, expectedExpireAt, scheduledAt);

        SmsNotificationEventEnqueueRequest request = captureRequest();
        assertEquals(SmsNotificationSceneEnum.APP_EXPIRED, request.scene());
        assertEquals("2693563", request.templateId());
        assertEquals("10#2026-08-01T23:59:59", request.bizKey());
        assertEquals(scheduledAt, request.availableAt());
    }

    @Test
    void shouldApplySendWindowGuardToExpiryEvent() {
        LocalDateTime expectedExpireAt = LocalDateTime.of(2026, 8, 1, 23, 59, 59);
        LocalDateTime scheduledAt = LocalDateTime.of(2026, 8, 2, 22, 0);

        service.enqueueAppExpired(10L, expectedExpireAt, scheduledAt);

        SmsNotificationEventEnqueueRequest request = captureRequest();
        assertEquals(LocalDateTime.of(2026, 8, 3, 8, 0), request.availableAt());
    }

    @Test
    void shouldDeferEventUntilActiveBusinessTransactionCommits() {
        TransactionSynchronization synchronization = registerAfterCommit(() ->
                service.enqueuePurchaseGuide(10L, "order-1", 7, Instant.now())
        );

        verify(eventService, never()).enqueue(any());

        synchronization.afterCommit();

        verify(eventService).enqueue(any());
    }

    @Test
    void shouldNotEnqueueEventWhenBusinessTransactionRollsBack() {
        TransactionSynchronization synchronization = registerAfterCommit(() ->
                service.enqueuePowerExhausted(10L, "PC-1", LocalDateTime.now())
        );

        synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

        verify(eventService, never()).enqueue(any());
    }

    @Test
    void shouldLogStructuredFailureAndNotThrowWhenAfterCommitEnqueueFails() {
        doThrow(new IllegalStateException("event insert failed"))
                .when(eventService).enqueue(any());
        Logger logger = (Logger) LoggerFactory.getLogger(MerchantLifecycleSmsService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            TransactionSynchronization synchronization = registerAfterCommit(() ->
                    service.enqueuePurchaseGuide(10L, "order-1", 7, Instant.now())
            );

            assertDoesNotThrow(synchronization::afterCommit);
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }

        String message = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .filter(item -> item.contains("业务短信事件入队失败"))
                .findFirst()
                .orElseThrow();
        assertTrue(message.contains("phase=AFTER_COMMIT"));
        assertTrue(message.contains("scene=PURCHASE_GUIDE"));
        assertTrue(message.contains("bizKey=order-1"));
        assertTrue(message.contains("shopId=10"));
        assertTrue(message.contains("orderId=order-1"));
        assertTrue(message.contains("templateId=2685915"));
        assertTrue(message.contains("errorType=IllegalStateException"));
        assertTrue(message.contains("errorMessage=event insert failed"));
        assertFalse(message.contains("AI智能体消息管理"));
        assertFalse(message.contains("7天"));
    }

    @Test
    void shouldLogOnlyNewNonExpiryEventsAtInfoLevel() {
        SmsNotificationEvent purchaseEvent = event(1L);
        SmsNotificationEvent duplicateEvent = event(1L);
        SmsNotificationEvent expiryEvent = event(2L);
        when(eventService.enqueue(any()))
                .thenReturn(new SmsNotificationEventEnqueueResult(purchaseEvent, true))
                .thenReturn(new SmsNotificationEventEnqueueResult(duplicateEvent, false))
                .thenReturn(new SmsNotificationEventEnqueueResult(expiryEvent, true));

        Logger logger = (Logger) LoggerFactory.getLogger(MerchantLifecycleSmsService.class);
        Level previousLevel = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.setLevel(Level.DEBUG);
        logger.addAppender(appender);

        try {
            service.enqueuePurchaseGuide(10L, "order-1", 7, Instant.now());
            service.enqueuePurchaseGuide(10L, "order-1", 7, Instant.now());
            service.enqueueAppExpired(
                    10L,
                    LocalDateTime.of(2026, 8, 1, 23, 59, 59),
                    LocalDateTime.of(2026, 8, 2, 10, 0)
            );
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(previousLevel);
            appender.stop();
        }

        List<ILoggingEvent> enqueueLogs = appender.list.stream()
                .filter(item -> item.getFormattedMessage().contains("业务短信"))
                .toList();
        assertEquals(3, enqueueLogs.size());
        assertEquals(Level.INFO, enqueueLogs.get(0).getLevel());
        assertTrue(enqueueLogs.get(0).getFormattedMessage().contains("新事件入队成功"));
        assertEquals(Level.DEBUG, enqueueLogs.get(1).getLevel());
        assertTrue(enqueueLogs.get(1).getFormattedMessage().contains("重复入队已忽略"));
        assertEquals(Level.DEBUG, enqueueLogs.get(2).getLevel());
        assertTrue(enqueueLogs.get(2).getFormattedMessage().contains("新事件入队成功"));
        assertTrue(enqueueLogs.get(2).getFormattedMessage().contains("scene=APP_EXPIRED"));
    }

    private SmsNotificationEvent event(Long id) {
        SmsNotificationEvent event = new SmsNotificationEvent();
        event.setId(id);
        return event;
    }

    private SmsNotificationEventEnqueueRequest captureRequest() {
        ArgumentCaptor<SmsNotificationEventEnqueueRequest> captor =
                ArgumentCaptor.forClass(SmsNotificationEventEnqueueRequest.class);
        verify(eventService).enqueue(captor.capture());
        return captor.getValue();
    }

    private TransactionSynchronization registerAfterCommit(Runnable action) {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            action.run();
            List<TransactionSynchronization> synchronizations =
                    TransactionSynchronizationManager.getSynchronizations();
            assertEquals(1, synchronizations.size());
            return synchronizations.get(0);
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
}
