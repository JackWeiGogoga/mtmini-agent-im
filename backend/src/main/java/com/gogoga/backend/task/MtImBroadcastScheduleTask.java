package com.gogoga.backend.task;

import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.service.MtImBroadcastScheduleService;
import com.gogoga.backend.service.RedisDistributedLockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * IM 定时群发调度任务。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "scheduled.enabled", havingValue = "true", matchIfMissing = true)
public class MtImBroadcastScheduleTask {

    private final MtImBroadcastScheduleService broadcastScheduleService;
    private final RedisDistributedLockService redisDistributedLockService;
    private final MtProperties mtProperties;

    @Scheduled(cron = "${mt.im-broadcast.schedule-cron:0 * * * * ?}")
    public void dispatchDueBroadcasts() {
        redisDistributedLockService.tryRun(
                MtImBroadcastScheduleService.SCHEDULE_LOCK_NAME,
                Duration.ofSeconds(mtProperties.getImBroadcast().getScheduleLockSeconds()),
                () -> {
                    long startTime = System.currentTimeMillis();
                    try {
                        broadcastScheduleService.dispatchDueBroadcasts();
                    } catch (RuntimeException e) {
                        log.error("定时任务执行失败，task=MtImBroadcastScheduleTask, durationMs={}",
                                System.currentTimeMillis() - startTime, e);
                        throw e;
                    }
                }
        );
    }
}
