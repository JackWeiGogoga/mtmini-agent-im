package com.gogoga.backend.service;

import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.config.PowerProperties;
import com.gogoga.backend.dto.ImAutoReplyChatResult;
import com.gogoga.backend.entity.mt.MtImAutoReply;
import com.gogoga.backend.entity.mt.MtImMessage;
import com.gogoga.backend.mapper.MtImAutoReplyMapper;
import com.gogoga.backend.mapper.MtImMessageMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MtImAutoReplyStateServiceTest {

    @Mock private MtImAutoReplyMapper autoReplyMapper;
    @Mock private MtImMessageMapper messageMapper;
    @Mock private MtImMessageCryptoService cryptoService;
    @Mock private MtImConversationService conversationService;
    @Mock private MtPowerConsumeService powerConsumeService;

    @Test
    void populatesEveryNotNullColumnWhenSavingGeneratedReply() {
        MtImAutoReply autoReply = new MtImAutoReply();
        autoReply.setId(7L);
        autoReply.setShopId(1L);
        autoReply.setConversationId(2L);
        autoReply.setSessionId(3L);
        autoReply.setTriggerMessageId(4L);
        autoReply.setOutboundMessageId(5L);
        when(autoReplyMapper.selectById(7L)).thenReturn(autoReply);
        MtImMessage trigger = new MtImMessage();
        trigger.setAppId(9638L);
        trigger.setOpenUserId(11L);
        MtImAutoReplyStateService service = new MtImAutoReplyStateService(
                new MtProperties(), new PowerProperties(), autoReplyMapper, messageMapper,
                cryptoService, conversationService, powerConsumeService);

        service.saveGenerated(7L, trigger, "回复内容",
                new ImAutoReplyChatResult("回复内容", 10, 20), null, 0, "ORDER");

        ArgumentCaptor<MtImMessage> saved = ArgumentCaptor.forClass(MtImMessage.class);
        verify(messageMapper).insert(saved.capture());
        MtImMessage message = saved.getValue();
        // sign_verified 是 NOT NULL；漏设会在插入时抛 DataIntegrityViolationException
        assertNotNull(message.getSignVerified());
        assertFalse(message.getSignVerified());
        assertNotNull(message.getRecordSource());
        assertNotNull(message.getDeliveryStatus());
        assertNotNull(message.getEventTime());
        assertNotNull(message.getParseStatus());
        assertNotNull(message.getDecryptStatus());
        assertNotNull(message.getReceivedAt());
    }

    @Test
    void doesNotChargeAgainWhenActualTokenBillingAlreadyCompleted() {
        MtImAutoReply row = new MtImAutoReply();
        row.setId(9L);
        row.setBilledAt(LocalDateTime.now());
        row.setPowerAmount(10);
        when(autoReplyMapper.selectById(9L)).thenReturn(row);
        MtImAutoReplyStateService service = new MtImAutoReplyStateService(
                new MtProperties(), new PowerProperties(), autoReplyMapper, messageMapper,
                cryptoService, conversationService, powerConsumeService);

        MtImAutoReply result = service.bill(9L);

        assertEquals(10, result.getPowerAmount());
        verify(powerConsumeService, never()).consume(any());
        verify(autoReplyMapper, never()).update(any());
    }
}
