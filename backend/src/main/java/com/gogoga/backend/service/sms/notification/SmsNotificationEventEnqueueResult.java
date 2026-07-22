package com.gogoga.backend.service.sms.notification;

import com.gogoga.backend.entity.sms.SmsNotificationEvent;

/**
 * 短信事件幂等入队结果。
 *
 * @param event   首次创建或已存在的事件
 * @param created 本次是否首次创建事件
 */
public record SmsNotificationEventEnqueueResult(
        SmsNotificationEvent event,
        boolean created
) {
}
