package com.gogoga.backend.exception;

/**
 * 门店算力不足异常。
 */
public class PowerInsufficientException extends BusinessException {

    public static final String REASON_CODE = "POWER_BALANCE_NOT_ENOUGH";
    public static final String DEFAULT_MESSAGE = "当前算力余额不足";

    public PowerInsufficientException() {
        super(400, DEFAULT_MESSAGE);
    }

    public PowerInsufficientException(String message, Throwable cause) {
        super(500, message, cause);
    }
}
