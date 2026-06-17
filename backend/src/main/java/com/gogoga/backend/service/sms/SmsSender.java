package com.gogoga.backend.service.sms;

/**
 * 短信发送器
 *
 * @author system
 * @since 2026-03-22
 */
public interface SmsSender {

    /**
     * 发送登录验证码
     *
     * @param phone 手机号
     * @param code 验证码
     * @param expireMinutes 过期分钟数
     */
    void sendLoginCode(String phone, String code, long expireMinutes);
}
