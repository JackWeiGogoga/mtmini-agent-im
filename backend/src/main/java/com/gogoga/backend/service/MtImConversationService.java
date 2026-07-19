package com.gogoga.backend.service;

import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.entity.mt.MtImConversation;
import com.gogoga.backend.entity.mt.MtImConversationSession;
import com.gogoga.backend.entity.mt.MtImMessage;
import com.gogoga.backend.enums.ImConversationBoundaryReasonEnum;
import com.gogoga.backend.enums.ImConversationSessionStatusEnum;
import com.gogoga.backend.mapper.MtImConversationMapper;
import com.gogoga.backend.mapper.MtImConversationSessionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 分配长期 Conversation 与空闲切分的短期 Session，并维护消息聚合。
 */
@Service
@RequiredArgsConstructor
public class MtImConversationService {

    private final MtProperties mtProperties;
    private final MtImConversationMapper conversationMapper;
    private final MtImConversationSessionMapper sessionMapper;

    public Assignment assign(Long shopId, Long openUserId, LocalDateTime eventTime) {
        if (shopId == null || openUserId == null) {
            throw new IllegalArgumentException("IM_CONVERSATION_KEY_MISSING");
        }
        LocalDateTime occurredAt = eventTime == null ? LocalDateTime.now() : eventTime;
        MtImConversation conversation = conversationMapper.selectByShopAndOpenUserForUpdate(shopId, openUserId);
        if (conversation == null) {
            conversation = createConversation(shopId, openUserId, occurredAt);
        }

        MtImConversationSession session = sessionMapper.selectActiveForUpdate(conversation.getId());
        if (session == null) {
            session = createSession(conversation, occurredAt, ImConversationBoundaryReasonEnum.INITIAL);
        } else if (shouldStartNewSession(session, occurredAt)) {
            sessionMapper.close(session.getId(), occurredAt);
            session = createSession(conversation, occurredAt, ImConversationBoundaryReasonEnum.IDLE_TIMEOUT);
        }
        return new Assignment(conversation, session);
    }

    public void recordMessage(MtImMessage message) {
        if (message == null || message.getId() == null || message.getConversationId() == null
                || message.getSessionId() == null || message.getSenderRole() == null) {
            return;
        }
        LocalDateTime eventTime = message.getEventTime() == null ? LocalDateTime.now() : message.getEventTime();
        conversationMapper.incrementMessageAggregate(
                message.getConversationId(), message.getId(), message.getSenderRole(), eventTime);
        sessionMapper.incrementMessageAggregate(message.getSessionId(), message.getSenderRole(), eventTime);
    }

    private MtImConversation createConversation(Long shopId, Long openUserId, LocalDateTime now) {
        MtImConversation conversation = new MtImConversation();
        conversation.setShopId(shopId);
        conversation.setOpenUserId(openUserId);
        conversation.setMessageCount(0);
        conversation.setUserMessageCount(0);
        conversation.setAiMessageCount(0);
        conversation.setMerchantMessageCount(0);
        conversation.setSessionCount(0);
        conversation.setVersion(0);
        conversation.setCreateTime(now);
        conversation.setUpdateTime(now);
        try {
            conversationMapper.insert(conversation);
            return conversation;
        } catch (DuplicateKeyException ignored) {
            MtImConversation existing = conversationMapper.selectByShopAndOpenUserForUpdate(shopId, openUserId);
            if (existing == null) {
                throw ignored;
            }
            return existing;
        }
    }

    private MtImConversationSession createSession(MtImConversation conversation,
                                                   LocalDateTime now,
                                                   ImConversationBoundaryReasonEnum reason) {
        int nextSessionNo = (conversation.getSessionCount() == null ? 0 : conversation.getSessionCount()) + 1;
        MtImConversationSession session = new MtImConversationSession();
        session.setConversationId(conversation.getId());
        session.setSessionNo(nextSessionNo);
        session.setStatus(ImConversationSessionStatusEnum.ACTIVE.name());
        session.setBoundaryReason(reason.name());
        session.setStartedAt(now);
        session.setLastActiveAt(now);
        session.setMessageCount(0);
        session.setUserMessageCount(0);
        session.setAiMessageCount(0);
        session.setMerchantMessageCount(0);
        session.setCreateTime(now);
        session.setUpdateTime(now);
        sessionMapper.insert(session);

        conversation.setCurrentSessionId(session.getId());
        conversation.setSessionCount(nextSessionNo);
        conversation.setUpdateTime(now);
        conversationMapper.updateCurrentSession(conversation);
        return session;
    }

    private boolean shouldStartNewSession(MtImConversationSession session, LocalDateTime eventTime) {
        if (session.getLastActiveAt() == null || !eventTime.isAfter(session.getLastActiveAt())) {
            return false;
        }
        long idleMinutes = Math.max(mtProperties.getImAutoReply().getSessionIdleMinutes(), 1L);
        return Duration.between(session.getLastActiveAt(), eventTime).compareTo(Duration.ofMinutes(idleMinutes)) > 0;
    }

    public record Assignment(MtImConversation conversation, MtImConversationSession session) {
    }
}
