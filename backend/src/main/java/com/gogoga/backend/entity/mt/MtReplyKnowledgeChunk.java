package com.gogoga.backend.entity.mt;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 评论回复知识片段。
 */
@Data
public class MtReplyKnowledgeChunk {

    private Long id;
    private Long shopId;
    private String sourceType;
    private Long sourceId;
    private Long itemId;
    private Long documentId;
    private String sentiment;
    private String chunkText;
    private String embeddingProvider;
    private String embeddingModel;
    private Integer embeddingDim;
    private byte[] embeddingBlob;
    private Integer tokenCount;
    private Boolean enabled;
    private Boolean deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
