package com.gogoga.backend.task;

import com.gogoga.backend.service.MtPromotionAutoBidService;
import com.gogoga.backend.service.RedisDistributedLockService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MtPromotionAutoBidTaskTest {

    @Mock
    private MtPromotionAutoBidService mtPromotionAutoBidService;

    @Mock
    private RedisDistributedLockService redisDistributedLockService;

    @Test
    void shouldSkipTaskBodyWhenDistributedLockNotAcquired() {
        MtPromotionAutoBidTask task = new MtPromotionAutoBidTask(mtPromotionAutoBidService, redisDistributedLockService);
        when(redisDistributedLockService.tryRun(eq("MtPromotionAutoBidTask"), any(Duration.class), any(Runnable.class)))
                .thenReturn(false);

        task.executeAutoBid();

        verify(mtPromotionAutoBidService, never()).executeCurrentSlot();
    }
}
