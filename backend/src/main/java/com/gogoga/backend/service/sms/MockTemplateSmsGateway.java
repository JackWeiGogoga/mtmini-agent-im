package com.gogoga.backend.service.sms;

import com.gogoga.backend.util.PhoneUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 通用模板短信 Mock 网关。
 */
@Slf4j
@Component
public class MockTemplateSmsGateway implements TemplateSmsGateway {

    @Override
    public TemplateSmsResult send(TemplateSmsRequest request) {
        if (request == null || !PhoneUtil.isValidCnPhone(request.phone())
                || StringUtils.isBlank(request.templateId())) {
            throw new TemplateSmsException(false, "INVALID_REQUEST", "短信手机号或模板 ID 无效");
        }
        String requestId = "mock-" + UUID.randomUUID();
        log.info("Mock 模板短信已受理, phone={}, templateId={}, context={}, requestId={}",
                PhoneUtil.maskPhone(request.phone()), request.templateId(), request.sceneContext(), requestId);
        return new TemplateSmsResult(requestId, requestId, "Ok", "Mock accepted");
    }
}
