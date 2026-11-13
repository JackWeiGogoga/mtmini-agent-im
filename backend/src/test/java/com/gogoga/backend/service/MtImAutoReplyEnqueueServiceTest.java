package com.gogoga.backend.service;

import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.entity.mt.MtAccessToken;
import com.gogoga.backend.entity.mt.MtImAutoReply;
import com.gogoga.backend.entity.mt.MtImMessage;
import com.gogoga.backend.enums.ImMessageDecryptStatusEnum;
import com.gogoga.backend.enums.ImMessageParseStatusEnum;
import com.gogoga.backend.enums.ImMessageSenderRoleEnum;
import com.gogoga.backend.enums.ImMessageTypeEnum;
import com.gogoga.backend.enums.MtAccessTokenAuthStatusEnum;
import com.gogoga.backend.enums.MtAsyncTaskTypeEnum;
import com.gogoga.backend.mapper.MtImAutoReplyMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MtImAutoReplyEnqueueServiceTest {

    @Mock private ShopOperationSettingsService settingsService;
    @Mock private MtShopRightService rightService;
    @Mock private MtAccessTokenService tokenService;
    @Mock private MtImAutoReplyMapper autoReplyMapper;
    @Mock private MtAsyncTaskService asyncTaskService;

    private MtProperties properties;
    private MtImAutoReplyEnqueueService service;

    @BeforeEach
    void setUp() {
        properties = new MtProperties();
        properties.getImAutoReply().setEnabled(true);
        service = new MtImAutoReplyEnqueueService(
                properties, settingsService, rightService, tokenService, autoReplyMapper, asyncTaskService,
                new ImOutboundMessageIdGenerator());
    }

    @Test
    void createsPendingReplyAndIdempotentTaskForEligibleUserText() {
        MtImMessage message = eligibleMessage();
        when(settingsService.isAutoReplyEnabled(1L)).thenReturn(true);
        when(rightService.hasAutoReplyRight(1L)).thenReturn(true);
        MtAccessToken token = new MtAccessToken();
        token.setAuthStatus(MtAccessTokenAuthStatusEnum.VALID.getCode());
        token.setAccessToken("token");
        token.setExpiresAt(LocalDateTime.now().plusHours(1));
        when(tokenService.findByShopId(1L)).thenReturn(token);
        when(autoReplyMapper.insert(any())).thenAnswer(invocation -> {
            ((MtImAutoReply) invocation.getArgument(0)).setId(99L);
            return 1;
        });

        MtImAutoReply result = service.enqueueIfEligible(message);

        assertNotNull(result);
        verify(asyncTaskService).enqueueTask(
                eq(MtAsyncTaskTypeEnum.IM_AUTO_REPLY), eq("99"), eq("11"), any(), any(), anyInt());
    }

    @Test
    void ignoresMerchantOrUnsupportedMessages() {
        MtImMessage message = eligibleMessage();
        message.setSenderRole(ImMessageSenderRoleEnum.MERCHANT.name());

        assertNull(service.enqueueIfEligible(message));
        verify(autoReplyMapper, never()).insert(any());
        verify(asyncTaskService, never()).enqueueTask(any(), anyString(), anyString(), any(), any(), anyInt());
    }

    @Test
    void queuesWhenAuthorizationIsValidButTokenNeedsRefresh() {
        MtImMessage message = eligibleMessage();
        when(settingsService.isAutoReplyEnabled(1L)).thenReturn(true);
        when(rightService.hasAutoReplyRight(1L)).thenReturn(true);
        MtAccessToken token = new MtAccessToken();
        token.setAuthStatus(MtAccessTokenAuthStatusEnum.VALID.getCode());
        token.setAccessToken("expired-token");
        token.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(tokenService.findByShopId(1L)).thenReturn(token);
        when(autoReplyMapper.insert(any())).thenAnswer(invocation -> {
            ((MtImAutoReply) invocation.getArgument(0)).setId(100L);
            return 1;
        });

        assertNotNull(service.enqueueIfEligible(message));
        verify(asyncTaskService).enqueueTask(
                eq(MtAsyncTaskTypeEnum.IM_AUTO_REPLY), eq("100"), eq("11"), any(), any(), anyInt());
    }

    @Test
    void queuesUserImageWithoutReadableContent() {
        MtImMessage message = eligibleMessage();
        // 图片消息不做解析，正文与密文都可能缺失，这不能成为漏回的理由
        message.setMsgType(ImMessageTypeEnum.IMAGE.getCode());
        message.setDecryptStatus(ImMessageDecryptStatusEnum.NOT_APPLICABLE.name());
        message.setMsgContentPlaintext(null);
        when(settingsService.isAutoReplyEnabled(1L)).thenReturn(true);
        when(rightService.hasAutoReplyRight(1L)).thenReturn(true);
        MtAccessToken token = new MtAccessToken();
        token.setAuthStatus(MtAccessTokenAuthStatusEnum.VALID.getCode());
        token.setAccessToken("token");
        token.setExpiresAt(LocalDateTime.now().plusHours(1));
        when(tokenService.findByShopId(1L)).thenReturn(token);
        when(autoReplyMapper.insert(any())).thenAnswer(invocation -> {
            ((MtImAutoReply) invocation.getArgument(0)).setId(101L);
            return 1;
        });

        assertNotNull(service.enqueueIfEligible(message));
        verify(asyncTaskService).enqueueTask(
                eq(MtAsyncTaskTypeEnum.IM_AUTO_REPLY), eq("101"), eq("11"), any(), any(), anyInt());
    }

    @Test
    void ignoresPlatformCardMessage() {
        MtImMessage message = eligibleMessage();
        message.setMsgType(ImMessageTypeEnum.CARD.getCode());

        assertNull(service.enqueueIfEligible(message));
        verify(autoReplyMapper, never()).insert(any());
    }

    @Test
    void ignoresUserTextWithoutDecryptedContent() {
        MtImMessage message = eligibleMessage();
        message.setMsgContentPlaintext(null);

        assertNull(service.enqueueIfEligible(message));
        verify(autoReplyMapper, never()).insert(any());
    }

    private MtImMessage eligibleMessage() {
        MtImMessage message = new MtImMessage();
        message.setId(31L);
        message.setShopId(1L);
        message.setConversationId(11L);
        message.setSessionId(21L);
        message.setOpenUserId(41L);
        message.setBizType(1);
        message.setMsgType(ImMessageTypeEnum.TEXT.getCode());
        message.setSenderRole(ImMessageSenderRoleEnum.USER.name());
        message.setParseStatus(ImMessageParseStatusEnum.SUCCESS.name());
        message.setDecryptStatus(ImMessageDecryptStatusEnum.SUCCESS.name());
        message.setMsgContentPlaintext("你好");
        return message;
    }
}
