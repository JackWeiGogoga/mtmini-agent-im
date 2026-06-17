package com.gogoga.backend.service;

import com.gogoga.backend.config.SmsProperties;
import com.gogoga.backend.exception.AuthenticationException;
import com.gogoga.backend.service.sms.MockSmsSender;
import com.gogoga.backend.service.sms.TencentSmsSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SmsLoginServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private MockSmsSender mockSmsSender;

    @Mock
    private TencentSmsSender tencentSmsSender;

    private SmsLoginService smsLoginService;

    @BeforeEach
    void setUp() {
        SmsProperties smsProperties = new SmsProperties();
        smsProperties.setMockEnabled(true);
        smsProperties.setCodeExpireSeconds(300);
        smsProperties.setSendIntervalSeconds(60);
        smsProperties.setDailySendLimit(20);
        smsProperties.setVerifyFailLimit(5);
        smsProperties.setIpMinuteSendLimit(10);
        smsProperties.setIpDailySendLimit(100);

        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        smsLoginService = new SmsLoginService(
                smsProperties,
                redisTemplate,
                mockSmsSender,
                tencentSmsSender
        );
    }

    @Test
    void shouldKeepCodeAvailableAfterSuccessfulVerification() {
        String phone = "13800138000";
        String code = "123456";
        when(valueOperations.get("auth:sms:verify-fail:" + phone)).thenReturn(null);
        when(valueOperations.get("auth:sms:code:" + phone)).thenReturn(code);

        String verifiedPhone = smsLoginService.verifyLoginCode(phone, code);

        assertEquals(phone, verifiedPhone);
        verify(redisTemplate, never()).delete("auth:sms:code:" + phone);
        verify(redisTemplate).delete("auth:sms:verify-fail:" + phone);
    }

    @Test
    void shouldRejectSendWhenCooldownExistsWithExpectedMessage() {
        String phone = "13800138000";
        when(redisTemplate.hasKey("auth:sms:cooldown:" + phone)).thenReturn(true);

        AuthenticationException exception = assertThrows(
                AuthenticationException.class,
                () -> smsLoginService.sendLoginCode(phone, "127.0.0.1")
        );

        assertEquals(AuthenticationException.SMS_SEND_TOO_FREQUENT, exception.getErrorCode());
        assertEquals("验证码发送过于频繁，请稍后再试", exception.getMessage());
    }
}
