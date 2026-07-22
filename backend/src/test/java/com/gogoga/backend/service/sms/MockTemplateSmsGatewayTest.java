package com.gogoga.backend.service.sms;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MockTemplateSmsGatewayTest {

    @Test
    void shouldReturnAcceptedResultWithoutExposingTemplateParameters() {
        TemplateSmsResult result = new MockTemplateSmsGateway().send(
                new TemplateSmsRequest("13800138000", "2685915", List.of("secret"), "PURCHASE_GUIDE:1")
        );

        assertEquals("Ok", result.providerCode());
        assertNotNull(result.providerRequestId());
    }
}
