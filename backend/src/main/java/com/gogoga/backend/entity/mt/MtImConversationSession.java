package com.gogoga.backend.entity.mt;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 长期会话中的短期记忆窗口。
 */
@Data
public class MtImConversationSession {

    private Long id;
    private Long conversationId;
    private Integer sessionNo;
    private String status;
    private String boundaryReason;
    private LocalDateTime startedAt;
    private LocalDateTime lastActiveAt;
    private LocalDateTime closedAt;
    private Integer messageCount;
    private Integer userMessageCount;
    private Integer aiMessageCount;
    private Integer merchantMessageCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
