package com.gogoga.backend.service.sms;

import lombok.Getter;

/**
 * 模板短信发送异常，明确区分是否值得重试。
 */
@Getter
public class TemplateSmsException extends RuntimeException {

    private final boolean retryable;
    private final String providerCode;
    private final String providerMessage;

    public TemplateSmsException(boolean retryable, String providerCode, String providerMessage) {
        super(providerMessage);
        this.retryable = retryable;
        this.providerCode = providerCode;
        this.providerMessage = providerMessage;
    }

    public TemplateSmsException(boolean retryable, String providerCode, String providerMessage, Throwable cause) {
        super(providerMessage, cause);
        this.retryable = retryable;
        this.providerCode = providerCode;
        this.providerMessage = providerMessage;
    }
}
