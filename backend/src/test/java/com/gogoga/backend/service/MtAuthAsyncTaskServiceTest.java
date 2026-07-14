package com.gogoga.backend.service;

import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.entity.mt.MtAccessToken;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.enums.MtAsyncTaskTypeEnum;
import com.gogoga.backend.service.async.MtAsyncTaskEnqueueResult;
import com.gogoga.backend.service.async.MtAuthPostProcessTaskPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MtAuthAsyncTaskServiceTest {

    @Mock
    private MtAsyncTaskService mtAsyncTaskService;

    @Mock
    private MtProperties mtProperties;

    @Mock
    private MtAccessTokenService mtAccessTokenService;

    @Mock
    private MtShopService mtShopService;

    @Mock
    private MtShopRightService mtShopRightService;

    @Mock
    private MtShopPoiIdBackfillService mtShopPoiIdBackfillService;

    @Mock
    private MtOrderPushControlService mtOrderPushControlService;

    @InjectMocks
    private MtAuthAsyncTaskService mtAuthAsyncTaskService;

    @BeforeEach
    void setUp() {
        lenient().when(mtProperties.getAsyncTask()).thenReturn(new MtProperties.AsyncTask());
        lenient().when(mtAsyncTaskService.enqueueTask(any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(new MtAsyncTaskEnqueueResult(1L, true));
    }

    @Test
    void shouldEnqueuePoiIdBackfillAsPostAuthorizationTask() {
        mtAuthAsyncTaskService.enqueuePostAuthorizationTasks(1L, "access-token");

        ArgumentCaptor<MtAsyncTaskTypeEnum> typeCaptor = ArgumentCaptor.forClass(MtAsyncTaskTypeEnum.class);
        verify(mtAsyncTaskService, times(4)).enqueueTask(
                typeCaptor.capture(),
                any(),
                eq("1"),
                any(MtAuthPostProcessTaskPayload.class),
                any(),
                anyInt()
        );
        assertEquals(List.of(
                MtAsyncTaskTypeEnum.AUTH_BLOCK_ORDER_PUSH,
                MtAsyncTaskTypeEnum.AUTH_BACKFILL_POI_ID,
                MtAsyncTaskTypeEnum.AUTH_SYNC_RIGHTS,
                MtAsyncTaskTypeEnum.AUTH_COMMENT_BACKFILL
        ), typeCaptor.getAllValues());
    }

    @Test
    void shouldExecutePoiIdBackfillWithCurrentAccessToken() {
        MtShop shop = new MtShop();
        shop.setId(1L);
        MtAccessToken token = new MtAccessToken();
        token.setShopId(1L);
        token.setAccessToken("current-token");
        MtAuthPostProcessTaskPayload payload = new MtAuthPostProcessTaskPayload();
        payload.setShopId(1L);

        when(mtShopService.findById(1L)).thenReturn(shop);
        when(mtAccessTokenService.findByShopId(1L)).thenReturn(token);

        mtAuthAsyncTaskService.executeAuthBackfillPoiIdTask(payload);

        verify(mtShopPoiIdBackfillService).tryBackfillAfterAuthorization(shop, "current-token");
    }
}
