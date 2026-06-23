package com.gogoga.backend.entity.mt;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 评价图片识别摘要。
 */
@Data
public class MtCommentImageSummary {

    private Long id;
    private Long shopId;
    private Long mtCommentId;
    private Long commentId;
    private Integer imageCount;
    private String imageUrlsJson;
    private String status;
    private String summaryJson;
    private String summaryText;
    private String modelProvider;
    private String model;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private Integer powerAmount;
    private String errorMessage;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
