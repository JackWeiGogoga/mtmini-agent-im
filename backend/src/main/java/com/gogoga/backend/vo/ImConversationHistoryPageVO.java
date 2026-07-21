package com.gogoga.backend.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ImConversationHistoryPageVO {
    private Long conversationId;
    private List<ImConversationMessageVO> list;
    private Boolean hasMore;
    private LocalDateTime nextCursorTime;
    private Long nextCursorId;
}
