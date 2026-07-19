package com.gogoga.backend.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 门店知识切片视图。
 */
@Data
public class KnowledgeChunkVO {

    private Long id;
    private Long shopId;
    private Long documentId;
    private String sourceType;
    private String chunkText;
    private Boolean enabled;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
