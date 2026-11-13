package com.gogoga.backend.task;

import com.gogoga.backend.config.SmsProperties;
import com.gogoga.backend.service.RedisDistributedLockService;
import com.gogoga.backend.service.sms.notification.SmsNotificationEventService;
import com.gogoga.backend.service.sms.notification.SmsNotificationProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

/**
 * 业务短信事件及投递工作器。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = {"scheduled.enabled", "sms.business.enabled", "sms.business.worker-enabled"},
        havingValue = "true"
)
public class SmsNotificationWorkerTask {

    private final SmsNotificationEventService eventService;
    private final SmsNotificationProcessingService processingService;
    private final RedisDistributedLockService distributedLockService;
    private final SmsProperties smsProperties;

    private final String workerId = "business-sms-" + UUID.randomUUID();

    @Scheduled(fixedDelayString = "${sms.business.poll-delay-ms:1000}")
    public void poll() {
        Duration lease = Duration.ofSeconds(Math.max(5L, smsProperties.getBusiness().getTimeoutSeconds()));
        distributedLockService.tryRun("SmsNotificationEventWorker", lease, this::processEvents);
        distributedLockService.tryRun("SmsNotificationDeliveryWorker", lease, this::processDeliveries);
    }

    @Scheduled(fixedDelayString = "${sms.business.recovery-delay-ms:30000}")
    public void recoverStale() {
        Duration lease = Duration.ofSeconds(Math.max(5L, smsProperties.getBusiness().getTimeoutSeconds()));
        distributedLockService.tryRun("SmsNotificationRecoveryWorker", lease, () -> {
            int recovered = processingService.recoverStale(businessNow());
            if (recovered > 0) {
                log.warn("业务短信超时任务已回收, recoveredCount={}", recovered);
            }
        });
    }

    private void processEvents() {
        LocalDateTime now = businessNow();
        for (Long eventId : eventService.selectExecutableIds(now)) {
            processingService.processEvent(eventId, workerId, businessNow());
        }
    }

    private void processDeliveries() {
        LocalDateTime now = businessNow();
        for (Long deliveryId : processingService.selectExecutableDeliveryIds(now)) {
            processingService.processDelivery(deliveryId, workerId, businessNow());
        }
    }

    private LocalDateTime businessNow() {
        return LocalDateTime.now(ZoneId.of(smsProperties.getBusiness().getZoneId()));
    }
}
