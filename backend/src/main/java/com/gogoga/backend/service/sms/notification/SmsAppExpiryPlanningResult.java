package com.gogoga.backend.service.sms.notification;

/**
 * 应用到期短信规划汇总。
 */
public record SmsAppExpiryPlanningResult(
        int candidateCount,
        int createdCount,
        int duplicateCount,
        int failedCount
) {
}
