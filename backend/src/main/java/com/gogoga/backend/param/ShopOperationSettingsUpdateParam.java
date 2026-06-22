package com.gogoga.backend.param;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 门店操作开关更新参数。
 */
@Data
public class ShopOperationSettingsUpdateParam {

    @NotNull(message = "AI回复主开关不能为空")
    private Boolean autoReplyEnabled;

    @NotNull(message = "差评处理/申诉开关不能为空")
    private Boolean negativeCommentAppealEnabled;
}
