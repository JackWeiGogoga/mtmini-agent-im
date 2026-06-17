package com.gogoga.backend.service.sms;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Mock 短信发送器
 *
 * @author system
 * @since 2026-03-22
 */
@Slf4j
@Component
public class MockSmsSender implements SmsSender {

    @Override
    public void sendLoginCode(String phone, String code, long expireMinutes) {
        log.info("Mock 短信发送成功, phone={}, code={}, expireMinutes={}",
                phone, code, expireMinutes);
    }
}
