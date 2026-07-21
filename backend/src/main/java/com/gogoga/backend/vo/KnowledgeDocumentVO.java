package com.gogoga.backend.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 门店知识文档视图。
 */
@Data
public class KnowledgeDocumentVO {

    private Long id;
    private Long shopId;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private String parseStatus;
    private String parseError;
    private Integer chunkCount;
    private Boolean enabled;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
