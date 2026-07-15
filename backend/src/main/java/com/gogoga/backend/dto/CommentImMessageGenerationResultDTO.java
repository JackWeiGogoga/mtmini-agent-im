package com.gogoga.backend.dto;

import lombok.Data;

/**
 * 差评匿名群聊首条消息生成结果。
 */
@Data
public class CommentImMessageGenerationResultDTO {

    private String messageContent;
    private String messageSource;
    private String fallbackReason;
    private String modelProvider;
    private String model;
    private String promptKey;
    private Integer promptVersion;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private Integer powerAmount;
    private Integer durationMs;
}
