package com.gogoga.backend.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ImConversationListVO {
    private Long conversationId;
    private Long shopId;
    private String shopName;
    private String poiId;
    private String epoiId;
    private Long openUserId;
    private Integer messageCount;
    private Integer userMessageCount;
    private Integer aiMessageCount;
    private Integer merchantMessageCount;
    private Integer sessionCount;
    private String lastMessageRole;
    private String lastMessageContent;
    private LocalDateTime firstMessageAt;
    private LocalDateTime lastMessageAt;
    private LocalDateTime lastAiReplyAt;
}
