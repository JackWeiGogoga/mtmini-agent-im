package com.gogoga.backend.param;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 评论回复测试参数。
 */
@Data
public class CommentReplyTestParam {

    @NotNull(message = "商品评分不能为空")
    @Min(value = 0, message = "商品评分不能小于0")
    @Max(value = 5, message = "商品评分不能大于5")
    private Integer productScore;

    @NotNull(message = "包装评分不能为空")
    @Min(value = 0, message = "包装评分不能小于0")
    @Max(value = 5, message = "包装评分不能大于5")
    private Integer packingScore;

    @NotNull(message = "口味评分不能为空")
    @Min(value = 0, message = "口味评分不能小于0")
    @Max(value = 5, message = "口味评分不能大于5")
    private Integer orderCommentScore;

    @NotNull(message = "配送评分不能为空")
    @Min(value = 0, message = "配送评分不能小于0")
    @Max(value = 5, message = "配送评分不能大于5")
    private Integer deliveryCommentScore;

    private String commentLabels;

    private String overDeliveryTimeDesc;

    @NotBlank(message = "评价内容不能为空")
    private String commentContent;

    private String addComment;

    private List<String> commentPictures;

    private List<String> temporaryImageObjectKeys;

    private String imageSummaryText;
}
