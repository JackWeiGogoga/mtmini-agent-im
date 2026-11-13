package com.gogoga.backend.service;

import com.gogoga.backend.client.MtImMessageClient;
import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.entity.mt.MtAccessToken;
import com.gogoga.backend.entity.mt.MtImBroadcastRecord;
import com.gogoga.backend.entity.mt.MtImBroadcastTask;
import com.gogoga.backend.enums.MtAccessTokenAuthStatusEnum;
import com.gogoga.backend.mapper.MtImBroadcastRecordMapper;
import com.gogoga.backend.mapper.MtImBroadcastSettingMapper;
import com.gogoga.backend.mapper.MtImBroadcastTaskMapper;
import com.gogoga.backend.service.async.MtImBroadcastSendTaskPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MtImBroadcastSendServiceTest {

    @Mock private MtImBroadcastTaskMapper taskMapper;
    @Mock private MtImBroadcastRecordMapper recordMapper;
    @Mock private MtImBroadcastSettingMapper settingMapper;
    @Mock private MtAccessTokenService accessTokenService;
    @Mock private MtImMessageClient messageClient;
    @Mock private ImBroadcastRateLimiter rateLimiter;
    @Mock private MtImBroadcastTimelineService timelineService;
    @Mock private ImBroadcastRateLimiter.SlotHandle slotHandle;

    private MtProperties mtProperties;
    private MtImBroadcastSendService service;

    @BeforeEach
    void setUp() {
        mtProperties = new MtProperties();
        // 用真实的鉴权失败封装，让刷新重试与标记重新授权的行为真正被覆盖，而不是被 mock 掉。
        MtMeituanAuthorizationFailureHandler authorizationFailureHandler =
                new MtMeituanAuthorizationFailureHandler(accessTokenService);
        service = new MtImBroadcastSendService(mtProperties, taskMapper, recordMapper, settingMapper,
                accessTokenService, authorizationFailureHandler, messageClient, rateLimiter, timelineService);

        when(rateLimiter.tryAcquireShopSlot(anyLong(), any(Duration.class))).thenReturn(slotHandle);
        when(rateLimiter.acquireGlobalPermit(anyLong())).thenReturn(true);
        when(accessTokenService.findByShopId(anyLong())).thenReturn(validToken());
        when(taskMapper.selectById(anyLong())).thenReturn(task());
    }

    private MtAccessToken validToken() {
        MtAccessToken token = new MtAccessToken();
        token.setAccessToken("token-abc");
        token.setAuthStatus(MtAccessTokenAuthStatusEnum.VALID.getCode());
        token.setExpiresAt(LocalDateTime.now().plusHours(1));
        return token;
    }

    private MtImBroadcastTask task() {
        MtImBroadcastTask task = new MtImBroadcastTask();
        task.setId(9L);
        task.setShopId(100L);
        task.setSettingId(1L);
        task.setStatus("SENDING");
        task.setContent("下午茶买一送一");
        task.setTargetCount(2);
        return task;
    }

    private MtImBroadcastRecord record(long id, long openUserId) {
        MtImBroadcastRecord record = new MtImBroadcastRecord();
        record.setId(id);
        record.setTaskId(9L);
        record.setShopId(100L);
        record.setConversationId(id);
        record.setOpenUserId(openUserId);
        record.setShardNo(0);
        record.setOutboundMessageId(1000L + id);
        record.setStatus("PENDING");
        return record;
    }

    private MtImMessageClient.SendResult success() {
        return new MtImMessageClient.SendResult(true, null, "{}");
    }

    private MtImMessageClient.SendResult failure(String errorCode) {
        return new MtImMessageClient.SendResult(false, errorCode, "{}");
    }

    @Test
    void sendsWithoutOrderIdAndSettlesBatchAsSuccess() {
        when(recordMapper.selectPendingByShard(eq(9L), eq(0), anyInt()))
                .thenReturn(List.of(record(1L, 8001L), record(2L, 8002L)));
        when(messageClient.sendTwoPartyMessage(anyString(), any())).thenReturn(success());
        when(recordMapper.markSent(anyLong(), any())).thenReturn(1);
        when(recordMapper.countByTask(9L, "SENT")).thenReturn(2L);
        when(recordMapper.countByTask(9L, "FAILED")).thenReturn(0L);

        service.execute(new MtImBroadcastSendTaskPayload(9L, 100L, 0));

        // 群发不依附订单，order_id 固定为 0。
        verify(messageClient, org.mockito.Mockito.times(2)).sendTwoPartyMessage(
                eq("token-abc"),
                org.mockito.ArgumentMatchers.argThat(command -> command.orderId() == 0L));
        verify(taskMapper).updateProgress(eq(9L), eq(2L), eq(0L), eq("SUCCESS"), any());
        verify(settingMapper).increaseTotalSentCount(1L, 2);
    }

    @Test
    void settlesBatchAsPartialWhenSomeSendsFail() {
        when(recordMapper.selectPendingByShard(eq(9L), eq(0), anyInt()))
                .thenReturn(List.of(record(1L, 8001L), record(2L, 8002L)));
        when(messageClient.sendTwoPartyMessage(anyString(), any()))
                .thenReturn(success())
                .thenReturn(failure("UPSTREAM_FAILED"));
        when(recordMapper.markSent(anyLong(), any())).thenReturn(1);
        when(recordMapper.countByTask(9L, "SENT")).thenReturn(1L);
        when(recordMapper.countByTask(9L, "FAILED")).thenReturn(1L);

        service.execute(new MtImBroadcastSendTaskPayload(9L, 100L, 0));

        verify(recordMapper).markFailed(eq(2L), eq("UPSTREAM_FAILED"), anyString());
        verify(taskMapper).updateProgress(eq(9L), eq(1L), eq(1L), eq("PARTIAL"), any());
        verify(settingMapper).increaseTotalSentCount(1L, 1);
    }

    @Test
    void keepsBatchSendingWhenTargetsAreStillOutstanding() {
        when(recordMapper.selectPendingByShard(eq(9L), eq(0), anyInt()))
                .thenReturn(List.of(record(1L, 8001L)));
        when(messageClient.sendTwoPartyMessage(anyString(), any())).thenReturn(success());
        when(recordMapper.markSent(anyLong(), any())).thenReturn(1);
        when(recordMapper.countByTask(9L, "SENT")).thenReturn(1L);
        when(recordMapper.countByTask(9L, "FAILED")).thenReturn(0L);

        service.execute(new MtImBroadcastSendTaskPayload(9L, 100L, 0));

        // 目标 2 人只处理了 1 人，批次不能提前收敛为终态。
        verify(taskMapper).updateProgress(eq(9L), eq(1L), eq(0L), eq("SENDING"), eq(null));
    }

    @Test
    void doesNotDoubleCountAccumulatedTotalOnRerun() {
        when(recordMapper.selectPendingByShard(eq(9L), eq(0), anyInt()))
                .thenReturn(List.of(record(1L, 8001L)));
        when(messageClient.sendTwoPartyMessage(anyString(), any())).thenReturn(success());
        // markSent 返回 0 表示该明细已是 SENT，重跑不得重复计入累计条数。
        when(recordMapper.markSent(anyLong(), any())).thenReturn(0);
        when(recordMapper.countByTask(9L, "SENT")).thenReturn(2L);
        when(recordMapper.countByTask(9L, "FAILED")).thenReturn(0L);

        service.execute(new MtImBroadcastSendTaskPayload(9L, 100L, 0));

        verify(settingMapper, never()).increaseTotalSentCount(anyLong(), anyInt());
        verify(timelineService, never()).persistSentMessage(any(), any());
    }

    @Test
    void abandonsShardImmediatelyOnceAuthFailureIsConfirmed() {
        when(recordMapper.selectPendingByShard(eq(9L), eq(0), anyInt()))
                .thenReturn(List.of(record(1L, 8001L), record(2L, 8002L)));
        when(messageClient.sendTwoPartyMessage(anyString(), any())).thenReturn(failure("OP_UNIAUTH_FAILED"));
        when(recordMapper.countByTask(9L, "SENT")).thenReturn(0L);
        when(recordMapper.countByTask(9L, "FAILED")).thenReturn(2L);

        org.junit.jupiter.api.Assertions.assertThrows(
                com.gogoga.backend.service.async.MtAsyncTaskTerminalFailureException.class,
                () -> service.execute(new MtImBroadcastSendTaskPayload(9L, 100L, 0)));

        // 首条确认鉴权失败后，第二条不再拿已失效的 token 空转。
        verify(messageClient, org.mockito.Mockito.times(1)).sendTwoPartyMessage(anyString(), any());
        verify(accessTokenService).markReauthRequired(100L);
        // 剩余明细整体置失败，批次仍能收敛为终态而不是卡在 SENDING。
        verify(recordMapper).markShardPendingFailed(9L, 0, "AUTH_REQUIRED", "AUTH_REQUIRED");
        verify(taskMapper).updateProgress(eq(9L), eq(0L), eq(2L), eq("FAILED"), any());
    }

    @Test
    void reschedulesWhenShopSlotIsBusy() {
        when(rateLimiter.tryAcquireShopSlot(anyLong(), any(Duration.class))).thenReturn(null);

        org.junit.jupiter.api.Assertions.assertThrows(
                com.gogoga.backend.service.async.MtAsyncTaskDryRunException.class,
                () -> service.execute(new MtImBroadcastSendTaskPayload(9L, 100L, 0)));

        verify(messageClient, never()).sendTwoPartyMessage(anyString(), any());
    }

    @Test
    void skipsExecutionWhenBatchAlreadySkipped() {
        MtImBroadcastTask skipped = task();
        skipped.setStatus("SKIPPED");
        when(taskMapper.selectById(anyLong())).thenReturn(skipped);

        service.execute(new MtImBroadcastSendTaskPayload(9L, 100L, 0));

        verify(messageClient, never()).sendTwoPartyMessage(anyString(), any());
    }
}
