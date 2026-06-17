package com.gogoga.backend.config;

import com.gogoga.backend.exception.AuthenticationException;
import com.gogoga.backend.vo.ResultVO;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

class GlobalExceptionResolverTest {

    private final GlobalExceptionResolver resolver = new GlobalExceptionResolver();

    @Test
    void shouldMapSmsTooFrequentAuthenticationExceptionToBadRequest() {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/auth/sms/send-code");

        ResultVO<Void> result = resolver.handleAuthenticationException(
                new AuthenticationException(
                        AuthenticationException.SMS_SEND_TOO_FREQUENT,
                        "验证码发送过于频繁，请稍后再试"
                ),
                request
        );

        assertEquals(400, result.getCode());
        assertEquals("验证码发送过于频繁，请稍后再试", result.getMsg());
    }

    @Test
    void shouldMapInvalidSmsCodeAuthenticationExceptionToBadRequest() {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/auth/login/sms");

        ResultVO<Void> result = resolver.handleAuthenticationException(
                new AuthenticationException(
                        AuthenticationException.SMS_CODE_EXPIRED,
                        "验证码已过期，请重新获取"
                ),
                request
        );

        assertEquals(400, result.getCode());
        assertEquals("验证码已过期，请重新获取", result.getMsg());
    }
}
