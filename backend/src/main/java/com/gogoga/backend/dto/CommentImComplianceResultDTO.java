package com.gogoga.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 评价匿名群聊内容合规结果。
 */
@Data
@AllArgsConstructor
public class CommentImComplianceResultDTO {

    private boolean passed;
    private String category;
}
