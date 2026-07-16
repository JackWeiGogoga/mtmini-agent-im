package com.gogoga.backend.service.impl;

import com.gogoga.backend.annotation.RequiresShopRight;
import com.gogoga.backend.client.MtCommentImClient;
import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.dto.CommentImMessageGenerationResultDTO;
import com.gogoga.backend.dto.MtImCreateGroupResultDTO;
import com.gogoga.backend.dto.MtImSendMessageResultDTO;
import com.gogoga.backend.dto.PowerConsumeRequestDTO;
import com.gogoga.backend.entity.mt.MtAccessToken;
import com.gogoga.backend.entity.mt.MtComment;
import com.gogoga.backend.entity.mt.MtCommentImFollowUp;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.mapper.MtAccessTokenMapper;
import com.gogoga.backend.mapper.MtCommentImFollowUpMapper;
import com.gogoga.backend.mapper.MtCommentMapper;
import com.gogoga.backend.mapper.MtShopMapper;
import com.gogoga.backend.service.CommentImMessageGenerationService;
import com.gogoga.backend.service.MtImMessageCryptoService;
import com.gogoga.backend.service.MtPowerConsumeService;
import com.gogoga.backend.service.MtShopRightService;
import com.gogoga.backend.service.ShopOperationSettingsService;
import com.gogoga.backend.service.async.MtAsyncTaskDryRunException;
import com.gogoga.backend.service.async.MtAsyncTaskTerminalFailureException;
import com.gogoga.backend.service.async.MtCommentAutoImFollowUpTaskPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MtCommentImFollowUpServiceImplTest {

    @Mock private MtCommentMapper commentMapper;
    @Mock private MtCommentImFollowUpMapper followUpMapper;
    @Mock private MtShopMapper shopMapper;
    @Mock private MtAccessTokenMapper tokenMapper;
    @Mock private ShopOperationSettingsService settingsService;
    @Mock private MtShopRightService shopRightService;
    @Mock private CommentImMessageGenerationService generationService;
    @Mock private MtPowerConsumeService powerConsumeService;
    @Mock private MtImMessageCryptoService cryptoService;
    @Mock private MtCommentImClient imClient;

    private MtProperties properties;
    private MtCommentImFollowUpServiceImpl service;

    @BeforeEach
    void setUp() {
        properties = new MtProperties();
        service = new MtCommentImFollowUpServiceImpl(
                properties, commentMapper, followUpMapper, shopMapper, tokenMapper,
                settingsService, shopRightService, generationService, powerConsumeService,
                cryptoService, imClient
        );
    }

    @Test
    void defaultCreateGroupDryRunGeneratesAndChargesButDoesNotClaimOrCallMeituan() {
        MtCommentImFollowUp pending = record("PENDING");
        MtCommentImFollowUp generated = record("GENERATED");
        generated.setMessageContent("您好，请提供订单和手机号🙏");
        stubExecutableContext(pending);
        when(generationService.generate(any())).thenReturn(generatedMessage());
        when(followUpMapper.markGenerated(any())).thenReturn(1);
        when(followUpMapper.selectByShopIdAndCommentId(100L, 542543250001L))
                .thenReturn(pending, generated);

        assertThrows(MtAsyncTaskDryRunException.class, () -> service.executeCommentAutoImFollowUpTask(payload()));

        ArgumentCaptor<PowerConsumeRequestDTO> powerCaptor = ArgumentCaptor.forClass(PowerConsumeRequestDTO.class);
        verify(powerConsumeService).consume(powerCaptor.capture());
        assertEquals("COMMENT_IM_FOLLOW_UP:542543250001", powerCaptor.getValue().getRequestNo());
        verify(followUpMapper, never()).claimCreateGroupAttempt(anyLong(), any(), any());
        verify(imClient, never()).createCommentGroup(anyString(), anyLong());
    }

    @Test
    void existingCreateAttemptWithoutGroupIsTerminalAndNeverCallsMeituanAgain() {
        properties.getComment().getImFollowUp().setCreateGroupDryRun(false);
        MtCommentImFollowUp generated = record("GENERATED");
        generated.setMessageContent("您好，请提供订单和手机号🙏");
        generated.setCreateGroupAttemptedAt(LocalDateTime.now().minusMinutes(1));
        stubExecutableContext(generated);

        assertThrows(MtAsyncTaskTerminalFailureException.class,
                () -> service.executeCommentAutoImFollowUpTask(payload()));

        verify(imClient, never()).createCommentGroup(anyString(), anyLong());
        verify(followUpMapper).markTerminal(eq(9L), eq("FAILED"), eq("CREATE_GROUP"),
                eq("COMMENT_IM_CREATE_ALREADY_ATTEMPTED"), anyString(), any(), any());
    }

    @Test
    void sendDryRunPreservesGroupWithoutEncryptingClaimingOrSending() {
        properties.getComment().getImFollowUp().setCreateGroupDryRun(false);
        MtCommentImFollowUp groupCreated = record("GROUP_CREATED");
        groupCreated.setGroupId(9988L);
        groupCreated.setMessageContent("您好，请提供订单和手机号🙏");
        stubExecutableContext(groupCreated);

        assertThrows(MtAsyncTaskDryRunException.class, () -> service.executeCommentAutoImFollowUpTask(payload()));

        verify(cryptoService, never()).encrypt(anyString());
        verify(followUpMapper, never()).initializeMessageIdentity(anyLong(), anyLong(), anyLong(), anyString(), any());
        verify(followUpMapper, never()).claimSendAttempt(anyLong(), any(), any());
        verify(imClient, never()).sendCommentMessage(anyString(), anyLong(), anyLong(), anyLong(), anyLong(), anyString());
    }

    @Test
    void createAndSendSuccessUsesOneAttemptPerExternalCall() {
        properties.getComment().getImFollowUp().setCreateGroupDryRun(false);
        properties.getComment().getImFollowUp().setSendMessageDryRun(false);
        MtCommentImFollowUp generated = record("GENERATED");
        generated.setMessageContent("您好，请提供订单和手机号🙏");
        MtCommentImFollowUp groupCreated = record("GROUP_CREATED");
        groupCreated.setMessageContent(generated.getMessageContent());
        groupCreated.setGroupId(9988L);
        groupCreated.setMessageId(1784034347000001L);
        groupCreated.setMessageCts(1784034347L);
        stubExecutableContext(generated);
        when(followUpMapper.selectByShopIdAndCommentId(100L, 542543250001L))
                .thenReturn(generated, groupCreated);
        when(followUpMapper.claimCreateGroupAttempt(eq(9L), any(), any())).thenReturn(1);
        when(imClient.createCommentGroup("access-token", 542543250001L)).thenReturn(groupResult());
        when(followUpMapper.markGroupCreated(any())).thenReturn(1);
        when(cryptoService.encrypt(groupCreated.getMessageContent())).thenReturn("encrypted-base64");
        when(followUpMapper.claimSendAttempt(eq(9L), any(), any())).thenReturn(1);
        MtImSendMessageResultDTO sendResult = new MtImSendMessageResultDTO();
        sendResult.setResponseSummary("{\"code\":\"OP_SUCCESS\"}");
        when(imClient.sendCommentMessage(anyString(), anyLong(), anyLong(), anyLong(), anyLong(), anyString()))
                .thenReturn(sendResult);

        service.executeCommentAutoImFollowUpTask(payload());

        verify(imClient, times(1)).createCommentGroup("access-token", 542543250001L);
        verify(imClient, times(1)).sendCommentMessage(
                "access-token", 542543250001L, 9988L, 1784034347000001L, 1784034347L, "encrypted-base64");
        verify(followUpMapper).markSuccess(any());
        verify(commentMapper).markImFollowUpStatus(eq(1L), eq("SUCCESS"), eq(9988L), any(), eq(null), any());
    }

    @Test
    void sendFailureIsRecordedTerminalAfterExactlyOneAttempt() {
        properties.getComment().getImFollowUp().setCreateGroupDryRun(false);
        properties.getComment().getImFollowUp().setSendMessageDryRun(false);
        MtCommentImFollowUp groupCreated = record("GROUP_CREATED");
        groupCreated.setGroupId(9988L);
        groupCreated.setMessageContent("您好，请提供订单和手机号🙏");
        groupCreated.setMessageId(1784034347000001L);
        groupCreated.setMessageCts(1784034347L);
        stubExecutableContext(groupCreated);
        when(cryptoService.encrypt(anyString())).thenReturn("encrypted-base64");
        when(followUpMapper.claimSendAttempt(eq(9L), any(), any())).thenReturn(1);
        when(imClient.sendCommentMessage(anyString(), anyLong(), anyLong(), anyLong(), anyLong(), anyString()))
                .thenThrow(new IllegalStateException("timeout"));

        assertThrows(MtAsyncTaskTerminalFailureException.class,
                () -> service.executeCommentAutoImFollowUpTask(payload()));

        verify(imClient, times(1)).sendCommentMessage(anyString(), anyLong(), anyLong(), anyLong(), anyLong(), anyString());
        verify(followUpMapper).markTerminal(eq(9L), eq("FAILED"), eq("SEND_MESSAGE"),
                eq("COMMENT_IM_SEND_FAILED"), eq("timeout"), any(), any());
    }

    @Test
    void rejectsCrossShopPayloadBeforeAnyExternalWork() {
        MtComment comment = comment();
        comment.setShopId(200L);
        when(commentMapper.selectByCommentId(542543250001L)).thenReturn(comment);

        assertThrows(IllegalStateException.class, () -> service.executeCommentAutoImFollowUpTask(payload()));

        verify(imClient, never()).createCommentGroup(anyString(), anyLong());
        verify(generationService, never()).generate(any());
    }

    @Test
    void invalidCommentIsMarkedSkippedBeforeGenerationOrMeituanCalls() {
        MtComment comment = comment();
        comment.setValid(0);
        when(commentMapper.selectByCommentId(542543250001L)).thenReturn(comment);
        when(shopMapper.selectById(100L)).thenReturn(shop());
        when(followUpMapper.selectByShopIdAndCommentId(100L, 542543250001L)).thenReturn(record("PENDING"));

        service.executeCommentAutoImFollowUpTask(payload());

        verify(followUpMapper).markTerminal(
                eq(9L), eq("SKIPPED"), eq(null), eq("COMMENT_IM_INVALID"), eq("评价已失效"), any(), any());
        verify(generationService, never()).generate(any());
        verify(imClient, never()).createCommentGroup(anyString(), anyLong());
        verify(imClient, never()).sendCommentMessage(anyString(), anyLong(), anyLong(), anyLong(), anyLong(), anyString());
    }

    @Test
    void unsupportedSensitiveTypeIsMarkedSkippedBeforeGenerationOrMeituanCalls() {
        MtComment comment = comment();
        comment.setCommentType(3);
        when(commentMapper.selectByCommentId(542543250001L)).thenReturn(comment);
        when(shopMapper.selectById(100L)).thenReturn(shop());
        when(followUpMapper.selectByShopIdAndCommentId(100L, 542543250001L)).thenReturn(record("PENDING"));

        service.executeCommentAutoImFollowUpTask(payload());

        verify(followUpMapper).markTerminal(
                eq(9L), eq("SKIPPED"), eq(null), eq("COMMENT_IM_TYPE_UNSUPPORTED"),
                eq("评论敏感类型不处理"), any(), any());
        verify(generationService, never()).generate(any());
        verify(imClient, never()).createCommentGroup(anyString(), anyLong());
    }

    private void stubExecutableContext(MtCommentImFollowUp record) {
        when(commentMapper.selectByCommentId(542543250001L)).thenReturn(comment());
        when(shopMapper.selectById(100L)).thenReturn(shop());
        when(followUpMapper.selectByShopIdAndCommentId(100L, 542543250001L)).thenReturn(record);
        when(settingsService.isAutoReplyEnabled(100L)).thenReturn(true);
        when(settingsService.isNegativeCommentAppealEnabled(100L)).thenReturn(true);
        when(shopRightService.hasShopRight(eq(100L), eq(RequiresShopRight.Logical.OR), any(String[].class))).thenReturn(true);
        when(tokenMapper.selectByShopId(100L)).thenReturn(token());
    }

    private MtCommentAutoImFollowUpTaskPayload payload() {
        MtCommentAutoImFollowUpTaskPayload payload = new MtCommentAutoImFollowUpTaskPayload();
        payload.setShopId(100L);
        payload.setCommentId(542543250001L);
        return payload;
    }

    private MtComment comment() {
        MtComment comment = new MtComment();
        comment.setId(1L);
        comment.setShopId(100L);
        comment.setCommentId(542543250001L);
        comment.setValid(1);
        comment.setCommentType(4);
        comment.setProductScore(2);
        comment.setPackingScore(5);
        comment.setReplyStatus(1);
        comment.setAutoReplyStatus("SUCCESS");
        return comment;
    }

    private MtShop shop() {
        MtShop shop = new MtShop();
        shop.setId(100L);
        shop.setEpoiId("epoi-001");
        return shop;
    }

    private MtAccessToken token() {
        MtAccessToken token = new MtAccessToken();
        token.setShopId(100L);
        token.setAccessToken("access-token");
        return token;
    }

    private MtCommentImFollowUp record(String status) {
        MtCommentImFollowUp record = new MtCommentImFollowUp();
        record.setId(9L);
        record.setShopId(100L);
        record.setMtCommentId(1L);
        record.setCommentId(542543250001L);
        record.setStatus(status);
        return record;
    }

    private CommentImMessageGenerationResultDTO generatedMessage() {
        CommentImMessageGenerationResultDTO result = new CommentImMessageGenerationResultDTO();
        result.setMessageContent("您好，请提供订单和手机号🙏");
        result.setMessageSource("FALLBACK_STATIC");
        result.setPowerAmount(1000);
        result.setDurationMs(2);
        return result;
    }

    private MtImCreateGroupResultDTO groupResult() {
        MtImCreateGroupResultDTO result = new MtImCreateGroupResultDTO();
        result.setGroupId(9988L);
        result.setNewGroup("true");
        result.setCtime(1784034347L);
        result.setState(1);
        result.setCloseType("NONE");
        result.setResponseSummary("{\"code\":\"OP_SUCCESS\"}");
        return result;
    }
}
