package com.gogoga.backend.mapper;

import com.gogoga.backend.param.ImAutoReplyLogQueryParam;
import com.gogoga.backend.param.ImConversationQueryParam;
import com.gogoga.backend.vo.ImAutoReplyLogVO;
import com.gogoga.backend.vo.ImConversationListVO;
import com.gogoga.backend.vo.ImConversationMessageVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface MtImReplyQueryMapper {
    List<ImAutoReplyLogVO> selectReplyLogs(ImAutoReplyLogQueryParam param);

    List<ImConversationListVO> selectConversations(ImConversationQueryParam param);

    List<ImConversationMessageVO> selectHistoryBefore(@Param("conversationId") Long conversationId,
                                                       @Param("cursorTime") LocalDateTime cursorTime,
                                                       @Param("cursorId") Long cursorId,
                                                       @Param("limit") int limit);
}
