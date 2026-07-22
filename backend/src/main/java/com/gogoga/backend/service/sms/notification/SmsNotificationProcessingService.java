package com.gogoga.backend.service.sms.notification;

import com.gogoga.backend.config.SmsProperties;
import com.gogoga.backend.entity.sms.SmsNotificationDelivery;
import com.gogoga.backend.entity.sms.SmsNotificationEvent;
import com.gogoga.backend.enums.SmsNotificationDeliveryStatusEnum;
import com.gogoga.backend.enums.SmsNotificationEventStatusEnum;
import com.gogoga.backend.enums.SmsNotificationSceneEnum;
import com.gogoga.backend.mapper.SmsNotificationDeliveryMapper;
import com.gogoga.backend.mapper.SmsNotificationEventMapper;
import com.gogoga.backend.service.sms.TemplateSmsException;
import com.gogoga.backend.service.sms.TemplateSmsRequest;
import com.gogoga.backend.service.sms.TemplateSmsResult;
import com.gogoga.backend.service.sms.BusinessTemplateSmsService;
import com.gogoga.backend.util.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 短信事件展开、逐手机号发送与失败恢复。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SmsNotificationProcessingService {

    private final SmsNotificationEventMapper eventMapper;
    private final SmsNotificationDeliveryMapper deliveryMapper;
    private final List<SmsNotificationSceneHandler> sceneHandlers;
    private final BusinessTemplateSmsService templateSmsService;
    private final SmsProperties smsProperties;
    private final SmsNotificationMetricsService metricsService;

    @Transactional(rollbackFor = Exception.class)
    public void processEvent(Long eventId, String workerId, LocalDateTime now) {
        if (eventMapper.claimPendingById(eventId, workerId, now) != 1) {
            return;
        }
        SmsNotificationEvent event = eventMapper.selectById(eventId);
        try {
            SmsNotificationSceneHandler handler = resolveHandler(event);
            SmsScenePreparation preparation = handler.prepare(event);
            if (preparation.cancelled()) {
                eventMapper.markTerminal(eventId, SmsNotificationEventStatusEnum.CANCELLED.name(), now,
                        abbreviate(preparation.reason(), 1000));
                metricsService.increment(event.getScene(), "cancelled");
                return;
            }
            if (preparation.recipients().isEmpty()) {
                eventMapper.markTerminal(eventId, SmsNotificationEventStatusEnum.NO_RECIPIENT.name(), now,
                        "NO_VALID_CONTACT_PHONE");
                metricsService.increment(event.getScene(), "no_recipient");
                return;
            }
            for (String phone : preparation.recipients()) {
                insertDelivery(event, phone, now);
            }
            eventMapper.markTerminal(eventId, SmsNotificationEventStatusEnum.EXPANDED.name(), now, null);
            metricsService.increment(event.getScene(), "expanded");
        } catch (RuntimeException e) {
            handleEventFailure(event, e, now);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void processDelivery(Long deliveryId, String workerId, LocalDateTime now) {
        if (deliveryMapper.claimPendingById(deliveryId, workerId, now) != 1) {
            return;
        }
        SmsNotificationDelivery delivery = deliveryMapper.selectById(deliveryId);
        SmsNotificationEvent event = eventMapper.selectById(delivery.getEventId());
        try {
            SmsNotificationSceneHandler handler = resolveHandler(event);
            if (!handler.canSend(event, delivery.getRecipientPhone())) {
                deliveryMapper.markCancelled(deliveryId, now, "SCENE_NO_LONGER_VALID");
                metricsService.increment(event.getScene(), "delivery_cancelled");
                return;
            }
            TemplateSmsResult result = templateSmsService.send(new TemplateSmsRequest(
                    delivery.getRecipientPhone(),
                    event.getTemplateId(),
                    JsonUtil.toList(event.getTemplateParamsJson(), String.class),
                    event.getScene() + ":" + event.getId()
            ));
            deliveryMapper.markSent(
                    deliveryId,
                    result.providerRequestId(),
                    result.providerSerialNo(),
                    result.providerCode(),
                    abbreviate(result.providerMessage(), 500),
                    now
            );
            metricsService.increment(event.getScene(), "sent");
        } catch (TemplateSmsException e) {
            handleDeliveryFailure(delivery, event, e.isRetryable(), e.getProviderCode(),
                    e.getProviderMessage(), now);
        } catch (RuntimeException e) {
            handleDeliveryFailure(delivery, event, true, "UNEXPECTED_ERROR", e.getMessage(), now);
        }
    }

    public List<Long> selectExecutableDeliveryIds(LocalDateTime now) {
        return deliveryMapper.selectExecutableIds(now, smsProperties.getBusiness().getPollSize());
    }

    @Transactional(rollbackFor = Exception.class)
    public int recoverStale(LocalDateTime now) {
        LocalDateTime claimedBefore = now.minusSeconds(smsProperties.getBusiness().getTimeoutSeconds());
        int limit = smsProperties.getBusiness().getPollSize();
        int recovered = 0;
        for (SmsNotificationEvent event : eventMapper.selectStaleProcessing(claimedBefore, limit)) {
            recoverEvent(event, now);
            recovered++;
        }
        for (SmsNotificationDelivery delivery : deliveryMapper.selectStaleProcessing(claimedBefore, limit)) {
            recoverDelivery(delivery, now);
            recovered++;
        }
        return recovered;
    }

    private void insertDelivery(SmsNotificationEvent event, String phone, LocalDateTime now) {
        SmsNotificationDelivery delivery = new SmsNotificationDelivery();
        delivery.setEventId(event.getId());
        delivery.setRecipientPhone(phone);
        delivery.setStatus(SmsNotificationDeliveryStatusEnum.PENDING.name());
        delivery.setRetryCount(0);
        delivery.setMaxRetryCount(event.getMaxRetryCount());
        delivery.setAvailableAt(now);
        delivery.setCreateTime(now);
        delivery.setUpdateTime(now);
        try {
            deliveryMapper.insert(delivery);
        } catch (DuplicateKeyException ignored) {
            // 事件重试或多工作器竞争时，由逐事件/手机号唯一键保证幂等。
        }
    }

    private SmsNotificationSceneHandler resolveHandler(SmsNotificationEvent event) {
        if (event == null || StringUtils.isBlank(event.getScene())) {
            throw new IllegalArgumentException("短信事件不存在或场景为空");
        }
        SmsNotificationSceneEnum scene;
        try {
            scene = SmsNotificationSceneEnum.valueOf(event.getScene());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("不支持的短信事件场景: " + event.getScene(), e);
        }
        return sceneHandlers.stream()
                .filter(handler -> handler.supports(scene))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未配置短信场景处理器: " + scene));
    }

    private void handleEventFailure(SmsNotificationEvent event, RuntimeException error, LocalDateTime now) {
        int retryCount = safeRetryCount(event.getRetryCount()) + 1;
        String message = abbreviate(error.getMessage(), 1000);
        if (retryCount > event.getMaxRetryCount()) {
            eventMapper.markDead(event.getId(), retryCount, now, message);
            metricsService.increment(event.getScene(), "dead");
            log.error("短信事件超过最大重试次数, scene={}, eventId={}", event.getScene(), event.getId(), error);
            return;
        }
        eventMapper.rescheduleFromProcessing(event.getId(), retryCount, nextAvailableAt(retryCount, now), message, now);
        metricsService.increment(event.getScene(), "retry");
        log.warn("短信事件处理失败并退避重试, scene={}, eventId={}, retryCount={}",
                event.getScene(), event.getId(), retryCount, error);
    }

    private void handleDeliveryFailure(
            SmsNotificationDelivery delivery,
            SmsNotificationEvent event,
            boolean retryable,
            String providerCode,
            String providerMessage,
            LocalDateTime now
    ) {
        int retryCount = safeRetryCount(delivery.getRetryCount()) + 1;
        String message = abbreviate(providerMessage, 1000);
        if (!retryable || retryCount > delivery.getMaxRetryCount()) {
            deliveryMapper.markDead(delivery.getId(), retryCount, now, message,
                    abbreviate(providerCode, 128), abbreviate(providerMessage, 500));
            metricsService.increment(event.getScene(), "delivery_dead");
            log.warn("模板短信投递终止, scene={}, eventId={}, deliveryId={}, code={}, retryable={}",
                    event.getScene(), event.getId(), delivery.getId(), providerCode, retryable);
            return;
        }
        deliveryMapper.rescheduleFromProcessing(delivery.getId(), retryCount,
                nextAvailableAt(retryCount, now), message,
                abbreviate(providerCode, 128), abbreviate(providerMessage, 500), now);
        metricsService.increment(event.getScene(), "delivery_retry");
        log.warn("模板短信投递退避重试, scene={}, eventId={}, deliveryId={}, code={}, retryCount={}",
                event.getScene(), event.getId(), delivery.getId(), providerCode, retryCount);
    }

    private void recoverEvent(SmsNotificationEvent event, LocalDateTime now) {
        int retryCount = safeRetryCount(event.getRetryCount()) + 1;
        if (retryCount > event.getMaxRetryCount()) {
            eventMapper.markDead(event.getId(), retryCount, now, "PROCESSING_TIMEOUT");
        } else {
            eventMapper.rescheduleFromProcessing(event.getId(), retryCount, now, "PROCESSING_TIMEOUT", now);
        }
        metricsService.increment(event.getScene(), "recovered");
    }

    private void recoverDelivery(SmsNotificationDelivery delivery, LocalDateTime now) {
        int retryCount = safeRetryCount(delivery.getRetryCount()) + 1;
        if (retryCount > delivery.getMaxRetryCount()) {
            deliveryMapper.markDead(delivery.getId(), retryCount, now, "PROCESSING_TIMEOUT",
                    "PROCESSING_TIMEOUT", "投递处理超时");
        } else {
            deliveryMapper.rescheduleFromProcessing(delivery.getId(), retryCount, now,
                    "PROCESSING_TIMEOUT", "PROCESSING_TIMEOUT", "投递处理超时", now);
        }
        metricsService.increment("delivery", "recovered");
    }

    private LocalDateTime nextAvailableAt(int retryCount, LocalDateTime now) {
        long initial = Math.max(1L, smsProperties.getBusiness().getInitialBackoffSeconds());
        long maximum = Math.max(initial, smsProperties.getBusiness().getMaxBackoffSeconds());
        long multiplier = 1L << Math.min(20, Math.max(0, retryCount - 1));
        long delay = Math.min(maximum, initial * multiplier);
        return now.plusSeconds(delay);
    }

    private int safeRetryCount(Integer retryCount) {
        return retryCount == null ? 0 : retryCount;
    }

    private String abbreviate(String value, int maxLength) {
        return StringUtils.abbreviate(StringUtils.defaultIfBlank(value, "UNKNOWN_ERROR"), maxLength);
    }
}
