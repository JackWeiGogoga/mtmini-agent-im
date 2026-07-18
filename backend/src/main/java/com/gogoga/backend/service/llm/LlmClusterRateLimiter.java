package com.gogoga.backend.service.llm;

import com.gogoga.backend.config.LlmRateLimitProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;

/**
 * 基于 Redis GCRA 的 OpenAI 兼容模型集群限流器。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LlmClusterRateLimiter {

    private static final DefaultRedisScript<String> GCRA_SCRIPT = new DefaultRedisScript<>("""
            local tat = tonumber(redis.call('get', KEYS[1]) or '0')
            local now = tonumber(ARGV[1])
            local interval = tonumber(ARGV[2])
            local burst = tonumber(ARGV[3])
            local ttl = tonumber(ARGV[4])
            local allow_at = tat - ((burst - 1) * interval)
            if now < allow_at then
              return '0:' .. tostring(allow_at - now)
            end
            local new_tat = math.max(now, tat) + interval
            redis.call('psetex', KEYS[1], ttl, tostring(new_tat))
            return '1:0'
            """, String.class);

    private static final DefaultRedisScript<Long> COOLDOWN_SCRIPT = new DefaultRedisScript<>("""
            local current = tonumber(redis.call('get', KEYS[1]) or '0')
            local blocked_until = tonumber(ARGV[1])
            local ttl = tonumber(ARGV[2])
            if blocked_until > current then
              redis.call('psetex', KEYS[1], ttl, tostring(blocked_until))
              return blocked_until
            end
            return current
            """, Long.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final LlmRateLimitProperties properties;
    private final LlmRateLimitKeyFactory keyFactory;
    private final LlmRateLimitMetricsService metricsService;

    public void acquire(LlmRateLimitDescriptor descriptor) {
        if (!properties.isEnabled()) {
            return;
        }
        LlmRateLimitKeyFactory.SafeBucket bucket = keyFactory.create(descriptor);
        LlmRateLimitProperties.ResolvedRule rule = properties.resolve(bucket.provider(), bucket.model());
        long maxWaitMillis = descriptor.interactive()
                ? properties.getInteractiveMaxWaitMillis()
                : properties.getMaxWaitMillis();
        long startedAt = System.currentTimeMillis();
        boolean waited = false;
        while (true) {
            LlmRateLimitPermit permit;
            try {
                permit = tryAcquire(bucket, rule, System.currentTimeMillis());
            } catch (RuntimeException e) {
                metricsService.increment(bucket.safeBucketId(), "redis_error");
                throw new LlmCapacityDeferredException(
                        "LLM_RATE_LIMIT_REDIS_UNAVAILABLE",
                        Math.max(properties.getShortWaitMillis(), 1000L),
                        e
                );
            }
            if (permit.allowed()) {
                metricsService.increment(bucket.safeBucketId(), "acquired");
                if (waited) {
                    metricsService.increment(bucket.safeBucketId(), "waited");
                    metricsService.addWaitMillis(bucket.safeBucketId(), System.currentTimeMillis() - startedAt);
                }
                return;
            }
            if (properties.isObserveOnly()) {
                metricsService.increment(bucket.safeBucketId(), "observed_deferred");
                return;
            }
            waited = true;
            long elapsed = System.currentTimeMillis() - startedAt;
            long remaining = maxWaitMillis - elapsed;
            if (remaining <= 0L || permit.retryAfterMillis() > remaining) {
                metricsService.increment(bucket.safeBucketId(), "deferred");
                metricsService.addWaitMillis(bucket.safeBucketId(), elapsed);
                throw new LlmCapacityDeferredException(permit.retryAfterMillis());
            }
            sleep(Math.min(permit.retryAfterMillis(), Math.min(properties.getShortWaitMillis(), remaining)), bucket);
        }
    }

    public void registerProviderRateLimit(LlmRateLimitDescriptor descriptor, long retryAfterMillis) {
        if (!properties.isEnabled()) {
            return;
        }
        LlmRateLimitKeyFactory.SafeBucket bucket = keyFactory.create(descriptor);
        long cooldownMillis = retryAfterMillis > 0L
                ? retryAfterMillis
                : Duration.ofSeconds(properties.getDefaultCooldownSeconds()).toMillis();
        long blockedUntil = System.currentTimeMillis() + cooldownMillis;
        long ttlMillis = Math.max(cooldownMillis + 60000L,
                Duration.ofSeconds(properties.getStateTtlSeconds()).toMillis());
        try {
            stringRedisTemplate.execute(
                    COOLDOWN_SCRIPT,
                    Collections.singletonList(bucket.cooldownKey()),
                    String.valueOf(blockedUntil),
                    String.valueOf(ttlMillis)
            );
            metricsService.increment(bucket.safeBucketId(), "provider_429");
            log.warn("模型供应商触发集群冷却，provider={}, model={}, bucket={}, cooldownMs={}",
                    bucket.provider(), bucket.model(), bucket.safeBucketId(), cooldownMillis);
        } catch (RuntimeException e) {
            metricsService.increment(bucket.safeBucketId(), "redis_error");
            throw new LlmCapacityDeferredException("LLM_RATE_LIMIT_REDIS_UNAVAILABLE", cooldownMillis, e);
        }
    }

    public long defaultCooldownMillis() {
        return Duration.ofSeconds(properties.getDefaultCooldownSeconds()).toMillis();
    }

    LlmRateLimitPermit tryAcquire(
            LlmRateLimitKeyFactory.SafeBucket bucket,
            LlmRateLimitProperties.ResolvedRule rule,
            long nowMillis
    ) {
        String cooldownValue = stringRedisTemplate.opsForValue().get(bucket.cooldownKey());
        long blockedUntil = parseLong(cooldownValue);
        if (blockedUntil > nowMillis) {
            return LlmRateLimitPermit.denied(blockedUntil - nowMillis);
        }
        long intervalMillis = Math.max((long) Math.ceil(60000D / rule.effectiveRpm()), 1L);
        long ttlMillis = Math.max(
                Duration.ofSeconds(properties.getStateTtlSeconds()).toMillis(),
                intervalMillis * Math.max(rule.burst(), 1) * 2L
        );
        String result = stringRedisTemplate.execute(
                GCRA_SCRIPT,
                Collections.singletonList(bucket.rateKey()),
                String.valueOf(nowMillis),
                String.valueOf(intervalMillis),
                String.valueOf(rule.burst()),
                String.valueOf(ttlMillis)
        );
        if (result == null) {
            throw new IllegalStateException("LLM_RATE_LIMIT_EMPTY_RESULT");
        }
        String[] fields = result.split(":", 2);
        if (fields.length != 2) {
            throw new IllegalStateException("LLM_RATE_LIMIT_INVALID_RESULT");
        }
        boolean allowed = Long.parseLong(fields[0]) == 1L;
        long retryAfter = Math.max(Long.parseLong(fields[1]), 0L);
        return allowed ? LlmRateLimitPermit.granted() : LlmRateLimitPermit.denied(retryAfter);
    }

    private void sleep(long millis, LlmRateLimitKeyFactory.SafeBucket bucket) {
        try {
            Thread.sleep(Math.max(millis, 1L));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            metricsService.increment(bucket.safeBucketId(), "deferred");
            throw new LlmCapacityDeferredException("LLM_RATE_LIMIT_INTERRUPTED", Math.max(millis, 1L), e);
        }
    }

    private long parseLong(String value) {
        try {
            return value == null ? 0L : Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

}
