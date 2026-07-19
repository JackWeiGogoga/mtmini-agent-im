package com.gogoga.backend.entity.mt;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * IM 自动回复的编排、RAG、计费与发送状态记录。
 */
@Data
public class MtImAutoReply {

    private Long id;
    private Long shopId;
    private Long conversationId;
    private Long sessionId;
    private Long triggerMessageId;
    private Long replyMessageId;
    private Long outboundMessageId;
    private String status;
    private String skipReason;
    private Boolean ragUsed;
    private Integer ragChunkCount;
    private BigDecimal ragMaxScore;
    private String modelProvider;
    private String modelName;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer powerAmount;
    private Integer rewriteCount;
    private LocalDateTime generatedAt;
    private LocalDateTime billedAt;
    private LocalDateTime sentAt;
    private String lastErrorCode;
    private String lastErrorSummary;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
