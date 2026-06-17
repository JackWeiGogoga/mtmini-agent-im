package com.gogoga.backend.param;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UserBehaviorEventReportParam {

    @NotBlank(message = "事件类型不能为空")
    private String eventType;

    private Long shopId;

    private String contextJson;
}
