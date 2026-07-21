package com.gogoga.backend.service;

import com.gogoga.backend.config.MtProperties;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 可续租的 Conversation 级分布式锁，确保同一顾客会话串行生成和发送。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImConversationLockService {

    private static final String KEY_PREFIX = "lock:im-conversation:";
    private static final DefaultRedisScript<Long> RENEW_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('pexpire', KEYS[1], ARGV[2]) else return 0 end",
            Long.class
    );
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('del', KEYS[1]) else return 0 end",
            Long.class
    );

    private final MtProperties mtProperties;
    private final StringRedisTemplate redisTemplate;
    private final ScheduledExecutorService renewExecutor = Executors.newScheduledThreadPool(1, runnable -> {
        Thread thread = new Thread(runnable, "im-conversation-lock-renew");
        thread.setDaemon(true);
        return thread;
    });

    public LockHandle tryAcquire(Long conversationId) {
        if (conversationId == null) {
            throw new IllegalArgumentException("IM_CONVERSATION_ID_MISSING");
        }
        long leaseSeconds = Math.max(mtProperties.getImAutoReply().getLockLeaseSeconds(), 3L);
        long renewSeconds = Math.min(
                Math.max(mtProperties.getImAutoReply().getLockRenewSeconds(), 1L),
                Math.max(leaseSeconds / 2L, 1L));
        String key = KEY_PREFIX + conversationId;
        String value = UUID.randomUUID().toString();
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(key, value, Duration.ofSeconds(leaseSeconds));
        if (!Boolean.TRUE.equals(locked)) {
            return null;
        }
        LockHandle handle = new LockHandle(key, value, leaseSeconds);
        ScheduledFuture<?> future = renewExecutor.scheduleAtFixedRate(
                handle::renew, renewSeconds, renewSeconds, TimeUnit.SECONDS);
        handle.setRenewFuture(future);
        return handle;
    }

    @PreDestroy
    void shutdown() {
        renewExecutor.shutdownNow();
    }

    public class LockHandle implements AutoCloseable {

        private final String key;
        private final String value;
        private final long leaseSeconds;
        private final AtomicBoolean valid = new AtomicBoolean(true);
        private ScheduledFuture<?> renewFuture;

        private LockHandle(String key, String value, long leaseSeconds) {
            this.key = key;
            this.value = value;
            this.leaseSeconds = leaseSeconds;
        }

        public boolean isValid() {
            return valid.get();
        }

        private void setRenewFuture(ScheduledFuture<?> renewFuture) {
            this.renewFuture = renewFuture;
        }

        private void renew() {
            if (!valid.get()) {
                return;
            }
            try {
                Long renewed = redisTemplate.execute(
                        RENEW_SCRIPT,
                        Collections.singletonList(key),
                        value,
                        String.valueOf(Duration.ofSeconds(leaseSeconds).toMillis())
                );
                if (!Long.valueOf(1L).equals(renewed)) {
                    valid.set(false);
                    log.warn("IM Conversation锁续租失败，lockKey={}", key);
                }
            } catch (RuntimeException e) {
                valid.set(false);
                log.warn("IM Conversation锁续租异常，lockKey={}", key, e);
            }
        }

        @Override
        public void close() {
            valid.set(false);
            if (renewFuture != null) {
                renewFuture.cancel(false);
            }
            try {
                redisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(key), value);
            } catch (RuntimeException e) {
                log.warn("释放IM Conversation锁失败，lockKey={}", key, e);
            }
        }
    }
}
