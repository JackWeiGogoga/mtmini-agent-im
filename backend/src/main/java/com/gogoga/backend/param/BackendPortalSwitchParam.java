package com.gogoga.backend.param;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 切换后台工作台参数
 */
@Data
public class BackendPortalSwitchParam {

    @NotBlank(message = "工作台不能为空")
    private String portal;
}
