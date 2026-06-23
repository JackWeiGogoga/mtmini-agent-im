package com.gogoga.backend.entity.mt;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 评价自动回复生成记录。
 */
@Data
public class MtCommentReplyGeneration {

    private Long id;
    private Long shopId;
    private Long mtCommentId;
    private Long commentId;
    private String replySource;
    private String sentiment;
    private String replyContent;
    private Long officialItemId;
    private String knowledgeHitJson;
    private String llmProvider;
    private String llmModel;
    private String embeddingProvider;
    private String embeddingModel;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer powerAmount;
    private String fallbackReason;
    private String errorStage;
    private String errorMessage;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
