package com.gogoga.backend.service.sms;

import com.gogoga.backend.config.SmsProperties;
import com.gogoga.backend.util.PhoneUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Mock 短信发送器
 *
 * @author system
 * @since 2026-03-22
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MockSmsSender implements SmsSender {

    private final SmsProperties smsProperties;
    private final MockTemplateSmsGateway templateSmsGateway;

    @Override
    public void sendLoginCode(String phone, String code, long expireMinutes) {
        templateSmsGateway.send(new TemplateSmsRequest(
                phone,
                smsProperties.getTencent().getLoginTemplateId(),
                List.of(code, String.valueOf(expireMinutes)),
                "login"
        ));
        log.info("Mock 登录短信验证码, phone={}, code={}, expireMinutes={}",
                PhoneUtil.maskPhone(phone), code, expireMinutes);
    }
}
