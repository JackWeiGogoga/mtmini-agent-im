package com.gogoga.backend.param;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 评论回复知识片段更新参数。
 */
@Data
public class CommentReplyKnowledgeChunkUpdateParam {

    @NotBlank(message = "评论类型不能为空")
    private String sentiment;
}
