package com.gogoga.backend.util;

import org.apache.commons.lang3.StringUtils;

/**
 * 手机号工具类
 *
 * @author system
 * @since 2026-03-22
 */
public final class PhoneUtil {

    private PhoneUtil() {
    }

    /**
     * 归一化中国大陆手机号
     *
     * @param phone 原始手机号
     * @return 11 位手机号
     */
    public static String normalizeCnPhone(String phone) {
        if (StringUtils.isBlank(phone)) {
            return null;
        }

        String normalized = phone.trim().replace(" ", "").replace("-", "");
        if (normalized.startsWith("+86")) {
            normalized = normalized.substring(3);
        } else if (normalized.startsWith("86") && normalized.length() == 13) {
            normalized = normalized.substring(2);
        }

        return normalized;
    }

    /**
     * 是否为合法的中国大陆手机号
     *
     * @param phone 原始手机号
     * @return 是否合法
     */
    public static boolean isValidCnPhone(String phone) {
        String normalized = normalizeCnPhone(phone);
        return normalized != null && normalized.matches("^1[3-9]\\d{9}$");
    }

    /**
     * 转换为腾讯云短信要求的 E.164 格式
     *
     * @param phone 中国大陆手机号
     * @return E.164 格式手机号
     */
    public static String toE164CnPhone(String phone) {
        return "+86" + normalizeCnPhone(phone);
    }
}
