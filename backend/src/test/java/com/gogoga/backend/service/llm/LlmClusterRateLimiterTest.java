package com.gogoga.backend.service.llm;

import com.gogoga.backend.config.LlmRateLimitProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LlmClusterRateLimiterTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private LlmRateLimitProperties properties;
    private LlmRateLimitKeyFactory keyFactory;
    private LlmRateLimitMetricsService metricsService;

    @BeforeEach
    void setUp() {
        properties = new LlmRateLimitProperties();
        keyFactory = new LlmRateLimitKeyFactory();
        metricsService = new LlmRateLimitMetricsService();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void shouldShareFiftyRpmBurstOneBucketAcrossLimiterInstances() {
        LlmRateLimitKeyFactory.SafeBucket bucket = bucket();
        when(valueOperations.get(bucket.cooldownKey())).thenReturn(null);
        doReturn("1:0", "0:1200")
                .when(redisTemplate)
                .execute(org.mockito.ArgumentMatchers.<RedisScript<String>>any(), anyList(), any(Object[].class));
        LlmRateLimitProperties.ResolvedRule rule = new LlmRateLimitProperties.ResolvedRule(50, 1);
        LlmClusterRateLimiter nodeOne = limiter();
        LlmClusterRateLimiter nodeTwo = limiter();

        LlmRateLimitPermit first = nodeOne.tryAcquire(bucket, rule, 10000L);
        LlmRateLimitPermit second = nodeTwo.tryAcquire(bucket, rule, 10000L);

        assertTrue(first.allowed());
        assertFalse(second.allowed());
        assertEquals(1200L, second.retryAfterMillis());
    }

    @Test
    void shouldHonorSharedCooldownBeforeGcra() {
        long now = 10000L;
        LlmRateLimitKeyFactory.SafeBucket bucket = bucket();
        when(valueOperations.get(bucket.cooldownKey())).thenReturn(String.valueOf(now + 5000L));

        LlmRateLimitPermit permit = limiter().tryAcquire(
                bucket,
                new LlmRateLimitProperties.ResolvedRule(50, 1),
                now
        );

        assertFalse(permit.allowed());
        assertEquals(5000L, permit.retryAfterMillis());
        verify(redisTemplate, never())
                .execute(org.mockito.ArgumentMatchers.<RedisScript<String>>any(), anyList(), any(Object[].class));
    }

    @Test
    void shouldFailClosedWhenRedisIsUnavailable() {
        properties.setMaxWaitMillis(1L);
        LlmRateLimitKeyFactory.SafeBucket bucket = bucket();
        when(valueOperations.get(bucket.cooldownKey())).thenReturn(null);
        doThrow(new IllegalStateException("redis down"))
                .when(redisTemplate)
                .execute(org.mockito.ArgumentMatchers.<RedisScript<String>>any(), anyList(), any(Object[].class));

        assertThrows(LlmCapacityDeferredException.class,
                () -> limiter().acquire(descriptor()));
        assertEquals(1L, metricsService.snapshot().values().stream().mapToLong(Long::longValue).sum());
    }

    @Test
    void observeOnlyShouldRecordWouldDeferWithoutBlocking() {
        properties.setObserveOnly(true);
        LlmRateLimitKeyFactory.SafeBucket bucket = bucket();
        when(valueOperations.get(bucket.cooldownKey())).thenReturn(null);
        doReturn("0:1200")
                .when(redisTemplate)
                .execute(org.mockito.ArgumentMatchers.<RedisScript<String>>any(), anyList(), any(Object[].class));

        limiter().acquire(descriptor());

        assertEquals(1L, metricsService.snapshot().get(bucket.safeBucketId() + ".observed_deferred"));
    }

    private LlmClusterRateLimiter limiter() {
        return new LlmClusterRateLimiter(redisTemplate, properties, keyFactory, metricsService);
    }

    private LlmRateLimitDescriptor descriptor() {
        return LlmRateLimitDescriptor.background(
                "TOKENHUB", "secret-key", "deepseek-v4-flash", "reply");
    }

    private LlmRateLimitKeyFactory.SafeBucket bucket() {
        return keyFactory.create(descriptor());
    }
}
