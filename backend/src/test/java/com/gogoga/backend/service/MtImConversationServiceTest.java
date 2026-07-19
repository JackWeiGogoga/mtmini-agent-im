package com.gogoga.backend.service;

import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.entity.mt.MtImConversation;
import com.gogoga.backend.entity.mt.MtImConversationSession;
import com.gogoga.backend.entity.mt.MtImMessage;
import com.gogoga.backend.enums.ImConversationSessionStatusEnum;
import com.gogoga.backend.enums.ImMessageSenderRoleEnum;
import com.gogoga.backend.mapper.MtImConversationMapper;
import com.gogoga.backend.mapper.MtImConversationSessionMapper;
import org.junit.jupiter.api.BeforeEach;
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
class MtImConversationServiceTest {

    @Mock private MtImConversationMapper conversationMapper;
    @Mock private MtImConversationSessionMapper sessionMapper;

    private MtImConversationService service;

    @BeforeEach
    void setUp() {
        MtProperties properties = new MtProperties();
        properties.getImAutoReply().setSessionIdleMinutes(30L);
        service = new MtImConversationService(properties, conversationMapper, sessionMapper);
    }

    @Test
    void createsOneLongConversationAndInitialSession() {
        when(conversationMapper.insert(any())).thenAnswer(invocation -> {
            ((MtImConversation) invocation.getArgument(0)).setId(11L);
            return 1;
        });
        when(sessionMapper.insert(any())).thenAnswer(invocation -> {
            ((MtImConversationSession) invocation.getArgument(0)).setId(21L);
            return 1;
        });

        MtImConversationService.Assignment result = service.assign(1L, 2L, LocalDateTime.of(2026, 7, 19, 10, 0));

        assertEquals(11L, result.conversation().getId());
        assertEquals(21L, result.session().getId());
        verify(conversationMapper).updateCurrentSession(any());
    }

    @Test
    void reusesActiveSessionWithinIdleWindowRegardlessOfOrder() {
        MtImConversation conversation = conversation(11L, 21L, 1);
        MtImConversationSession session = session(21L, LocalDateTime.of(2026, 7, 19, 10, 0));
        when(conversationMapper.selectByShopAndOpenUserForUpdate(1L, 2L)).thenReturn(conversation);
        when(sessionMapper.selectActiveForUpdate(11L)).thenReturn(session);

        MtImConversationService.Assignment result = service.assign(1L, 2L, LocalDateTime.of(2026, 7, 19, 10, 30));

        assertEquals(21L, result.session().getId());
        verify(sessionMapper, never()).close(any(), any());
        verify(sessionMapper, never()).insert(any());
    }

    @Test
    void startsChildSessionOnlyAfterIdleTimeout() {
        MtImConversation conversation = conversation(11L, 21L, 1);
        MtImConversationSession session = session(21L, LocalDateTime.of(2026, 7, 19, 10, 0));
        when(conversationMapper.selectByShopAndOpenUserForUpdate(1L, 2L)).thenReturn(conversation);
        when(sessionMapper.selectActiveForUpdate(11L)).thenReturn(session);
        when(sessionMapper.insert(any())).thenAnswer(invocation -> {
            ((MtImConversationSession) invocation.getArgument(0)).setId(22L);
            return 1;
        });

        MtImConversationService.Assignment result = service.assign(1L, 2L, LocalDateTime.of(2026, 7, 19, 10, 31));

        assertEquals(11L, result.conversation().getId());
        assertEquals(22L, result.session().getId());
        verify(sessionMapper).close(21L, LocalDateTime.of(2026, 7, 19, 10, 31));
    }

    @Test
    void updatesConversationAndSessionAggregatesAfterMessageInsert() {
        MtImMessage message = new MtImMessage();
        message.setId(31L);
        message.setConversationId(11L);
        message.setSessionId(21L);
        message.setSenderRole(ImMessageSenderRoleEnum.USER.name());
        message.setEventTime(LocalDateTime.of(2026, 7, 19, 10, 0));

        service.recordMessage(message);

        verify(conversationMapper).incrementMessageAggregate(
                11L, 31L, ImMessageSenderRoleEnum.USER.name(), message.getEventTime());
        verify(sessionMapper).incrementMessageAggregate(
                21L, ImMessageSenderRoleEnum.USER.name(), message.getEventTime());
    }

    private MtImConversation conversation(long id, long sessionId, int sessionCount) {
        MtImConversation conversation = new MtImConversation();
        conversation.setId(id);
        conversation.setCurrentSessionId(sessionId);
        conversation.setSessionCount(sessionCount);
        return conversation;
    }

    private MtImConversationSession session(long id, LocalDateTime lastActiveAt) {
        MtImConversationSession session = new MtImConversationSession();
        session.setId(id);
        session.setStatus(ImConversationSessionStatusEnum.ACTIVE.name());
        session.setLastActiveAt(lastActiveAt);
        return session;
    }
}
