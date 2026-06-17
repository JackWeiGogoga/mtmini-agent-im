package com.gogoga.backend.task;

import com.gogoga.backend.service.MtPromotionRightExpiryShutdownService;
import com.gogoga.backend.service.RedisDistributedLockService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MtPromotionRightExpiryShutdownTaskTest {

    @Mock
    private MtPromotionRightExpiryShutdownService service;

    @Mock
    private RedisDistributedLockService redisDistributedLockService;

    @Test
    void shouldRunShutdownInsideDistributedLock() {
        MtPromotionRightExpiryShutdownTask task = new MtPromotionRightExpiryShutdownTask(service, redisDistributedLockService);
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(2).run();
            return true;
        }).when(redisDistributedLockService).tryRun(eq("MtPromotionRightExpiryShutdownTask"), any(Duration.class), any(Runnable.class));

        task.processExpiredPromotionRights();

        verify(service).processExpiredPromotionRights(anyInt());
    }

    @Test
    void shouldSkipTaskBodyWhenDistributedLockNotAcquired() {
        MtPromotionRightExpiryShutdownTask task = new MtPromotionRightExpiryShutdownTask(service, redisDistributedLockService);
        when(redisDistributedLockService.tryRun(eq("MtPromotionRightExpiryShutdownTask"), any(Duration.class), any(Runnable.class)))
                .thenReturn(false);

        task.processExpiredPromotionRights();

        verify(service, never()).processExpiredPromotionRights(anyInt());
    }
}
