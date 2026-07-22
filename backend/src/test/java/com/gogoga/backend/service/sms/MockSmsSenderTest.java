package com.gogoga.backend.service.sms;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.gogoga.backend.config.SmsProperties;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MockSmsSenderTest {

    @Test
    void shouldLogLoginCodeWithMaskedPhoneAfterMockGatewayAcceptsRequest() {
        SmsProperties properties = new SmsProperties();
        properties.getTencent().setLoginTemplateId("000000");
        MockTemplateSmsGateway gateway = mock(MockTemplateSmsGateway.class);
        when(gateway.send(any())).thenReturn(new TemplateSmsResult(
                "mock-request", "mock-request", "Ok", "Mock accepted"
        ));
        MockSmsSender sender = new MockSmsSender(properties, gateway);
        Logger logger = (Logger) LoggerFactory.getLogger(MockSmsSender.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            sender.sendLoginCode("18512341385", "654321", 5L);
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }

        verify(gateway).send(new TemplateSmsRequest(
                "18512341385", "000000", List.of("654321", "5"), "login"
        ));
        String message = appender.list.get(0).getFormattedMessage();
        assertTrue(message.contains("phone=185****1385"));
        assertTrue(message.contains("code=654321"));
        assertTrue(message.contains("expireMinutes=5"));
        assertFalse(message.contains("18512341385"));
    }
}
