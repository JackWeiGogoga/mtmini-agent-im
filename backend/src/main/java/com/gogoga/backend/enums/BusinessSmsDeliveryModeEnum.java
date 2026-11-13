package com.gogoga.backend.enums;

/**
 * 业务短信最终投递模式。
 */
public enum BusinessSmsDeliveryModeEnum {

    /** 仅记录脱敏日志，不调用短信供应商 */
    LOG_ONLY,

    /** 调用真实短信供应商 */
    REAL
}
