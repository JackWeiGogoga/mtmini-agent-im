package com.gogoga.backend.service.sms;

import com.gogoga.backend.config.SmsProperties;
import com.gogoga.backend.enums.BusinessSmsDeliveryModeEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BusinessTemplateSmsServiceTest {

    @Mock
    private LoggingTemplateSmsGateway loggingGateway;

    @Mock
    private TencentTemplateSmsGateway tencentGateway;

    private SmsProperties properties;
    private TemplateSmsRequest request;

    @BeforeEach
    void setUp() {
        properties = new SmsProperties();
        request = new TemplateSmsRequest(
                "13800138000",
                "2685915",
                List.of("AI智能体消息管理", "7天"),
                "PURCHASE_GUIDE:1"
        );
    }

    @Test
    void shouldUseLoggingGatewayWhenLoginUsesRealSms() {
        properties.setMockEnabled(false);
        properties.getBusiness().setDeliveryMode(BusinessSmsDeliveryModeEnum.LOG_ONLY);
        TemplateSmsResult expected = new TemplateSmsResult(
                "log-only-1", "log-only-1", "LOG_ONLY", "Logged without provider call"
        );
        when(loggingGateway.send(request)).thenReturn(expected);

        TemplateSmsResult actual = service().send(request);

        assertSame(expected, actual);
        verify(loggingGateway).send(request);
        verify(tencentGateway, never()).send(request);
    }

    @Test
    void shouldUseTencentGatewayInRealModeEvenWhenLoginUsesMockSms() {
        properties.setMockEnabled(true);
        properties.getBusiness().setDeliveryMode(BusinessSmsDeliveryModeEnum.REAL);
        TemplateSmsResult expected = new TemplateSmsResult("request-1", "serial-1", "Ok", "send success");
        when(tencentGateway.send(request)).thenReturn(expected);

        TemplateSmsResult actual = service().send(request);

        assertSame(expected, actual);
        verify(tencentGateway).send(request);
        verify(loggingGateway, never()).send(request);
    }

    private BusinessTemplateSmsService service() {
        return new BusinessTemplateSmsService(properties, loggingGateway, tencentGateway);
    }
}
