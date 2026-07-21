package com.gogoga.backend.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ImConversationMessageVO {
    private Long id;
    private Long conversationId;
    private Long sessionId;
    private Integer sessionNo;
    private String sessionBoundaryReason;
    private LocalDateTime sessionStartedAt;
    private String senderRole;
    private String recordSource;
    private String content;
    private String deliveryStatus;
    private String autoReplyStatus;
    private Long replyToMessageId;
    private Long orderId;
    private LocalDateTime eventTime;
}
