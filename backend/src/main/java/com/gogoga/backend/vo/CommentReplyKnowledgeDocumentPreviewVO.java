package com.gogoga.backend.vo;

import lombok.Data;

import java.util.List;

/**
 * 评论回复知识文档解析预览。
 */
@Data
public class CommentReplyKnowledgeDocumentPreviewVO {

    private String fileName;
    private String fileType;
    private Long fileSize;
    private Integer chunkCount;
    private List<ChunkPreview> chunks;

    @Data
    public static class ChunkPreview {
        private Integer index;
        private String sentiment;
        private String chunkText;
    }
}
