package com.gogoga.backend.service.sms.notification;

import com.gogoga.backend.config.SmsProperties;
import com.gogoga.backend.entity.sms.SmsNotificationDelivery;
import com.gogoga.backend.entity.sms.SmsNotificationEvent;
import com.gogoga.backend.enums.BusinessSmsDeliveryModeEnum;
import com.gogoga.backend.enums.SmsNotificationSceneEnum;
import com.gogoga.backend.mapper.SmsNotificationDeliveryMapper;
import com.gogoga.backend.mapper.SmsNotificationEventMapper;
import com.gogoga.backend.service.sms.BusinessTemplateSmsService;
import com.gogoga.backend.service.sms.LoggingTemplateSmsGateway;
import com.gogoga.backend.service.sms.TemplateSmsRequest;
import com.gogoga.backend.service.sms.TencentTemplateSmsGateway;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BusinessSmsLogOnlyEndToEndTest {

    @Test
    void shouldLogAllThreeScenesForEveryDeduplicatedRecipientWithoutTencentCalls() {
        SmsProperties properties = new SmsProperties();
        properties.setMockEnabled(false);
        properties.getBusiness().setEnabled(true);
        properties.getBusiness().setDeliveryMode(BusinessSmsDeliveryModeEnum.LOG_ONLY);
        SmsNotificationEventMapper eventMapper = mock(SmsNotificationEventMapper.class);
        SmsNotificationDeliveryMapper deliveryMapper = mock(SmsNotificationDeliveryMapper.class);
        SmsNotificationSceneHandler handler = mock(SmsNotificationSceneHandler.class);
        LoggingTemplateSmsGateway loggingGateway = spy(new LoggingTemplateSmsGateway());
        TencentTemplateSmsGateway tencentGateway = mock(TencentTemplateSmsGateway.class);
        BusinessTemplateSmsService templateSmsService = new BusinessTemplateSmsService(
                properties,
                loggingGateway,
                tencentGateway
        );
        SmsNotificationProcessingService processingService = new SmsNotificationProcessingService(
                eventMapper,
                deliveryMapper,
                List.of(handler),
                templateSmsService,
                properties,
                new SmsNotificationMetricsService()
        );

        Map<Long, SmsNotificationEvent> events = buildEvents();
        Map<Long, SmsNotificationDelivery> deliveries = new LinkedHashMap<>();
        AtomicLong deliveryId = new AtomicLong(100);
        when(eventMapper.claimPendingById(any(), any(), any())).thenReturn(1);
        when(eventMapper.selectById(any())).thenAnswer(invocation -> events.get(invocation.<Long>getArgument(0)));
        when(handler.supports(any())).thenReturn(true);
        when(handler.prepare(any())).thenReturn(SmsScenePreparation.ready(
                List.of("19355654200", "13858611082")
        ));
        when(handler.canSend(any(), any())).thenReturn(true);
        when(deliveryMapper.insert(any())).thenAnswer(invocation -> {
            SmsNotificationDelivery delivery = invocation.getArgument(0);
            delivery.setId(deliveryId.incrementAndGet());
            deliveries.put(delivery.getId(), delivery);
            return 1;
        });
        when(deliveryMapper.claimPendingById(any(), any(), any())).thenReturn(1);
        when(deliveryMapper.selectById(any())).thenAnswer(invocation -> deliveries.get(invocation.<Long>getArgument(0)));

        LocalDateTime now = LocalDateTime.of(2026, 7, 22, 12, 0);
        events.keySet().forEach(id -> processingService.processEvent(id, "worker", now));
        new ArrayList<>(deliveries.keySet()).forEach(id -> processingService.processDelivery(id, "worker", now));

        verify(deliveryMapper, org.mockito.Mockito.times(6)).markSent(
                any(), any(), any(), org.mockito.ArgumentMatchers.eq("LOG_ONLY"),
                org.mockito.ArgumentMatchers.eq("Logged without provider call"),
                org.mockito.ArgumentMatchers.eq(now)
        );
        ArgumentCaptor<TemplateSmsRequest> requestCaptor = ArgumentCaptor.forClass(TemplateSmsRequest.class);
        verify(loggingGateway, org.mockito.Mockito.times(6)).send(requestCaptor.capture());
        verify(tencentGateway, org.mockito.Mockito.never()).send(any());
        List<TemplateSmsRequest> requests = requestCaptor.getAllValues();
        assertEquals(2, requests.stream().filter(item -> "2685915".equals(item.templateId())
                && item.templateParams().equals(List.of("AI智能体消息管理", "7天"))).count());
        assertEquals(2, requests.stream().filter(item -> "2687571".equals(item.templateId())
                && item.templateParams().equals(List.of("AI智能体消息管理"))).count());
        assertEquals(2, requests.stream().filter(item -> "2687569".equals(item.templateId())
                && item.templateParams().equals(List.of("AI智能体消息管理"))).count());
    }

    private Map<Long, SmsNotificationEvent> buildEvents() {
        Map<Long, SmsNotificationEvent> events = new LinkedHashMap<>();
        events.put(1L, event(1L, SmsNotificationSceneEnum.PURCHASE_GUIDE, "2685915",
                "[\"AI智能体消息管理\",\"7天\"]"));
        events.put(2L, event(2L, SmsNotificationSceneEnum.POWER_EXHAUSTED, "2687571",
                "[\"AI智能体消息管理\"]"));
        events.put(3L, event(3L, SmsNotificationSceneEnum.APP_EXPIRED, "2687569",
                "[\"AI智能体消息管理\"]"));
        return events;
    }

    private SmsNotificationEvent event(
            Long id,
            SmsNotificationSceneEnum scene,
            String templateId,
            String paramsJson
    ) {
        SmsNotificationEvent event = new SmsNotificationEvent();
        event.setId(id);
        event.setScene(scene.name());
        event.setTemplateId(templateId);
        event.setTemplateParamsJson(paramsJson);
        event.setRetryCount(0);
        event.setMaxRetryCount(3);
        return event;
    }
}
