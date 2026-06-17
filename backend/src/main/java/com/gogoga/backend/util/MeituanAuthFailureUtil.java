package com.gogoga.backend.util;

import org.apache.commons.lang3.StringUtils;

/**
 * 美团授权失败判断工具。
 */
public final class MeituanAuthFailureUtil {

    private static final String OP_UNIAUTH_FAILED = "OP_UNIAUTH_FAILED";
    private static final String AUTH_FAILURE_TEXT = "鉴权失败";

    private MeituanAuthFailureUtil() {
    }

    public static boolean isAuthFailure(String code, String message) {
        return isAuthFailureCode(code)
                || StringUtils.contains(message, AUTH_FAILURE_TEXT);
    }

    public static boolean isAuthFailureCode(String code) {
        return StringUtils.equals(code, OP_UNIAUTH_FAILED);
    }
}
