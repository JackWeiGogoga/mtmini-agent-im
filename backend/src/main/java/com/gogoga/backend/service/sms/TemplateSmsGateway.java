package com.gogoga.backend.service.sms;

/**
 * 业务无关的模板短信网关。
 */
public interface TemplateSmsGateway {

    TemplateSmsResult send(TemplateSmsRequest request);
}
