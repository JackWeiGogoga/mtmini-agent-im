package com.gogoga.backend.service.sms;

import com.gogoga.backend.config.SmsProperties;
import com.gogoga.backend.exception.AuthenticationException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TencentSmsSenderTest {

    @Test
    void shouldAdaptLoginCodeToGenericTemplateGatewayInOrder() {
        SmsProperties properties = new SmsProperties();
        properties.getTencent().setLoginTemplateId("login-template");
        TencentTemplateSmsGateway gateway = mock(TencentTemplateSmsGateway.class);
        TencentSmsSender sender = new TencentSmsSender(properties, gateway);

        sender.sendLoginCode("13800138000", "123456", 5);

        ArgumentCaptor<TemplateSmsRequest> captor = ArgumentCaptor.forClass(TemplateSmsRequest.class);
        verify(gateway).send(captor.capture());
        assertEquals("login-template", captor.getValue().templateId());
        assertEquals(java.util.List.of("123456", "5"), captor.getValue().templateParams());
    }

    @Test
    void shouldPreserveLoginAuthenticationExceptionSemantics() {
        SmsProperties properties = new SmsProperties();
        properties.getTencent().setLoginTemplateId("login-template");
        TencentTemplateSmsGateway gateway = mock(TencentTemplateSmsGateway.class);
        when(gateway.send(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new TemplateSmsException(true, "InternalError", "temporary"));
        TencentSmsSender sender = new TencentSmsSender(properties, gateway);

        AuthenticationException exception = assertThrows(
                AuthenticationException.class,
                () -> sender.sendLoginCode("13800138000", "123456", 5)
        );

        assertEquals(AuthenticationException.SMS_SEND_ERROR, exception.getErrorCode());
    }
}
