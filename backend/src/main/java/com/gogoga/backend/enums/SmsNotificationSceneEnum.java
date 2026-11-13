package com.gogoga.backend.enums;

import lombok.Getter;

/**
 * 业务短信通知场景。
 */
@Getter
public enum SmsNotificationSceneEnum {

    PURCHASE_GUIDE("购买应用后引导客服"),
    POWER_EXHAUSTED("推广算力耗尽"),
    APP_EXPIRED("应用到期");

    private final String desc;

    SmsNotificationSceneEnum(String desc) {
        this.desc = desc;
    }
}
