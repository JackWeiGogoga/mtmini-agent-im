package com.gogoga.backend.dto;

import lombok.Data;

import java.util.List;

/**
 * 评价图片识别结果。
 */
@Data
public class CommentImageSummaryResultDTO {

    private Long summaryId;
    private String status;
    private Integer imageCount;
    private List<String> imageUrls;
    private String summaryText;
    private String summaryJson;
    private String modelProvider;
    private String model;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private Integer powerAmount;
    private String errorMessage;
}
