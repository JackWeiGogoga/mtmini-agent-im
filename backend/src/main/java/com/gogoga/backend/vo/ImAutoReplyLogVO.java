package com.gogoga.backend.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ImAutoReplyLogVO {
    private Long autoReplyId;
    private Long shopId;
    private String shopName;
    private String poiId;
    private String epoiId;
    private Long conversationId;
    private Long sessionId;
    private Long openUserId;
    private Long orderId;
    private Long userMessageId;
    private String userContent;
    private LocalDateTime userMessageTime;
    private Long aiMessageId;
    private String aiContent;
    private LocalDateTime aiMessageTime;
    private String deliveryStatus;
    private String status;
    private String skipReason;
    private Boolean ragUsed;
    private Integer ragChunkCount;
    private BigDecimal ragMaxScore;
    private String modelName;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer powerAmount;
    private Integer rewriteCount;
    private LocalDateTime sentAt;
    private String lastErrorCode;
    private LocalDateTime createTime;
}
