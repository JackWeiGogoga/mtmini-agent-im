package com.gogoga.backend.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 评论回复知识片段视图。
 */
@Data
public class CommentReplyKnowledgeChunkVO {

    private Long id;
    private Long shopId;
    private Long documentId;
    private String sourceType;
    private String sentiment;
    private String chunkText;
    private Boolean enabled;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
