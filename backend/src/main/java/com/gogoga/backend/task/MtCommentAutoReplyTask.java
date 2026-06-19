package com.gogoga.backend.task;

import com.gogoga.backend.service.MtCommentAutoReplyService;
import com.gogoga.backend.service.RedisDistributedLockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 美团评价自动回复定时任务。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "scheduled.enabled", havingValue = "true", matchIfMissing = true)
public class MtCommentAutoReplyTask {

    private static final Duration LOCK_LEASE_DURATION = Duration.ofHours(2);

    private final MtCommentAutoReplyService mtCommentAutoReplyService;
    private final RedisDistributedLockService redisDistributedLockService;

    @Scheduled(cron = "${mt.comment.pull-cron:0 0 8,13,19 * * ?}")
    public void syncAndReplyRecentUnrepliedComments() {
        redisDistributedLockService.tryRun("MtCommentAutoReplyTask", LOCK_LEASE_DURATION, () -> {
            long startTime = System.currentTimeMillis();
            log.info("定时任务开始执行，task=MtCommentAutoReplyTask");
            try {
                mtCommentAutoReplyService.syncAndReplyRecentUnrepliedComments();
                log.info("定时任务执行完成，task=MtCommentAutoReplyTask, durationMs={}",
                        System.currentTimeMillis() - startTime);
            } catch (RuntimeException e) {
                log.error("定时任务执行失败，task=MtCommentAutoReplyTask, durationMs={}",
                        System.currentTimeMillis() - startTime, e);
                throw e;
            }
        });
    }
}
