package com.gogoga.backend.service;

import com.gogoga.backend.client.MtImMessageClient;
import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.constant.ImAutoReplyPromptConstant;
import com.gogoga.backend.dto.ImAutoReplyChatMessage;
import com.gogoga.backend.dto.ImAutoReplyChatResult;
import com.gogoga.backend.dto.ResolvedSystemPromptDTO;
import com.gogoga.backend.dto.SensitiveWordHitDTO;
import com.gogoga.backend.entity.mt.MtAccessToken;
import com.gogoga.backend.entity.mt.MtImAutoReply;
import com.gogoga.backend.entity.mt.MtImMessage;
import com.gogoga.backend.enums.ImMessageDeliveryStatusEnum;
import com.gogoga.backend.enums.ImMessageSenderRoleEnum;
import com.gogoga.backend.enums.ImMessageTypeEnum;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
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
    @Mock private ImConversationLockService lockService;
    @Mock private ImConversationLockService.LockHandle lockHandle;
    @Mock private ImAutoReplyContextService contextService;
    @Mock private ImMessageClassificationService messageClassificationService;
    @Mock private ImAutoReplyRagService ragService;
    @Mock private ImAutoReplyChatService chatService;
    @Mock private SystemPromptService promptService;
    @Mock private SensitiveWordService sensitiveWordService;
    @Mock private ImReplyContactSuffixService contactSuffixService;
    @Mock private MtImAutoReplyStateService stateService;
    @Mock private MtImMessageClient messageClient;

    private MtProperties properties;
    private MtImAutoReplyOrchestrationService service;

    @BeforeEach
    void setUp() {
        properties = new MtProperties();
        properties.getImAutoReply().setEnabled(true);
        // 用真实的鉴权失败封装，让刷新重试与标记重新授权的行为真正被覆盖，而不是被 mock 掉。
        MtMeituanAuthorizationFailureHandler authFailureHandler =
                new MtMeituanAuthorizationFailureHandler(tokenService);
        service = new MtImAutoReplyOrchestrationService(
                properties, autoReplyMapper, messageMapper, shopMapper, settingsService, rightService,
                tokenService, authFailureHandler, lockService, contextService, messageClassificationService,
                ragService, new ShopBasicInfoPromptService(), chatService,
                promptService, sensitiveWordService, contactSuffixService, stateService, messageClient);
        // 默认无联系方式可拼：正文原样透传，个别用例再覆盖成有联系方式的门店。
        lenient().when(contactSuffixService.appendPhone(anyString(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void supersedesOlderTaskWhenNewerUserMessageExists() {
        MtImAutoReply autoReply = autoReply();
        MtImMessage trigger = trigger();
        MtImMessage newer = new MtImMessage();
        newer.setSenderRole(ImMessageSenderRoleEnum.USER.name());
        newer.setMsgType(ImMessageTypeEnum.TEXT.getCode());
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
    void doesNotSupersedeWhenNewerUserMessageIsOnlyACard() {
        MtImAutoReply autoReply = autoReply();
        autoReply.setReplyMessageId(32L);
        autoReply.setBilledAt(LocalDateTime.now());
        autoReply.setStatus(MtImAutoReplyStatusEnum.BILLED.name());
        MtImMessage reply = new MtImMessage();
        reply.setId(32L);
        reply.setMsgContentPlaintext("固定回复");
        reply.setMessageCts(1784374000L);
        reply.setDeliveryStatus(ImMessageDeliveryStatusEnum.FAILED.name());
        MtImMessage card = new MtImMessage();
        card.setSenderRole(ImMessageSenderRoleEnum.USER.name());
        card.setMsgType(ImMessageTypeEnum.CARD.getCode());
        when(autoReplyMapper.selectById(9L)).thenReturn(autoReply);
        when(lockService.tryAcquire(11L)).thenReturn(lockHandle);
        when(lockHandle.isValid()).thenReturn(true);
        when(messageMapper.selectById(31L)).thenReturn(trigger());
        when(messageMapper.selectById(32L)).thenReturn(reply);
        when(messageMapper.selectAfterMessageInConversation(11L, 31L)).thenReturn(List.of(card));
        mockReadyShop();
        when(messageClient.sendTwoPartyMessage(anyString(), any()))
                .thenReturn(new MtImMessageClient.SendResult(true, null, null));

        service.execute(payload());

        // CARD 自身不会触发回复，若允许它顶掉本次回复，用户提问将永远无人应答
        verify(stateService, never())
                .markStatus(9L, MtImAutoReplyStatusEnum.SUPERSEDED, "NEWER_USER_MESSAGE");
        verify(messageClient).sendTwoPartyMessage(anyString(), any());
    }

    @Test
    void doesNotTreatMerchantCardAsManualReply() {
        MtImAutoReply autoReply = autoReply();
        autoReply.setReplyMessageId(32L);
        autoReply.setBilledAt(LocalDateTime.now());
        autoReply.setStatus(MtImAutoReplyStatusEnum.BILLED.name());
        MtImMessage reply = new MtImMessage();
        reply.setId(32L);
        reply.setMsgContentPlaintext("固定回复");
        reply.setMessageCts(1784374000L);
        reply.setDeliveryStatus(ImMessageDeliveryStatusEnum.FAILED.name());
        MtImMessage merchantCard = new MtImMessage();
        merchantCard.setSenderRole(ImMessageSenderRoleEnum.MERCHANT.name());
        merchantCard.setMsgType(ImMessageTypeEnum.CARD.getCode());
        when(autoReplyMapper.selectById(9L)).thenReturn(autoReply);
        when(lockService.tryAcquire(11L)).thenReturn(lockHandle);
        when(lockHandle.isValid()).thenReturn(true);
        when(messageMapper.selectById(31L)).thenReturn(trigger());
        when(messageMapper.selectById(32L)).thenReturn(reply);
        when(messageMapper.selectAfterMessageInConversation(11L, 31L)).thenReturn(List.of(merchantCard));
        mockReadyShop();
        when(messageClient.sendTwoPartyMessage(anyString(), any()))
                .thenReturn(new MtImMessageClient.SendResult(true, null, null));

        service.execute(payload());

        verify(stateService, never())
                .markStatus(9L, MtImAutoReplyStatusEnum.SKIPPED, "MANUAL_REPLY_AFTER_TRIGGER");
        verify(messageClient).sendTwoPartyMessage(anyString(), any());
    }

    @Test
    void stillTreatsMerchantImageAsManualReply() {
        MtImAutoReply autoReply = autoReply();
        MtImMessage merchantImage = new MtImMessage();
        merchantImage.setSenderRole(ImMessageSenderRoleEnum.MERCHANT.name());
        merchantImage.setMsgType(ImMessageTypeEnum.IMAGE.getCode());
        when(autoReplyMapper.selectById(9L)).thenReturn(autoReply);
        when(lockService.tryAcquire(11L)).thenReturn(lockHandle);
        when(messageMapper.selectById(31L)).thenReturn(trigger());
        mockReadyShop();
        when(messageMapper.selectAfterMessageInConversation(11L, 31L)).thenReturn(List.of(merchantImage));

        service.execute(payload());

        // 商家发图片是真实的人工介入，放行会让顾客同时收到人工回复和自动回复
        verify(stateService).markStatus(9L, MtImAutoReplyStatusEnum.SKIPPED, "MANUAL_REPLY_AFTER_TRIGGER");
        verify(chatService, never()).generate(anyString(), anyString(), any());
    }

    @Test
    void holdsReplyWhileMerchantSilenceWindowIsOpen() {
        MtImMessage merchant = new MtImMessage();
        merchant.setSenderRole(ImMessageSenderRoleEnum.MERCHANT.name());
        merchant.setMsgType(ImMessageTypeEnum.TEXT.getCode());
        merchant.setEventTime(LocalDateTime.now().minusSeconds(10));
        when(autoReplyMapper.selectById(9L)).thenReturn(autoReply());
        when(lockService.tryAcquire(11L)).thenReturn(lockHandle);
        when(messageMapper.selectById(31L)).thenReturn(trigger());
        mockReadyShop();
        when(messageMapper.selectAfterMessageInConversation(11L, 31L)).thenReturn(List.of());
        when(messageMapper.selectLatestMerchantMessageInConversation(11L)).thenReturn(merchant);

        // 商家 10 秒前说过话，40 秒窗口未到期，任务应无损重排而非放弃
        MtAsyncTaskDryRunException reschedule =
                assertThrows(MtAsyncTaskDryRunException.class, () -> service.execute(payload()));

        assertEquals("MERCHANT_HOLD_WINDOW", reschedule.getMessage());
        assertEquals(30L, reschedule.getRescheduleSeconds());
        verify(chatService, never()).generate(anyString(), anyString(), any());
        verify(stateService, never()).markStatus(eq(9L), any(), anyString());
    }

    @Test
    void generatesOnceMerchantSilenceWindowHasExpired() {
        MtImMessage merchant = new MtImMessage();
        merchant.setSenderRole(ImMessageSenderRoleEnum.MERCHANT.name());
        merchant.setMsgType(ImMessageTypeEnum.TEXT.getCode());
        merchant.setEventTime(LocalDateTime.now().minusSeconds(41));
        MtImAutoReply generated = autoReply();
        generated.setReplyMessageId(32L);
        generated.setBilledAt(LocalDateTime.now());
        generated.setSendNotBefore(LocalDateTime.now().minusSeconds(1));
        MtImMessage reply = new MtImMessage();
        reply.setId(32L);
        reply.setMsgContentPlaintext("固定回复");
        reply.setMessageCts(1784374000L);
        reply.setDeliveryStatus(ImMessageDeliveryStatusEnum.FAILED.name());
        when(autoReplyMapper.selectById(9L)).thenReturn(generated);
        when(lockService.tryAcquire(11L)).thenReturn(lockHandle);
        when(lockHandle.isValid()).thenReturn(true);
        when(messageMapper.selectById(31L)).thenReturn(trigger());
        when(messageMapper.selectById(32L)).thenReturn(reply);
        mockReadyShop();
        when(messageMapper.selectAfterMessageInConversation(11L, 31L)).thenReturn(List.of());
        when(messageMapper.selectLatestMerchantMessageInConversation(11L)).thenReturn(merchant);
        when(messageClient.sendTwoPartyMessage(anyString(), any()))
                .thenReturn(new MtImMessageClient.SendResult(true, null, null));

        service.execute(payload());

        verify(messageClient).sendTwoPartyMessage(anyString(), any());
    }

    @Test
    void holdsSendUntilRandomDelayElapsesAndDoesNotRerollAfterReschedule() {
        MtImAutoReply generated = autoReply();
        generated.setReplyMessageId(32L);
        generated.setBilledAt(LocalDateTime.now());
        generated.setSendNotBefore(LocalDateTime.now().plusSeconds(5));
        MtImMessage reply = new MtImMessage();
        reply.setId(32L);
        reply.setMsgContentPlaintext("固定回复");
        reply.setDeliveryStatus(ImMessageDeliveryStatusEnum.FAILED.name());
        when(autoReplyMapper.selectById(9L)).thenReturn(generated);
        when(lockService.tryAcquire(11L)).thenReturn(lockHandle);
        when(lockHandle.isValid()).thenReturn(true);
        when(messageMapper.selectById(31L)).thenReturn(trigger());
        when(messageMapper.selectById(32L)).thenReturn(reply);
        mockReadyShop();
        when(messageMapper.selectAfterMessageInConversation(11L, 31L)).thenReturn(List.of());

        MtAsyncTaskDryRunException reschedule =
                assertThrows(MtAsyncTaskDryRunException.class, () -> service.execute(payload()));

        assertEquals("SEND_DELAY", reschedule.getMessage());
        verify(messageClient, never()).sendTwoPartyMessage(anyString(), any());
        // sendNotBefore 已落库，重排后不得重新随机
        verify(stateService, never()).assignSendNotBefore(anyLong(), anyLong());
    }

    @Test
    void assignsSendDelayWithinConfiguredBoundsOnFirstAttempt() {
        MtImAutoReply generated = autoReply();
        generated.setReplyMessageId(32L);
        generated.setBilledAt(LocalDateTime.now());
        MtImMessage reply = new MtImMessage();
        reply.setId(32L);
        reply.setMsgContentPlaintext("固定回复");
        reply.setDeliveryStatus(ImMessageDeliveryStatusEnum.FAILED.name());
        when(autoReplyMapper.selectById(9L)).thenReturn(generated);
        when(lockService.tryAcquire(11L)).thenReturn(lockHandle);
        when(lockHandle.isValid()).thenReturn(true);
        when(messageMapper.selectById(31L)).thenReturn(trigger());
        when(messageMapper.selectById(32L)).thenReturn(reply);
        mockReadyShop();
        when(messageMapper.selectAfterMessageInConversation(11L, 31L)).thenReturn(List.of());
        when(stateService.assignSendNotBefore(eq(9L), anyLong()))
                .thenAnswer(call -> LocalDateTime.now().plusSeconds(call.getArgument(1, Long.class)));

        assertThrows(MtAsyncTaskDryRunException.class, () -> service.execute(payload()));

        ArgumentCaptor<Long> delay = ArgumentCaptor.forClass(Long.class);
        verify(stateService).assignSendNotBefore(eq(9L), delay.capture());
        assertTrue(delay.getValue() >= 3L && delay.getValue() <= 10L,
                "随机延迟应落在配置的 3-10 秒区间，实际=" + delay.getValue());
    }

    @Test
    void repliesFixedFallbackWithoutModelWhenTriggerIsNotText() {
        MtImAutoReply pending = autoReply();
        MtImAutoReply generated = autoReply();
        generated.setReplyMessageId(32L);
        MtImAutoReply billed = autoReply();
        billed.setReplyMessageId(32L);
        billed.setBilledAt(LocalDateTime.now());
        billed.setSendNotBefore(LocalDateTime.now().minusSeconds(1));
        when(autoReplyMapper.selectById(9L)).thenReturn(pending, pending, generated);
        when(stateService.markBilledFree(9L)).thenReturn(billed);
        when(lockService.tryAcquire(11L)).thenReturn(lockHandle);
        when(lockHandle.isValid()).thenReturn(true);
        MtImMessage trigger = trigger(ImMessageTypeEnum.IMAGE);
        MtImMessage reply = new MtImMessage();
        reply.setId(32L);
        reply.setMsgContentPlaintext("商家正在忙\n可以联系门店 13800000000");
        reply.setDeliveryStatus(ImMessageDeliveryStatusEnum.FAILED.name());
        when(messageMapper.selectById(31L)).thenReturn(trigger);
        when(messageMapper.selectById(32L)).thenReturn(reply);
        mockReadyShop();
        when(messageMapper.selectAfterMessageInConversation(11L, 31L)).thenReturn(List.of());
        SensitiveWordHitDTO safe = new SensitiveWordHitDTO();
        safe.setHit(false);
        when(sensitiveWordService.detect("商家正在忙")).thenReturn(safe);
        when(contactSuffixService.append(1L, "商家正在忙"))
                .thenReturn("商家正在忙\n可以联系门店 13800000000");
        when(messageClient.sendTwoPartyMessage(anyString(), any()))
                .thenReturn(new MtImMessageClient.SendResult(true, null, null));

        service.execute(payload());

        // 不解析图片、不召回知识、不分类、不生成：整条链路都不碰大模型
        verify(chatService, never()).generate(anyString(), anyString(), any());
        verify(ragService, never()).retrieve(anyLong(), any(), anyString());
        verify(messageClassificationService, never()).classify(any());
        // 兜底话术仍要拼联系方式，并按零 token 过账后正常发送
        verify(stateService).saveGenerated(eq(9L), eq(trigger), eq("商家正在忙\n可以联系门店 13800000000"),
                any(), eq(null), eq(0), eq("GENERAL"));
        verify(stateService).markBilledFree(9L);
        verify(stateService, never()).bill(anyLong());
        verify(messageClient).sendTwoPartyMessage(anyString(), any());
    }

    @Test
    void skipsNonTextFallbackWhenMerchantAlreadyRepliedAfterTrigger() {
        MtImAutoReply pending = autoReply();
        when(autoReplyMapper.selectById(9L)).thenReturn(pending);
        when(lockService.tryAcquire(11L)).thenReturn(lockHandle);
        when(messageMapper.selectById(31L)).thenReturn(trigger(ImMessageTypeEnum.IMAGE));
        mockReadyShop();
        MtImMessage merchant = new MtImMessage();
        merchant.setSenderRole(ImMessageSenderRoleEnum.MERCHANT.name());
        merchant.setMsgType(ImMessageTypeEnum.TEXT.getCode());
        when(messageMapper.selectAfterMessageInConversation(11L, 31L)).thenReturn(List.of(merchant));

        service.execute(payload());

        verify(stateService).markStatus(9L, MtImAutoReplyStatusEnum.SKIPPED, "MANUAL_REPLY_AFTER_TRIGGER");
        verify(messageClient, never()).sendTwoPartyMessage(anyString(), any());
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
        newer.setMsgType(ImMessageTypeEnum.TEXT.getCode());
        when(messageMapper.selectAfterMessageInConversation(11L, 31L))
                .thenReturn(List.of())
                .thenReturn(List.of(newer));
        mockReadyShop();
        when(contextService.build(21L)).thenReturn(new ImAutoReplyContextService.Context(
                List.of(new ImAutoReplyChatMessage("user", "问题")), List.of("问题")));
        when(messageClassificationService.classify(List.of("问题")))
                .thenReturn(com.gogoga.backend.enums.KnowledgeCategoryEnum.GENERAL);
        when(ragService.retrieve(1L, List.of("问题"), "GENERAL"))
                .thenReturn(ImAutoReplyRagService.RagContext.empty());
        ResolvedSystemPromptDTO prompt = new ResolvedSystemPromptDTO();
        prompt.setContent("系统");
        when(promptService.render(eq("IM_AUTO_REPLY_SYSTEM"), any(), anyString())).thenReturn(prompt);
        String protectedPrompt = ImAutoReplyPromptConstant.enforceBusinessScopePolicy("系统");
        when(chatService.generate(eq(protectedPrompt), eq(""), any()))
                .thenReturn(new ImAutoReplyChatResult("风险答复", 10, 2));
        when(chatService.rewrite(eq(protectedPrompt), eq(""), any(), eq("风险答复")))
                .thenReturn(new ImAutoReplyChatResult("安全答复", 5, 1));
        SensitiveWordHitDTO hit = new SensitiveWordHitDTO();
        hit.setHit(true);
        SensitiveWordHitDTO safe = new SensitiveWordHitDTO();
        safe.setHit(false);
        when(sensitiveWordService.detect("风险答复")).thenReturn(hit);
        when(sensitiveWordService.detect("安全答复")).thenReturn(safe);

        service.execute(payload());

        ArgumentCaptor<ImAutoReplyChatResult> usage = ArgumentCaptor.forClass(ImAutoReplyChatResult.class);
        verify(stateService).saveGenerated(eq(9L), eq(trigger), eq("安全答复"), usage.capture(), any(), eq(1), eq("GENERAL"));
        assertEquals(15, usage.getValue().promptTokens());
        assertEquals(3, usage.getValue().completionTokens());
    }

    @Test
    void persistsReplyWithShopContactSuffix() {
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
        newer.setMsgType(ImMessageTypeEnum.TEXT.getCode());
        when(messageMapper.selectAfterMessageInConversation(11L, 31L))
                .thenReturn(List.of())
                .thenReturn(List.of(newer));
        mockReadyShop();
        when(contextService.build(21L)).thenReturn(new ImAutoReplyContextService.Context(
                List.of(new ImAutoReplyChatMessage("user", "问题")), List.of("问题")));
        when(messageClassificationService.classify(List.of("问题")))
                .thenReturn(com.gogoga.backend.enums.KnowledgeCategoryEnum.GENERAL);
        when(ragService.retrieve(1L, List.of("问题"), "GENERAL"))
                .thenReturn(ImAutoReplyRagService.RagContext.empty());
        ResolvedSystemPromptDTO prompt = new ResolvedSystemPromptDTO();
        prompt.setContent("系统");
        when(promptService.render(eq("IM_AUTO_REPLY_SYSTEM"), any(), anyString())).thenReturn(prompt);
        ArgumentCaptor<String> modelContext = ArgumentCaptor.forClass(String.class);
        when(chatService.generate(
                eq(ImAutoReplyPromptConstant.enforceBusinessScopePolicy("系统")), modelContext.capture(), any()))
                .thenReturn(new ImAutoReplyChatResult("答复", 10, 2));
        SensitiveWordHitDTO safe = new SensitiveWordHitDTO();
        safe.setHit(false);
        when(sensitiveWordService.detect("答复")).thenReturn(safe);
        when(contactSuffixService.resolveContactPhone(1L)).thenReturn("400-820-8820");
        when(contactSuffixService.appendPhone("答复", "400-820-8820"))
                .thenReturn("答复\n可以联系门店 400-820-8820");

        service.execute(payload());

        verify(stateService).saveGenerated(eq(9L), eq(trigger), eq("答复\n可以联系门店 400-820-8820"),
                any(), any(), eq(0), eq("GENERAL"));
        // 有号码时必须告知模型，否则它会答“没有联系方式”再被后缀拼上号码，自相矛盾
        assertTrue(modelContext.getValue().contains("门店联系方式：已配置"));
        assertFalse(modelContext.getValue().contains("400-820-8820"));
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
        when(rightService.hasAutoReplyRight(1L)).thenReturn(true);
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
        return trigger(ImMessageTypeEnum.TEXT);
    }

    private MtImMessage trigger(ImMessageTypeEnum msgType) {
        MtImMessage message = new MtImMessage();
        message.setId(31L);
        message.setConversationId(11L);
        message.setOrderId(0L);
        message.setOpenUserId(41L);
        // 触发消息类型决定是否走大模型，缺省会被当成非文本消息回兜底话术
        message.setMsgType(msgType.getCode());
        return message;
    }

    private MtImAutoReplyTaskPayload payload() {
        return new MtImAutoReplyTaskPayload(9L, 31L, 11L, 21L, 1L);
    }
}
