package com.gogoga.backend.param;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 后台官方评论回复知识参数。
 */
@Data
public class AdminCommentReplyOfficialKnowledgeParam {

    @NotBlank(message = "标题不能为空")
    private String title;

    @NotBlank(message = "评论类型不能为空")
    private String sentiment;

    @NotBlank(message = "回复内容不能为空")
    private String answer;

    @NotNull(message = "启用状态不能为空")
    private Boolean enabled;
}
