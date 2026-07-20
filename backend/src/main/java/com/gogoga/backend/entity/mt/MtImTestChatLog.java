package com.gogoga.backend.entity.mt;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库测试聊天审计与限流记录。仅管理后台可见，不参与线上顾客会话与统计。
 */
@Data
public class MtImTestChatLog {

    private Long id;
    private Long shopId;
    private String sessionId;
    private String operatorUserId;
    private String operatorName;
    private String userMessage;
    private String aiReply;
    private String userMessageCategory;
    private Boolean ragUsed;
    private Integer ragChunkCount;
    private Double ragMaxScore;
    private Integer rewriteCount;
    private Boolean blocked;
    private String modelProvider;
    private String modelName;
    private Integer promptTokens;
    private Integer completionTokens;
    private LocalDateTime createTime;
}
