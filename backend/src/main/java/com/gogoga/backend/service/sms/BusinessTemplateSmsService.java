package com.gogoga.backend.service.sms;

import com.gogoga.backend.config.SmsProperties;
import com.gogoga.backend.enums.BusinessSmsDeliveryModeEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 业务短信统一入口，根据独立投递模式选择日志演练或真实网关。
 */
@Service
@RequiredArgsConstructor
public class BusinessTemplateSmsService {

    private final SmsProperties smsProperties;
    private final LoggingTemplateSmsGateway loggingTemplateSmsGateway;
    private final TencentTemplateSmsGateway tencentTemplateSmsGateway;

    public TemplateSmsResult send(TemplateSmsRequest request) {
        TemplateSmsGateway gateway = smsProperties.getBusiness().getDeliveryMode()
                == BusinessSmsDeliveryModeEnum.REAL
                ? tencentTemplateSmsGateway
                : loggingTemplateSmsGateway;
        return gateway.send(request);
    }
}
