package com.gogoga.backend.dto;

import lombok.Data;

/**
 * 差评申诉测试预览结果。
 */
@Data
public class CommentComplaintPreviewDTO {

    private boolean shouldComplain;
    private String skipReason;
    private String sentiment;
    private Integer reportTypeCode;
    private String reportTypeName;
    private Boolean typeDefaulted;
    private String typeReason;
    private String reportContent;
    private Boolean hasPicture;
    private String model;
    private String typePromptKey;
    private Integer typePromptVersion;
    private String contentPromptKey;
    private Integer contentPromptVersion;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private Integer estimatedPowerAmount;
    private String sensitiveErrorMessage;
    private String imageSummaryStatus;
    private String imageSummaryText;
    private String imageSummaryJson;
    private String imageSummaryErrorMessage;
    private Integer imagePromptTokens;
    private Integer imageCompletionTokens;
    private Integer imageTotalTokens;
    private Integer imagePowerAmount;
    private Integer durationMs;
}
