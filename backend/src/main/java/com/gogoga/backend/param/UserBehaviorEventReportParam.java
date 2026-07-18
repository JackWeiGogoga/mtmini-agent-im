package com.gogoga.backend.param;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserBehaviorEventReportParam {

    @NotBlank(message = "事件类型不能为空")
    private String eventType;

    @NotBlank(message = "客户端事件ID不能为空")
    @Size(max = 96, message = "客户端事件ID长度不能超过96")
    private String clientEventId;

    @NotBlank(message = "会话ID不能为空")
    @Size(max = 96, message = "会话ID长度不能超过96")
    private String sessionId;

    private Long shopId;

    @Size(max = 4000, message = "事件上下文长度不能超过4000")
    private String contextJson;
}
