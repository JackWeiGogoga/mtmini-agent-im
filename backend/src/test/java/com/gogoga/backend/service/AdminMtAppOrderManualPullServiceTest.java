package com.gogoga.backend.service;

import com.gogoga.backend.exception.BusinessException;
import com.gogoga.backend.param.AdminMtAppOrderManualPullParam;
import com.gogoga.backend.vo.AdminMtAppOrderManualPullVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminMtAppOrderManualPullServiceTest {

    @Mock
    private MtAppOrderService mtAppOrderService;

    @Mock
    private RedisDistributedLockService redisDistributedLockService;

    @InjectMocks
    private AdminMtAppOrderManualPullService service;

    @Test
    void shouldPullOrdersWithSharedScheduledTaskLock() {
        AdminMtAppOrderManualPullParam param = buildParam(
                LocalDateTime.of(2026, 7, 17, 17, 30),
                LocalDateTime.of(2026, 7, 17, 18, 30)
        );
        MtAppOrderService.PullResult result = new MtAppOrderService.PullResult(
                154, 1L, 2L, 1, 9, 9, 0, 0, 9
        );
        when(mtAppOrderService.pullOrders(1_784_280_600L, 1_784_284_200L)).thenReturn(result);
        when(redisDistributedLockService.tryRun(
                eq(MtAppOrderService.PULL_LOCK_NAME),
                eq(MtAppOrderService.PULL_LOCK_LEASE_DURATION),
                any(Runnable.class)
        )).thenAnswer(invocation -> {
            invocation.getArgument(2, Runnable.class).run();
            return true;
        });

        AdminMtAppOrderManualPullVO response = service.pull(param);

        assertEquals(154, response.getAppId());
        assertEquals(9, response.getTotalFetched());
        assertEquals(9, response.getCreatedCount());
        assertEquals(9, response.getAppliedRightsCount());
        verify(mtAppOrderService).pullOrders(1_784_280_600L, 1_784_284_200L);
    }

    @Test
    void shouldRejectWhenScheduledPullOwnsLock() {
        AdminMtAppOrderManualPullParam param = buildParam(
                LocalDateTime.now().minusHours(2),
                LocalDateTime.now().minusHours(1)
        );
        when(redisDistributedLockService.tryRun(any(), any(), any())).thenReturn(false);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.pull(param));

        assertEquals(409, exception.getCode());
        verify(mtAppOrderService, never()).pullOrders(anyLong(), anyLong());
    }

    @Test
    void shouldRejectRangeLongerThanSevenDays() {
        AdminMtAppOrderManualPullParam param = buildParam(
                LocalDateTime.now().minusDays(8),
                LocalDateTime.now().minusHours(1)
        );

        BusinessException exception = assertThrows(BusinessException.class, () -> service.pull(param));

        assertEquals(400, exception.getCode());
        assertEquals("单次拉取时间范围不能超过 7 天", exception.getMessage());
        verify(redisDistributedLockService, never()).tryRun(any(), any(), any());
    }

    private AdminMtAppOrderManualPullParam buildParam(LocalDateTime startTime, LocalDateTime endTime) {
        AdminMtAppOrderManualPullParam param = new AdminMtAppOrderManualPullParam();
        param.setStartTime(startTime);
        param.setEndTime(endTime);
        return param;
    }
}
