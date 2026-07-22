package com.gogoga.backend.service.sms.notification;

import java.util.List;

/**
 * 场景发送前准备结果。
 */
public record SmsScenePreparation(List<String> recipients, boolean cancelled, String reason) {

    public SmsScenePreparation {
        recipients = recipients == null ? List.of() : List.copyOf(recipients);
    }

    public static SmsScenePreparation ready(List<String> recipients) {
        return new SmsScenePreparation(recipients, false, null);
    }

    public static SmsScenePreparation cancelled(String reason) {
        return new SmsScenePreparation(List.of(), true, reason);
    }
}
