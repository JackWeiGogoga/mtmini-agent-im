package com.gogoga.backend.service.sms;

import com.gogoga.backend.config.SmsProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TencentTemplateSmsGatewayTest {

    private final TencentTemplateSmsGateway gateway = new TencentTemplateSmsGateway(new SmsProperties());

    @Test
    void shouldClassifyTemporaryAndDeterministicProviderErrors() {
        assertTrue(gateway.isRetryableProviderCode("InternalError"));
        assertTrue(gateway.isRetryableProviderCode("RequestLimitExceeded.UinLimitExceeded"));
        assertFalse(gateway.isRetryableProviderCode("FailedOperation.TemplateIncorrectOrUnapproved"));
        assertFalse(gateway.isRetryableProviderCode("InvalidParameterValue.PhoneNumberFormatError"));
    }

    @Test
    void shouldRejectInvalidPhoneBeforeCallingTencent() {
        TemplateSmsException exception = assertThrows(
                TemplateSmsException.class,
                () -> gateway.send(new TemplateSmsRequest("057188888888", "2685915", List.of("x"), "test"))
        );

        assertFalse(exception.isRetryable());
    }
}
