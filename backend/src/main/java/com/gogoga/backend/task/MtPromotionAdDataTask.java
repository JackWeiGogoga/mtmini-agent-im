package com.gogoga.backend.task;

import com.gogoga.backend.service.MtPromotionAdDataService;
import com.gogoga.backend.service.RedisDistributedLockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 门店推广广告明细同步任务
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "scheduled.enabled", havingValue = "true", matchIfMissing = true)
public class MtPromotionAdDataTask {

    private static final Duration LOCK_LEASE_DURATION = Duration.ofHours(6);

    private final MtPromotionAdDataService mtPromotionAdDataService;
    private final RedisDistributedLockService redisDistributedLockService;

    @Scheduled(cron = "${mt.promotion-ad-data.pull-cron:0 30 13 * * ?}")
    public void syncYesterdayAdData() {
        redisDistributedLockService.tryRun("MtPromotionAdDataTask", LOCK_LEASE_DURATION, () -> {
            long startTime = System.currentTimeMillis();
            log.info("定时任务开始执行，task=MtPromotionAdDataTask");
            try {
                mtPromotionAdDataService.syncYesterdayAdData();
                log.info("定时任务执行完成，task=MtPromotionAdDataTask, durationMs={}",
                        System.currentTimeMillis() - startTime);
            } catch (RuntimeException e) {
                log.error("定时任务执行失败，task=MtPromotionAdDataTask, durationMs={}",
                        System.currentTimeMillis() - startTime, e);
                throw e;
            }
        });
    }
}
