package com.gogoga.backend.param;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AdminImpersonationParam {

    @NotBlank(message = "目标用户不能为空")
    private String targetUserId;

    private String portal;
}
