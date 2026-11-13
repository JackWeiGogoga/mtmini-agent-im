package com.gogoga.backend.entity.sms;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 短信通知业务事件。
 */
@Data
public class SmsNotificationEvent {

    private Long id;
    private String scene;
    private String bizKey;
    private Long shopId;
    private String orderId;
    private String templateId;
    private String templateParamsJson;
    private String contextJson;
    private String status;
    private Integer retryCount;
    private Integer maxRetryCount;
    private LocalDateTime availableAt;
    private LocalDateTime claimedAt;
    private LocalDateTime finishedAt;
    private String workerId;
    private String lastError;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
