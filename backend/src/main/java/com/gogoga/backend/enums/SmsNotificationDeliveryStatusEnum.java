package com.gogoga.backend.enums;

/**
 * 短信逐手机号投递状态。
 */
public enum SmsNotificationDeliveryStatusEnum {
    PENDING,
    PROCESSING,
    SENT,
    CANCELLED,
    DEAD
}
