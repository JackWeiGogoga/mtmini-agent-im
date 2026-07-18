package com.gogoga.backend.task;

import com.gogoga.backend.service.MtAppOrderService;
import com.gogoga.backend.service.RedisDistributedLockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 美团订阅订单拉取任务
 *
 * @author system
 * @since 2026-03-22
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "scheduled.enabled", havingValue = "true", matchIfMissing = true)
public class MtAppOrderPullTask {

    private final MtAppOrderService mtAppOrderService;
    private final RedisDistributedLockService redisDistributedLockService;

    /**
     * 定时拉取订阅订单
     */
    @Scheduled(cron = "${mt.app-order.pull-cron:0 0/30 * * * ?}")
    public void pullRecentOrders() {
        redisDistributedLockService.tryRun(
                MtAppOrderService.PULL_LOCK_NAME,
                MtAppOrderService.PULL_LOCK_LEASE_DURATION,
                () -> {
                    long startTime = System.currentTimeMillis();
                    log.info("定时任务开始执行，task=MtAppOrderPullTask");
                    try {
                        mtAppOrderService.pullRecentOrders();
                        log.info("定时任务执行完成，task=MtAppOrderPullTask, durationMs={}",
                                System.currentTimeMillis() - startTime);
                    } catch (RuntimeException e) {
                        log.error("定时任务执行失败，task=MtAppOrderPullTask, durationMs={}",
                                System.currentTimeMillis() - startTime, e);
                        throw e;
                    }
                }
        );
    }
}
