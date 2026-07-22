package com.gogoga.backend.enums;

/**
 * 短信通知事件状态。
 */
public enum SmsNotificationEventStatusEnum {
    PENDING,
    PROCESSING,
    EXPANDED,
    NO_RECIPIENT,
    CANCELLED,
    DEAD
}
