package com.gogoga.backend.vo;

import lombok.Data;

/**
 * 官方回复文案导入结果。
 */
@Data
public class CommentReplyOfficialTemplateImportVO {

    private Integer totalCount;
    private Integer createdCount;
    private Integer updatedCount;
    private Integer duplicateCount;
    private Integer skippedCount;
    private Integer goodCount;
    private Integer neutralCount;
    private Integer badCount;
}
