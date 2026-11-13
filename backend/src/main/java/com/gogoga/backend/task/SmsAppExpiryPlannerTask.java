package com.gogoga.backend.task;

import com.gogoga.backend.service.RedisDistributedLockService;
import com.gogoga.backend.service.sms.notification.SmsAppExpiryPlanningResult;
import com.gogoga.backend.service.sms.notification.SmsAppExpiryPlannerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 应用到期短信事件规划任务。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = {"scheduled.enabled", "sms.business.enabled", "sms.business.expiry-planner-enabled"},
        havingValue = "true"
)
public class SmsAppExpiryPlannerTask {

    private static final Duration LOCK_LEASE_DURATION = Duration.ofMinutes(10);

    private final SmsAppExpiryPlannerService plannerService;
    private final RedisDistributedLockService distributedLockService;

    @EventListener(ApplicationReadyEvent.class)
    public void planAtStartup() {
        plan();
    }

    @Scheduled(cron = "${sms.business.expiry-planner-cron:0 */10 * * * ?}",
            zone = "${sms.business.zone-id:Asia/Shanghai}")
    public void plan() {
        distributedLockService.tryRun("SmsAppExpiryPlannerTask", LOCK_LEASE_DURATION, () -> {
            SmsAppExpiryPlanningResult result = plannerService.planActiveRights();
            log.info("应用到期短信事件规划完成, candidateCount={}, createdCount={}, duplicateCount={}, "
                            + "failedCount={}",
                    result.candidateCount(), result.createdCount(), result.duplicateCount(),
                    result.failedCount());
        });
    }
}
