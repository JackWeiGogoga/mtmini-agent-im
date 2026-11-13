package com.gogoga.backend.service.sms;

/**
 * 模板短信供应商受理结果。
 */
public record TemplateSmsResult(
        String providerRequestId,
        String providerSerialNo,
        String providerCode,
        String providerMessage
) {
}
