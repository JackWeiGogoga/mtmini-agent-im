package com.gogoga.backend.entity.mt;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 差评评价匿名群聊跟进记录。
 */
@Data
public class MtCommentImFollowUp {

    private Long id;
    private Long shopId;
    private Long mtCommentId;
    private Long commentId;
    private String status;
    private String failedStage;
    private String errorCode;
    private String errorMessage;

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
    private LocalDateTime generatedAt;

    private LocalDateTime createGroupAttemptedAt;
    private Long groupId;
    private String newGroup;
    private Long groupCtime;
    private Integer groupState;
    private String groupCloseType;
    private String createGroupResponseSummary;
    private LocalDateTime groupCreatedAt;

    private Long messageId;
    private Long messageCts;
    private LocalDateTime sendAttemptedAt;
    private String messageContentHash;
    private String sendResponseSummary;
    private LocalDateTime sentAt;

    private Integer generationDurationMs;
    private Integer createGroupDurationMs;
    private Integer sendDurationMs;
    private Integer durationMs;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
