package com.gogoga.backend.entity.sms;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 短信通知逐手机号投递记录。
 */
@Data
public class SmsNotificationDelivery {

    private Long id;
    private Long eventId;
    private String recipientPhone;
    private String status;
    private Integer retryCount;
    private Integer maxRetryCount;
    private LocalDateTime availableAt;
    private LocalDateTime claimedAt;
    private LocalDateTime finishedAt;
    private String workerId;
    private String providerRequestId;
    private String providerSerialNo;
    private String providerCode;
    private String providerMessage;
    private String lastError;
    private LocalDateTime sentAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
