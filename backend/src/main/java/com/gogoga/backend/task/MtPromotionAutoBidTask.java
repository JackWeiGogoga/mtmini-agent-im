package com.gogoga.backend.task;

import com.gogoga.backend.service.MtPromotionAutoBidService;
import com.gogoga.backend.service.RedisDistributedLockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 自动调价定时任务
 *
 * @author system
 * @since 2026-03-26
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "scheduled.enabled", havingValue = "true", matchIfMissing = true)
public class MtPromotionAutoBidTask {

    private static final Duration LOCK_LEASE_DURATION = Duration.ofMinutes(30);

    private final MtPromotionAutoBidService mtPromotionAutoBidService;
    private final RedisDistributedLockService redisDistributedLockService;

    /**
     * 每 10 分钟执行一次自动调价
     */
    @Scheduled(cron = "${mt.promotion.auto-bid-cron:0 */10 * * * ?}")
    public void executeAutoBid() {
        redisDistributedLockService.tryRun("MtPromotionAutoBidTask", LOCK_LEASE_DURATION, () -> {
            long startTime = System.currentTimeMillis();
            log.info("定时任务开始执行，task=MtPromotionAutoBidTask");
            try {
                mtPromotionAutoBidService.executeCurrentSlot();
                log.info("定时任务执行完成，task=MtPromotionAutoBidTask, durationMs={}", System.currentTimeMillis() - startTime);
            } catch (RuntimeException e) {
                log.error("定时任务执行失败，task=MtPromotionAutoBidTask, durationMs={}", System.currentTimeMillis() - startTime, e);
                throw e;
            }
        });
    }
}
