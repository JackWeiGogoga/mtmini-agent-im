package com.gogoga.backend.service.sms;

import com.gogoga.backend.util.PhoneUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 业务短信日志演练网关，只推进投递状态，不调用短信供应商。
 */
@Slf4j
@Component
public class LoggingTemplateSmsGateway implements TemplateSmsGateway {

    @Override
    public TemplateSmsResult send(TemplateSmsRequest request) {
        if (request == null || !PhoneUtil.isValidCnPhone(request.phone())
                || StringUtils.isBlank(request.templateId())) {
            throw new TemplateSmsException(false, "INVALID_REQUEST", "短信手机号或模板 ID 无效");
        }

        String requestId = "log-only-" + UUID.randomUUID();
        log.info("业务模板短信日志演练, phone={}, templateId={}, context={}, paramCount={}, requestId={}",
                PhoneUtil.maskPhone(request.phone()), request.templateId(), request.sceneContext(),
                request.templateParams().size(), requestId);
        return new TemplateSmsResult(
                requestId,
                requestId,
                "LOG_ONLY",
                "Logged without provider call"
        );
    }
}
