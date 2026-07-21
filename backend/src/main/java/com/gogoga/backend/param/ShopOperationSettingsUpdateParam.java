package com.gogoga.backend.param;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 门店操作开关更新参数。
 */
@Data
public class ShopOperationSettingsUpdateParam {

    @NotNull(message = "IM 自动回复主开关不能为空")
    private Boolean autoReplyEnabled;
}
