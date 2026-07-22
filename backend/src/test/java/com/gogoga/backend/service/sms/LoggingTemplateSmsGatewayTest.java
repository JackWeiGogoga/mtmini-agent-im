package com.gogoga.backend.service.sms;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoggingTemplateSmsGatewayTest {

    private final LoggingTemplateSmsGateway gateway = new LoggingTemplateSmsGateway();

    @Test
    void shouldReturnDistinctLogOnlyAuditResult() {
        TemplateSmsResult result = gateway.send(new TemplateSmsRequest(
                "13800138000",
                "2685915",
                List.of("AI智能体消息管理", "7天"),
                "PURCHASE_GUIDE:1"
        ));

        assertEquals("LOG_ONLY", result.providerCode());
        assertEquals("Logged without provider call", result.providerMessage());
        assertNotNull(result.providerRequestId());
        assertTrue(result.providerRequestId().startsWith("log-only-"));
    }

    @Test
    void shouldRejectInvalidPhoneWithoutCallingAnyProvider() {
        TemplateSmsException exception = assertThrows(TemplateSmsException.class, () -> gateway.send(
                new TemplateSmsRequest("010-12345678", "2685915", List.of(), "PURCHASE_GUIDE:1")
        ));

        assertEquals("INVALID_REQUEST", exception.getProviderCode());
    }
}
