package com.gogoga.backend.service;

import com.gogoga.backend.annotation.RequiresShopRight;
import com.gogoga.backend.client.MtImMessageClient;
import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.dto.ImAutoReplyChatMessage;
import com.gogoga.backend.dto.ImAutoReplyChatResult;
import com.gogoga.backend.dto.ResolvedSystemPromptDTO;
import com.gogoga.backend.dto.SensitiveWordHitDTO;
import com.gogoga.backend.entity.mt.MtAccessToken;
import com.gogoga.backend.entity.mt.MtImAutoReply;
import com.gogoga.backend.entity.mt.MtImMessage;
import com.gogoga.backend.enums.ImMessageDeliveryStatusEnum;
import com.gogoga.backend.enums.ImMessageSenderRoleEnum;
import com.gogoga.backend.enums.MtAccessTokenAuthStatusEnum;
import com.gogoga.backend.enums.MtImAutoReplyStatusEnum;
import com.gogoga.backend.mapper.MtImAutoReplyMapper;
import com.gogoga.backend.mapper.MtImMessageMapper;
import com.gogoga.backend.mapper.MtShopMapper;
import com.gogoga.backend.service.async.MtImAutoReplyTaskPayload;
import com.gogoga.backend.service.async.MtAsyncTaskDryRunException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MtImAutoReplyOrchestrationServiceTest {

    @Mock private MtImAutoReplyMapper autoReplyMapper;
    @Mock private MtImMessageMapper messageMapper;
    @Mock private MtShopMapper shopMapper;
    @Mock private ShopOperationSettingsService settingsService;
    @Mock private MtShopRightService rightService;
    @Mock private MtAccessTokenService tokenService;
    @Mock private MtMeituanAuthorizationFailureHandler authFailureHandler;
    @Mock private ImConversationLockService lockService;
    @Mock private ImConversationLockService.LockHandle lockHandle;
    @Mock private ImAutoReplyContextService contextService;
    @Mock private ImAutoReplyRagService ragService;
    @Mock private ImAutoReplyChatService chatService;
    @Mock private SystemPromptService promptService;
    @Mock private SensitiveWordService sensitiveWordService;
    @Mock private MtImAutoReplyStateService stateService;
    @Mock private MtImMessageClient messageClient;

    private MtProperties properties;
    private MtImAutoReplyOrchestrationService service;

    @BeforeEach
    void setUp() {
        properties = new MtProperties();
        properties.getImAutoReply().setEnabled(true);
        service = new MtImAutoReplyOrchestrationService(
                properties, autoReplyMapper, messageMapper, shopMapper, settingsService, rightService,
                tokenService, authFailureHandler, lockService, contextService, ragService, chatService,
                promptService, sensitiveWordService, stateService, messageClient);
    }

    @Test
    void supersedesOlderTaskWhenNewerUserMessageExists() {
        MtImAutoReply autoReply = autoReply();
        MtImMessage trigger = trigger();
        MtImMessage newer = new MtImMessage();
        newer.setSenderRole(ImMessageSenderRoleEnum.USER.name());
        when(autoReplyMapper.selectById(9L)).thenReturn(autoReply);
        when(lockService.tryAcquire(11L)).thenReturn(lockHandle);
        when(messageMapper.selectById(31L)).thenReturn(trigger);
        mockReadyShop();
        when(messageMapper.selectAfterMessageInConversation(11L, 31L)).thenReturn(List.of(newer));

        service.execute(payload());

        verify(stateService).markStatus(9L, MtImAutoReplyStatusEnum.SUPERSEDED, "NEWER_USER_MESSAGE");
        verify(chatService, never()).generate(anyString(), anyString(), any());
    }

    @Test
    void reschedulesWithoutRetryWhenConversationLockIsBusy() {
        when(autoReplyMapper.selectById(9L)).thenReturn(autoReply());
        when(lockService.tryAcquire(11L)).thenReturn(null);

        assertThrows(MtAsyncTaskDryRunException.class, () -> service.execute(payload()));

        verify(chatService, never()).generate(anyString(), anyString(), any());
    }

    @Test
    void rewritesSensitiveReplyAndAccumulatesActualUsage() {
        MtImAutoReply pending = autoReply();
        MtImAutoReply generated = autoReply();
        generated.setReplyMessageId(32L);
        generated.setBilledAt(LocalDateTime.now());
        when(autoReplyMapper.selectById(9L)).thenReturn(pending, pending, generated);
        when(lockService.tryAcquire(11L)).thenReturn(lockHandle);
        when(lockHandle.isValid()).thenReturn(true);
        MtImMessage trigger = trigger();
        trigger.setMsgContentPlaintext("问题");
        when(messageMapper.selectById(31L)).thenReturn(trigger);
        MtImMessage newer = new MtImMessage();
        newer.setSenderRole(ImMessageSenderRoleEnum.USER.name());
        when(messageMapper.selectAfterMessageInConversation(11L, 31L))
                .thenReturn(List.of())
                .thenReturn(List.of(newer));
        mockReadyShop();
        when(contextService.build(21L)).thenReturn(new ImAutoReplyContextService.Context(
                List.of(new ImAutoReplyChatMessage("user", "问题")), List.of("问题")));
        when(ragService.retrieve(1L, List.of("问题"))).thenReturn(ImAutoReplyRagService.RagContext.empty());
        ResolvedSystemPromptDTO prompt = new ResolvedSystemPromptDTO();
        prompt.setContent("系统");
        when(promptService.render(eq("IM_AUTO_REPLY_SYSTEM"), any(), anyString())).thenReturn(prompt);
        when(chatService.generate(eq("系统"), eq(""), any()))
                .thenReturn(new ImAutoReplyChatResult("风险答复", 10, 2));
        when(chatService.rewrite(eq("系统"), eq(""), any(), eq("风险答复")))
                .thenReturn(new ImAutoReplyChatResult("安全答复", 5, 1));
        SensitiveWordHitDTO hit = new SensitiveWordHitDTO();
        hit.setHit(true);
        SensitiveWordHitDTO safe = new SensitiveWordHitDTO();
        safe.setHit(false);
        when(sensitiveWordService.detect("风险答复")).thenReturn(hit);
        when(sensitiveWordService.detect("安全答复")).thenReturn(safe);

        service.execute(payload());

        ArgumentCaptor<ImAutoReplyChatResult> usage = ArgumentCaptor.forClass(ImAutoReplyChatResult.class);
        verify(stateService).saveGenerated(eq(9L), eq(trigger), eq("安全答复"), usage.capture(), any(), eq(1));
        assertEquals(15, usage.getValue().promptTokens());
        assertEquals(3, usage.getValue().completionTokens());
    }

    @Test
    void retriesSameGeneratedBodyAndOutboundIdWithoutRegeneration() {
        MtImAutoReply autoReply = autoReply();
        autoReply.setReplyMessageId(32L);
        autoReply.setBilledAt(LocalDateTime.now());
        autoReply.setStatus(MtImAutoReplyStatusEnum.BILLED.name());
        MtImMessage trigger = trigger();
        MtImMessage reply = new MtImMessage();
        reply.setId(32L);
        reply.setMsgContentPlaintext("固定回复");
        reply.setMessageCts(1784374000L);
        reply.setDeliveryStatus(ImMessageDeliveryStatusEnum.FAILED.name());
        when(autoReplyMapper.selectById(9L)).thenReturn(autoReply);
        when(lockService.tryAcquire(11L)).thenReturn(lockHandle);
        when(lockHandle.isValid()).thenReturn(true);
        when(messageMapper.selectById(31L)).thenReturn(trigger);
        when(messageMapper.selectById(32L)).thenReturn(reply);
        when(messageMapper.selectAfterMessageInConversation(11L, 31L)).thenReturn(List.of());
        mockReadyShop();
        when(messageClient.sendTwoPartyMessage(anyString(), any()))
                .thenReturn(new MtImMessageClient.SendResult(false, "UPSTREAM_BUSY", null))
                .thenReturn(new MtImMessageClient.SendResult(true, null, null));

        assertThrows(IllegalStateException.class, () -> service.execute(payload()));
        service.execute(payload());

        ArgumentCaptor<MtImMessageClient.TwoPartyMessageCommand> command =
                ArgumentCaptor.forClass(MtImMessageClient.TwoPartyMessageCommand.class);
        verify(messageClient, org.mockito.Mockito.times(2)).sendTwoPartyMessage(anyString(), command.capture());
        assertEquals(88L, command.getAllValues().get(0).messageId());
        assertEquals(command.getAllValues().get(0).messageId(), command.getAllValues().get(1).messageId());
        assertEquals("固定回复", command.getAllValues().get(1).plaintextContent());
        verify(chatService, never()).generate(anyString(), anyString(), any());
    }

    @Test
    void marksBusinessRecordDeadAfterTaskRetryExhaustion() {
        when(autoReplyMapper.selectById(9L)).thenReturn(autoReply());

        service.markDeadAfterRetryExhausted(payload());

        verify(stateService).markStatus(9L, MtImAutoReplyStatusEnum.DEAD, "RETRY_EXHAUSTED");
    }

    private void mockReadyShop() {
        when(settingsService.isAutoReplyEnabled(1L)).thenReturn(true);
        when(rightService.hasShopRight(eq(1L), eq(RequiresShopRight.Logical.OR), any(), any())).thenReturn(true);
        MtAccessToken token = new MtAccessToken();
        token.setAccessToken("token");
        token.setAuthStatus(MtAccessTokenAuthStatusEnum.VALID.getCode());
        token.setExpiresAt(LocalDateTime.now().plusHours(1));
        when(tokenService.findByShopId(1L)).thenReturn(token);
    }

    private MtImAutoReply autoReply() {
        MtImAutoReply row = new MtImAutoReply();
        row.setId(9L);
        row.setShopId(1L);
        row.setConversationId(11L);
        row.setSessionId(21L);
        row.setTriggerMessageId(31L);
        row.setOutboundMessageId(88L);
        row.setStatus(MtImAutoReplyStatusEnum.PENDING.name());
        return row;
    }

    private MtImMessage trigger() {
        MtImMessage message = new MtImMessage();
        message.setId(31L);
        message.setConversationId(11L);
        message.setOrderId(0L);
        message.setOpenUserId(41L);
        return message;
    }

    private MtImAutoReplyTaskPayload payload() {
        return new MtImAutoReplyTaskPayload(9L, 31L, 11L, 21L, 1L);
    }
}
