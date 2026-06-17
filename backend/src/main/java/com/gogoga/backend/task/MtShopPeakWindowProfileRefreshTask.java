package com.gogoga.backend.task;

import com.gogoga.backend.service.RedisDistributedLockService;
import com.gogoga.backend.service.promotion.ShopPeakWindowProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 门店高峰画像刷新任务
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "scheduled.enabled", havingValue = "true", matchIfMissing = true)
public class MtShopPeakWindowProfileRefreshTask {

    private static final Duration LOCK_LEASE_DURATION = Duration.ofMinutes(30);

    private final ShopPeakWindowProfileService shopPeakWindowProfileService;
    private final RedisDistributedLockService redisDistributedLockService;

    @Scheduled(cron = "${mt.promotion.peak-profile-refresh-cron:0 10 4 * * ?}")
    public void refreshProfiles() {
        redisDistributedLockService.tryRun("MtShopPeakWindowProfileRefreshTask", LOCK_LEASE_DURATION, () -> {
            long startTime = System.currentTimeMillis();
            log.info("定时任务开始执行，task=MtShopPeakWindowProfileRefreshTask");
            try {
                shopPeakWindowProfileService.refreshProfilesForInScopeShops(LocalDateTime.now());
                log.info("定时任务执行完成，task=MtShopPeakWindowProfileRefreshTask, durationMs={}",
                        System.currentTimeMillis() - startTime);
            } catch (RuntimeException e) {
                log.error("定时任务执行失败，task=MtShopPeakWindowProfileRefreshTask, durationMs={}",
                        System.currentTimeMillis() - startTime, e);
                throw e;
            }
        });
    }
}
