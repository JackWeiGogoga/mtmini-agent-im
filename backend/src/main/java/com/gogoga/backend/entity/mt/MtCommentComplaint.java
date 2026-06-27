package com.gogoga.backend.entity.mt;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 评价差评自动投诉记录。
 */
@Data
public class MtCommentComplaint {

    private Long id;
    private Long shopId;
    private Long mtCommentId;
    private Long commentId;
    private String status;
    private Integer reportTypeCode;
    private String reportTypeName;
    private String reportContent;
    private Boolean hasPicture;
    private Boolean typeDefaulted;
    private String typeReason;
    private String modelProvider;
    private String model;
    private String typePromptKey;
    private Integer typePromptVersion;
    private String contentPromptKey;
    private Integer contentPromptVersion;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private Integer powerAmount;
    private String requestPayloadJson;
    private String responsePayloadJson;
    private String errorMessage;
    private LocalDateTime submittedAt;
    private Integer durationMs;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
