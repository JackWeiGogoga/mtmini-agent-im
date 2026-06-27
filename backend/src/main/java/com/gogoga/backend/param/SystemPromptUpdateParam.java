package com.gogoga.backend.param;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 系统提示词更新参数。
 */
@Data
public class SystemPromptUpdateParam {

    @NotBlank(message = "提示词内容不能为空")
    private String content;

    @NotNull(message = "启用状态不能为空")
    private Boolean enabled;

    private String remark;
}
