package com.gogoga.backend.task;

import com.gogoga.backend.service.MtPromotionRightExpiryShutdownService;
import com.gogoga.backend.service.RedisDistributedLockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 功能权益过期后的推广链路关停任务。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "scheduled.enabled", havingValue = "true", matchIfMissing = true)
public class MtPromotionRightExpiryShutdownTask {

    private static final Duration LOCK_LEASE_DURATION = Duration.ofMinutes(30);

    private final MtPromotionRightExpiryShutdownService mtPromotionRightExpiryShutdownService;
    private final RedisDistributedLockService redisDistributedLockService;

    @Value("${mt.promotion.right-expiry-shutdown.batch-size:100}")
    private int batchSize;

    @Scheduled(cron = "${mt.promotion.right-expiry-shutdown.cron:0 */10 * * * ?}")
    public void processExpiredPromotionRights() {
        redisDistributedLockService.tryRun("MtPromotionRightExpiryShutdownTask", LOCK_LEASE_DURATION, () -> {
            long startTime = System.currentTimeMillis();
            log.info("定时任务开始执行，task=MtPromotionRightExpiryShutdownTask, batchSize={}", batchSize);
            try {
                mtPromotionRightExpiryShutdownService.processExpiredPromotionRights(batchSize);
                log.info("定时任务执行完成，task=MtPromotionRightExpiryShutdownTask, durationMs={}",
                        System.currentTimeMillis() - startTime);
            } catch (RuntimeException e) {
                log.error("定时任务执行失败，task=MtPromotionRightExpiryShutdownTask, durationMs={}",
                        System.currentTimeMillis() - startTime, e);
                throw e;
            }
        });
    }
}
