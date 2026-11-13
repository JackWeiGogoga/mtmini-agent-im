package com.gogoga.backend.service.sms;

import java.util.List;

/**
 * 通用模板短信请求。
 *
 * @param phone          中国大陆手机号（11 位格式）
 * @param templateId     短信模板 ID
 * @param templateParams 模板有序参数
 * @param sceneContext   仅用于供应商会话上下文和审计，不包含敏感信息
 */
public record TemplateSmsRequest(
        String phone,
        String templateId,
        List<String> templateParams,
        String sceneContext
) {

    public TemplateSmsRequest {
        templateParams = templateParams == null ? List.of() : List.copyOf(templateParams);
    }
}
