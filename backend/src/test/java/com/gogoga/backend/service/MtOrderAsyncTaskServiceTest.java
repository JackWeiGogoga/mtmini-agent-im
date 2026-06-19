package com.gogoga.backend.service;

import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.enums.MtAsyncTaskTypeEnum;
import com.gogoga.backend.service.async.MtAsyncTaskEnqueueResult;
import com.gogoga.backend.service.async.MtOrderNotifyTaskPayload;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MtOrderAsyncTaskServiceTest {

    @Mock
    private MtAsyncTaskService mtAsyncTaskService;

    @Mock
    private MtOrderService mtOrderService;

    @Spy
    private MtProperties mtProperties = new MtProperties();

    @InjectMocks
    private MtOrderAsyncTaskService mtOrderAsyncTaskService;

    @Test
    void shouldEnqueueOrderNotifyTaskWhenAsyncEnabled() {
        mtProperties.getAsyncTask().setEnabled(true);
        when(mtAsyncTaskService.enqueueTask(eq(MtAsyncTaskTypeEnum.ORDER_NOTIFY), any(), any(), any(), any(), eq(3)))
                .thenReturn(new MtAsyncTaskEnqueueResult(1L, true));

        mtOrderAsyncTaskService.acceptOrderNotify(Map.of(
                "msgId", new String[]{"msg-001"},
                "ePoiId", new String[]{"shop-001"},
                "message", new String[]{"{\"order_id\":\"order-001\",\"status\":\"2\"}"}
        ));

        ArgumentCaptor<MtOrderNotifyTaskPayload> payloadCaptor = ArgumentCaptor.forClass(MtOrderNotifyTaskPayload.class);
        verify(mtAsyncTaskService).enqueueTask(
                eq(MtAsyncTaskTypeEnum.ORDER_NOTIFY),
                eq("msg-001"),
                eq("order-001"),
                payloadCaptor.capture(),
                any(),
                eq(3)
        );
        assertEquals("order-001", payloadCaptor.getValue().getOrderId());
        verify(mtOrderService, never()).handleOrderNotify(org.mockito.ArgumentMatchers.<Map<String, String[]>>any());
    }

    @Test
    void shouldFallbackToSyncWhenAsyncDisabled() {
        mtProperties.getAsyncTask().setEnabled(false);
        Map<String, String[]> parameterMap = Map.of(
                "ePoiId", new String[]{"shop-001"},
                "message", new String[]{"{\"order_id\":\"order-001\",\"status\":\"2\"}"}
        );

        mtOrderAsyncTaskService.acceptOrderNotify(parameterMap);

        verify(mtOrderService).handleOrderNotify(parameterMap);
        verify(mtAsyncTaskService, never()).enqueueTask(any(), any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void shouldRetryAsyncExecutionWhenOrderNotifyHitsDeadlock() {
        MtOrderNotifyTaskPayload payload = new MtOrderNotifyTaskPayload();
        payload.setEPoiId("shop-001");
        payload.setOrderId("order-001");
        payload.setMessage("{\"order_id\":\"order-001\",\"status\":\"2\"}");

        doThrow(new CannotAcquireLockException("deadlock"))
                .doNothing()
                .when(mtOrderService)
                .handleOrderNotify(org.mockito.ArgumentMatchers.<Map<String, String[]>>any());

        mtOrderAsyncTaskService.executeOrderNotifyTask(payload);

        verify(mtOrderService, times(2)).handleOrderNotify(org.mockito.ArgumentMatchers.<Map<String, String[]>>any());
    }
}
