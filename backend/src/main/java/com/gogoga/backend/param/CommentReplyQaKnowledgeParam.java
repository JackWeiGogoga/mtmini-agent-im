package com.gogoga.backend.param;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 商户手工 QA 知识参数。
 */
@Data
public class CommentReplyQaKnowledgeParam {

    @NotBlank(message = "标题不能为空")
    private String title;

    @NotBlank(message = "评论类型不能为空")
    private String sentiment;

    @NotBlank(message = "问题不能为空")
    private String question;

    @NotBlank(message = "回答不能为空")
    private String answer;

    @NotNull(message = "启用状态不能为空")
    private Boolean enabled;
}
