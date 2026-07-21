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
    /** 消息类型，见 ImMessageTypeEnum。图片消息的正文是图片 URL，前端据此渲染成图片而不是链接文本。 */
    private Integer msgType;
    private String content;
    private String deliveryStatus;
    private String autoReplyStatus;
    private Long replyToMessageId;
    private Long orderId;
    private LocalDateTime eventTime;
}
