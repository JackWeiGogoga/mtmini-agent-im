package com.gogoga.backend.task;

import com.gogoga.backend.service.MtAccessTokenService;
import com.gogoga.backend.service.RedisDistributedLockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 美团 token 刷新任务
 *
 * @author system
 * @since 2026-03-22
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "scheduled.enabled", havingValue = "true", matchIfMissing = true)
public class MtAccessTokenRefreshTask {

    private static final Duration LOCK_LEASE_DURATION = Duration.ofMinutes(30);

    private final MtAccessTokenService mtAccessTokenService;
    private final RedisDistributedLockService redisDistributedLockService;

    /**
     * 定时刷新 token
     */
    @Scheduled(cron = "${mt.token.refresh-cron:0 */10 * * * ?}")
    public void refreshTokens() {
        redisDistributedLockService.tryRun("MtAccessTokenRefreshTask", LOCK_LEASE_DURATION, () -> {
            long startTime = System.currentTimeMillis();
            log.info("定时任务开始执行，task=MtAccessTokenRefreshTask");
            try {
                mtAccessTokenService.refreshExpiringTokens();
                log.info("定时任务执行完成，task=MtAccessTokenRefreshTask, durationMs={}", System.currentTimeMillis() - startTime);
            } catch (RuntimeException e) {
                log.error("定时任务执行失败，task=MtAccessTokenRefreshTask, durationMs={}", System.currentTimeMillis() - startTime, e);
                throw e;
            }
        });
    }
}
