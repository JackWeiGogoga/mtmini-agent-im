package com.gogoga.backend.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库测试聊天留痕记录（仅管理后台可见）。
 */
@Data
public class ImTestChatLogVO {

    private Long id;
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
    private Integer promptTokens;
    private Integer completionTokens;
    private LocalDateTime createTime;
}
