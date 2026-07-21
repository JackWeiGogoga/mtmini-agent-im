package com.gogoga.backend.mapper;

import com.gogoga.backend.entity.mt.MtImConversationSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface MtImConversationSessionMapper {

    int insert(MtImConversationSession session);

    MtImConversationSession selectById(@Param("id") Long id);

    MtImConversationSession selectActiveForUpdate(@Param("conversationId") Long conversationId);

    int close(@Param("id") Long id, @Param("closedAt") LocalDateTime closedAt);

    int incrementMessageAggregate(@Param("id") Long id,
                                  @Param("senderRole") String senderRole,
                                  @Param("eventTime") LocalDateTime eventTime);
}
