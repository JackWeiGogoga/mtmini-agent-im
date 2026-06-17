package com.gogoga.backend.service;

import com.gogoga.backend.client.MtUpdateBlockMsgListClient;
import com.gogoga.backend.entity.mt.MtAccessToken;
import com.gogoga.backend.mapper.MtAccessTokenMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MtOrderPushControlServiceTest {

    @Mock
    private MtAccessTokenMapper mtAccessTokenMapper;

    @Mock
    private MtUpdateBlockMsgListClient mtUpdateBlockMsgListClient;

    @InjectMocks
    private MtOrderPushControlService mtOrderPushControlService;

    @Test
    void shouldUnblockOrderPushAndRecordSuccess() {
        MtAccessToken token = buildToken(false);
        token.setOrderPushBlocked(null);
        when(mtUpdateBlockMsgListClient.unblockOrderPushResult("access-token", "op-001"))
                .thenReturn(MtUpdateBlockMsgListClient.UpdateBlockMsgListResult.success(List.of(), null));

        MtOrderPushControlService.OrderPushSyncResult result =
                mtOrderPushControlService.unblockOrderPush(token, "MANUAL_SWITCH_ENABLE");

        assertTrue(result.success());
        verify(mtAccessTokenMapper).updateOrderPushSyncState(eq(1L), eq(false), any(LocalDateTime.class), eq(null));
    }

    @Test
    void shouldBlockOrderPushAndRecordSuccess() {
        MtAccessToken token = buildToken(false);
        when(mtUpdateBlockMsgListClient.blockOrderPushResult("access-token", "op-001"))
                .thenReturn(MtUpdateBlockMsgListClient.UpdateBlockMsgListResult.success(List.of(16102), null));

        MtOrderPushControlService.OrderPushSyncResult result =
                mtOrderPushControlService.blockOrderPush(token, "MANUAL_SWITCH_DISABLE");

        assertTrue(result.success());
        verify(mtAccessTokenMapper).updateOrderPushSyncState(eq(1L), eq(true), any(LocalDateTime.class), eq(null));
    }

    @Test
    void shouldKeepExistingBlockedStateWhenRemoteSyncFails() {
        MtAccessToken token = buildToken(false);
        when(mtUpdateBlockMsgListClient.blockOrderPushResult("access-token", "op-001"))
                .thenReturn(MtUpdateBlockMsgListClient.UpdateBlockMsgListResult.failure(
                        List.of(16102), "FAIL", "remote failed", "trace-1", null));

        MtOrderPushControlService.OrderPushSyncResult result =
                mtOrderPushControlService.blockOrderPush(token, "INITIAL_AUTH");

        assertFalse(result.success());
        verify(mtAccessTokenMapper).updateOrderPushSyncState(eq(1L), eq(false), any(LocalDateTime.class), eq("remote failed"));
    }

    @Test
    void shouldSkipRemoteCallWhenTokenRecordMissing() {
        MtOrderPushControlService.OrderPushSyncResult result =
                mtOrderPushControlService.unblockOrderPush(null, "MANUAL_SWITCH_ENABLE");

        assertFalse(result.success());
        verify(mtUpdateBlockMsgListClient, never()).unblockOrderPushResult(any(), any());
        verify(mtUpdateBlockMsgListClient, never()).blockOrderPushResult(any(), any());
    }

    private MtAccessToken buildToken(Boolean orderPushBlocked) {
        MtAccessToken token = new MtAccessToken();
        token.setId(1L);
        token.setShopId(101L);
        token.setAccessToken("access-token");
        token.setOpBizCode("op-001");
        token.setOrderPushBlocked(orderPushBlocked);
        return token;
    }
}
