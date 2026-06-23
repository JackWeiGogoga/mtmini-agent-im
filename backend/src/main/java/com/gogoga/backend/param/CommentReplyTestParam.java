package com.gogoga.backend.param;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 评论回复测试参数。
 */
@Data
public class CommentReplyTestParam {

    @NotNull(message = "星级不能为空")
    @Min(value = 0, message = "星级不能小于0")
    @Max(value = 5, message = "星级不能大于5")
    private Integer foodCommentScore;

    @NotBlank(message = "评价内容不能为空")
    private String commentContent;
}
