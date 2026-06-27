package com.gogoga.backend.entity.mt;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 美团门店评价实体。
 */
@Data
public class MtComment {

    private Long id;
    private Long shopId;
    private String epoiId;
    private String poiId;
    private Long commentId;
    private String result;
    private String commentContent;
    private Integer orderCommentScore;
    private Integer foodCommentScore;
    private Integer deliveryCommentScore;
    private String addComment;
    private String addCommentTime;
    private Integer packingScore;
    private String commentTime;
    private String commentLables;
    private Integer shipTime;
    private String commentPictures;
    private String imageSummaryText;
    private String praiseFoodList;
    private String criticFoodList;
    private Integer replyStatus;
    private String commentOrderDetailJson;
    private String eReplyContent;
    private String eReplyTime;
    private Integer commentType;
    private Integer valid;
    private Long userId;
    private String overDeliveryTimeDesc;
    private Integer commentScoreType;
    private Integer productScore;
    private Integer bizCode;
    private String queryStartTime;
    private String queryEndTime;
    private String rawPayloadJson;
    private String autoReplyStatus;
    private String autoReplyContent;
    private LocalDateTime autoReplyAt;
    private String autoReplyError;
    private String autoReplySource;
    private String autoReplySentiment;
    private Long autoReplyGenerationId;
    private String autoReplyKnowledgeHitJson;
    private String autoReplyModel;
    private Integer autoReplyPowerAmount;
    private Integer autoReplyDurationMs;
    private String analysisStatus;
    private String analysisText;
    private String analysisModel;
    private String analysisPromptKey;
    private Integer analysisPromptVersion;
    private Integer analysisPromptTokens;
    private Integer analysisCompletionTokens;
    private Integer analysisPowerAmount;
    private Integer analysisDurationMs;
    private String analysisError;
    private LocalDateTime analysisAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
