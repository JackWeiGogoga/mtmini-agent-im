package com.gogoga.backend.service.sms.notification;

import com.gogoga.backend.config.SmsProperties;
import com.gogoga.backend.entity.sms.SmsNotificationDelivery;
import com.gogoga.backend.entity.sms.SmsNotificationEvent;
import com.gogoga.backend.enums.SmsNotificationEventStatusEnum;
import com.gogoga.backend.enums.SmsNotificationSceneEnum;
import com.gogoga.backend.mapper.SmsNotificationDeliveryMapper;
import com.gogoga.backend.mapper.SmsNotificationEventMapper;
import com.gogoga.backend.service.sms.BusinessSmsSendWindowPolicy;
import com.gogoga.backend.service.sms.BusinessTemplateSmsService;
import com.gogoga.backend.service.sms.TemplateSmsException;
import com.gogoga.backend.service.sms.TemplateSmsResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SmsNotificationProcessingServiceTest {

    private SmsNotificationEventMapper eventMapper;
    private SmsNotificationDeliveryMapper deliveryMapper;
    private SmsNotificationSceneHandler handler;
    private BusinessTemplateSmsService templateSmsService;
    private SmsNotificationProcessingService service;

    @BeforeEach
    void setUp() {
        eventMapper = mock(SmsNotificationEventMapper.class);
        deliveryMapper = mock(SmsNotificationDeliveryMapper.class);
        handler = mock(SmsNotificationSceneHandler.class);
        templateSmsService = mock(BusinessTemplateSmsService.class);
        SmsProperties properties = new SmsProperties();
        properties.getBusiness().setMaxRetryCount(3);
        properties.getBusiness().setInitialBackoffSeconds(30);
        when(handler.supports(SmsNotificationSceneEnum.PURCHASE_GUIDE)).thenReturn(true);
        service = new SmsNotificationProcessingService(
                eventMapper,
                deliveryMapper,
                List.of(handler),
                templateSmsService,
                properties,
                mock(SmsNotificationMetricsService.class),
                new BusinessSmsSendWindowPolicy()
        );
    }

    @Test
    void shouldExpandOneEventIntoIndependentPerPhoneDeliveries() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 22, 12, 0);
        SmsNotificationEvent event = event(1L);
        when(eventMapper.claimPendingById(1L, "worker", now)).thenReturn(1);
        when(eventMapper.selectById(1L)).thenReturn(event);
        when(handler.prepare(event)).thenReturn(SmsScenePreparation.ready(
                List.of("19355654200", "13858611082")
        ));

        service.processEvent(1L, "worker", now);

        ArgumentCaptor<SmsNotificationDelivery> captor = ArgumentCaptor.forClass(SmsNotificationDelivery.class);
        verify(deliveryMapper, org.mockito.Mockito.times(2)).insert(captor.capture());
        assertEquals(List.of("19355654200", "13858611082"), captor.getAllValues().stream()
                .map(SmsNotificationDelivery::getRecipientPhone).toList());
        verify(eventMapper).markTerminal(1L, SmsNotificationEventStatusEnum.EXPANDED.name(), now, null);
    }

    @Test
    void shouldFinishEventWithoutDeliveryWhenNoValidRecipientExists() {
        LocalDateTime now = LocalDateTime.now();
        SmsNotificationEvent event = event(1L);
        when(eventMapper.claimPendingById(1L, "worker", now)).thenReturn(1);
        when(eventMapper.selectById(1L)).thenReturn(event);
        when(handler.prepare(event)).thenReturn(SmsScenePreparation.ready(List.of()));

        service.processEvent(1L, "worker", now);

        verify(deliveryMapper, never()).insert(any());
        verify(eventMapper).markTerminal(1L, SmsNotificationEventStatusEnum.NO_RECIPIENT.name(), now,
                "NO_VALID_CONTACT_PHONE");
    }

    @Test
    void shouldNotExecuteWhenAnotherWorkerAlreadyClaimedEvent() {
        LocalDateTime now = LocalDateTime.now();
        when(eventMapper.claimPendingById(1L, "worker", now)).thenReturn(0);

        service.processEvent(1L, "worker", now);

        verify(eventMapper, never()).selectById(any());
        verify(deliveryMapper, never()).insert(any());
    }

    @Test
    void shouldSendOneDeliveryAndRetryTemporaryFailureOfAnotherIndependently() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 22, 12, 0);
        SmsNotificationEvent event = event(1L);
        SmsNotificationDelivery first = delivery(11L, "19355654200");
        SmsNotificationDelivery second = delivery(12L, "13858611082");
        when(eventMapper.selectById(1L)).thenReturn(event);
        when(deliveryMapper.claimPendingById(eq(11L), anyString(), any())).thenReturn(1);
        when(deliveryMapper.claimPendingById(eq(12L), anyString(), any())).thenReturn(1);
        when(deliveryMapper.selectById(11L)).thenReturn(first);
        when(deliveryMapper.selectById(12L)).thenReturn(second);
        when(handler.canSend(eq(event), anyString())).thenReturn(true);
        when(templateSmsService.send(any()))
                .thenReturn(new TemplateSmsResult("request-1", "serial-1", "Ok", "accepted"))
                .thenThrow(new TemplateSmsException(true, "InternalError", "temporary"));

        service.processDelivery(11L, "worker", now);
        service.processDelivery(12L, "worker", now);

        verify(deliveryMapper).markSent(11L, "request-1", "serial-1", "Ok", "accepted", now);
        verify(deliveryMapper).rescheduleFromProcessing(
                eq(12L), eq(1), eq(now.plusSeconds(30)), eq("temporary"),
                eq("InternalError"), eq("temporary"), eq(now)
        );
    }

    @Test
    void shouldDeferDeliveryOutsideSendWindowWithoutCallingGatewayOrUsingRetry() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 24, 2, 0, 11);
        SmsNotificationEvent event = event(1L);
        SmsNotificationDelivery delivery = delivery(11L, "19355654200");
        when(eventMapper.selectById(1L)).thenReturn(event);
        when(deliveryMapper.claimPendingById(11L, "worker", now)).thenReturn(1);
        when(deliveryMapper.selectById(11L)).thenReturn(delivery);

        service.processDelivery(11L, "worker", now);

        verify(deliveryMapper).deferFromProcessing(
                11L,
                LocalDateTime.of(2026, 7, 25, 8, 0),
                "OUTSIDE_BUSINESS_SMS_SEND_WINDOW",
                now
        );
        verify(handler, never()).canSend(any(), anyString());
        verify(templateSmsService, never()).send(any());
        verify(deliveryMapper, never()).rescheduleFromProcessing(
                any(), anyInt(), any(), anyString(), anyString(), anyString(), any()
        );
    }

    @Test
    void shouldTerminateDeterministicProviderFailureWithoutRetry() {
        LocalDateTime now = LocalDateTime.now();
        SmsNotificationEvent event = event(1L);
        SmsNotificationDelivery delivery = delivery(11L, "19355654200");
        when(eventMapper.selectById(1L)).thenReturn(event);
        when(deliveryMapper.claimPendingById(11L, "worker", now)).thenReturn(1);
        when(deliveryMapper.selectById(11L)).thenReturn(delivery);
        when(handler.canSend(event, delivery.getRecipientPhone())).thenReturn(true);
        when(templateSmsService.send(any())).thenThrow(new TemplateSmsException(
                false,
                "InvalidParameterValue.PhoneNumberFormatError",
                "invalid"
        ));

        service.processDelivery(11L, "worker", now);

        verify(deliveryMapper).markDead(
                11L,
                1,
                now,
                "invalid",
                "InvalidParameterValue.PhoneNumberFormatError",
                "invalid"
        );
        verify(deliveryMapper, never()).rescheduleFromProcessing(
                any(), anyInt(), any(), anyString(), anyString(), anyString(), any()
        );
    }

    @Test
    void shouldRecoverStaleProcessingAndStopAfterMaximumRetries() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 22, 12, 0);
        SmsNotificationEvent event = event(1L);
        SmsNotificationDelivery delivery = delivery(11L, "19355654200");
        delivery.setRetryCount(3);
        when(eventMapper.selectStaleProcessing(any(), anyInt())).thenReturn(List.of(event));
        when(deliveryMapper.selectStaleProcessing(any(), anyInt())).thenReturn(List.of(delivery));

        assertEquals(2, service.recoverStale(now));

        verify(eventMapper).rescheduleFromProcessing(
                1L, 1, now, "PROCESSING_TIMEOUT", now
        );
        verify(deliveryMapper).markDead(
                11L, 4, now, "PROCESSING_TIMEOUT", "PROCESSING_TIMEOUT", "投递处理超时"
        );
    }

    private SmsNotificationEvent event(Long id) {
        SmsNotificationEvent event = new SmsNotificationEvent();
        event.setId(id);
        event.setScene(SmsNotificationSceneEnum.PURCHASE_GUIDE.name());
        event.setTemplateId("2685915");
        event.setTemplateParamsJson("[\"AI智能体消息管理\",\"7天\"]");
        event.setRetryCount(0);
        event.setMaxRetryCount(3);
        return event;
    }

    private SmsNotificationDelivery delivery(Long id, String phone) {
        SmsNotificationDelivery delivery = new SmsNotificationDelivery();
        delivery.setId(id);
        delivery.setEventId(1L);
        delivery.setRecipientPhone(phone);
        delivery.setRetryCount(0);
        delivery.setMaxRetryCount(3);
        return delivery;
    }
}
