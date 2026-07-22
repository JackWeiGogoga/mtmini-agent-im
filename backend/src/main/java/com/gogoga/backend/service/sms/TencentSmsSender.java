package com.gogoga.backend.service.sms;

import com.gogoga.backend.exception.AuthenticationException;
import com.gogoga.backend.config.SmsProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 腾讯云短信发送器
 *
 * @author system
 * @since 2026-03-22
 */
@Component
@RequiredArgsConstructor
public class TencentSmsSender implements SmsSender {

    private final SmsProperties smsProperties;
    private final TencentTemplateSmsGateway templateSmsGateway;

    @Override
    public void sendLoginCode(String phone, String code, long expireMinutes) {
        try {
            templateSmsGateway.send(new TemplateSmsRequest(
                    phone,
                    smsProperties.getTencent().getLoginTemplateId(),
                    List.of(code, String.valueOf(expireMinutes)),
                    "login"
            ));
        } catch (TemplateSmsException e) {
            throw new AuthenticationException(AuthenticationException.SMS_SEND_ERROR,
                    "短信发送失败，请稍后重试", e);
        }
    }
}
