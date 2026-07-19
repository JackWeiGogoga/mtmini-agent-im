package com.gogoga.backend.entity.mt;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 门店知识文档。
 */
@Data
public class MtKnowledgeDocument {

    private Long id;
    private Long shopId;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private String storageProvider;
    private String objectKey;
    private String parseStatus;
    private String parseError;
    private Integer chunkCount;
    private Boolean enabled;
    private Boolean deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
