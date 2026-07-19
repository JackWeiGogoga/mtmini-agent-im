package com.gogoga.backend.service;

import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.config.PowerProperties;
import com.gogoga.backend.entity.mt.MtImAutoReply;
import com.gogoga.backend.mapper.MtImAutoReplyMapper;
import com.gogoga.backend.mapper.MtImMessageMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
