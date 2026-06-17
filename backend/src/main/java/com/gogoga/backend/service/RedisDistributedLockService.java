package com.gogoga.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;

/**
 * 基于 Redis 的轻量分布式锁服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisDistributedLockService {

    private static final String LOCK_KEY_PREFIX = "lock:scheduled:";
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('del', KEYS[1]) "
                    + "else return 0 end",
            Long.class
    );

    private final StringRedisTemplate stringRedisTemplate;

    public boolean tryRun(String lockName, Duration leaseDuration, Runnable action) {
        if (StringUtils.isBlank(lockName)) {
            throw new IllegalArgumentException("lockName 不能为空");
        }
        if (leaseDuration == null || leaseDuration.isZero() || leaseDuration.isNegative()) {
            throw new IllegalArgumentException("leaseDuration 必须大于 0");
        }
        if (action == null) {
            throw new IllegalArgumentException("action 不能为空");
        }

        String lockKey = LOCK_KEY_PREFIX + lockName;
        String lockValue = UUID.randomUUID().toString();
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, leaseDuration);
        if (!Boolean.TRUE.equals(locked)) {
            log.info("分布式锁未获取，跳过执行，lockKey={}", lockKey);
            return false;
        }

        try {
            action.run();
            return true;
        } finally {
            safelyUnlock(lockKey, lockValue);
        }
    }

    private void safelyUnlock(String lockKey, String lockValue) {
        try {
            stringRedisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(lockKey), lockValue);
        } catch (Exception e) {
            log.warn("释放分布式锁失败，lockKey={}", lockKey, e);
        }
    }
}
