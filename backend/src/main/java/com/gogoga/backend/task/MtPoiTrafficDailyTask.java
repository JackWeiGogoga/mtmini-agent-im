package com.gogoga.backend.task;

import com.gogoga.backend.service.MtPoiTrafficDailyService;
import com.gogoga.backend.service.RedisDistributedLockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 门店营业分析日流量同步任务
 *
 * @author system
 * @since 2026-04-10
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "scheduled.enabled", havingValue = "true", matchIfMissing = true)
public class MtPoiTrafficDailyTask {

    private static final Duration LOCK_LEASE_DURATION = Duration.ofHours(6);

    private final MtPoiTrafficDailyService mtPoiTrafficDailyService;
    private final RedisDistributedLockService redisDistributedLockService;

    /**
     * 每天凌晨同步前一天的流量曝光与入店数据
     */
    @Scheduled(cron = "${mt.poi-traffic.pull-cron:0 0 5 * * ?}")
    public void syncYesterdayTrafficData() {
        redisDistributedLockService.tryRun("MtPoiTrafficDailyTask", LOCK_LEASE_DURATION, () -> {
            long startTime = System.currentTimeMillis();
            log.info("定时任务开始执行，task=MtPoiTrafficDailyTask");
            try {
                mtPoiTrafficDailyService.syncYesterdayTrafficData();
                log.info("定时任务执行完成，task=MtPoiTrafficDailyTask, durationMs={}",
                        System.currentTimeMillis() - startTime);
            } catch (RuntimeException e) {
                log.error("定时任务执行失败，task=MtPoiTrafficDailyTask, durationMs={}",
                        System.currentTimeMillis() - startTime, e);
                throw e;
            }
        });
    }
}
