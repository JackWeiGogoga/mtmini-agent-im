package com.gogoga.backend.entity.mt;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 门店与顾客唯一的长期 IM 会话流。
 */
@Data
public class MtImConversation {

    private Long id;
    private Long shopId;
    private Long openUserId;
    private Long currentSessionId;
    private Long lastMessageId;
    private Integer messageCount;
    private Integer userMessageCount;
    private Integer aiMessageCount;
    private Integer merchantMessageCount;
    private Integer sessionCount;
    private LocalDateTime firstMessageAt;
    private LocalDateTime lastMessageAt;
    private LocalDateTime lastUserMessageAt;
    private LocalDateTime lastAiReplyAt;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
