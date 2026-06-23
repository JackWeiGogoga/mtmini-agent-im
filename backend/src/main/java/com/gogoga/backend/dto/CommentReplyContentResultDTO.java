package com.gogoga.backend.dto;

import lombok.Data;

/**
 * 评论回复内容生成结果。
 */
@Data
public class CommentReplyContentResultDTO {

    private boolean shouldReply;
    private String skipReason;
    private String replyContent;
    private String replySource;
    private String sentiment;
    private Long generationId;
    private String knowledgeHitJson;
    private String model;
    private Integer powerAmount;
    private String imageSummaryStatus;
    private String imageSummaryText;
    private String imageSummaryJson;
    private String imageSummaryErrorMessage;
    private Integer imagePromptTokens;
    private Integer imageCompletionTokens;
    private Integer imageTotalTokens;
    private Integer imagePowerAmount;
}
