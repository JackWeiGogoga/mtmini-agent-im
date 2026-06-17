package com.gogoga.backend.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisDistributedLockServiceTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Test
    void shouldRunAndReleaseLockWhenLockAcquired() {
        RedisDistributedLockService service = new RedisDistributedLockService(stringRedisTemplate);
        AtomicBoolean executed = new AtomicBoolean(false);

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(stringRedisTemplate.execute(anyRedisScript(), anyList(), anyString())).thenReturn(1L);

        boolean result = service.tryRun("demo", Duration.ofMinutes(1), () -> executed.set(true));

        assertTrue(result);
        assertTrue(executed.get());
        verify(stringRedisTemplate).execute(anyRedisScript(), anyList(), anyString());
    }

    @Test
    void shouldSkipWhenLockNotAcquired() {
        RedisDistributedLockService service = new RedisDistributedLockService(stringRedisTemplate);
        AtomicBoolean executed = new AtomicBoolean(false);

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);

        boolean result = service.tryRun("demo", Duration.ofMinutes(1), () -> executed.set(true));

        assertFalse(result);
        assertFalse(executed.get());
        verify(stringRedisTemplate, never()).execute(anyRedisScript(), anyList(), anyString());
    }

    @SuppressWarnings("unchecked")
    private RedisScript<Long> anyRedisScript() {
        return (RedisScript<Long>) any(RedisScript.class);
    }
}
