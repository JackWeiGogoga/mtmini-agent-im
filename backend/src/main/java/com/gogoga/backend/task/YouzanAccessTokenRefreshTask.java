package com.gogoga.backend.task;

import com.gogoga.backend.service.RedisDistributedLockService;
import com.gogoga.backend.service.YouzanTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 有赞 token 刷新任务
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "scheduled.enabled", havingValue = "true", matchIfMissing = true)
public class YouzanAccessTokenRefreshTask {

    private static final Duration LOCK_LEASE_DURATION = Duration.ofMinutes(30);

    private final YouzanTokenService youzanTokenService;
    private final RedisDistributedLockService redisDistributedLockService;

    /**
     * 定时刷新有赞 access_token
     */
    @Scheduled(cron = "${youzan.token.refresh-cron:0 */10 * * * ?}")
    public void refreshTokens() {
        redisDistributedLockService.tryRun("YouzanAccessTokenRefreshTask", LOCK_LEASE_DURATION, () -> {
            long startTime = System.currentTimeMillis();
            log.info("定时任务开始执行，task=YouzanAccessTokenRefreshTask");
            try {
                youzanTokenService.refreshExpiringTokens();
                log.info("定时任务执行完成，task=YouzanAccessTokenRefreshTask, durationMs={}", System.currentTimeMillis() - startTime);
            } catch (RuntimeException e) {
                log.error("定时任务执行失败，task=YouzanAccessTokenRefreshTask, durationMs={}", System.currentTimeMillis() - startTime, e);
                throw e;
            }
        });
    }
}
