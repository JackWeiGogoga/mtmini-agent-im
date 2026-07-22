package com.gogoga.backend.service.sms.notification;

import com.gogoga.backend.enums.SmsNotificationSceneEnum;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 持久化短信业务事件入队参数。
 */
public record SmsNotificationEventEnqueueRequest(
        SmsNotificationSceneEnum scene,
        String bizKey,
        Long shopId,
        String orderId,
        String templateId,
        List<String> templateParams,
        Map<String, Object> context,
        LocalDateTime availableAt
) {
}
