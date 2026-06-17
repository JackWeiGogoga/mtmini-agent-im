package com.gogoga.backend.exception;

/**
 * 认证异常类
 * 
 * 用于认证失败时抛出的统一异常
 *
 * @author JackWei
 * @since 2026-01-01
 */
public class AuthenticationException extends RuntimeException {

    /**
     * 错误码
     */
    private final String errorCode;

    public AuthenticationException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public AuthenticationException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }

    // 预定义错误码常量
    public static final String INVALID_CREDENTIALS = "INVALID_CREDENTIALS";
    public static final String USER_NOT_FOUND = "USER_NOT_FOUND";
    public static final String USER_DISABLED = "USER_DISABLED";
    public static final String EMAIL_ALREADY_EXISTS = "EMAIL_ALREADY_EXISTS";
    public static final String PHONE_ALREADY_EXISTS = "PHONE_ALREADY_EXISTS";
    public static final String OAUTH_ERROR = "OAUTH_ERROR";
    public static final String INVALID_TOKEN = "INVALID_TOKEN";
    public static final String SESSION_EXPIRED = "SESSION_EXPIRED";
    public static final String PASSWORD_TOO_WEAK = "PASSWORD_TOO_WEAK";
    public static final String INVALID_PHONE = "INVALID_PHONE";
    public static final String INVALID_SMS_CODE = "INVALID_SMS_CODE";
    public static final String SMS_CODE_EXPIRED = "SMS_CODE_EXPIRED";
    public static final String SMS_SEND_TOO_FREQUENT = "SMS_SEND_TOO_FREQUENT";
    public static final String SMS_SEND_LIMIT_EXCEEDED = "SMS_SEND_LIMIT_EXCEEDED";
    public static final String SMS_SEND_ERROR = "SMS_SEND_ERROR";
    public static final String SMS_LOGIN_RETRY_LIMIT_EXCEEDED = "SMS_LOGIN_RETRY_LIMIT_EXCEEDED";
    public static final String SMS_IP_RATE_LIMIT_EXCEEDED = "SMS_IP_RATE_LIMIT_EXCEEDED";
}
