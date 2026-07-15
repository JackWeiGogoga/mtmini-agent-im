package com.gogoga.backend.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理后台评价处理聚合日志。
 */
@Data
public class CommentProcessingLogVO {

    private Long id;
    private Long shopId;
    private String shopName;
    private String epoiId;
    private String poiId;
    private Long commentId;
    private String commentTime;
    private String commentContent;
    private String addComment;
    private String addCommentTime;
    private Integer orderCommentScore;
    private Integer deliveryCommentScore;
    private Integer productScore;
    private Integer packingScore;
    private String sentiment;
    private List<String> imageUrls;

    private String autoReplyStatus;
    private String autoReplyContent;
    private String autoReplySource;
    private String autoReplyModel;
    private Integer autoReplyPromptTokens;
    private Integer autoReplyCompletionTokens;
    private Integer autoReplyPowerAmount;
    private Integer autoReplyDurationMs;
    private LocalDateTime autoReplyAt;
    private String autoReplyError;

    private String analysisStatus;
    private String analysisText;
    private String analysisModel;
    private String analysisPromptKey;
    private Integer analysisPromptVersion;
    private Integer analysisPromptTokens;
    private Integer analysisCompletionTokens;
    private Integer analysisPowerAmount;
    private Integer analysisDurationMs;
    private LocalDateTime analysisAt;
    private String analysisError;

    private String complaintStatus;
    private Integer complaintReportTypeCode;
    private String complaintReportTypeName;
    private String complaintReportContent;
    private Integer complaintPowerAmount;
    private Integer complaintDurationMs;
    private LocalDateTime complaintAt;
    private String complaintError;

    private String imFollowUpStatus;
    private String imFailedStage;
    private String imErrorCode;
    private String imErrorMessage;
    private String imMessageContent;
    private String imMessageSource;
    private String imFallbackReason;
    private String imModel;
    private String imPromptKey;
    private Integer imPromptVersion;
    private Integer imPromptTokens;
    private Integer imCompletionTokens;
    private Integer imTotalTokens;
    private Integer imPowerAmount;
    private LocalDateTime imGeneratedAt;
    private LocalDateTime createGroupAttemptedAt;
    private String groupId;
    private String newGroup;
    private Long groupCtime;
    private Integer groupState;
    private String groupCloseType;
    private LocalDateTime groupCreatedAt;
    private String messageId;
    private Long messageCts;
    private LocalDateTime sendAttemptedAt;
    private LocalDateTime sentAt;
    private Integer imGenerationDurationMs;
    private Integer imCreateGroupDurationMs;
    private Integer imSendDurationMs;
    private Integer imDurationMs;
}
