package com.gogoga.backend.service.sms.notification;

import com.gogoga.backend.config.SmsProperties;
import com.gogoga.backend.entity.sms.SmsNotificationEvent;
import com.gogoga.backend.enums.SmsNotificationSceneEnum;
import com.gogoga.backend.mapper.SmsNotificationEventMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SmsNotificationEventServiceTest {

    private SmsNotificationEventMapper eventMapper;
    private SmsProperties properties;
    private SmsNotificationEventService service;

    @BeforeEach
    void setUp() {
        eventMapper = mock(SmsNotificationEventMapper.class);
        properties = new SmsProperties();
        properties.getBusiness().setEnabled(true);
        service = new SmsNotificationEventService(
                eventMapper,
                properties,
                mock(SmsNotificationMetricsService.class)
        );
    }

    @Test
    void shouldSaveTemplateAndOrderedParameterSnapshot() {
        LocalDateTime scheduledAt = LocalDateTime.of(2026, 7, 23, 10, 0);

        SmsNotificationEventEnqueueResult result = service.enqueue(request("order-1", scheduledAt));

        ArgumentCaptor<SmsNotificationEvent> captor = ArgumentCaptor.forClass(SmsNotificationEvent.class);
        verify(eventMapper).insert(captor.capture());
        SmsNotificationEvent event = captor.getValue();
        assertEquals("PURCHASE_GUIDE", event.getScene());
        assertEquals("order-1", event.getBizKey());
        assertEquals("[\"AI智能体消息管理\",\"7天\"]", event.getTemplateParamsJson());
        assertEquals(scheduledAt, event.getAvailableAt());
        assertSame(event, result.event());
        assertTrue(result.created());
    }

    @Test
    void shouldTreatDuplicateBusinessEventAsIdempotentSuccess() {
        SmsNotificationEvent existing = new SmsNotificationEvent();
        existing.setId(9L);
        when(eventMapper.insert(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new DuplicateKeyException("duplicate"));
        when(eventMapper.selectBySceneAndBizKey("PURCHASE_GUIDE", "order-1")).thenReturn(existing);

        SmsNotificationEventEnqueueResult result = service.enqueue(
                request("order-1", LocalDateTime.now())
        );

        assertSame(existing, result.event());
        assertFalse(result.created());
    }

    @Test
    void shouldNotCreateBusinessEventWhenIndependentSwitchIsOff() {
        properties.getBusiness().setEnabled(false);

        SmsNotificationEventEnqueueResult result = service.enqueue(
                request("order-1", LocalDateTime.now())
        );

        assertNull(result);
        verify(eventMapper, never()).insert(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldAlwaysUseIndependentTransactionForEventPersistence() throws Exception {
        Transactional transactional = SmsNotificationEventService.class
                .getMethod("enqueue", SmsNotificationEventEnqueueRequest.class)
                .getAnnotation(Transactional.class);

        assertEquals(Propagation.REQUIRES_NEW, transactional.propagation());
    }

    private SmsNotificationEventEnqueueRequest request(String bizKey, LocalDateTime scheduledAt) {
        return new SmsNotificationEventEnqueueRequest(
                SmsNotificationSceneEnum.PURCHASE_GUIDE,
                bizKey,
                10L,
                bizKey,
                "2685915",
                List.of("AI智能体消息管理", "7天"),
                Map.of("days", 7),
                scheduledAt
        );
    }
}
