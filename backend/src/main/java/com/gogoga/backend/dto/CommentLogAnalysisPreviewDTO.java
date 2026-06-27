package com.gogoga.backend.dto;

import lombok.Data;

/**
 * 评论日志分析预览结果。
 */
@Data
public class CommentLogAnalysisPreviewDTO {

    private String analysisText;

    private String model;

    private Integer promptTokens;

    private Integer completionTokens;

    private Integer totalTokens;

    private Integer estimatedPowerAmount;

    private Integer durationMs;

    private String errorMessage;
}
