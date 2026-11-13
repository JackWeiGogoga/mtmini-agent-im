package com.gogoga.backend.service.sms.notification;

import com.gogoga.backend.entity.sms.SmsNotificationEvent;
import com.gogoga.backend.enums.SmsNotificationSceneEnum;

/**
 * 短信业务场景准备与发送前复核扩展点。
 */
public interface SmsNotificationSceneHandler {

    boolean supports(SmsNotificationSceneEnum scene);

    SmsScenePreparation prepare(SmsNotificationEvent event);

    /**
     * 投递真正调用供应商前再次复核，主要用于可能续费失效的到期事件。
     */
    default boolean canSend(SmsNotificationEvent event, String recipientPhone) {
        return true;
    }
}
